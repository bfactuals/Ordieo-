package com.example

import com.example.data.model.AutoCorrectRule
import com.example.data.model.TextCasing
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordChunkMode
import com.example.data.srt.FormattedCue
import com.example.data.srt.SrtEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtEngineTest {

    @Test
    fun parseSrt_preservesExactTimestamps() {
        val raw = """
            1
            00:00:01,500 --> 00:00:04,250
            नमस्ते दोस्तों, welcome back!

            2
            00:00:04,500 --> 00:00:07,000
            Today we build captions.
        """.trimIndent()

        val (segments, isSrt) = SrtEngine.parseSrtOrPlainText(raw)
        assertTrue(isSrt)
        assertEquals(2, segments.size)
        assertEquals(1500L, segments[0].startMs)
        assertEquals(4250L, segments[0].endMs)
        assertEquals("नमस्ते दोस्तों, welcome back!", segments[0].text)
        assertEquals(4500L, segments[1].startMs)
        assertEquals(7000L, segments[1].endMs)
    }

    @Test
    fun buildFormattedCues_appliesFillerRemovalWordChunkStripPunctuationCasingAndShift() {
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                startMs = 1000L,
                endMs = 4000L,
                text = "Umm, hello, world! Ahh matlab welcome back."
            )
        )

        val cues = SrtEngine.buildFormattedCues(
            segments = segments,
            wordChunkMode = WordChunkMode.TWO_WORDS,
            removeFillerWords = true,
            stripPunctuation = true,
            casing = TextCasing.ALL_CAPS,
            timestampShiftMs = 500L
        )

        assertEquals(2, cues.size)
        assertEquals("HELLO WORLD", cues[0].text)
        assertEquals("WELCOME BACK", cues[1].text)
        assertEquals(1500L, cues[0].startMs)
        assertEquals(4500L, cues[1].endMs)
    }

    @Test
    fun applyAutoCorrectDictionary_replacesCaseInsensitiveWholeWords() {
        val segments = listOf(
            TranscriptSegment(id = 1, startMs = 0L, endMs = 2000L, text = "Welcome to gemini studio and grok api")
        )
        val rules = listOf(
            AutoCorrectRule("1", "gemini", "Gemini"),
            AutoCorrectRule("2", "grok", "Groq")
        )
        val corrected = SrtEngine.applyAutoCorrectDictionary(segments, rules)
        assertEquals("Welcome to Gemini studio and Groq api", corrected[0].text)
    }

    @Test
    fun findActiveWordIndex_progressesAcrossCueDuration() {
        val cue = FormattedCue(
            index = 1,
            sourceSegmentId = 1,
            startMs = 1000L,
            endMs = 4000L,
            text = "Hello world today"
        )
        assertEquals(0, SrtEngine.findActiveWordIndex(cue, 1100L))
        assertEquals(1, SrtEngine.findActiveWordIndex(cue, 2500L))
        assertEquals(2, SrtEngine.findActiveWordIndex(cue, 3800L))
    }

    @Test
    fun exportSrt_formatsStandardSubRipStructure() {
        val segments = listOf(
            TranscriptSegment(id = 1, startMs = 0L, endMs = 2000L, text = "aaj hum seekhenge")
        )
        val srt = SrtEngine.exportSrt(
            segments = segments,
            wordChunkMode = WordChunkMode.FULL_LINE,
            removeFillerWords = false,
            stripPunctuation = false,
            casing = TextCasing.TITLE_CASE,
            timestampShiftMs = 0L
        )
        assertTrue(srt.contains("00:00:00,000 --> 00:00:02,000"))
        assertTrue(srt.contains("Aaj Hum Seekhenge"))
    }

    @Test
    fun transliterateDevanagariToHinglishOffline_convertsHindiAndPreservesEnglish() {
        val segments = listOf(
            TranscriptSegment(id = 1, startMs = 0L, endMs = 3000L, text = "नमस्ते दोस्तों, आज हम video बनाएंगे।")
        )
        val result = SrtEngine.transliterateDevanagariToHinglishOffline(segments)
        assertEquals(1, result.size)
        assertTrue(result[0].text.startsWith("namaste doston, aaj hum video "))
        assertTrue(result[0].text.none { it in '\u0900'..'\u097F' })
    }

    @Test
    fun splitMergeAndDeleteSegment_updatesIdsAndTimestampsCleanly() {
        val initial = listOf(
            TranscriptSegment(id = 1, startMs = 0L, endMs = 4000L, text = "First part second part"),
            TranscriptSegment(id = 2, startMs = 4000L, endMs = 7000L, text = "Third part")
        )

        val split = SrtEngine.splitSegment(initial, 1)
        assertEquals(3, split.size)
        assertEquals("First part", split[0].text)
        assertEquals("second part", split[1].text)
        assertEquals(split[0].endMs, split[1].startMs)
        assertEquals(3, split[2].id)

        val merged = SrtEngine.mergeWithNextSegment(split, 1)
        assertEquals(2, merged.size)
        assertEquals("First part second part", merged[0].text)
        assertEquals(4000L, merged[0].endMs)

        val deleted = SrtEngine.deleteSegment(merged, 1)
        assertEquals(1, deleted.size)
        assertEquals(1, deleted[0].id)
        assertEquals("Third part", deleted[0].text)
    }

    @Test
    fun countMatches_countsCaseInsensitiveOccurrencesAcrossSegments() {
        val segments = listOf(
            TranscriptSegment(id = 1, startMs = 0L, endMs = 2000L, text = "Hello world hello"),
            TranscriptSegment(id = 2, startMs = 2000L, endMs = 4000L, text = "HELLO again")
        )
        assertEquals(3, SrtEngine.countMatches(segments, "hello"))
        assertEquals(0, SrtEngine.countMatches(segments, ""))
    }
}
