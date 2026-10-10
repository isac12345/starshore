package app.starshore

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

class Cfg {
    var codec = 0   // 0 = HEVC, 1 = H.264
    var res = 1     // index RES
    var fps = 0     // index FPS
    var br = 3.5f   // Mbps
    var aud = 0     // index AUD

    fun resValue(): Int = RES[res]
    fun fpsValue(): Int = FPS[fps]
    fun audValue(): Int = AUD[aud]

    fun set(c: Int, r: Int, f: Int, b: Float, a: Int) {
        codec = c; res = r; fps = f; br = b; aud = a
    }

    fun preset(i: Int) {
        when (i) {
            0 -> set(0, 0, 0, 6f, 0)      // Jernih
            1 -> set(0, 1, 0, 3.5f, 0)    // Seimbang
            else -> set(0, 2, 2, 1.5f, 1) // Kecil
        }
    }

    fun matches(i: Int): Boolean {
        val t = Cfg()
        t.preset(i)
        return t.codec == codec && t.res == res && t.fps == fps && t.br == br && t.aud == aud
    }

    companion object {
        val RES = intArrayOf(0, 1080, 720, 480)
        val FPS = intArrayOf(0, 60, 30, 24)
        val AUD = intArrayOf(128, 64, 0)
    }
}

object Compressor {
    class Job(
        private val ctx: Context,
        private val src: File,
        private val out: File,
        private val cfg: Cfg,
        private val w: Int,
        private val h: Int,
        private val tick: (Int) -> Unit,
        private val done: (String?) -> Unit
    ) {
        private val main = Handler(Looper.getMainLooper())
        private val holder = ProgressHolder()
        private var tr: Transformer? = null
        private var finished = false

        private fun even(v: Int): Int = if (v % 2 == 0) v else v + 1

        fun start() {
            val vfx = ArrayList<Effect>()
            val target = cfg.resValue()
            val sh = minOf(w, h)
            if (target > 0 && w > 0 && h > 0 && sh > target) {
                val s = target.toFloat() / sh
                val nw = even(Math.round(w * s))
                val nh = even(Math.round(h * s))
                vfx.add(Presentation.createForWidthAndHeight(nw, nh, Presentation.LAYOUT_SCALE_TO_FIT))
            }
            if (cfg.fpsValue() > 0) {
                vfx.add(FrameDropEffect.createDefaultFrameDropEffect(cfg.fpsValue().toFloat()))
            }

            val vs = VideoEncoderSettings.Builder().setBitrate((cfg.br * 1_000_000f).toInt()).build()
            val efb = DefaultEncoderFactory.Builder(ctx).setRequestedVideoEncoderSettings(vs)
            if (cfg.audValue() > 0) {
                efb.setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder().setBitrate(cfg.audValue() * 1000).build()
                )
            }

            val t = Transformer.Builder(ctx)
                .setVideoMimeType(if (cfg.codec == 0) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                .setEncoderFactory(efb.build())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finish(null)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        finish(exportException.message ?: "gagal")
                    }
                })
                .build()
            tr = t

            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(src)))
                .setRemoveAudio(cfg.audValue() == 0)
                .setEffects(Effects(listOf<AudioProcessor>(), vfx))
                .build()
            try {
                t.start(item, out.absolutePath)
                poll()
            } catch (e: Exception) {
                finish(e.message ?: "gagal mulai")
            }
        }

        private fun poll() {
            if (finished) return
            val t = tr ?: return
            val st = t.getProgress(holder)
            if (st == Transformer.PROGRESS_STATE_AVAILABLE) tick(holder.progress)
            main.postDelayed({ poll() }, 500)
        }

        private fun finish(err: String?) {
            if (finished) return
            finished = true
            main.removeCallbacksAndMessages(null)
            done(err)
        }

        fun cancel() {
            finished = true
            main.removeCallbacksAndMessages(null)
            try { tr?.cancel() } catch (e: Exception) { }
        }
    }
}
