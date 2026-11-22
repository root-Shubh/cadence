package com.cadence.debug

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DebugLogTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun setUp() {
        DebugLog.detachForTest()
        DebugLog.clear()
        DebugLog.setCapacityForTest(DebugLog.DEFAULT_CAPACITY)
    }

    @After
    fun tearDown() {
        DebugLog.detachForTest()
        DebugLog.clear()
        DebugLog.setCapacityForTest(DebugLog.DEFAULT_CAPACITY)
    }

    @Test
    fun keepsEventsInOrderWithTagAndMessage() {
        DebugLog.add("counter", "raw=10", nowMillis = 0L)
        DebugLog.add("service", "destroyed", nowMillis = 1000L)

        val lines = DebugLog.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("[counter] raw=10"))
        assertTrue(lines[1].endsWith("[service] destroyed"))
    }

    @Test
    fun dropsOldestLinesOnceCapacityIsReached() {
        DebugLog.setCapacityForTest(3)
        for (i in 1..5) DebugLog.add("t", "event $i", nowMillis = i.toLong())

        val lines = DebugLog.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith("event 3"))
        assertTrue(lines[2].endsWith("event 5"))
    }

    @Test
    fun shrinkingCapacityDropsExtraLinesImmediately() {
        for (i in 1..4) DebugLog.add("t", "event $i", nowMillis = i.toLong())
        DebugLog.setCapacityForTest(2)

        assertEquals(2, DebugLog.lines().size)
        assertTrue(DebugLog.lines()[1].endsWith("event 4"))
    }

    @Test
    fun clearEmptiesTheBuffer() {
        DebugLog.add("t", "something")
        DebugLog.clear()

        assertTrue(DebugLog.lines().isEmpty())
        assertEquals("", DebugLog.text())
    }

    @Test
    fun textJoinsLinesWithNewlines() {
        DebugLog.add("a", "one", nowMillis = 0L)
        DebugLog.add("b", "two", nowMillis = 0L)

        val text = DebugLog.text()
        assertEquals(2, text.split("\n").size)
        assertFalse(text.endsWith("\n"))
    }

    @Test
    fun formatLineStartsWithDateAndTimeOfDay() {
        val line = DebugLog.formatLine(nowMillis = 0L, tag = "x", message = "y")
        assertTrue(Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \[x] y$""").matches(line))
    }

    @Test
    fun attachedLogIsWrittenToFileAndReloadedAfterRestart() {
        val dir = temp.newFolder()
        DebugLog.attach(dir)
        DebugLog.add("service", "created", nowMillis = 0L)
        DebugLog.add("counter", "raw=5", nowMillis = 1L)

        // Simulates the process dying: memory is gone, the file is not.
        DebugLog.detachForTest()
        DebugLog.setCapacityForTest(DebugLog.DEFAULT_CAPACITY)
        DebugLog.clear()
        DebugLog.add("service", "created again", nowMillis = 2L)
        DebugLog.attach(dir)

        val lines = DebugLog.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith("[service] created"))
        assertTrue(lines[1].endsWith("[counter] raw=5"))
        assertTrue(lines[2].endsWith("[service] created again"))
    }

    @Test
    fun fileIsTrimmedToCapacity() {
        val dir = temp.newFolder()
        DebugLog.setCapacityForTest(3)
        DebugLog.attach(dir)
        for (i in 1..10) DebugLog.add("t", "event $i", nowMillis = i.toLong())

        val fileLines = File(dir, DebugLog.FILE_NAME).readLines().filter { it.isNotBlank() }
        assertTrue(fileLines.size <= 6)
        assertTrue(fileLines.last().endsWith("event 10"))
    }

    @Test
    fun clearAlsoEmptiesTheFile() {
        val dir = temp.newFolder()
        DebugLog.attach(dir)
        DebugLog.add("t", "something")
        DebugLog.clear()

        assertEquals("", File(dir, DebugLog.FILE_NAME).readText())
    }

    /** Regression for STEPW-61: the log must hold a full day of one-a-minute heartbeats. */
    @Test
    fun capacityHoldsADayOfHeartbeats() {
        val heartbeatsPerDay = 24 * 60
        assertTrue(DebugLog.DEFAULT_CAPACITY >= heartbeatsPerDay * 2)
        repeat(heartbeatsPerDay) { DebugLog.add("heartbeat", "alive $it", nowMillis = it * 60_000L) }
        assertEquals(heartbeatsPerDay, DebugLog.lines().size)
        assertTrue(DebugLog.lines().first().endsWith("alive 0"))
    }
}
