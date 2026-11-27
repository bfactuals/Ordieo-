package com.example

import com.example.data.media.VideoSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSplitterTest {

    @Test
    fun buildSplitParts_exact18MinutesBy1Min_produces18Parts() {
        val parts = VideoSplitter.buildSplitParts(durationMs = 18L * 60_000L, chunkDurationMs = 60_000L)
        assertEquals(18, parts.size)
        assertEquals(1, parts.first().index)
        assertEquals(0L, parts.first().startMs)
        assertEquals(60_000L, parts.first().endMs)
        assertEquals(18, parts.last().index)
        assertEquals(17L * 60_000L, parts.last().startMs)
        assertEquals(18L * 60_000L, parts.last().endMs)
        assertTrue(parts.all { it.selected })
    }

    @Test
    fun buildSplitParts_partialTail_keepsPartialFinalPart() {
        val parts = VideoSplitter.buildSplitParts(durationMs = 150_000L, chunkDurationMs = 60_000L)
        assertEquals(3, parts.size)
        assertEquals(120_000L, parts[2].startMs)
        assertEquals(150_000L, parts[2].endMs)
    }

    @Test
    fun buildSplitParts_tinyTrailingSliver_mergesIntoLastPart() {
        val parts = VideoSplitter.buildSplitParts(durationMs = 60_250L, chunkDurationMs = 60_000L)
        assertEquals(1, parts.size)
        assertEquals(0L, parts.first().startMs)
        assertEquals(60_250L, parts.first().endMs)
    }

    @Test
    fun formatPartFileName_padsTwoOrThreeDigitsAndSanitizes() {
        assertEquals(
            "My_Lecture_HD_part_01.mp4",
            VideoSplitter.formatPartFileName("My Lecture (HD).mp4", partIndex = 1, totalParts = 18)
        )
        assertEquals(
            "long_clip_part_007.mp4",
            VideoSplitter.formatPartFileName("long_clip.mov", partIndex = 7, totalParts = 120)
        )
    }

    @Test
    fun formatRangeTimestamp_supportsUnderAndOverOneHour() {
        assertEquals("00:00", VideoSplitter.formatRangeTimestamp(0L))
        assertEquals("05:30", VideoSplitter.formatRangeTimestamp(330_000L))
        assertEquals("1:05:00", VideoSplitter.formatRangeTimestamp(3_900_000L))
    }
}
