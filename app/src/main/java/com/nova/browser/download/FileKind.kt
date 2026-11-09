package com.nova.browser

import java.net.URLDecoder

object FileKind {
    private val star = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
    private val quoted = Regex("""filename\s*=\s*\x22((?:\\.|[^\x22\\])*)\x22""", RegexOption.IGNORE_CASE)
    private val plain = Regex("""filename\s*=\s*([^;\x22']+)""", RegexOption.IGNORE_CASE)
    private val urlKeys = listOf("filename", "file_name", "file", "fn", "name", "download", "attname")
    private val weak = setOf("bin", "php", "asp", "aspx", "jsp", "cgi", "do", "action", "ashx", "download", "tmp", "dat", "unknown", "octet-stream")

    private fun pct(s: String, cs: String): String =
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), cs.ifBlank { "UTF-8" }) }.getOrDefault(s)

    fun clean(n: String): String {
        var s = n.trim().trim('"', '\'')
        s = s.substringAfterLast('/').substringAfterLast('\\')
        s = s.filter { it.code >= 32 && it !in "<>:\"|?*" }
        s = s.trim().trimEnd('.')
        if (s.length > 120) {
            val e = s.substringAfterLast('.', "")
            s = if (e.length in 1..8) s.take(110) + "." + e else s.take(120)
        }
        return s
    }

    fun hasExt(n: String): Boolean {
        val e = n.substringAfterLast('.', "")
        return n.contains('.') && e.length in 1..8 && e.all { it.isLetterOrDigit() } && e.any { it.isLetter() }
    }

    fun nameFromHeader(cd: String?): String? {
        if (cd.isNullOrBlank()) return null
        star.find(cd)?.let { m ->
            clean(pct(m.groupValues[2].trim(), m.groupValues[1].trim())).takeIf { it.isNotEmpty() }?.let { return it }
        }
        quoted.find(cd)?.let { m ->
            clean(m.groupValues[1].replace("\\\"", "\"")).takeIf { it.isNotEmpty() }?.let { return it }
        }
        plain.find(cd)?.let { m -> clean(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { return it } }
        return null
    }

    fun nameFromUrl(url: String): String? {
        val q = url.substringAfter('?', "").substringBefore('#')
        val params = q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i < 0) null else it.substring(0, i).lowercase() to pct(it.substring(i + 1), "UTF-8")
        }
        params.firstOrNull { it.first == "response-content-disposition" }?.let { p -> nameFromHeader(p.second)?.let { return it } }
        for (k in urlKeys) {
            val v = params.firstOrNull { it.first == k }?.second ?: continue
            val n = clean(v)
            if (hasExt(n)) return n
        }
        val n = clean(pct(url.substringBefore('?').substringBefore('#').substringAfterLast('/'), "UTF-8"))
        return if (hasExt(n)) n else null
    }

    fun needsSniff(name: String): Boolean {
        if (!name.contains('.')) return true
        return name.substringAfterLast('.').lowercase() in weak
    }

    fun withExt(name: String, ext: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0 && name.substring(dot + 1).lowercase() in weak) name.substring(0, dot) else name
        return base.ifBlank { "download" } + "." + ext
    }

    private fun le16(b: ByteArray, p: Int): Int = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)

    private fun le32(b: ByteArray, p: Int): Long =
        (le16(b, p).toLong()) or (le16(b, p + 2).toLong() shl 16)

    private fun at(b: ByteArray, p: Int, vararg v: Int): Boolean {
        if (p < 0 || p + v.size > b.size) return false
        for (i in v.indices) if ((b[p + i].toInt() and 0xff) != v[i]) return false
        return true
    }

    private fun ascii(b: ByteArray, p: Int, s: String): Boolean {
        if (p < 0 || p + s.length > b.size) return false
        for (i in s.indices) if (b[p + i].toInt().toChar() != s[i]) return false
        return true
    }

    fun zipNames(head: ByteArray, tail: ByteArray, size: Long): List<String> {
        val out = ArrayList<String>()
        var e = tail.size - 22
        val min = maxOf(0, tail.size - 22 - 65535)
        while (e >= min && le32(tail, e) != 0x06054b50L) e--
        if (e >= min && e >= 0) {
            val count = le16(tail, e + 10)
            val cdOff = le32(tail, e + 16)
            val pl = cdOff - (size - tail.size)
            if (pl >= 0 && pl <= tail.size) {
                var p = pl.toInt()
                var i = 0
                while (i < count && p + 46 <= tail.size && le32(tail, p) == 0x02014b50L && out.size < 8000) {
                    val n = le16(tail, p + 28)
                    val m = le16(tail, p + 30)
                    val k = le16(tail, p + 32)
                    if (p + 46 + n > tail.size) break
                    out.add(String(tail, p + 46, n, Charsets.UTF_8))
                    p += 46 + n + m + k
                    i++
                }
                if (out.isNotEmpty()) return out
            }
        }
        var p = 0
        while (p + 30 <= head.size && le32(head, p) == 0x04034b50L && out.size < 2000) {
            val flags = le16(head, p + 6)
            val comp = le32(head, p + 18)
            val n = le16(head, p + 26)
            val m = le16(head, p + 28)
            if (p + 30 + n > head.size) break
            out.add(String(head, p + 30, n, Charsets.UTF_8))
            if (flags and 8 != 0 && comp == 0L) break
            val next = p.toLong() + 30 + n + m + comp
            if (next >= head.size) break
            p = next.toInt()
        }
        return out
    }

    fun classifyZip(names: List<String>, head: ByteArray): String {
        val l = names.map { it.lowercase() }
        fun has(x: String) = l.contains(x)
        val apks = l.any { it.endsWith(".apk") }
        return when {
            has("bundleconfig.pb") -> "aab"
            l.any { it.endsWith("androidmanifest.xml") } && (l.any { it.endsWith(".dex") } || has("resources.arsc")) -> "apk"
            has("manifest.json") && apks -> "xapk"
            has("info.json") && apks -> "apkm"
            apks && (has("toc.pb") || has("base.apk")) -> "apks"
            has("[content_types].xml") -> when {
                l.any { it.startsWith("word/") } -> "docx"
                l.any { it.startsWith("xl/") } -> "xlsx"
                l.any { it.startsWith("ppt/") } -> "pptx"
                else -> "zip"
            }
            has("meta-inf/manifest.mf") -> "jar"
            has("mimetype") && String(head, 0, minOf(head.size, 120), Charsets.ISO_8859_1).contains("application/epub+zip") -> "epub"
            else -> "zip"
        }
    }

    fun sniff(head: ByteArray, tail: ByteArray, size: Long): String? {
        if (head.size < 4) return null
        return when {
            at(head, 0, 0x50, 0x4B, 0x03, 0x04) -> classifyZip(zipNames(head, tail, size), head)
            ascii(head, 0, "%PDF") -> "pdf"
            at(head, 0, 0x89, 0x50, 0x4E, 0x47) -> "png"
            at(head, 0, 0xFF, 0xD8, 0xFF) -> "jpg"
            ascii(head, 0, "GIF8") -> "gif"
            ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP") -> "webp"
            ascii(head, 0, "RIFF") && ascii(head, 8, "WAVE") -> "wav"
            ascii(head, 0, "RIFF") && ascii(head, 8, "AVI ") -> "avi"
            ascii(head, 4, "ftyp") -> when {
                ascii(head, 8, "M4A ") -> "m4a"
                ascii(head, 8, "qt  ") -> "mov"
                ascii(head, 8, "heic") || ascii(head, 8, "heix") || ascii(head, 8, "mif1") -> "heic"
                ascii(head, 8, "3gp") -> "3gp"
                else -> "mp4"
            }
            ascii(head, 0, "ID3") || at(head, 0, 0xFF, 0xFB) || at(head, 0, 0xFF, 0xF3) || at(head, 0, 0xFF, 0xF2) -> "mp3"
            ascii(head, 0, "OggS") -> "ogg"
            ascii(head, 0, "fLaC") -> "flac"
            at(head, 0, 0x1A, 0x45, 0xDF, 0xA3) ->
                if (String(head, 0, minOf(head.size, 64), Charsets.ISO_8859_1).contains("webm")) "webm" else "mkv"
            at(head, 0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> "7z"
            ascii(head, 0, "Rar!") -> "rar"
            at(head, 0, 0x1F, 0x8B) -> "gz"
            ascii(head, 0, "BZh") -> "bz2"
            at(head, 0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> "xz"
            ascii(head, 0, "dex\n") -> "dex"
            ascii(head, 0, "SQLite format 3") -> "db"
            ascii(head, 257, "ustar") -> "tar"
            ascii(head, 0, "{\\rtf") -> "rtf"
            at(head, 0, 0x4D, 0x5A) -> "exe"
            else -> {
                val t = String(head, 0, minOf(head.size, 256), Charsets.ISO_8859_1).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
                when {
                    t.startsWith("<!doctype html") || t.startsWith("<html") -> "html"
                    t.startsWith("<?xml") -> "xml"
                    else -> null
                }
            }
        }
    }
}
