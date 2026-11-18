package com.cadence.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternCodecTest {

    @Test
    fun dayFileRoundTrips() {
        val steps = (0 until 1_203L).map { 1_700_000_000_000L + it * 1_000L }
        val points = listOf(
            RoutePointRecord(1_700_000_010_000L, 4.5f, 51.5012345, -0.1234567, 51.5012, -0.12345),
            RoutePointRecord(1_700_000_020_000L, 12.0f, 51.5013, -0.1235, 51.50131, -0.12351),
        )
        val day = DayPatterns("2026-11-18", steps, points)

        val decoded = PatternCodec.decode(PatternCodec.encode(day))

        assertEquals(day, decoded)
    }

    @Test
    fun encodedFileIsOneJsonObjectPerLine() {
        val day = DayPatterns("2026-11-18", listOf(1L, 2L), listOf(
            RoutePointRecord(3L, 5f, 1.0, 2.0, 1.1, 2.1),
        ))
        val lines = PatternCodec.encode(day).trim().lines()
        assertEquals(3, lines.size)
        lines.forEach { line ->
            assertEquals('{', line.first())
            assertEquals('}', line.last())
        }
    }

    @Test
    fun damagedLinesAreSkipped() {
        val text = """
            {"type":"day","version":1,"date":"2026-11-18"}
            {"type":"steps","t":[10,20]}
            {"type":"point","t":5,"acc":
            not json at all
            {"type":"steps","t":[30]}
        """.trimIndent()
        val decoded = PatternCodec.decode(text)
        assertEquals(listOf(10L, 20L, 30L), decoded?.stepMillis)
        assertEquals(0, decoded?.points?.size)
    }

    @Test
    fun fileWithoutHeaderIsRejected() {
        assertNull(PatternCodec.decode("{\"type\":\"steps\",\"t\":[1]}\n"))
    }

    @Test
    fun longStepListsAreSplitIntoBatches() {
        val steps = (0 until PatternCodec.STEPS_PER_LINE * 2 + 3).map { it.toLong() }
        val lines = PatternCodec.encode(DayPatterns("2026-11-18", steps)).trim().lines()
        assertEquals(1 + 3, lines.size)
        assertEquals(steps, PatternCodec.decode(PatternCodec.encode(DayPatterns("2026-11-18", steps)))?.stepMillis)
    }
}
