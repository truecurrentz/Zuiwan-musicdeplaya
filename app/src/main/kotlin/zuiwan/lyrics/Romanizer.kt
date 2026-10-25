package zuiwan.lyrics

import com.github.promeg.pinyinhelper.Pinyin

object Romanizer {
    private val hangulOnset = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
        "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
    )
    private val hangulVowel = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
    )
    private val hangulCoda = arrayOf(
        "", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "p", "l", "l",
        "p", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t",
    )

    private val kanaMap = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo", 'っ' to "",
    )

    private val cyrillicMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    fun needsromanize(text: String): Boolean =
        text.any { c ->
            c.code in 0x3040..0x30FF ||
                c.code in 0x4E00..0x9FFF ||
                c.code in 0xAC00..0xD7A3 ||
                c.code in 0x0400..0x04FF
        }

    fun romanizeline(text: String): String? {
        if (!needsromanize(text)) return null
        val hasKana = text.any { it.code in 0x3040..0x30FF }
        val sb = StringBuilder()

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.code in 0xAC00..0xD7A3 -> {
                    val code = ch.code - 0xAC00
                    val onset = code / (21 * 28)
                    val vowel = (code % (21 * 28)) / 28
                    val coda = code % 28
                    sb.append(hangulOnset[onset]).append(hangulVowel[vowel]).append(hangulCoda[coda])
                }

                ch.code in 0x3040..0x30FF -> {
                    val hira = if (ch.code in 0x30A1..0x30F6) (ch.code - 0x60).toChar() else ch
                    if (hira == 'っ' && i + 1 < text.length) {
                        val nextChar = text[i + 1]
                        val nextHira = if (nextChar.code in 0x30A1..0x30F6) (nextChar.code - 0x60).toChar() else nextChar
                        val nextRom = kanaMap[nextHira]
                        if (!nextRom.isNullOrEmpty()) sb.append(nextRom[0])
                    } else {
                        val base = kanaMap[hira]
                        if (base != null) {
                            val nextRaw = text.getOrNull(i + 1)
                            val nextHira = nextRaw?.let { if (it.code in 0x30A1..0x30F6) (it.code - 0x60).toChar() else it }
                            if (nextHira in setOf('ゃ', 'ゅ', 'ょ') && base.length == 2 && base.endsWith('i')) {
                                sb.append(base.dropLast(1)).append(kanaMap[nextHira])
                                i++
                            } else {
                                sb.append(base)
                            }
                        } else if (ch == 'ー' && sb.isNotEmpty()) {
                            sb.append(sb.last())
                        } else {
                            sb.append(ch)
                        }
                    }
                }

                !hasKana && Pinyin.isChinese(ch) -> {
                    val py = Pinyin.toPinyin(ch).lowercase()
                    if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    sb.append(py)
                    if (i + 1 < text.length && text[i + 1] != ' ') sb.append(' ')
                }

                ch.code in 0x0400..0x04FF -> {
                    val lower = ch.lowercaseChar()
                    val mapped = cyrillicMap[lower]
                    if (mapped != null) {
                        sb.append(if (ch.isUpperCase()) mapped.replaceFirstChar { it.uppercase() } else mapped)
                    } else {
                        sb.append(ch)
                    }
                }

                else -> sb.append(ch)
            }
            i++
        }

        val out = sb.toString().replace(Regex("""\s+"""), " ").trim()
        return out.takeIf { it.isNotBlank() && !it.equals(text.trim(), ignoreCase = true) }
    }
}
