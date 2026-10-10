package app.starshore

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

class Vid(val f: File) {
    var sel = false
    var durMs = 0L
    var w = 0
    var h = 0
    var thumb: Bitmap? = null
}

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
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
    private var items: List<Item> = emptyList()
    private var vids: List<Vid> = emptyList()
    private val results = ArrayList<Pair<File, File>>()
    private var job: Compressor.Job? = null
    private var curOut: File? = null
    private var beforeFile: File? = null
    private var afterFile: File? = null
    private var cleanMsg = ""
    private var cleanOk = false

    private val modeBtns = ArrayList<Button>()
    private val tabBtns = ArrayList<Button>()
    private lateinit var modeTv: TextView
    private lateinit var permBtn: Button
    private lateinit var cleanBox: LinearLayout
    private lateinit var compBox: LinearLayout
    private lateinit var settingsBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var estTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var progBar: ProgressBar
    private lateinit var goBtn: Button
    private lateinit var beforeImg: ImageView
    private lateinit var afterImg: ImageView
    private lateinit var beforeTv: TextView
    private lateinit var afterTv: TextView
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

    private fun pill(t: String, on: Boolean, click: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.setAllCaps(false)
        b.textSize = 13f
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

        val banner = ImageView(this)
        banner.scaleType = ImageView.ScaleType.CENTER_CROP
        banner.setImageResource(R.drawable.banner)
        col.addView(banner, LinearLayout.LayoutParams(MP, dp(190)))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(18), dp(14), dp(18), 0)
        val logo = ImageView(this)
        logo.setImageResource(R.mipmap.ic_launcher)
        head.addView(logo, LinearLayout.LayoutParams(dp(56), dp(56)))
        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        tcol.setPadding(dp(12), 0, 0, 0)
        tcol.addView(tv("Starshore", 26f, cIce, true))
        tcol.addView(tv("Bersihkan sampah, kecilkan video", 13f, cMute))
        head.addView(tcol)
        col.addView(head)

        // kartu akses
        val mc = card()
        mc.addView(tv("Akses", 14f, cMute))
        val mr = LinearLayout(this)
        mr.orientation = LinearLayout.HORIZONTAL
        listOf("Biasa", "Shizuku", "Root").forEachIndexed { i, n ->
            val btn = pill(n, false) { setMode(i) }
            modeBtns.add(btn)
            mr.addView(btn, lp(0, WC, 1f, 2))
        }
        mc.addView(mr)
        modeTv = tv("", 13f, cMute)
        modeTv.setPadding(0, dp(8), 0, 0)
        mc.addView(modeTv)
        permBtn = pill("Izinkan akses semua file", true) { askStorage() }
        mc.addView(permBtn, lp(MP, WC, 0f, 0).also { it.topMargin = dp(8) })
        col.addView(mc, cardLp())

        // tab
        val tr = LinearLayout(this)
        tr.orientation = LinearLayout.HORIZONTAL
        listOf("Bersihkan", "Kompres video").forEachIndexed { i, n ->
            val btn = pill(n, false) { showTab(i) }
            tabBtns.add(btn)
            tr.addView(btn, lp(0, WC, 1f, 2))
        }
        val tlp = cardLp()
        col.addView(tr, tlp)

        cleanBox = LinearLayout(this)
        cleanBox.orientation = LinearLayout.VERTICAL
        col.addView(cleanBox, LinearLayout.LayoutParams(MP, WC))

        compBox = LinearLayout(this)
        compBox.orientation = LinearLayout.VERTICAL
        col.addView(compBox, LinearLayout.LayoutParams(MP, WC))
        buildComp()

        val pad = View(this)
        col.addView(pad, LinearLayout.LayoutParams(MP, dp(32)))
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
        updateEst()
        refreshMode("")
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        permBtn.visibility = if (hasStorage()) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        Sh.unlisten()
        job?.cancel()
        super.onDestroy()
    }

    // ---------- izin & mode ----------

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
        for (i in modeBtns.indices) styleBtn(modeBtns[i], i == Sh.mode)
        modeTv.text = if (msg.isNotEmpty()) msg else when (Sh.mode) {
            Sh.SHIZUKU -> "Shizuku aktif."
            Sh.ROOT -> "Root aktif."
            else -> "Mode biasa. Cache app, log sistem, dan TRIM butuh Shizuku atau root."
        }
        permBtn.visibility = if (hasStorage()) View.GONE else View.VISIBLE
    }

    private fun showTab(i: Int) {
        cleanBox.visibility = if (i == 0) View.VISIBLE else View.GONE
        compBox.visibility = if (i == 1) View.VISIBLE else View.GONE
        for (k in tabBtns.indices) styleBtn(tabBtns[k], k == i)
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
                    "Yang dihapus beneran: file duplikat, file sementara dan log, APK sisa, folder kosong. " +
                        "Hasil pindai ditampilkan dulu, kamu yang pilih.", 13f, cMute
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
            c.addView(tv("Ketuk nama untuk lihat daftar file.", 12f, cMute))
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
        for (it in items) {
            if (it.checked && !it.locked) {
                total += it.bytes
                any = true
            }
        }
        b.text = if (total > 0) "Bersihkan " + fmt(total) else "Bersihkan"
        b.isEnabled = any && !busy
        b.alpha = if (b.isEnabled) 1f else 0.5f
    }

    private fun showFiles(item: Item) {
        if (item.files.isEmpty()) return
        val sb = StringBuilder()
        val base = Cleaner.root.path
        for ((i, f) in item.files.withIndex()) {
            if (i >= 150) {
                sb.append("... dan ").append(item.files.size - 150).append(" lainnya")
                break
            }
            sb.append(f.path.removePrefix(base)).append("  (").append(fmt(f.length())).append(")\n")
        }
        AlertDialog.Builder(this).setTitle(item.title).setMessage(sb.toString())
            .setPositiveButton("Tutup", null).show()
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
            val list = ArrayList<Item>()
            list.add(
                Item(
                    "cache", "Cache aplikasi",
                    if (priv) "Semua app, internal dan eksternal" else "Butuh Shizuku atau root",
                    cb, emptyList(), priv, !priv
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
                    if (s.junk.isEmpty()) "Tidak ada" else "${s.junk.size} file (.tmp .log .bak, thumbnail, folder cache)",
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
            list.add(
                Item(
                    "log", "Log sistem",
                    if (priv) "logcat, ANR, tombstone" else "Butuh Shizuku atau root",
                    0L, emptyList(), priv, !priv
                )
            )
            list.add(
                Item(
                    "trim", "TRIM penyimpanan (fstrim)",
                    if (priv) "Rapikan blok kosong, bikin I/O lebih lancar" else "Butuh Shizuku atau root",
                    0L, emptyList(), priv, !priv
                )
            )
            ui.post {
                busy = false
                items = list
                cleanMsg = ""
                renderClean()
            }
        }
    }

    private fun doClean() {
        if (busy) return
        val sel = ArrayList<Item>()
        for (it in items) if (it.checked && !it.locked) sel.add(it)
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
                    "log" -> Cleaner.cleanLogs()
                    "trim" -> {
                        val o = Cleaner.trim().trim()
                        notes.add(if (o.isEmpty()) "TRIM sudah dijalankan" else "TRIM: " + o.take(100))
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

        // daftar video
        val lc = card()
        lc.addView(tv("Video", 16f, cIce, true))
        lc.addView(pill("Cari video di penyimpanan", true) { findVideos() }, lp(MP, WC, 0f, 0).also { it.topMargin = dp(8) })
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        lc.addView(listBox)
        estTv = tv("", 13f, cMute)
        estTv.setPadding(0, dp(8), 0, 0)
        lc.addView(estTv)
        compBox.addView(lc, cardLp())

        // preview sebelum / sesudah
        val pc = card()
        pc.addView(tv("Sebelum dan sesudah", 16f, cIce, true))
        pc.addView(tv("Ketuk gambar untuk memutar videonya.", 12f, cMute))
        val pr = LinearLayout(this)
        pr.orientation = LinearLayout.HORIZONTAL
        pr.setPadding(0, dp(8), 0, 0)
        beforeImg = ImageView(this)
        afterImg = ImageView(this)
        beforeTv = tv("Sebelum", 12f, cMute)
        afterTv = tv("Sesudah", 12f, cMute)
        for (p in listOf(Pair(beforeImg, beforeTv), Pair(afterImg, afterTv))) {
            val colv = LinearLayout(this)
            colv.orientation = LinearLayout.VERTICAL
            p.first.scaleType = ImageView.ScaleType.CENTER_CROP
            p.first.background = shape(cLine, 10)
            colv.addView(p.first, LinearLayout.LayoutParams(MP, dp(120)))
            colv.addView(p.second)
            pr.addView(colv, lp(0, WC, 1f, 4))
        }
        beforeImg.setOnClickListener { beforeFile?.let { f -> play(f) } }
        afterImg.setOnClickListener { afterFile?.let { f -> play(f) } }
        pc.addView(pr)
        compBox.addView(pc, cardLp())

        // tombol jalan
        val ac = card()
        statusTv = tv("Pilih video, atur kualitas, lalu kompres.", 13f, cMute)
        ac.addView(statusTv)
        progBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progBar.max = 100
        ac.addView(progBar, lp(MP, WC, 0f, 0).also { it.topMargin = dp(8) })
        goBtn = big("Kompres") {
            if (busy) cancelCompress() else startCompress()
        }
        ac.addView(goBtn, lp(MP, WC, 0f, 0).also { it.topMargin = dp(8) })
        compBox.addView(ac, cardLp())
    }

    private fun renderSettings() {
        settingsBox.removeAllViews()
        val c = card()
        c.addView(tv("Kualitas", 16f, cIce, true))
        val pr = LinearLayout(this)
        pr.orientation = LinearLayout.HORIZONTAL
        listOf("Jernih", "Seimbang", "Kecil").forEachIndexed { i, n ->
            pr.addView(pill(n, cfg.matches(i)) {
                cfg.preset(i)
                renderSettings()
                updateEst()
            }, lp(0, WC, 1f, 2))
        }
        c.addView(pr, lp(MP, WC, 0f, 0).also { it.topMargin = dp(6) })

        c.addView(tv("Codec", 13f, cMute).also { it.setPadding(0, dp(10), 0, 0) })
        c.addView(segRow(listOf("HEVC (H.265)", "H.264"), cfg.codec) {
            cfg.codec = it
            renderSettings()
        })
        c.addView(tv("Resolusi", 13f, cMute).also { it.setPadding(0, dp(10), 0, 0) })
        c.addView(segRow(listOf("Asli", "1080p", "720p", "480p"), cfg.res) {
            cfg.res = it
            renderSettings()
        })
        c.addView(tv("FPS", 13f, cMute).also { it.setPadding(0, dp(10), 0, 0) })
        c.addView(segRow(listOf("Asli", "60", "30", "24"), cfg.fps) {
            cfg.fps = it
            renderSettings()
        })

        val lbl = tv("Bitrate video: " + "%.1f".format(cfg.br) + " Mbps", 13f, cMute)
        lbl.setPadding(0, dp(10), 0, 0)
        c.addView(lbl)
        val sb = SeekBar(this)
        sb.max = 23
        sb.progress = Math.round(cfg.br * 2f) - 1
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                cfg.br = (p + 1) / 2f
                lbl.text = "Bitrate video: " + "%.1f".format(cfg.br) + " Mbps"
                updateEst()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {
                renderSettings()
            }
        })
        c.addView(sb)

        c.addView(tv("Audio", 13f, cMute).also { it.setPadding(0, dp(10), 0, 0) })
        c.addView(segRow(listOf("128k", "64k", "Tanpa"), cfg.aud) {
            cfg.aud = it
            renderSettings()
            updateEst()
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
                if (on) loadMeta(v)
                updateEst()
            }
            r.addView(cb, LinearLayout.LayoutParams(WC, WC))
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

    private fun updateEst() {
        var cur = 0L
        var est = 0L
        var n = 0
        for (v in vids) {
            if (!v.sel) continue
            n++
            val size = v.f.length()
            cur += size
            val e = if (v.durMs > 0) {
                val bytes = (v.durMs / 1000.0) * (cfg.br * 1e6 + cfg.audValue() * 1000.0) / 8.0
                minOf(size, bytes.toLong())
            } else size
            est += e
        }
        estTv.text = if (n == 0) "Belum ada video dipilih" else "$n video: " + fmt(cur) + " jadi sekitar " + fmt(est)
        if (!busy) {
            goBtn.text = if (n == 0) "Kompres" else "Kompres $n video"
            goBtn.isEnabled = n > 0
            goBtn.alpha = if (n > 0) 1f else 0.5f
        }
    }

    private fun mmss(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun thumbOf(r: MediaMetadataRetriever, durMs: Long): Bitmap? {
        val bmp = r.getFrameAtTime(durMs * 500L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val w = 480
        val h = maxOf(1, bmp.height * w / maxOf(1, bmp.width))
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private fun loadMeta(v: Vid) {
        io.execute {
            if (v.durMs == 0L) {
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
                    v.thumb = thumbOf(r, v.durMs)
                } catch (e: Exception) {
                } finally {
                    try { r.release() } catch (e: Exception) { }
                }
            }
            ui.post {
                beforeFile = v.f
                beforeImg.setImageBitmap(v.thumb)
                beforeTv.text = "Sebelum: ${v.w}x${v.h}, ${mmss(v.durMs)}, ${fmt(v.f.length())}"
                updateEst()
            }
        }
    }

    private fun showAfter(out: File) {
        io.execute {
            var bmp: Bitmap? = null
            var dur = 0L
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(out.absolutePath)
                dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                bmp = thumbOf(r, dur)
            } catch (e: Exception) {
            } finally {
                try { r.release() } catch (e: Exception) { }
            }
            ui.post {
                afterFile = out
                afterImg.setImageBitmap(bmp)
                afterTv.text = "Sesudah: ${mmss(dur)}, ${fmt(out.length())}"
            }
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

    private fun startCompress() {
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
        busy = true
        results.clear()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goBtn.text = "Batal"
        goBtn.isEnabled = true
        goBtn.alpha = 1f
        runNext(q, 0)
    }

    private fun runNext(q: List<Vid>, i: Int) {
        if (i >= q.size) {
            finishQueue()
            return
        }
        val v = q[i]
        if (v.w == 0 || v.h == 0) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(v.f.absolutePath)
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
            } catch (e: Exception) {
            } finally {
                try { r.release() } catch (e: Exception) { }
            }
        }
        val out = File(v.f.parentFile, v.f.nameWithoutExtension + "_starshore.mp4")
        out.delete()
        curOut = out
        progBar.progress = 0
        statusTv.text = "(${i + 1}/${q.size}) ${v.f.name}"
        val j = Compressor.Job(this, v.f, out, cfg, v.w, v.h, { p ->
            progBar.progress = p
            statusTv.text = "(${i + 1}/${q.size}) ${v.f.name}: $p%"
        }, { err ->
            if (busy) {
                job = null
                if (err != null || !out.exists() || out.length() >= v.f.length()) {
                    out.delete()
                } else {
                    results.add(Pair(v.f, out))
                    showAfter(out)
                    MediaScannerConnection.scanFile(this, arrayOf(out.path), null, null)
                }
                v.sel = false
                runNext(q, i + 1)
            }
        })
        job = j
        j.start()
    }

    private fun cancelCompress() {
        busy = false
        job?.cancel()
        job = null
        curOut?.delete()
        finishQueue()
    }

    private fun finishQueue() {
        busy = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progBar.progress = 0
        renderList()
        updateEst()
        if (results.isEmpty()) {
            statusTv.text = "Selesai. Tidak ada video yang jadi lebih kecil, jadi tidak ada yang disimpan."
            return
        }
        var save = 0L
        for (r in results) save += r.first.length() - r.second.length()
        statusTv.text = "Selesai: ${results.size} video, hemat ${fmt(save)} kalau file asli dihapus."
        AlertDialog.Builder(this)
            .setTitle("Hapus file asli?")
            .setMessage(
                "${results.size} video sudah dikompres. Kalau file asli disimpan, ukuran di HP malah nambah. " +
                    "Kalau dihapus, hemat ${fmt(save)}. Hasil kompres akan memakai nama file asli."
            )
            .setPositiveButton("Hapus asli") { _, _ -> deleteOriginals() }
            .setNegativeButton("Simpan keduanya", null)
            .setCancelable(false)
            .show()
    }

    private fun deleteOriginals() {
        val gone = HashSet<File>()
        val paths = ArrayList<String>()
        for (r in results) {
            val src = r.first
            val out = r.second
            val target = File(src.parentFile, src.nameWithoutExtension + ".mp4")
            val sp = src.path
            if (src.delete()) {
                gone.add(src)
                paths.add(sp)
                if (out.renameTo(target)) paths.add(target.path)
            }
        }
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(this, paths.toTypedArray(), null, null)
        val keep = ArrayList<Vid>()
        for (v in vids) if (!gone.contains(v.f)) keep.add(v)
        vids = keep
        results.clear()
        afterFile = null
        renderList()
        updateEst()
        statusTv.text = "File asli dihapus."
    }
}
