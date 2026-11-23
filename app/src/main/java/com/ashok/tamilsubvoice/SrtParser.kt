package com.ashok.tamilsubvoice

data class Cue(val startMs: Long, val endMs: Long, val text: String, var tamil: String = "")

object SrtParser {

    fun parse(content: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        val blocks = content.replace("\r", "").trim().split(Regex("\n\\s*\n"))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            val timeIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeIndex == -1) continue
            val parts = lines[timeIndex].split("-->")
            if (parts.size != 2) continue
            val start = toMs(parts[0].trim())
            val end = toMs(parts[1].trim())
            val text = lines.drop(timeIndex + 1)
                .joinToString(" ")
                .replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\{[^}]*\\}"), "")
                .trim()
            if (text.isNotEmpty()) cues.add(Cue(start, end, text))
        }
        return cues.sortedBy { it.startMs }
    }

    private fun toMs(t: String): Long {
        val clean = t.replace(',', '.')
        val p = clean.split(':')
        if (p.size != 3) return 0L
        val h = p[0].toLongOrNull() ?: 0L
        val m = p[1].toLongOrNull() ?: 0L
        val sec = p[2].toDoubleOrNull() ?: 0.0
        return h * 3600000L + m * 60000L + (sec * 1000).toLong()
    }
}
