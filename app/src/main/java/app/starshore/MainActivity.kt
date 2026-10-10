package app.starshore

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.io.File
import java.util.concurrent.Executors

private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val thumbIo = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private val cInk = Color.parseColor("#060A1C")
    private val cPanel = Color.parseColor("#0E1A42")
    private val cStar = Color.parseColor("#5B8CFF")
    private val cIce = Color.parseColor("#D6E9FF")
    private val cMute = Color.parseColor("#8FA4D4")
    private val cLine = Color.parseColor("#1F2F66")
    private val cOk = Color.parseColor("#6FE3C1")

    private val cfg = Cfg()
    private var busy = false
    private var previewing = false
    private var items: List<Item> = emptyList()
    private var corpsePaths: List<String> = emptyList()
    private var vids: List<Vid> = emptyList()
    private var lastSel: Vid? = null
    private var ana: Analysis? = null
    private var cleanMsg = ""
    private var cleanOk = false
    private var shownStamp = 0L
    private var seeking = false
    private var previewJob: Compressor.Job? = null

    private val tabBtns = ArrayList<Button>()
    private lateinit var modeTv: TextView
    private lateinit var chipTv: TextView
    private lateinit var chipDot: View
    private lateinit var permBtn: Button
    private lateinit var cleanBox: LinearLayout
    private lateinit var compBox: LinearLayout
    private lateinit var anaBox: LinearLayout
    private lateinit var settingsBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var infoTv: TextView
    private lateinit var estTv: TextView
    private lateinit var resCard: LinearLayout
    private lateinit var cmpHolder: LinearLayout
    private lateinit var cmp: CompareView
    private lateinit var ppBtn: Button
    private lateinit var seek: SeekBar
    private lateinit var posTv: TextView
    private lateinit var chipsBox: LinearLayout
    private lateinit var cinfo: TextView
    private lateinit var decideBox: LinearLayout
    private lateinit var statusTv: TextView
    private lateinit var etaTv: TextView
    private lateinit var logTv: TextView
    private lateinit var progBar: ProgressBar
    private lateinit var goBtn: Button
    private lateinit var prevBtn: Button
    private var cleanBtn: Button? = null

    // ---------- helper tampilan ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun tv(t: String, sp: Float = 15f, c: Int = cIce, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sp
        v.setTextColor(c)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f, m: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, wt)
        p.setMargins(dp(m), dp(m), dp(m), dp(m))
        return p
    }

    private fun cardLp(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(MP, WC)
        p.setMargins(dp(16), dp(12), dp(16), 0)
        return p
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(16))
        c.background = shape(cPanel, 18)
        return c
    }

    private fun styleBtn(b: Button, on: Boolean) {
        b.setTextColor(if (on) Color.WHITE else cIce)
        b.background = shape(if (on) cStar else cLine, 12)
    }

    private fun styleTab(b: Button, on: Boolean) {
        b.setTextColor(if (on) Color.WHITE else cMute)
        b.background = shape(if (on) cStar else Color.TRANSPARENT, 12)
    }

    private fun pill(t: String, on: Boolean, sp: Float = 13f, click: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.setAllCaps(false)
        b.textSize = sp
        b.stateListAnimator = null
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(dp(6), dp(10), dp(6), dp(10))
        styleBtn(b, on)
        b.setOnClickListener { click() }
        return b
    }

    private fun big(t: String, click: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.setAllCaps(false)
        b.textSize = 16f
        b.stateListAnimator = null
        b.setTextColor(Color.WHITE)
        b.background = shape(cStar, 14)
        b.setPadding(dp(8), dp(14), dp(8), dp(14))
        b.setOnClickListener { click() }
        return b
    }

    private fun label(t: String): TextView {
        val v = tv(t, 13f, cMute)
        v.setPadding(0, dp(10), 0, 0)
        return v
    }

    private fun segRow(labels: List<String>, sel: Int, pick: (Int) -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        labels.forEachIndexed { i, l -> r.addView(pill(l, i == sel) { pick(i) }, lp(0, WC, 1f, 2)) }
        return r
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- siklus hidup ----------

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = cInk
        window.navigationBarColor = cInk
        cfg.codec = if (Hw.hevc) 0 else 1
        cfg.preset(1)

        val root = FrameLayout(this)
        root.setBackgroundColor(cInk)
        val bg = ImageView(this)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        bg.setImageResource(R.drawable.bg)
        root.addView(bg, FrameLayout.LayoutParams(MP, MP))

        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        scroll.addView(col, ViewGroup.LayoutParams(MP, WC))
        root.addView(scroll, FrameLayout.LayoutParams(MP, MP))

        val bannerBox = FrameLayout(this)
        val banner = ImageView(this)
        banner.scaleType = ImageView.ScaleType.CENTER_CROP
        banner.setImageResource(R.drawable.banner)
        bannerBox.addView(banner, FrameLayout.LayoutParams(MP, MP))
        val fade = View(this)
        fade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(0, 6, 10, 28), Color.argb(0, 6, 10, 28), Color.argb(235, 6, 10, 28))
        )
        bannerBox.addView(fade, FrameLayout.LayoutParams(MP, MP))
        val rad = dp(28)
        bannerBox.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, -rad, view.width, view.height, rad.toFloat())
            }
        }
        bannerBox.clipToOutline = true
        col.addView(bannerBox, LinearLayout.LayoutParams(MP, dp(210)))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.BOTTOM
        head.setPadding(dp(20), 0, dp(20), 0)
        val logo = ImageView(this)
        logo.scaleType = ImageView.ScaleType.CENTER_CROP
        logo.setImageResource(R.mipmap.ic_launcher)
        val lbg = shape(cInk, 22)
        lbg.setStroke(dp(2), cIce)
        logo.background = lbg
        logo.setPadding(dp(2), dp(2), dp(2), dp(2))
        logo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(22).toFloat())
            }
        }
        logo.clipToOutline = true
        head.addView(logo, LinearLayout.LayoutParams(dp(76), dp(76)))
        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        tcol.setPadding(dp(14), 0, 0, 0)
        tcol.addView(tv("Starshore", 28f, cIce, true))
        tcol.addView(tv("Bersihkan sampah, kecilkan video", 13f, cMute))
        head.addView(tcol)
        val hp = LinearLayout.LayoutParams(MP, WC)
        hp.topMargin = -dp(44)
        col.addView(head, hp)

        // chip status akses, ketuk untuk ganti mode
        val chip = LinearLayout(this)
        chip.orientation = LinearLayout.HORIZONTAL
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.setPadding(dp(12), dp(7), dp(14), dp(7))
        val chipBg = shape(cPanel, 99)
        chipBg.setStroke(dp(1), cLine)
        chip.background = chipBg
        chipDot = View(this)
        val dotLp = LinearLayout.LayoutParams(dp(8), dp(8))
        dotLp.rightMargin = dp(8)
        chip.addView(chipDot, dotLp)
        chipTv = tv("", 13f, cIce)
        chip.addView(chipTv)
        chip.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Mode akses")
                .setItems(arrayOf("Biasa", "Shizuku", "Root")) { _, which -> setMode(which) }
                .show()
        }
        val chipLp = LinearLayout.LayoutParams(WC, WC)
        chipLp.setMargins(dp(20), dp(14), dp(20), 0)
        col.addView(chip, chipLp)
        modeTv = tv("", 12f, cMute)
        modeTv.setPadding(dp(22), dp(6), dp(20), 0)
        col.addView(modeTv)
        permBtn = pill("Izinkan akses semua file", true) { askStorage() }
        col.addView(permBtn, cardLp())

        val tr = LinearLayout(this)
        tr.orientation = LinearLayout.HORIZONTAL
        tr.setPadding(dp(4), dp(4), dp(4), dp(4))
        tr.background = shape(cPanel, 16)
        listOf("Bersihkan", "Kompres", "Analisis").forEachIndexed { i, n ->
            val btn = pill(n, false) { showTab(i) }
            tabBtns.add(btn)
            tr.addView(btn, lp(0, WC, 1f, 0))
        }
        col.addView(tr, cardLp())

        cleanBox = LinearLayout(this)
        cleanBox.orientation = LinearLayout.VERTICAL
        col.addView(cleanBox, LinearLayout.LayoutParams(MP, WC))

        compBox = LinearLayout(this)
        compBox.orientation = LinearLayout.VERTICAL
        col.addView(compBox, LinearLayout.LayoutParams(MP, WC))
        buildComp()

        anaBox = LinearLayout(this)
        anaBox.orientation = LinearLayout.VERTICAL
        col.addView(anaBox, LinearLayout.LayoutParams(MP, WC))

        col.addView(View(this), LinearLayout.LayoutParams(MP, dp(32)))
        setContentView(root)

        Sh.listen {
            runOnUiThread {
                Sh.mode = Sh.SHIZUKU
                saveMode(Sh.SHIZUKU)
                refreshMode("Shizuku aktif.")
            }
        }
        val saved = getSharedPreferences("s", 0).getInt("mode", 0)
        if (saved != 0) {
            io.execute {
                val ok = if (saved == Sh.SHIZUKU) Sh.shizukuReady() else Sh.checkRoot()
                if (ok) Sh.mode = saved
                ui.post { refreshMode("") }
            }
        }

        renderClean()
        renderSettings()
        renderAna()
        updateEst()
        refreshMode("")
        showTab(0)
    }

    private val posRun = object : Runnable {
        override fun run() {
            if (resCard.visibility == View.VISIBLE && !seeking) {
                val d = cmp.duration()
                if (d > 0) {
                    seek.max = d
                    seek.progress = cmp.position()
                    posTv.text = mmss(cmp.position() / 1000L)
                }
            }
            ui.postDelayed(this, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        permBtn.visibility = if (hasStorage()) View.GONE else View.VISIBLE
        Queue.onChange = { refreshComp() }
        refreshComp()
        ui.removeCallbacks(posRun)
        ui.post(posRun)
    }

    override fun onPause() {
        Queue.onChange = null
        ui.removeCallbacks(posRun)
        cmp.pause()
        ppBtn.text = "Putar"
        super.onPause()
    }

    override fun onDestroy() {
        Sh.unlisten()
        previewJob?.cancel()
        cmp.release()
        super.onDestroy()
    }

    // ---------- izin dan mode ----------

    private fun hasStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun askStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
            )
        }
    }

    private fun saveMode(m: Int) {
        getSharedPreferences("s", 0).edit().putInt("mode", m).apply()
    }

    private fun setMode(m: Int) {
        if (m == Sh.NONE) {
            Sh.mode = Sh.NONE
            saveMode(0)
            refreshMode("")
            return
        }
        if (m == Sh.SHIZUKU) {
            if (!Sh.shizukuAlive()) {
                refreshMode("Shizuku belum jalan. Buka app Shizuku lalu start dulu.")
                return
            }
            if (Sh.shizukuReady()) {
                Sh.mode = Sh.SHIZUKU
                saveMode(Sh.SHIZUKU)
                refreshMode("")
            } else {
                Sh.ask()
                refreshMode("Izinkan Starshore di dialog Shizuku.")
            }
            return
        }
        refreshMode("Meminta akses root...")
        io.execute {
            val ok = Sh.checkRoot()
            ui.post {
                if (ok) {
                    Sh.mode = Sh.ROOT
                    saveMode(Sh.ROOT)
                    refreshMode("")
                } else {
                    refreshMode("Root ditolak atau tidak ada.")
                }
            }
        }
    }

    private fun refreshMode(msg: String) {
        chipTv.text = when (Sh.mode) {
            Sh.SHIZUKU -> "Shizuku aktif · tanpa root"
            Sh.ROOT -> "Root aktif"
            else -> "Mode biasa · ketuk untuk ganti"
        }
        chipDot.background = shape(if (Sh.mode == Sh.NONE) cMute else cOk, 99)
        modeTv.text = msg
        modeTv.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
        permBtn.visibility = if (hasStorage()) View.GONE else View.VISIBLE
    }

    private fun showTab(i: Int) {
        cleanBox.visibility = if (i == 0) View.VISIBLE else View.GONE
        compBox.visibility = if (i == 1) View.VISIBLE else View.GONE
        anaBox.visibility = if (i == 2) View.VISIBLE else View.GONE
        for (k in tabBtns.indices) styleTab(tabBtns[k], k == i)
    }

    private fun isInstalled(p: String): Boolean = try {
        packageManager.getPackageInfo(p, 0)
        true
    } catch (e: Exception) {
        false
    }

    // ---------- tab Bersihkan ----------

    private fun renderClean() {
        cleanBox.removeAllViews()
        val c = card()
        if (items.isEmpty()) {
            val title = if (cleanMsg.isEmpty()) "Belum dipindai" else cleanMsg
            c.addView(tv(title, 16f, if (cleanOk) cOk else cIce, true))
            c.addView(
                tv(
                    "Yang dihapus beneran: duplikat, file sementara dan log, APK sisa, folder kosong, " +
                        "sisa aplikasi yang sudah dihapus. Hasil pindai ditampilkan dulu, kamu yang pilih.", 13f, cMute
                )
            )
            val b = big(if (busy) "Tunggu sebentar..." else if (cleanMsg.isEmpty()) "Pindai sekarang" else "Pindai lagi") {
                doScan()
            }
            b.isEnabled = !busy
            cleanBtn = null
            c.addView(b, lp(MP, WC, 0f, 0).also { it.topMargin = dp(12) })
        } else {
            c.addView(tv("Hasil pindai", 16f, cIce, true))
            c.addView(tv("Ketuk nama untuk lihat daftar.", 12f, cMute))
            for (item in items) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER_VERTICAL
                r.setPadding(0, dp(6), 0, dp(6))
                val cb = CheckBox(this)
                cb.isChecked = item.checked
                cb.isEnabled = !item.locked
                cb.setOnCheckedChangeListener { _, v ->
                    item.checked = v
                    updateCleanBtn()
                }
                r.addView(cb, LinearLayout.LayoutParams(WC, WC))
                val t = LinearLayout(this)
                t.orientation = LinearLayout.VERTICAL
                t.addView(tv(item.title, 15f, if (item.locked) cMute else cIce, true))
                t.addView(tv(item.sub, 12f, cMute))
                t.setOnClickListener { showFiles(item) }
                r.addView(t, LinearLayout.LayoutParams(0, WC, 1f))
                r.addView(tv(if (item.bytes > 0) fmt(item.bytes) else "-", 14f, cIce))
                c.addView(r)
            }
            val b = big("Bersihkan") { doClean() }
            cleanBtn = b
            c.addView(b, lp(MP, WC, 0f, 0).also { it.topMargin = dp(12) })
            updateCleanBtn()
        }
        cleanBox.addView(c, cardLp())
    }

    private fun updateCleanBtn() {
        val b = cleanBtn ?: return
        var total = 0L
        var any = false
        for (item in items) {
            if (item.checked && !item.locked) {
                total += item.bytes
                any = true
            }
        }
        b.text = if (total > 0) "Bersihkan " + fmt(total) else "Bersihkan"
        b.isEnabled = any && !busy
        b.alpha = if (b.isEnabled) 1f else 0.5f
    }

    private fun showFiles(item: Item) {
        val msg: String
        if (item.detail.isNotEmpty()) {
            msg = item.detail
        } else if (item.files.isNotEmpty()) {
            val sb = StringBuilder()
            val base = Cleaner.root.path
            for ((i, f) in item.files.withIndex()) {
                if (i >= 150) {
                    sb.append("... dan ").append(item.files.size - 150).append(" lainnya")
                    break
                }
                sb.append(f.path.removePrefix(base)).append("  (").append(fmt(f.length())).append(")\n")
            }
            msg = sb.toString()
        } else {
            return
        }
        AlertDialog.Builder(this).setTitle(item.title).setMessage(msg).setPositiveButton("Tutup", null).show()
    }

    private fun doScan() {
        if (busy) return
        if (!hasStorage()) {
            askStorage()
            toast("Izinkan akses semua file dulu")
            return
        }
        busy = true
        cleanOk = false
        cleanMsg = "Memindai... bisa beberapa menit di HP lemot"
        renderClean()
        val priv = Sh.mode != Sh.NONE
        io.execute {
            val s = Cleaner.scan()
            val d = Cleaner.dups(s.files)
            val cb = if (priv) Cleaner.cacheBytes() else 0L
            val cp = if (priv) Cleaner.corpses { isInstalled(it) } else Corpse(emptyList(), 0L, "")
            val list = ArrayList<Item>()
            val need = "Butuh Shizuku atau root"
            list.add(
                Item("cache", "Cache aplikasi", if (priv) "Semua app, internal dan eksternal" else need, cb, emptyList(), priv, !priv)
            )
            list.add(
                Item(
                    "corpse", "Sisa aplikasi yang sudah dihapus",
                    if (!priv) need else if (cp.paths.isEmpty()) "Tidak ada" else "${cp.paths.size} folder di Android/data, obb, media",
                    cp.bytes, emptyList(), cp.paths.isNotEmpty(), !priv || cp.paths.isEmpty(), cp.detail
                )
            )
            list.add(
                Item(
                    "dup", "File duplikat",
                    if (d.first.isEmpty()) "Tidak ada" else "${d.first.size} salinan di ${d.second} grup. 1 asli tiap grup tetap ada",
                    Cleaner.size(d.first), d.first, d.first.isNotEmpty(), d.first.isEmpty()
                )
            )
            list.add(
                Item(
                    "junk", "File sementara dan log",
                    if (s.junk.isEmpty()) "Tidak ada" else "${s.junk.size} file (.tmp .log .bak, thumbnail, sampah galeri)",
                    Cleaner.size(s.junk), s.junk, s.junk.isNotEmpty(), s.junk.isEmpty()
                )
            )
            list.add(
                Item(
                    "apk", "APK sisa",
                    if (s.apks.isEmpty()) "Tidak ada" else "${s.apks.size} file .apk",
                    Cleaner.size(s.apks), s.apks, s.apks.isNotEmpty(), s.apks.isEmpty()
                )
            )
            list.add(
                Item(
                    "empty", "Folder kosong",
                    if (s.empty.isEmpty()) "Tidak ada" else "${s.empty.size} folder",
                    0L, s.empty, s.empty.isNotEmpty(), s.empty.isEmpty()
                )
            )
            list.add(Item("log", "Log sistem", if (priv) "logcat, ANR, tombstone" else need, 0L, emptyList(), priv, !priv))
            list.add(
                Item(
                    "trim", "TRIM penyimpanan (fstrim)",
                    if (priv) "Rapikan blok kosong supaya I/O tetap lancar" else need,
                    0L, emptyList(), priv, !priv
                )
            )
            ui.post {
                busy = false
                corpsePaths = cp.paths
                items = list
                cleanMsg = ""
                renderClean()
            }
        }
    }

    private fun doClean() {
        if (busy) return
        val sel = ArrayList<Item>()
        for (item in items) if (item.checked && !item.locked) sel.add(item)
        if (sel.isEmpty()) return
        busy = true
        updateCleanBtn()
        io.execute {
            var freed = 0L
            val notes = ArrayList<String>()
            val paths = ArrayList<String>()
            for (item in sel) {
                when (item.key) {
                    "cache" -> {
                        Cleaner.cleanCache()
                        freed += item.bytes
                    }
                    "corpse" -> {
                        Cleaner.deleteCorpses(corpsePaths)
                        freed += item.bytes
                    }
                    "log" -> Cleaner.cleanLogs()
                    "trim" -> {
                        val o = Cleaner.trim()
                        val low = o.lowercase()
                        if (o.isEmpty()) {
                            notes.add("TRIM dijalankan lewat sistem (ROM tidak menampilkan hasil)")
                        } else if (low.contains("not found") || low.contains("inaccessible") ||
                            low.contains("unknown") || low.contains("denied") || low.contains("exception")
                        ) {
                            notes.add("TRIM tidak didukung ROM ini, dilewati")
                        } else {
                            notes.add("TRIM: " + o.lines().first().take(90))
                        }
                    }
                    "empty" -> Cleaner.deleteDirs(item.files)
                    else -> {
                        freed += Cleaner.delete(item.files)
                        for (f in item.files) if (paths.size < 400) paths.add(f.path)
                    }
                }
            }
            if (paths.isNotEmpty()) {
                MediaScannerConnection.scanFile(this, paths.toTypedArray(), null, null)
            }
            ui.post {
                busy = false
                items = emptyList()
                cleanOk = true
                cleanMsg = "Selesai, sekitar " + fmt(freed) + " dibebaskan" +
                    (if (notes.isEmpty()) "" else "\n" + notes.joinToString("\n"))
                renderClean()
            }
        }
    }

    // ---------- tab Kompres ----------

    private fun buildComp() {
        settingsBox = LinearLayout(this)
        settingsBox.orientation = LinearLayout.VERTICAL
        compBox.addView(settingsBox, LinearLayout.LayoutParams(MP, WC))

        val lc = card()
        lc.addView(tv("Video", 16f, cIce, true))
        lc.addView(pill("Cari video di penyimpanan", true) { findVideos() }, lp(MP, WC, 0f, 0).also { it.topMargin = dp(8) })
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        lc.addView(listBox)
        infoTv = tv("", 12f, cMute)
        infoTv.setPadding(0, dp(8), 0, 0)
        lc.addView(infoTv)
        estTv = tv("", 13f, cIce, true)
        estTv.setPadding(0, dp(4), 0, 0)
        lc.addView(estTv)
        compBox.addView(lc, cardLp())

        // kartu hasil / preview
        resCard = card()
        resCard.visibility = View.GONE
        resCard.addView(tv("Bandingkan", 16f, cIce, true))
        cmpHolder = LinearLayout(this)
        cmpHolder.orientation = LinearLayout.VERTICAL
        cmpHolder.setPadding(0, dp(8), 0, 0)
        cmp = CompareView(this)
        cmpHolder.addView(cmp, LinearLayout.LayoutParams(dp(100), dp(100)))
        resCard.addView(cmpHolder)
        val cr = LinearLayout(this)
        cr.orientation = LinearLayout.HORIZONTAL
        cr.gravity = Gravity.CENTER_VERTICAL
        cr.setPadding(0, dp(8), 0, 0)
        ppBtn = pill("Jeda", true) {
            ppBtn.text = if (cmp.toggle()) "Jeda" else "Putar"
        }
        cr.addView(ppBtn, LinearLayout.LayoutParams(dp(80), WC))
        seek = SeekBar(this)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) cmp.seek(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }

            override fun onStopTrackingTouch(s: SeekBar?) { seeking = false }
        })
        cr.addView(seek, LinearLayout.LayoutParams(0, WC, 1f))
        posTv = tv("0:00", 12f, cMute)
        cr.addView(posTv)
        resCard.addView(cr)
        resCard.addView(tv("Geser garis putih. Kiri video asli, kanan hasil kompres. Suara dari hasil kompres.", 12f, cMute))
        chipsBox = LinearLayout(this)
        chipsBox.orientation = LinearLayout.HORIZONTAL
        resCard.addView(chipsBox)
        cinfo = tv("", 13f, cIce)
        cinfo.setPadding(0, dp(8), 0, 0)
        resCard.addView(cinfo)
        decideBox = LinearLayout(this)
        decideBox.orientation = LinearLayout.HORIZONTAL
        decideBox.addView(big("Hapus file asli") { deleteOriginals() }, lp(0, WC, 1f, 3))
        decideBox.addView(big("Simpan keduanya") { keepBoth() }, lp(0, WC, 1f, 3))
        resCard.addView(decideBox, lp(MP, WC, 0f, 0).also { it.topMargin = dp(10) })
        compBox.addView(resCard, cardLp())

        val ac = card()
        statusTv = tv("Pilih video, atur kualitas, lalu kompres.", 13f, cMute)
        ac.addView(statusTv)
        etaTv = tv("", 12f, cMute)
        ac.addView(etaTv)
        progBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progBar.max = 100
        ac.addView(progBar, lp(MP, WC, 0f, 0).also { it.topMargin = dp(8) })
        logTv = tv("", 12f, cMute)
        logTv.setPadding(0, dp(6), 0, 0)
        ac.addView(logTv)
        val br = LinearLayout(this)
        br.orientation = LinearLayout.HORIZONTAL
        prevBtn = pill("Preview 5 detik", false) { doPreview() }
        br.addView(prevBtn, lp(0, WC, 1f, 3))
        goBtn = big("Kompres") {
            if (Queue.running) Queue.cancel() else startCompress()
        }
        br.addView(goBtn, lp(0, WC, 1.4f, 3))
        ac.addView(br, lp(MP, WC, 0f, 0).also { it.topMargin = dp(8) })
        compBox.addView(ac, cardLp())
    }

    private fun slider(c: LinearLayout, max: Int, prog: Int, text: (Int) -> String, onSet: (Int) -> Unit) {
        val lbl = label(text(prog))
        c.addView(lbl)
        val sb = SeekBar(this)
        sb.max = max
        sb.progress = prog
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                onSet(p)
                lbl.text = text(p)
                updateEst()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {
                renderSettings()
            }
        })
        c.addView(sb)
    }

    private fun renderSettings() {
        settingsBox.removeAllViews()
        val c = card()
        c.addView(tv("Kualitas", 16f, cIce, true))
        val pr = LinearLayout(this)
        pr.orientation = LinearLayout.HORIZONTAL
        listOf("Jernih", "Seimbang", "Kecil", "Rek. layar").forEachIndexed { i, n ->
            val pb = pill(n, cfg.matches(i), 12f) {
                cfg.preset(i)
                renderSettings()
                updateEst()
            }
            pb.setSingleLine(true)
            pb.setPadding(dp(2), dp(10), dp(2), dp(10))
            pr.addView(pb, lp(0, WC, 1f, 2))
        }
        c.addView(pr, lp(MP, WC, 0f, 0).also { it.topMargin = dp(6) })

        c.addView(label("Codec"))
        c.addView(segRow(listOf("HEVC (H.265)", "H.264"), cfg.codec) {
            cfg.codec = it
            renderSettings()
        })
        if (cfg.codec == 0 && !Hw.hevc) {
            c.addView(tv("HP ini tidak punya encoder HEVC hardware, jadi otomatis dipakai H.264 (jauh lebih cepat).", 12f, cMute))
        }
        c.addView(label("Resolusi"))
        c.addView(segRow(listOf("Asli", "1080p", "720p", "480p"), cfg.res) {
            cfg.res = it
            renderSettings()
            updateEst()
        })
        c.addView(label("FPS"))
        c.addView(segRow(listOf("Asli", "60", "30", "24"), cfg.fps) {
            cfg.fps = it
            renderSettings()
        })
        c.addView(label("Ukuran hasil"))
        c.addView(segRow(listOf("Otomatis", "Target ukuran", "Manual"), cfg.mode) {
            cfg.mode = it
            renderSettings()
            updateEst()
        })
        when (cfg.mode) {
            0 -> slider(c, 16, (cfg.pct - 20) / 5, { "Ukuran hasil: ${20 + it * 5}% dari bitrate asli" }, { cfg.pct = 20 + it * 5 })
            1 -> {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.setPadding(0, dp(6), 0, 0)
                listOf(10, 25, 50, 100).forEach { mb ->
                    r.addView(pill("$mb MB", cfg.targetMb == mb) {
                        cfg.targetMb = mb
                        renderSettings()
                        updateEst()
                    }, lp(0, WC, 1f, 2))
                }
                c.addView(r)
                slider(c, 39, cfg.targetMb / 5 - 1, { "Target: ${(it + 1) * 5} MB per video" }, { cfg.targetMb = (it + 1) * 5 })
            }
            else -> slider(
                c, 23, Math.round(cfg.br * 2f) - 1,
                { "Bitrate: " + "%.1f".format((it + 1) / 2f) + " Mbps (maks 90% bitrate asli)" },
                { cfg.br = (it + 1) / 2f }
            )
        }
        c.addView(label("Audio"))
        c.addView(segRow(listOf("Asli", "64k", "Tanpa"), cfg.aud) {
            cfg.aud = it
            renderSettings()
            updateEst()
        })
        c.addView(tv("Hasil selalu dibatasi di bawah bitrate asli, jadi file tidak membesar.", 12f, cMute).also {
            it.setPadding(0, dp(8), 0, 0)
        })
        settingsBox.addView(c, cardLp())
    }

    private fun findVideos() {
        if (!hasStorage()) {
            askStorage()
            toast("Izinkan akses semua file dulu")
            return
        }
        statusTv.text = "Mencari video..."
        io.execute {
            val l = ArrayList<Vid>()
            for (f in Cleaner.videos()) l.add(Vid(f))
            ui.post {
                vids = l
                lastSel = null
                renderList()
                updateEst()
                statusTv.text = if (l.isEmpty()) "Tidak ada video besar ditemukan." else "${l.size} video ditemukan."
            }
        }
    }

    private fun renderList() {
        listBox.removeAllViews()
        for (v in vids) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(0, dp(4), 0, dp(4))
            val cb = CheckBox(this)
            cb.isChecked = v.sel
            cb.setOnCheckedChangeListener { _, on ->
                v.sel = on
                if (on) {
                    lastSel = v
                    loadMeta(v)
                }
                updateEst()
            }
            r.addView(cb, LinearLayout.LayoutParams(WC, WC))
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.background = shape(cLine, 8)
            iv.setOnClickListener { play(v.f) }
            val sm = v.small
            if (sm != null) {
                iv.setImageBitmap(sm)
            } else {
                thumbIo.execute {
                    val bm = smallThumb(v.f)
                    v.small = bm
                    ui.post { if (bm != null) iv.setImageBitmap(bm) }
                }
            }
            r.addView(iv, lp(dp(68), dp(42), 0f, 4))
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            val name = tv(v.f.name, 14f, cIce, true)
            name.maxLines = 1
            name.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            t.addView(name)
            t.addView(tv(fmt(v.f.length()), 12f, cMute))
            t.setOnClickListener { cb.toggle() }
            r.addView(t, LinearLayout.LayoutParams(0, WC, 1f))
            listBox.addView(r)
        }
    }

    private fun smallThumb(f: File): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            val b = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (b == null) null
            else Bitmap.createScaledBitmap(b, 160, maxOf(1, b.height * 160 / maxOf(1, b.width)), true)
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
    }

    private fun probe(v: Vid) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(v.f.absolutePath)
            v.durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w
                w = h
                h = t
            }
            v.w = w
            v.h = h
            v.bps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
    }

    private fun probeSize(f: File): Pair<Int, Int> {
        val v = Vid(f)
        probe(v)
        return Pair(v.w, v.h)
    }

    private fun loadMeta(v: Vid) {
        io.execute {
            if (!v.probed()) probe(v)
            ui.post {
                val mbps = "%.1f".format(maxOf(v.bps, v.f.length() * 8000L / maxOf(1L, v.durMs)) / 1e6)
                infoTv.text = "${v.f.name}: ${v.w}x${v.h}, ${mmss(v.durMs / 1000L)}, ${fmt(v.f.length())}, ±$mbps Mbps"
                updateEst()
            }
        }
    }

    private fun updateEst() {
        var cur = 0L
        var est = 0L
        var n = 0
        for (v in vids) {
            if (!v.sel) continue
            n++
            val size = v.f.length()
            cur += size
            est += if (v.probed()) minOf(size, cfg.estBytes(v)) else size / 2
        }
        estTv.text = if (n == 0) "Belum ada video dipilih" else "$n video: " + fmt(cur) + " jadi sekitar " + fmt(est)
        if (!Queue.running) {
            goBtn.text = if (n == 0) "Kompres" else "Kompres $n video"
            goBtn.isEnabled = n > 0
            goBtn.alpha = if (n > 0) 1f else 0.5f
        }
    }

    private fun play(f: File) {
        if (!f.exists()) return
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val vv = VideoView(this)
        val mc = MediaController(this)
        mc.setAnchorView(vv)
        vv.setMediaController(mc)
        vv.setVideoPath(f.absolutePath)
        vv.setOnPreparedListener { it.start() }
        val fl = FrameLayout(this)
        fl.addView(vv, FrameLayout.LayoutParams(MP, MP, Gravity.CENTER))
        d.setContentView(fl)
        d.setOnDismissListener { vv.stopPlayback() }
        d.show()
    }

    // ---- kompres ----

    private fun startCompress() {
        if (previewing) return
        val q = ArrayList<Vid>()
        for (v in vids) if (v.sel) q.add(v)
        if (q.isEmpty()) {
            toast("Pilih video dulu")
            return
        }
        if (!hasStorage()) {
            askStorage()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
        goBtn.isEnabled = false
        statusTv.text = "Menyiapkan..."
        io.execute {
            for (v in q) if (!v.probed()) probe(v)
            ui.post {
                resCard.visibility = View.GONE
                cmp.release()
                Queue.start(applicationContext, q, cfg)
                refreshComp()
            }
        }
    }

    private fun refreshComp() {
        if (previewing) return
        statusTv.text = if (Queue.status.isEmpty()) "Pilih video, atur kualitas, lalu kompres." else Queue.status
        progBar.progress = Queue.percent
        etaTv.text = if (Queue.eta.isEmpty()) "" else "sisa ±" + Queue.eta
        logTv.text = Queue.log.joinToString("\n")
        if (Queue.running) {
            goBtn.text = "Batal"
            goBtn.isEnabled = true
            goBtn.alpha = 1f
        } else {
            updateEst()
        }
        if (!Queue.running && Queue.done.isNotEmpty() && Queue.stamp != shownStamp) {
            shownStamp = Queue.stamp
            buildChips()
            showResult(Queue.done.size - 1)
        }
    }

    private fun buildChips() {
        chipsBox.removeAllViews()
        if (Queue.done.size < 2) return
        for (i in Queue.done.indices) {
            chipsBox.addView(pill("${i + 1}", false) { showResult(i) }, lp(WC, WC, 0f, 2))
        }
    }

    private fun showResult(i: Int) {
        if (i < 0 || i >= Queue.done.size) return
        val p = Queue.done[i]
        val sz = probeSize(p.second)
        val save = p.first.length() - p.second.length()
        showCompare(
            p.first, p.second, sz.first, sz.second, 0,
            "${p.first.name}: ${fmt(p.first.length())} jadi ${fmt(p.second.length())}, hemat ${fmt(save)}", true
        )
    }

    private fun showCompare(before: File, after: File, w: Int, h: Int, offset: Int, info: String, decision: Boolean) {
        resCard.visibility = View.VISIBLE
        cinfo.text = info
        decideBox.visibility = if (decision) View.VISIBLE else View.GONE
        val maxW = resources.displayMetrics.widthPixels - dp(64)
        val maxH = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        cmp.show(before.absolutePath, after.absolutePath, w, h, maxW, maxH, offset)
        ppBtn.text = "Jeda"
    }

    private fun doPreview() {
        val v = lastSel
        if (v == null || !v.sel) {
            toast("Pilih video dulu")
            return
        }
        if (previewing || Queue.running) return
        previewing = true
        prevBtn.isEnabled = false
        statusTv.text = "Membuat preview 5 detik..."
        io.execute {
            if (!v.probed()) probe(v)
            ui.post { runPreview(v) }
        }
    }

    private fun runPreview(v: Vid) {
        val dur = v.durMs
        val clipLen = minOf(5000L, dur)
        if (clipLen <= 0L) {
            previewing = false
            prevBtn.isEnabled = true
            statusTv.text = "Video ini tidak bisa dibaca."
            return
        }
        val start = maxOf(0L, dur / 2 - clipLen / 2)
        val out = File(cacheDir, "preview.mp4")
        out.delete()
        val t0 = SystemClock.elapsedRealtime()
        val job = Compressor.Job(
            applicationContext, v.f, out, cfg, v.w, v.h, cfg.videoBps(v), start, start + clipLen,
            { p -> statusTv.text = "Preview: $p%" },
            { err, enc ->
                previewing = false
                prevBtn.isEnabled = true
                if (err != null || !out.exists()) {
                    statusTv.text = "Preview gagal: " + (err ?: "tidak ada hasil")
                } else {
                    val full = (out.length() * (dur / clipLen.toDouble())).toLong()
                    val sec = maxOf(0.1, (SystemClock.elapsedRealtime() - t0) / 1000.0)
                    val speed = "%.1f".format(clipLen / 1000.0 / sec)
                    statusTv.text = "Preview jadi: ${speed}x real-time, ${enc ?: "?"}"
                    showCompare(
                        v.f, out, v.w, v.h, start.toInt(),
                        "Perkiraan hasil penuh ±" + fmt(full) + " dari " + fmt(v.f.length()) + " (dihitung dari klip 5 detik)",
                        false
                    )
                }
            }
        )
        previewJob = job
        job.start()
    }

    private fun deleteOriginals() {
        val paths = ArrayList<String>()
        var saved = 0L
        for (p in Queue.done) {
            val src = p.first
            val out = p.second
            val target = File(src.parentFile, src.nameWithoutExtension + ".mp4")
            val sp = src.path
            val gain = src.length() - out.length()
            if (src.delete()) {
                saved += gain
                paths.add(sp)
                if (out.renameTo(target)) paths.add(target.path)
            }
        }
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(this, paths.toTypedArray(), null, null)
        val gone = HashSet<String>()
        for (p in Queue.done) if (!p.first.exists()) gone.add(p.first.path)
        val keep = ArrayList<Vid>()
        for (v in vids) if (!gone.contains(v.f.path)) keep.add(v)
        vids = keep
        finishDecision("File asli dihapus, hemat ${fmt(saved)}. Hasil kompres memakai nama file asli.")
        renderList()
    }

    private fun keepBoth() {
        var extra = 0L
        for (p in Queue.done) extra += p.second.length()
        finishDecision("Dua-duanya disimpan. Penyimpanan terpakai ${fmt(extra)} lebih banyak (hasil bernama _starshore.mp4).")
    }

    private fun finishDecision(msg: String) {
        Queue.done.clear()
        Queue.status = msg
        cmp.release()
        resCard.visibility = View.GONE
        refreshComp()
    }

    // ---------- tab Analisis ----------

    private fun renderAna() {
        anaBox.removeAllViews()
        val c = card()
        c.addView(tv("Penyimpanan", 16f, cIce, true))
        val a = ana
        if (a == null) {
            c.addView(tv(if (busy) "Menganalisis..." else "Lihat folder dan file terbesar di penyimpanan. Folder Android/ tidak ikut dihitung.", 13f, cMute))
        } else {
            c.addView(tv("Total terpindai: " + fmt(a.total), 13f, cMute))
            c.addView(label("Folder terbesar"))
            for (f in a.folders) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.setPadding(0, dp(4), 0, dp(4))
                val n = tv(f.first, 14f, cIce)
                n.maxLines = 1
                n.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                r.addView(n, LinearLayout.LayoutParams(0, WC, 1f))
                r.addView(tv(fmt(f.second), 14f, cIce, true))
                c.addView(r)
            }
            c.addView(label("File terbesar (ketuk untuk hapus)"))
            for (f in a.big) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.setPadding(0, dp(6), 0, dp(6))
                val n = tv(f.name, 13f, cIce)
                n.maxLines = 1
                n.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                r.addView(n, LinearLayout.LayoutParams(0, WC, 1f))
                r.addView(tv(fmt(f.length()), 13f, cIce, true))
                r.setOnClickListener { confirmDelete(f) }
                c.addView(r)
            }
        }
        val b = big(if (busy) "Tunggu sebentar..." else if (a == null) "Analisis sekarang" else "Analisis lagi") { doAna() }
        b.isEnabled = !busy
        c.addView(b, lp(MP, WC, 0f, 0).also { it.topMargin = dp(12) })
        anaBox.addView(c, cardLp())
    }

    private fun doAna() {
        if (busy) return
        if (!hasStorage()) {
            askStorage()
            toast("Izinkan akses semua file dulu")
            return
        }
        busy = true
        renderAna()
        io.execute {
            val r = Cleaner.analyze()
            ui.post {
                busy = false
                ana = r
                renderAna()
            }
        }
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setMessage(f.path.removePrefix(Cleaner.root.path) + "\n" + fmt(f.length()) + "\n\nHapus file ini?")
            .setPositiveButton("Hapus") { _, _ ->
                val path = f.path
                if (f.delete()) {
                    MediaScannerConnection.scanFile(this, arrayOf(path), null, null)
                    val a = ana
                    if (a != null) {
                        val nb = ArrayList<File>()
                        for (x in a.big) if (x.path != path) nb.add(x)
                        ana = Analysis(a.folders, nb, a.total)
                    }
                    renderAna()
                } else {
                    toast("Gagal menghapus")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
