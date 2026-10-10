package app.starshore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
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

class Vid(val f: File) {
    var sel = false
    var durMs = 0L
    var w = 0
    var h = 0
    var bps = 0L
    var thumb: Bitmap? = null
    var small: Bitmap? = null
    fun probed(): Boolean = durMs > 0L && w > 0 && h > 0
}

/** Cek encoder HEVC hardware. Kalau tidak ada, HEVC jatuh ke software dan sangat lambat. */
object Hw {
    val hevc: Boolean by lazy {
        try {
            var ok = false
            for (ci in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (!ci.isEncoder) continue
                var has = false
                for (t in ci.supportedTypes) if (t.equals("video/hevc", true)) has = true
                if (!has) continue
                val sw = if (Build.VERSION.SDK_INT >= 29) ci.isSoftwareOnly
                else (ci.name.startsWith("OMX.google.") || ci.name.startsWith("c2.android."))
                if (!sw) ok = true
            }
            ok
        } catch (e: Exception) {
            false
        }
    }
}

class Cfg {
    var codec = 1      // 0 = HEVC, 1 = H.264
    var res = 0        // index RES
    var fps = 0        // index FPS
    var mode = 0       // 0 otomatis (% dari asli), 1 target ukuran, 2 manual
    var pct = 55
    var targetMb = 25
    var br = 3.5f      // Mbps, mode manual
    var aud = 0        // 0 asli, 1 64k, 2 tanpa

    fun resValue(): Int = RES[res]
    fun fpsValue(): Int = FPS[fps]
    fun audBps(): Long = when (aud) { 1 -> 64_000L; 2 -> 0L; else -> 128_000L }

    fun copy(): Cfg {
        val c = Cfg()
        c.codec = codec; c.res = res; c.fps = fps; c.mode = mode
        c.pct = pct; c.targetMb = targetMb; c.br = br; c.aud = aud
        return c
    }

    fun preset(i: Int) {
        mode = 0
        when (i) {
            0 -> { pct = 75; res = 0; fps = 0; aud = 0 }   // Jernih
            1 -> { pct = 55; res = 1; fps = 0; aud = 0 }   // Seimbang
            2 -> { pct = 35; res = 2; fps = 2; aud = 1 }   // Kecil
            else -> { pct = 30; res = 1; fps = 2; aud = 0 } // Rekaman layar
        }
    }

    fun matches(i: Int): Boolean {
        val t = Cfg()
        t.preset(i)
        return mode == 0 && t.pct == pct && t.res == res && t.fps == fps && t.aud == aud
    }

    private fun srcVideoBps(v: Vid): Long {
        val total = if (v.bps > 0L) v.bps else if (v.durMs > 0L) v.f.length() * 8000L / v.durMs else 4_000_000L
        return maxOf(200_000L, total - 128_000L)
    }

    private fun resFactor(v: Vid): Double {
        val t = resValue()
        val sh = minOf(v.w, v.h)
        if (t <= 0 || sh <= 0 || sh <= t) return 1.0
        val s = t.toDouble() / sh
        return s * s
    }

    /** Bitrate video target. Selalu di bawah bitrate asli supaya file tidak membesar. */
    fun videoBps(v: Vid): Long {
        val src = srcVideoBps(v)
        val durS = maxOf(1.0, v.durMs / 1000.0)
        var bps = when (mode) {
            1 -> (targetMb * 1_000_000.0 * 0.93 * 8.0 / durS - audBps()).toLong()
            2 -> (br * 1_000_000f).toLong()
            else -> (src * pct / 100.0 * Math.pow(resFactor(v), 0.75)).toLong()
        }
        val cap = if (mode == 2) (src * 0.9).toLong() else (src * 0.95).toLong()
        if (bps > cap) bps = cap
        if (bps < 150_000L) bps = 150_000L
        return bps
    }

    fun estBytes(v: Vid): Long = (v.durMs / 1000.0 * (videoBps(v) + audBps()) / 8.0).toLong()

    companion object {
        val RES = intArrayOf(0, 1080, 720, 480)
        val FPS = intArrayOf(0, 60, 30, 24)
    }
}

object Notif {
    private const val CH = "starshore"

    fun build(ctx: Context, text: String, pct: Int): Notification {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val b: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Kompres video", NotificationManager.IMPORTANCE_LOW))
            b = Notification.Builder(ctx, CH)
        } else {
            b = Notification.Builder(ctx)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        b.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Starshore")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, false)
            .setContentIntent(pi)
        return b.build()
    }
}

/** Service kosong yang cuma menjaga proses tetap hidup selama antrean kompres jalan. */
class KeepAlive : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notif.build(this, "Mengompres video...", 0)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }
        return START_NOT_STICKY
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
        private val bps: Long,
        private val clipStart: Long,
        private val clipEnd: Long,
        private val tick: (Int) -> Unit,
        private val done: (String?, String?) -> Unit
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

            val vs = VideoEncoderSettings.Builder().setBitrate(bps.toInt()).build()
            val efb = DefaultEncoderFactory.Builder(ctx).setRequestedVideoEncoderSettings(vs)
            // audio "Asli"/64k: default encoder (AudioEncoderSettings tak ada di Media3 1.4.1)

            val useHevc = cfg.codec == 0 && Hw.hevc
            val t = Transformer.Builder(ctx)
                .setVideoMimeType(if (useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                .setEncoderFactory(efb.build())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finish(null, exportResult.videoEncoderName)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        finish(exportException.message ?: "gagal", null)
                    }
                })
                .build()
            tr = t

            val mi = if (clipEnd > 0L) {
                MediaItem.Builder()
                    .setUri(Uri.fromFile(src))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clipStart)
                            .setEndPositionMs(clipEnd)
                            .build()
                    )
                    .build()
            } else {
                MediaItem.fromUri(Uri.fromFile(src))
            }
            val item = EditedMediaItem.Builder(mi)
                .setRemoveAudio(cfg.aud == 2)
                .setEffects(Effects(listOf<AudioProcessor>(), vfx))
                .build()
            try {
                t.start(item, out.absolutePath)
                poll()
            } catch (e: Exception) {
                finish(e.message ?: "gagal mulai", null)
            }
        }

        private fun poll() {
            if (finished) return
            val t = tr ?: return
            val st = t.getProgress(holder)
            if (st == Transformer.PROGRESS_STATE_AVAILABLE) tick(holder.progress)
            main.postDelayed({ poll() }, 500)
        }

        private fun finish(err: String?, enc: String?) {
            if (finished) return
            finished = true
            main.removeCallbacksAndMessages(null)
            done(err, enc)
        }

        fun cancel() {
            finished = true
            main.removeCallbacksAndMessages(null)
            try { tr?.cancel() } catch (e: Exception) { }
        }
    }
}

/** Antrean kompres. Hidup di proses (bukan di Activity), jadi tetap jalan kalau layar dimatikan. */
object Queue {
    @Volatile var running = false
    var status = ""
    var percent = 0
    var eta = ""
    var stamp = 0L
    val log = ArrayList<String>()
    val done = ArrayList<Pair<File, File>>()
    var onChange: (() -> Unit)? = null

    private lateinit var app: Context
    private var cfg = Cfg()
    private var list: List<Vid> = emptyList()
    private var idx = 0
    private var job: Compressor.Job? = null
    private var t0 = 0L
    private var lastN = 0L
    private var attempt = 0
    private var bpsOverride = 0L
    private var curBps = 0L
    private var curOut: File? = null
    private var wl: PowerManager.WakeLock? = null

    private fun changed() { onChange?.invoke() }

    fun start(ctx: Context, vids: List<Vid>, c: Cfg) {
        if (running) return
        app = ctx.applicationContext
        cfg = c.copy()
        list = ArrayList(vids)
        idx = 0
        done.clear()
        log.clear()
        running = true
        try {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "starshore:queue")
            wl?.acquire(3 * 60 * 60 * 1000L)
        } catch (e: Exception) { }
        try {
            val i = Intent(app, KeepAlive::class.java)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
        } catch (e: Exception) { }
        next()
    }

    private fun stopAll() {
        running = false
        try { wl?.release() } catch (e: Exception) { }
        wl = null
        try { app.stopService(Intent(app, KeepAlive::class.java)) } catch (e: Exception) { }
    }

    private fun next() {
        if (idx >= list.size) {
            finish()
            return
        }
        val v = list[idx]
        val out = File(v.f.parentFile, v.f.nameWithoutExtension + "_starshore.mp4")
        out.delete()
        curOut = out
        attempt = 0
        bpsOverride = 0L
        run(v, out)
    }

    private fun run(v: Vid, out: File) {
        t0 = SystemClock.elapsedRealtime()
        status = "(${idx + 1}/${list.size}) ${v.f.name}"
        percent = 0
        eta = ""
        changed()
        curBps = if (bpsOverride > 0L) bpsOverride else cfg.videoBps(v)
        val j = Compressor.Job(app, v.f, out, cfg, v.w, v.h, curBps, 0L, 0L, { p -> tick(v, p) }, { err, enc -> end(v, out, err, enc) })
        job = j
        j.start()
    }

    private fun tick(v: Vid, p: Int) {
        percent = p
        val now = SystemClock.elapsedRealtime()
        val el = (now - t0) / 1000.0
        eta = if (p >= 3) mmss((el * (100 - p) / p).toLong()) else ""
        status = "(${idx + 1}/${list.size}) ${v.f.name}: $p%"
        if (now - lastN > 1000L) {
            lastN = now
            try {
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1, Notif.build(app, status, p))
            } catch (e: Exception) { }
        }
        changed()
    }

    private fun end(v: Vid, out: File, err: String?, enc: String?) {
        if (!running) return
        job = null
        val name = v.f.name
        if (err != null || !out.exists()) {
            out.delete()
            log.add("$name: gagal (${err ?: "tidak ada hasil"})")
            idx++
            next()
            return
        }
        val size = v.f.length()
        val o = out.length()
        if (cfg.mode == 1 && attempt < 1 && o > cfg.targetMb * 1_000_000L * 1.03 && o < size) {
            attempt++
            val nb = (curBps * (cfg.targetMb * 1_000_000.0 * 0.95 / o)).toLong()
            bpsOverride = maxOf(150_000L, nb)
            out.delete()
            run(v, out)
            return
        }
        if (o >= size) {
            out.delete()
            log.add("$name: dilewati, hasil tidak lebih kecil (${fmt(o)} dari ${fmt(size)})")
        } else {
            done.add(Pair(v.f, out))
            val sec = maxOf(0.1, (SystemClock.elapsedRealtime() - t0) / 1000.0)
            val speed = "%.1f".format(v.durMs / 1000.0 / sec)
            log.add("$name: ${fmt(size)} jadi ${fmt(o)}, ${speed}x real-time, ${enc ?: "?"}")
            try { MediaScannerConnection.scanFile(app, arrayOf(out.path), null, null) } catch (e: Exception) { }
        }
        idx++
        next()
    }

    private fun finish() {
        stopAll()
        percent = 0
        eta = ""
        status = if (done.isEmpty()) "Selesai. Tidak ada video yang jadi lebih kecil." else "Selesai: ${done.size} video."
        stamp = SystemClock.elapsedRealtime()
        changed()
    }

    fun cancel() {
        if (!running) return
        stopAll()
        job?.cancel()
        job = null
        curOut?.delete()
        percent = 0
        eta = ""
        status = "Dibatalkan."
        stamp = SystemClock.elapsedRealtime()
        changed()
    }
}
