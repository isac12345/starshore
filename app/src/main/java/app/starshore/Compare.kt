package app.starshore

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

private const val MATCH = -1

/** Satu video, kiri = sebelum, kanan = sesudah. Garis putih bisa digeser. Dua pemutar jalan sinkron. */
class CompareView(ctx: Context) : FrameLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val after = TextureView(ctx)
    private val beforeWrap = FrameLayout(ctx)
    private val before = TextureView(ctx)
    private val line = View(ctx)
    private val knob = TextView(ctx)
    private val tagL = TextView(ctx)
    private val tagR = TextView(ctx)
    private val h = Handler(Looper.getMainLooper())

    private var mpA: MediaPlayer? = null   // sesudah (suara dari sini)
    private var mpB: MediaPlayer? = null   // sebelum (dibisukan)
    private var sA: Surface? = null
    private var sB: Surface? = null
    private var pA = ""
    private var pB = ""
    private var offset = 0
    private var want = false
    private var ready = 0
    private var playing = false
    private var cw = 1
    private var ch = 1
    private var frac = 0.5f

    private val syncRun = object : Runnable {
        override fun run() {
            val a = mpA
            val b = mpB
            if (a != null && b != null) {
                try {
                    val p = a.currentPosition
                    val bp = b.currentPosition - offset
                    if (Math.abs(bp - p) > 300) b.seekTo(offset + p)
                } catch (e: Exception) { }
            }
            h.postDelayed(this, 400)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(after, FrameLayout.LayoutParams(MATCH, MATCH))
        beforeWrap.addView(before, FrameLayout.LayoutParams(1, 1))
        addView(beforeWrap, FrameLayout.LayoutParams(1, 1))
        line.setBackgroundColor(Color.WHITE)
        addView(line, FrameLayout.LayoutParams(2, 1))
        knob.text = "<>"
        knob.setTextColor(Color.parseColor("#0B1536"))
        knob.gravity = Gravity.CENTER
        knob.textSize = 12f
        val g = GradientDrawable()
        g.setColor(Color.WHITE)
        g.shape = GradientDrawable.OVAL
        knob.background = g
        addView(knob, FrameLayout.LayoutParams(1, 1))
        for (t in listOf(tagL, tagR)) {
            t.setTextColor(Color.WHITE)
            t.textSize = 12f
            t.setPadding((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#B3060A1C"))
            bg.cornerRadius = 12 * d
            t.background = bg
        }
        tagL.text = "Sebelum"
        tagR.text = "Sesudah"
        val lpL = FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP)
        lpL.setMargins((8 * d).toInt(), (8 * d).toInt(), 0, 0)
        val lpR = FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP)
        lpR.setMargins(0, (8 * d).toInt(), (8 * d).toInt(), 0)
        addView(tagL, lpL)
        addView(tagR, lpR)

        after.surfaceTextureListener = listener(true)
        before.surfaceTextureListener = listener(false)

        setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                parent?.requestDisallowInterceptTouchEvent(true)
                frac = (e.x / cw).coerceIn(0f, 1f)
                applyFrac()
            }
            true
        }
    }

    private fun listener(isAfter: Boolean): TextureView.SurfaceTextureListener {
        return object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                if (isAfter) sA = Surface(st) else sB = Surface(st)
                tryStart()
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                if (isAfter) {
                    sA?.release()
                    sA = null
                } else {
                    sB?.release()
                    sB = null
                }
                stopPlayers()
                want = pA.isNotEmpty()
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun applyFrac() {
        val x = (cw * frac).toInt()
        beforeWrap.layoutParams = FrameLayout.LayoutParams(maxOf(1, x), ch)
        val lp = FrameLayout.LayoutParams(maxOf(1, (2 * d).toInt()), ch)
        lp.leftMargin = x - d.toInt()
        line.layoutParams = lp
        val k = (34 * d).toInt()
        val kp = FrameLayout.LayoutParams(k, k)
        kp.leftMargin = x - k / 2
        kp.topMargin = ch / 2 - k / 2
        knob.layoutParams = kp
    }

    fun show(pathBefore: String, pathAfter: String, vw: Int, vh: Int, maxW: Int, maxH: Int, offsetMs: Int) {
        release()
        pB = pathBefore
        pA = pathAfter
        offset = offsetMs
        val w0 = if (vw > 0) vw else 16
        val h0 = if (vh > 0) vh else 9
        val sc = minOf(maxW.toFloat() / w0, maxH.toFloat() / h0)
        cw = maxOf(1, (w0 * sc).toInt())
        ch = maxOf(1, (h0 * sc).toInt())
        val lp = LinearLayout.LayoutParams(cw, ch)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = lp
        before.layoutParams = FrameLayout.LayoutParams(cw, ch)
        frac = 0.5f
        applyFrac()
        want = true
        tryStart()
    }

    private fun tryStart() {
        val a = sA
        val b = sB
        if (!want || a == null || b == null) return
        want = false
        ready = 0
        mpA = make(pA, a, false)
        mpB = make(pB, b, true)
    }

    private fun make(path: String, s: Surface, isBefore: Boolean): MediaPlayer {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(path)
            mp.setSurface(s)
            mp.isLooping = true
            if (isBefore) mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                if (isBefore && offset > 0) it.seekTo(offset)
                ready++
                if (ready == 2) startBoth()
            }
            mp.prepareAsync()
        } catch (e: Exception) { }
        return mp
    }

    private fun startBoth() {
        try {
            mpA?.start()
            mpB?.start()
            playing = true
        } catch (e: Exception) { }
        h.removeCallbacks(syncRun)
        h.postDelayed(syncRun, 400)
    }

    fun toggle(): Boolean {
        try {
            if (playing) {
                mpA?.pause()
                mpB?.pause()
                playing = false
            } else if (mpA != null) {
                mpA?.start()
                mpB?.start()
                playing = true
            }
        } catch (e: Exception) { }
        return playing
    }

    fun pause() {
        try {
            mpA?.pause()
            mpB?.pause()
        } catch (e: Exception) { }
        playing = false
    }

    fun duration(): Int = try { mpA?.duration ?: 0 } catch (e: Exception) { 0 }

    fun position(): Int = try { mpA?.currentPosition ?: 0 } catch (e: Exception) { 0 }

    fun seek(ms: Int) {
        try {
            mpA?.seekTo(ms)
            mpB?.seekTo(offset + ms)
        } catch (e: Exception) { }
    }

    private fun stopPlayers() {
        h.removeCallbacks(syncRun)
        try { mpA?.release() } catch (e: Exception) { }
        try { mpB?.release() } catch (e: Exception) { }
        mpA = null
        mpB = null
        playing = false
    }

    fun release() {
        stopPlayers()
        want = false
        pA = ""
        pB = ""
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
