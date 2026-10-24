package zuiwan.lyrics

import zuiwan.model.LyricLine
import zuiwan.model.LyricPayload
import zuiwan.model.LyricWord

object LyricParser {
    private val lineRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>([^<\[]+)""")

    fun parselyrics(
        raw: String,
        source: String,
        romanize: Boolean = true,
    ): LyricPayload {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return LyricPayload(source = source)

        val timed = mutableListOf<LyricLine>()
        val rawLines = trimmed.lines()

        for (rawLine in rawLines) {
            val lineMatches = lineRegex.findAll(rawLine).toList()
            if (lineMatches.isEmpty()) continue
            val contentPart = rawLine.substring(lineMatches.last().range.last + 1).trim()
            val wordMatches = wordRegex.findAll(contentPart).toList()

            for (m in lineMatches) {
                val startMs = toms(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                if (wordMatches.isNotEmpty()) {
                    val words = wordMatches.mapIndexed { idx, wm ->
                        val wStart = toms(wm.groupValues[1], wm.groupValues[2], wm.groupValues[3])
                        val wNext = wordMatches.getOrNull(idx + 1)?.let {
                            toms(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                        } ?: (wStart + 450L)
                        LyricWord(
                            text = wm.groupValues[4],
                            startMs = wStart,
                            endMs = wNext.coerceAtLeast(wStart + 80L),
                        )
                    }
                    val fullText = words.joinToString("") { it.text }.trim()
                    timed += LyricLine(
                        timeMs = startMs,
                        endMs = words.lastOrNull()?.endMs ?: (startMs + 3_000L),
                        text = fullText,
                        romanized = if (romanize) Romanizer.romanizeline(fullText) else null,
                        words = words,
                    )
                } else {
                    timed += LyricLine(
                        timeMs = startMs,
                        text = contentPart,
                        romanized = if (romanize && contentPart.isNotBlank()) Romanizer.romanizeline(contentPart) else null,
                    )
                }
            }
        }

        if (timed.size >= 2) {
            val sorted = timed.sortedBy { it.timeMs }
            val merged = mutableListOf<LyricLine>()
            var idx = 0
            while (idx < sorted.size) {
                val cur = sorted[idx]
                val next = sorted.getOrNull(idx + 1)
                if (next != null && next.timeMs == cur.timeMs && cur.words.isEmpty() && next.words.isEmpty() && cur.text.isNotBlank() && next.text.isNotBlank()) {
                    merged += cur.copy(
                        translated = next.text,
                        endMs = sorted.getOrNull(idx + 2)?.timeMs ?: (cur.timeMs + 4_000L),
                    )
                    idx += 2
                } else {
                    val computedEnd = if (cur.endMs > cur.timeMs) {
                        cur.endMs
                    } else {
                        next?.timeMs ?: (cur.timeMs + 4_500L)
                    }
                    merged += cur.copy(endMs = computedEnd)
                    idx++
                }
            }
            val hasWords = merged.any { it.words.isNotEmpty() }
            return LyricPayload(
                source = source,
                synced = true,
                wordSynced = hasWords,
                lines = merged,
                raw = trimmed,
            )
        }

        val plainLines = rawLines
            .map { it.replace(lineRegex, "").trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { idx, txt ->
                LyricLine(
                    timeMs = idx * 3_500L,
                    endMs = (idx + 1) * 3_500L,
                    text = txt,
                    romanized = if (romanize) Romanizer.romanizeline(txt) else null,
                )
            }
        return LyricPayload(
            source = source,
            synced = false,
            wordSynced = false,
            lines = plainLines,
            raw = trimmed,
        )
    }

    fun activeline(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var ans = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs + 80L) {
                ans = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return ans
    }

    private fun toms(minStr: String, secStr: String, fracStr: String): Long {
        val m = minStr.toLongOrNull() ?: 0L
        val s = secStr.toLongOrNull() ?: 0L
        val ms = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return m * 60_000L + s * 1_000L + ms
    }
}
