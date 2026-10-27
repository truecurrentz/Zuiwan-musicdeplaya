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

    fun syllablesfor(line: LyricLine): List<LyricWord> {
        if (line.text.isBlank()) return emptyList()
        if (line.words.isNotEmpty()) {
            val out = ArrayList<LyricWord>(line.words.size * 2)
            for (w in line.words) {
                val pieces = splitsyl(w.text)
                if (pieces.size <= 1) {
                    out += w
                } else {
                    val span = (w.endMs - w.startMs).coerceAtLeast(pieces.size * 40L)
                    val totalWeight = pieces.sumOf { sylweight(it) }.coerceAtLeast(1)
                    var cursor = w.startMs
                    pieces.forEachIndexed { idx, piece ->
                        val dur = if (idx == pieces.lastIndex) {
                            (w.endMs - cursor).coerceAtLeast(30L)
                        } else {
                            (span * sylweight(piece) / totalWeight).coerceAtLeast(30L)
                        }
                        out += LyricWord(piece, cursor, cursor + dur)
                        cursor += dur
                    }
                }
            }
            return out
        }

        val pieces = splitsyl(line.text)
        if (pieces.isEmpty()) return emptyList()
        val rawSpan = (line.endMs - line.timeMs).coerceIn(600L, 12_000L)
        val activeSpan = (rawSpan * 86L / 100L).coerceAtLeast(pieces.size * 50L)
        val totalWeight = pieces.sumOf { sylweight(it) }.coerceAtLeast(1)
        val out = ArrayList<LyricWord>(pieces.size)
        var cursor = line.timeMs
        pieces.forEachIndexed { idx, piece ->
            val dur = if (idx == pieces.lastIndex) {
                (line.timeMs + activeSpan - cursor).coerceAtLeast(45L)
            } else {
                (activeSpan * sylweight(piece) / totalWeight).coerceAtLeast(45L)
            }
            out += LyricWord(piece, cursor, cursor + dur)
            cursor += dur
        }
        return out
    }

    private fun sylweight(piece: String): Int {
        val trimmed = piece.trim()
        if (trimmed.isEmpty()) return 1
        var w = trimmed.length.coerceIn(1, 6)
        val last = trimmed.last()
        if (last == ',' || last == ';' || last == '—') w += 3
        if (last == '.' || last == '!' || last == '?') w += 4
        return w
    }

    private fun splitsyl(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val tokens = ArrayList<String>()
        val tokenRegex = Regex("""\S+\s*|\s+""")
        for (m in tokenRegex.findAll(text)) {
            val tok = m.value
            if (tok.isBlank()) {
                if (tokens.isNotEmpty()) {
                    tokens[tokens.lastIndex] = tokens.last() + tok
                } else {
                    tokens += tok
                }
                continue
            }
            val trailingWs = tok.takeLastWhile { it.isWhitespace() }
            val core = tok.dropLast(trailingWs.length)
            val coreSyls = splitword(core)
            coreSyls.forEachIndexed { i, s ->
                tokens += if (i == coreSyls.lastIndex) s + trailingWs else s
            }
        }
        return tokens
    }

    private fun splitword(word: String): List<String> {
        if (word.length <= 3) return listOf(word)
        val hasCjk = word.any { ch ->
            Character.UnicodeBlock.of(ch) in setOf(
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES,
            )
        }
        if (hasCjk) {
            return word.map { it.toString() }
        }
        val vowels = "aeiouyAEIOUY"
        val parts = ArrayList<String>()
        val buf = StringBuilder()
        var seenVowel = false
        for (i in word.indices) {
            val ch = word[i]
            buf.append(ch)
            val isV = vowels.indexOf(ch) >= 0
            if (isV) {
                seenVowel = true
            } else if (seenVowel && i < word.lastIndex - 1) {
                val nextIsV = vowels.indexOf(word[i + 1]) >= 0
                if (nextIsV && buf.length >= 2) {
                    val tail = buf.last()
                    buf.setLength(buf.length - 1)
                    parts += buf.toString()
                    buf.setLength(0)
                    buf.append(tail)
                    seenVowel = false
                } else if (!nextIsV && buf.length >= 3 && i + 2 <= word.lastIndex && vowels.indexOf(word[i + 2]) >= 0) {
                    parts += buf.toString()
                    buf.setLength(0)
                    seenVowel = false
                }
            }
        }
        if (buf.isNotEmpty()) {
            if (parts.isNotEmpty() && buf.none { vowels.indexOf(it) >= 0 }) {
                parts[parts.lastIndex] = parts.last() + buf.toString()
            } else {
                parts += buf.toString()
            }
        }
        return if (parts.isEmpty()) listOf(word) else parts
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
