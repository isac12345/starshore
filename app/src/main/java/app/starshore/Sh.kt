package app.starshore

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Jalankan perintah shell lewat Shizuku atau root. */
object Sh {
    const val NONE = 0
    const val SHIZUKU = 1
    const val ROOT = 2

    @Volatile
    var mode = NONE

    private var cb: (() -> Unit)? = null
    private val listener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) cb?.invoke()
    }

    fun listen(f: () -> Unit) {
        cb = f
        try { Shizuku.addRequestPermissionResultListener(listener) } catch (e: Throwable) { }
    }

    fun unlisten() {
        try { Shizuku.removeRequestPermissionResultListener(listener) } catch (e: Throwable) { }
    }

    fun shizukuAlive(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun ask() {
        try { Shizuku.requestPermission(77) } catch (e: Throwable) { }
    }

    fun checkRoot(): Boolean = try {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val o = p.inputStream.bufferedReader().readText()
        p.waitFor()
        o.contains("uid=0")
    } catch (e: Throwable) { false }

    // Shizuku.newProcess private sejak 13.x, dipanggil lewat refleksi
    private fun shizukuProc(cmd: String): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, arrayOf("sh", "-c", "$cmd 2>&1"), null, null) as Process
    }

    /** Harus dipanggil dari thread background. */
    fun run(cmd: String): String {
        return try {
            val p: Process = when (mode) {
                ROOT -> ProcessBuilder("su", "-c", "$cmd 2>&1").redirectErrorStream(true).start()
                SHIZUKU -> shizukuProc(cmd)
                else -> return ""
            }
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        } catch (e: Throwable) {
            ""
        }
    }
}
