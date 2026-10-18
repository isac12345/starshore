package app.starshore

import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

fun fmt(b: Long): String = when {
    b >= 1_000_000_000L -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000L -> "%.0f MB".format(b / 1e6)
    b >= 1_000L -> "%.0f KB".format(b / 1e3)
    else -> "$b B"
}

fun mmss(s: Long): String = "%d:%02d".format(s / 60, s % 60)

class Item(
    val key: String,
    val title: String,
    val sub: String,
    val bytes: Long,
    val files: List<File>,
    var checked: Boolean,
    val locked: Boolean,
    val detail: String = ""
)

class Scan(val files: List<File>, val junk: List<File>, val apks: List<File>, val empty: List<File>)
class Corpse(val paths: List<String>, val bytes: Long, val detail: String)
class Analysis(val folders: List<Pair<String, Long>>, val big: List<File>, val total: Long)

object Cleaner {
    val root: File = Environment.getExternalStorageDirectory()

    private val junkExt = setOf("tmp", "temp", "log", "bak", "old", "chk", "dmp")
    private val junkDirs = setOf(".thumbnails", ".cache", "lost.dir", ".trash", ".trashed", "cache", "thumbnails")
    private val videoExt = setOf("mp4", "mkv", "mov", "3gp", "webm", "m4v", "avi")

    private const val EXT = "/sdcard/Android/data/*/cache /sdcard/Android/data/*/code_cache /sdcard/Android/media/*/cache"
    private const val INT = "/data/data/*/cache /data/data/*/code_cache /data/user_de/0/*/cache"

    fun size(l: List<File>): Long {
        var t = 0L
        for (f in l) t += f.length()
        return t
    }

    fun scan(): Scan {
        val files = ArrayList<File>()
        val junk = ArrayList<File>()
        val apks = ArrayList<File>()
        val empty = ArrayList<File>()
        val stack = ArrayList<Triple<File, Int, Boolean>>()
        stack.add(Triple(root, 0, false))
        while (stack.isNotEmpty()) {
            val (dir, depth, inJunk) = stack.removeAt(stack.size - 1)
            val list = dir.listFiles() ?: continue
            if (list.isEmpty() && depth >= 2) empty.add(dir)
            for (f in list) {
                if (f.isDirectory) {
                    if (depth == 0 && f.name == "Android") continue
                    if (depth < 12) stack.add(Triple(f, depth + 1, inJunk || f.name.lowercase() in junkDirs))
                } else {
                    val ext = f.extension.lowercase()
                    val n = f.name
                    if (ext == "apk") apks.add(f)
                    else if (inJunk || ext in junkExt || n.startsWith(".thumbdata") ||
                        n.startsWith(".trashed-") || n.startsWith(".pending-")
                    ) junk.add(f)
                    else files.add(f)
                }
            }
        }
        return Scan(files, junk, apks, empty)
    }

    fun videos(): List<File> {
        val out = ArrayList<File>()
        val stack = ArrayList<Pair<File, Int>>()
        stack.add(Pair(root, 0))
        while (stack.isNotEmpty()) {
            val (dir, depth) = stack.removeAt(stack.size - 1)
            val list = dir.listFiles() ?: continue
            for (f in list) {
                if (f.isDirectory) {
                    if (!(depth == 0 && f.name == "Android") && depth < 12) stack.add(Pair(f, depth + 1))
                } else if (f.extension.lowercase() in videoExt && f.length() >= 1_000_000L &&
                    !f.name.endsWith("_starshore.mp4")
                ) {
                    out.add(f)
                }
            }
        }
        out.sortByDescending { it.length() }
        return if (out.size > 80) ArrayList(out.subList(0, 80)) else out
    }

    fun analyze(): Analysis {
        val sizes = HashMap<String, Long>()
        val heap = java.util.PriorityQueue<Pair<Long, File>>(
            32, Comparator<Pair<Long, File>> { a, b -> a.first.compareTo(b.first) }
        )
        var total = 0L
        val stack = ArrayList<Triple<File, Int, String>>()
        stack.add(Triple(root, 0, ""))
        while (stack.isNotEmpty()) {
            val (dir, depth, top) = stack.removeAt(stack.size - 1)
            val list = dir.listFiles() ?: continue
            for (f in list) {
                if (f.isDirectory) {
                    if (depth == 0 && f.name == "Android") continue
                    if (depth < 14) stack.add(Triple(f, depth + 1, if (depth == 0) f.name else top))
                } else {
                    val len = f.length()
                    val key = if (depth == 0) "(file di root)" else top
                    sizes[key] = (sizes[key] ?: 0L) + len
                    total += len
                    if (heap.size < 25) heap.add(Pair(len, f))
                    else if (len > heap.peek().first) {
                        heap.poll()
                        heap.add(Pair(len, f))
                    }
                }
            }
        }
        val folders = ArrayList<Pair<String, Long>>()
        for ((k, v) in sizes) folders.add(Pair(k, v))
        folders.sortByDescending { it.second }
        val big = ArrayList<File>()
        for (p in heap) big.add(p.second)
        big.sortByDescending { it.length() }
        return Analysis(if (folders.size > 15) ArrayList(folders.subList(0, 15)) else folders, big, total)
    }

    private fun md5(f: File, limit: Long): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(f).use { ins ->
                val buf = ByteArray(65536)
                var left = limit
                while (left != 0L) {
                    val want = if (left in 1..65535) left.toInt() else 65536
                    val n = ins.read(buf, 0, want)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                    if (left > 0) left -= n
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Salinan yang bisa dihapus + jumlah grup. Satu file asli (paling lama) tiap grup dipertahankan. */
    fun dups(files: List<File>): Pair<List<File>, Int> {
        val bySize = HashMap<Long, MutableList<File>>()
        for (f in files) {
            val l = f.length()
            if (l >= 10240L) bySize.getOrPut(l) { ArrayList() }.add(f)
        }
        val del = ArrayList<File>()
        var groups = 0
        for ((_, g) in bySize) {
            if (g.size < 2) continue
            val quick = HashMap<String, MutableList<File>>()
            for (f in g) {
                val h = md5(f, 65536L) ?: continue
                quick.getOrPut(h) { ArrayList() }.add(f)
            }
            for ((_, q) in quick) {
                if (q.size < 2) continue
                val full = HashMap<String, MutableList<File>>()
                for (f in q) {
                    val h = md5(f, -1L) ?: continue
                    full.getOrPut(h) { ArrayList() }.add(f)
                }
                for ((_, s) in full) {
                    if (s.size < 2) continue
                    s.sortWith(compareBy<File>({ it.lastModified() }, { it.path.length }))
                    groups++
                    for (i in 1 until s.size) del.add(s[i])
                }
            }
        }
        return Pair(del, groups)
    }

    fun delete(l: List<File>): Long {
        var freed = 0L
        for (f in l) {
            val s = f.length()
            if (f.delete()) freed += s
        }
        return freed
    }

    fun deleteDirs(l: List<File>) {
        for (d in l.sortedByDescending { it.path.length }) d.delete()
    }

    // ---- butuh Shizuku / root ----

    private fun cachePaths(): String = if (Sh.mode == Sh.ROOT) "$EXT $INT" else EXT

    fun cacheBytes(): Long {
        val out = Sh.run("du -sk ${cachePaths()} 2>/dev/null")
        var kb = 0L
        for (line in out.lines()) {
            val p = line.trim().split(Regex("\\s+"), 2)
            if (p.size == 2) kb += p[0].toLongOrNull() ?: 0L
        }
        return kb * 1024L
    }

    fun cleanCache() {
        val g = cachePaths().split(" ").joinToString(" ") { "$it/*" }
        Sh.run("rm -rf $g")
        Sh.run("pm trim-caches 999999999999")
    }

    fun cleanLogs() {
        Sh.run("logcat -b all -c")
        if (Sh.mode == Sh.ROOT) {
            Sh.run("rm -rf /data/anr/* /data/tombstones/* /data/system/dropbox/* /data/misc/logd/*")
        }
    }

    /** Sisa data aplikasi yang sudah dihapus: folder paket di Android/data, obb, media. */
    fun corpses(isInstalled: (String) -> Boolean): Corpse {
        val bases = listOf("/sdcard/Android/data", "/sdcard/Android/obb", "/sdcard/Android/media")
        val pat = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        val found = ArrayList<String>()
        for (b in bases) {
            val out = Sh.run("ls -1 $b 2>/dev/null")
            for (line in out.lines()) {
                val n = line.trim()
                if (pat.matches(n) && !isInstalled(n)) found.add("$b/$n")
            }
        }
        if (found.isEmpty()) return Corpse(found, 0L, "")
        val du = Sh.run("du -sk " + found.joinToString(" "))
        val sizes = HashMap<String, Long>()
        for (line in du.lines()) {
            val p = line.trim().split(Regex("\\s+"), 2)
            if (p.size == 2) sizes[p[1].trim()] = (p[0].toLongOrNull() ?: 0L) * 1024L
        }
        var total = 0L
        val sb = StringBuilder()
        for (path in found.sortedByDescending { sizes[it] ?: 0L }) {
            val s = sizes[path] ?: 0L
            total += s
            sb.append(path.removePrefix("/sdcard/")).append("  (").append(fmt(s)).append(")\n")
        }
        return Corpse(found, total, sb.toString())
    }

    /** Bungkus path untuk sh -c dengan aman: kutip tunggal, dan kutip di dalam path di-escape. */
    private fun shQuote(p: String): String = "'" + p.replace("'", "'\\''") + "'"

    fun deleteCorpses(paths: List<String>) {
        if (paths.isEmpty()) return
        // Quote dulu supaya nama file berisi tanda kutip tidak menutup kutip shell
        Sh.run("rm -rf " + paths.joinToString(" ") { shQuote(it) })
    }

    fun trim(): String {
        val cmd = if (Sh.mode == Sh.ROOT) {
            "if command -v fstrim >/dev/null 2>&1; then " +
                "for p in /data /cache /system /vendor /product; do fstrim -v \$p 2>&1; done; " +
                "else sm fstrim 2>&1 || sm idle-maint run 2>&1; fi"
        } else {
            "sm fstrim 2>&1 || sm idle-maint run 2>&1"
        }
        return Sh.run(cmd).trim()
    }
}
