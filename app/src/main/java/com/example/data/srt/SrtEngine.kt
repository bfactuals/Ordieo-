package com.example.data.srt

import com.example.data.model.AutoCorrectRule
import com.example.data.model.TextCasing
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordChunkMode
import java.util.Locale

data class FormattedCue(
    val index: Int,
    val sourceSegmentId: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SrtEngine {

    private val punctuationRegex = Regex("""[,.;:!?"'“”‘’।|—–\-()\[\]{}]""")
    private val multiSpaceRegex = Regex("""\s+""")
    private val fillerWordsRegex = Regex(
        """(?i)(?<=^|[\s,.;:!?])(umm+|um+|uhh+|uh+|ahh+|ah+|hmm+|hm+|err+|matlab|मतलब)(?=$|[\s,.;:!?])"""
    )
    private val srtTimeLineRegex = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    fun formatTimestampSrt(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        val millis = clamped % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatTimestampCompact(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSeconds = clamped / 1_000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val tenths = (clamped % 1_000L) / 100L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
        }
    }

    fun formatDurationShort(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSeconds = clamped / 1_000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun applyAutoCorrectDictionary(
        segments: List<TranscriptSegment>,
        rules: List<AutoCorrectRule>
    ): List<TranscriptSegment> {
        if (rules.isEmpty() || segments.isEmpty()) return segments
        val compiled = rules
            .filter { it.wrong.isNotBlank() && it.correct.isNotBlank() }
            .map { rule ->
                val pattern = Regex("(?i)(?<!\\w)${Regex.escape(rule.wrong.trim())}(?!\\w)")
                pattern to rule.correct.trim()
            }
        if (compiled.isEmpty()) return segments

        return segments.map { seg ->
            var updated = seg.text
            compiled.forEach { (regex, replacement) ->
                updated = regex.replace(updated, Regex.escapeReplacement(replacement))
            }
            if (updated == seg.text) seg else seg.copy(text = updated, words = emptyList())
        }
    }

    fun formatText(
        raw: String,
        removeFillerWords: Boolean = false,
        stripPunctuation: Boolean = false,
        casing: TextCasing = TextCasing.ORIGINAL
    ): String {
        var result = raw.trim()
        if (removeFillerWords) {
            result = result.replace(fillerWordsRegex, " ")
                .replace(Regex("""^[\s,.;:!?]+"""), "")
                .replace(Regex("""\s+([,.;:!?])"""), "$1")
                .replace(Regex("""([,.;:!?])\s*[,.;:!?]+"""), "$1")
                .replace(multiSpaceRegex, " ")
                .trim()
        }
        if (stripPunctuation) {
            result = result.replace(punctuationRegex, " ")
                .replace(multiSpaceRegex, " ")
                .trim()
        }
        result = when (casing) {
            TextCasing.ORIGINAL -> result
            TextCasing.ALL_CAPS -> result.uppercase(Locale.getDefault())
            TextCasing.LOWERCASE -> result.lowercase(Locale.getDefault())
            TextCasing.TITLE_CASE -> toTitleCase(result)
        }
        return result
    }

    private fun toTitleCase(input: String): String {
        if (input.isBlank()) return input
        return input.split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) {
                word
            } else {
                val first = word.substring(0, 1).uppercase(Locale.getDefault())
                val rest = word.substring(1).lowercase(Locale.getDefault())
                first + rest
            }
        }
    }

    /**
     * Generates chunked, formatted, and timestamp-shifted cues used by both
     * the Live SRT Caption Preview and the exported .SRT file.
     */
    fun buildFormattedCues(
        segments: List<TranscriptSegment>,
        wordChunkMode: WordChunkMode,
        removeFillerWords: Boolean = false,
        stripPunctuation: Boolean = false,
        casing: TextCasing = TextCasing.ORIGINAL,
        timestampShiftMs: Long = 0L
    ): List<FormattedCue> {
        val result = mutableListOf<FormattedCue>()
        var cueIndex = 1

        for (seg in segments) {
            val shiftedStart = (seg.startMs + timestampShiftMs).coerceAtLeast(0L)
            val shiftedEnd = (seg.endMs + timestampShiftMs).coerceAtLeast(shiftedStart + 200L)
            val maxWords = wordChunkMode.maxWords

            val cleanedForChunking = formatText(
                raw = seg.text,
                removeFillerWords = removeFillerWords,
                stripPunctuation = stripPunctuation,
                casing = TextCasing.ORIGINAL
            )

            if (cleanedForChunking.isBlank()) continue

            if (maxWords == null) {
                val formatted = formatText(
                    raw = cleanedForChunking,
                    removeFillerWords = false,
                    stripPunctuation = false,
                    casing = casing
                )
                if (formatted.isNotBlank()) {
                    result.add(
                        FormattedCue(
                            index = cueIndex++,
                            sourceSegmentId = seg.id,
                            startMs = shiftedStart,
                            endMs = shiftedEnd,
                            text = formatted
                        )
                    )
                }
                continue
            }

            val tokens = cleanedForChunking.split(multiSpaceRegex).filter { it.isNotBlank() }
            if (tokens.isEmpty()) continue

            val useWordTimings = !removeFillerWords && !stripPunctuation && seg.words.size == tokens.size && seg.words.isNotEmpty()
            val groups = tokens.chunked(maxWords)

            if (useWordTimings) {
                var offset = 0
                for (group in groups) {
                    val firstWord = seg.words[offset]
                    val lastWord = seg.words[offset + group.size - 1]
                    offset += group.size
                    val cStart = (firstWord.startMs + timestampShiftMs).coerceAtLeast(0L)
                    val cEnd = (lastWord.endMs + timestampShiftMs).coerceAtLeast(cStart + 150L)
                    val formatted = formatText(
                        raw = group.joinToString(" "),
                        removeFillerWords = false,
                        stripPunctuation = stripPunctuation,
                        casing = casing
                    )
                    if (formatted.isNotBlank()) {
                        result.add(
                            FormattedCue(
                                index = cueIndex++,
                                sourceSegmentId = seg.id,
                                startMs = cStart,
                                endMs = cEnd,
                                text = formatted
                            )
                        )
                    }
                }
            } else {
                val totalChars = groups.sumOf { g -> g.sumOf { it.length }.coerceAtLeast(1) }
                val totalDuration = (shiftedEnd - shiftedStart).coerceAtLeast(200L)
                var cursor = shiftedStart

                groups.forEachIndexed { idx, group ->
                    val groupWeight = group.sumOf { it.length }.coerceAtLeast(1)
                    val sliceDuration = if (idx == groups.lastIndex) {
                        (shiftedEnd - cursor).coerceAtLeast(120L)
                    } else {
                        ((totalDuration * groupWeight) / totalChars).coerceAtLeast(120L)
                    }
                    val cEnd = (cursor + sliceDuration).coerceAtMost(shiftedEnd.coerceAtLeast(cursor + 120L))
                    val formatted = formatText(
                        raw = group.joinToString(" "),
                        removeFillerWords = false,
                        stripPunctuation = stripPunctuation,
                        casing = casing
                    )
                    if (formatted.isNotBlank()) {
                        result.add(
                            FormattedCue(
                                index = cueIndex++,
                                sourceSegmentId = seg.id,
                                startMs = cursor,
                                endMs = cEnd,
                                text = formatted
                            )
                        )
                    }
                    cursor = cEnd
                }
            }
        }
        return result
    }

    fun findActiveCue(cues: List<FormattedCue>, positionMs: Long): FormattedCue? {
        if (cues.isEmpty()) return null
        cues.firstOrNull { positionMs in it.startMs..it.endMs }?.let { return it }
        return cues.lastOrNull { it.endMs <= positionMs && (positionMs - it.endMs) <= 350L }
    }

    /**
     * Resolves the currently spoken word index inside a cue for real-time Active Word Highlighting.
     */
    fun findActiveWordIndex(cue: FormattedCue, positionMs: Long): Int {
        val words = cue.text.split(multiSpaceRegex).filter { it.isNotBlank() }
        if (words.size <= 1) return 0
        if (positionMs <= cue.startMs) return 0
        if (positionMs >= cue.endMs) return words.lastIndex

        val duration = (cue.endMs - cue.startMs).coerceAtLeast(1L)
        val progress = ((positionMs - cue.startMs).toDouble() / duration.toDouble()).coerceIn(0.0, 0.999)
        val totalWeight = words.sumOf { it.length.coerceAtLeast(2) }.toDouble()
        var acc = 0.0
        words.forEachIndexed { idx, word ->
            acc += word.length.coerceAtLeast(2) / totalWeight
            if (progress <= acc) return idx
        }
        return words.lastIndex
    }

    fun exportSrt(
        segments: List<TranscriptSegment>,
        wordChunkMode: WordChunkMode,
        removeFillerWords: Boolean = false,
        stripPunctuation: Boolean = false,
        casing: TextCasing = TextCasing.ORIGINAL,
        timestampShiftMs: Long = 0L
    ): String {
        val cues = buildFormattedCues(
            segments = segments,
            wordChunkMode = wordChunkMode,
            removeFillerWords = removeFillerWords,
            stripPunctuation = stripPunctuation,
            casing = casing,
            timestampShiftMs = timestampShiftMs
        )
        return buildString {
            cues.forEachIndexed { idx, cue ->
                append(idx + 1)
                append('\n')
                append(formatTimestampSrt(cue.startMs))
                append(" --> ")
                append(formatTimestampSrt(cue.endMs))
                append('\n')
                append(cue.text)
                append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    fun exportPlainText(
        segments: List<TranscriptSegment>,
        removeFillerWords: Boolean = false,
        stripPunctuation: Boolean = false,
        casing: TextCasing = TextCasing.ORIGINAL
    ): String {
        return segments
            .map { formatText(it.text, removeFillerWords, stripPunctuation, casing) }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun parseSrtOrPlainText(input: String): Pair<List<TranscriptSegment>, Boolean> {
        val normalized = input.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return Pair(emptyList(), false)

        if (srtTimeLineRegex.containsMatchIn(normalized)) {
            val parsed = parseSrt(normalized)
            if (parsed.isNotEmpty()) {
                return Pair(parsed, true)
            }
        }

        val lines = normalized
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val segments = mutableListOf<TranscriptSegment>()
        var cursorMs = 0L
        lines.forEachIndexed { idx, line ->
            val wordCount = line.split(multiSpaceRegex).size.coerceAtLeast(1)
            val estDurationMs = (wordCount * 420L).coerceIn(1800L, 6500L)
            segments.add(
                TranscriptSegment(
                    id = idx + 1,
                    startMs = cursorMs,
                    endMs = cursorMs + estDurationMs,
                    text = line
                )
            )
            cursorMs += estDurationMs + 150L
        }
        return Pair(segments, false)
    }

    private fun parseSrt(content: String): List<TranscriptSegment> {
        val blocks = content.split(Regex("""\n\s*\n"""))
        val segments = mutableListOf<TranscriptSegment>()
        var nextId = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timeLineIndex = lines.indexOfFirst { srtTimeLineRegex.containsMatchIn(it) }
            if (timeLineIndex == -1) continue

            val match = srtTimeLineRegex.find(lines[timeLineIndex]) ?: continue
            val startMs = toMillis(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4]
            )
            val endMs = toMillis(
                match.groupValues[5],
                match.groupValues[6],
                match.groupValues[7],
                match.groupValues[8]
            )
            val textLines = lines.drop(timeLineIndex + 1).joinToString(" ").trim()
            if (textLines.isNotEmpty()) {
                segments.add(
                    TranscriptSegment(
                        id = nextId++,
                        startMs = startMs,
                        endMs = endMs.coerceAtLeast(startMs + 200L),
                        text = textLines
                    )
                )
            }
        }
        return segments
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        val paddedMs = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return (h.toLongOrNull() ?: 0L) * 3_600_000L +
            (m.toLongOrNull() ?: 0L) * 60_000L +
            (s.toLongOrNull() ?: 0L) * 1_000L +
            paddedMs
    }

    fun countMatches(segments: List<TranscriptSegment>, query: String): Int {
        if (query.isEmpty() || segments.isEmpty()) return 0
        val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        return segments.sumOf { seg -> regex.findAll(seg.text).count() }
    }

    fun splitSegment(
        segments: List<TranscriptSegment>,
        segmentId: Int
    ): List<TranscriptSegment> {
        val idx = segments.indexOfFirst { it.id == segmentId }
        if (idx == -1) return segments
        val target = segments[idx]
        val words = target.text.trim().split(multiSpaceRegex).filter { it.isNotBlank() }
        if (words.size < 2) return segments

        val midWordIndex = words.size / 2
        val firstText = words.take(midWordIndex).joinToString(" ")
        val secondText = words.drop(midWordIndex).joinToString(" ")

        val totalChars = (firstText.length + secondText.length).coerceAtLeast(1)
        val totalDuration = (target.endMs - target.startMs).coerceAtLeast(400L)
        val midMs = (target.startMs + (totalDuration * firstText.length) / totalChars)
            .coerceIn(target.startMs + 200L, target.endMs - 200L)

        val firstSeg = target.copy(endMs = midMs, text = firstText, words = emptyList())
        val secondSeg = target.copy(startMs = midMs, text = secondText, words = emptyList())

        val updated = segments.toMutableList()
        updated.removeAt(idx)
        updated.add(idx, secondSeg)
        updated.add(idx, firstSeg)
        return reindexSegments(updated)
    }

    fun mergeWithNextSegment(
        segments: List<TranscriptSegment>,
        segmentId: Int
    ): List<TranscriptSegment> {
        val idx = segments.indexOfFirst { it.id == segmentId }
        if (idx == -1 || idx >= segments.lastIndex) return segments
        val current = segments[idx]
        val next = segments[idx + 1]
        val merged = TranscriptSegment(
            id = current.id,
            startMs = current.startMs,
            endMs = next.endMs.coerceAtLeast(current.endMs),
            text = "${current.text.trim()} ${next.text.trim()}".trim(),
            words = emptyList()
        )
        val updated = segments.toMutableList()
        updated[idx] = merged
        updated.removeAt(idx + 1)
        return reindexSegments(updated)
    }

    fun deleteSegment(
        segments: List<TranscriptSegment>,
        segmentId: Int
    ): List<TranscriptSegment> {
        val filtered = segments.filterNot { it.id == segmentId }
        return reindexSegments(filtered)
    }

    private fun reindexSegments(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        return segments.mapIndexed { index, seg ->
            if (seg.id == index + 1) seg else seg.copy(id = index + 1)
        }
    }

    // --- Offline Devanagari -> Romanized Hinglish Transliterator ---

    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e"
    )

    private val matraVowels = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u",
        'ू' to "oo", 'ृ' to "ri", 'े' to "e", 'ै' to "ai",
        'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e"
    )

    private val baseConsonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ळ' to "l", 'क़' to "q", 'ख़' to "kh", 'ग़' to "gh",
        'ज़' to "z", 'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f", 'य़' to "y"
    )

    private val nuktaConsonants = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "gh", 'ज' to "z",
        'ड' to "r", 'ढ' to "rh", 'फ' to "f", 'य' to "y"
    )

    private val devanagariDigits = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    private val commonHindiWords = mapOf(
        "है" to "hai", "हैं" to "hain", "हूँ" to "hoon", "हूं" to "hoon",
        "मैं" to "main", "मेरा" to "mera", "मेरी" to "meri", "मेरे" to "mere",
        "तुम" to "tum", "आप" to "aap", "हम" to "hum", "वह" to "woh", "यह" to "yeh",
        "क्या" to "kya", "क्यों" to "kyun", "कैसे" to "kaise", "कब" to "kab",
        "कहाँ" to "kahan", "कहां" to "kahan", "नहीं" to "nahi", "नही" to "nahi",
        "और" to "aur", "लेकिन" to "lekin", "क्योंकि" to "kyunki", "अगर" to "agar",
        "आज" to "aaj", "कल" to "kal", "अब" to "ab", "यहाँ" to "yahan", "वहाँ" to "wahan",
        "बहुत" to "bahut", "अच्छा" to "accha", "ठीक" to "theek", "सही" to "sahi",
        "वीडियो" to "video", "ऑडियो" to "audio", "चैनल" to "channel", "लाइक" to "like",
        "शेयर" to "share", "सब्सक्राइब" to "subscribe", "कमेंट" to "comment",
        "दोस्तो" to "dosto", "दोस्तों" to "doston", "नमस्ते" to "namaste"
    )

    fun transliterateDevanagariToHinglishOffline(
        segments: List<TranscriptSegment>
    ): List<TranscriptSegment> {
        return segments.map { seg ->
            seg.copy(
                text = transliterateTextOffline(seg.text),
                words = emptyList()
            )
        }
    }

    fun transliterateTextOffline(text: String): String {
        if (text.isBlank()) return text
        // Preserve whitespace while transliterating word tokens
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                result.append(text[i])
                i++
            } else {
                val start = i
                while (i < text.length && !text[i].isWhitespace()) {
                    i++
                }
                val token = text.substring(start, i)
                result.append(transliterateWordOffline(token))
            }
        }
        return result.toString()
    }

    private fun transliterateWordOffline(rawWord: String): String {
        if (rawWord.none { it in '\u0900'..'\u097F' }) return rawWord

        val leadPunct = rawWord.takeWhile { it !in '\u0900'..'\u097F' && !it.isLetterOrDigit() }
        val tailPunct = rawWord.drop(leadPunct.length).takeLastWhile { it !in '\u0900'..'\u097F' && !it.isLetterOrDigit() }
        val core = rawWord.substring(leadPunct.length, rawWord.length - tailPunct.length)

        commonHindiWords[core]?.let { mapped ->
            return leadPunct + mapped + tailPunct.replace('।', '.')
        }

        val sb = StringBuilder()
        var idx = 0
        val len = core.length
        while (idx < len) {
            val ch = core[idx]
            when {
                ch == '।' -> {
                    sb.append('.')
                    idx++
                }
                ch == '॥' -> {
                    sb.append('.')
                    idx++
                }
                devanagariDigits.containsKey(ch) -> {
                    sb.append(devanagariDigits[ch])
                    idx++
                }
                independentVowels.containsKey(ch) -> {
                    sb.append(independentVowels[ch])
                    idx++
                }
                baseConsonants.containsKey(ch) -> {
                    val hasNukta = (idx + 1 < len && core[idx + 1] == '़')
                    val sound = if (hasNukta) {
                        idx++
                        nuktaConsonants[ch] ?: baseConsonants[ch]!!
                    } else {
                        baseConsonants[ch]!!
                    }
                    sb.append(sound)
                    val next = if (idx + 1 < len) core[idx + 1] else null
                    when {
                        next == '्' -> {
                            // Halant suppresses inherent vowel
                            idx += 2
                        }
                        next != null && matraVowels.containsKey(next) -> {
                            sb.append(matraVowels[next])
                            idx += 2
                        }
                        else -> {
                            // Schwa deletion at word end unless single-consonant word
                            val isAtWordEnd = (idx + 1 >= len) ||
                                (idx + 1 < len && core[idx + 1] !in '\u0900'..'\u097F')
                            if (!isAtWordEnd || sb.length <= 2) {
                                sb.append('a')
                            }
                            idx++
                        }
                    }
                }
                ch == 'ं' || ch == 'ँ' -> {
                    val nextChar = if (idx + 1 < len) core[idx + 1] else null
                    val nasal = if (nextChar in setOf('प', 'फ', 'ब', 'भ', 'म')) "m" else "n"
                    sb.append(nasal)
                    idx++
                }
                ch == 'ः' -> {
                    sb.append('h')
                    idx++
                }
                ch == '्' || ch == '़' -> {
                    idx++
                }
                else -> {
                    sb.append(ch)
                    idx++
                }
            }
        }
        return leadPunct + sb.toString() + tailPunct.replace('।', '.')
    }
}
