package com.cadence.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ActivitySlotsTest {

    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(5, 30)

    private fun millis(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun slotsAreFiveMinutesFromMidnight() {
        assertEquals(288, SLOTS_PER_DAY)
        assertEquals(0, slotOf(millis(22, 0, 0), zone))
        assertEquals(0, slotOf(millis(22, 0, 4), zone))
        assertEquals(1, slotOf(millis(22, 0, 5), zone))
        assertEquals(17 * 12 + 9, slotOf(millis(22, 17, 49), zone))
        assertEquals(287, slotOf(millis(22, 23, 59), zone))
    }

    @Test
    fun aWriteAfterMidnightForYesterdayGoesInItsLastSlot() {
        assertEquals(287, slotForWrite("2026-11-22", millis(23, 0, 1), zone))
        assertEquals(0, slotForWrite("2026-11-22", millis(21, 23, 0), zone))
        assertEquals(slotOf(millis(22, 9, 0), zone), slotForWrite("2026-11-22", millis(22, 9, 0), zone))
    }

    @Test
    fun newStepsAreTimed() {
        val split = planSlotWrite(slotSum = 100, newTotal = 130, previousTotal = 100, recoveredOwed = 0)
        assertEquals(SlotSplit(timed = 30, untimed = 0, recoveredUsed = 0), split)
    }

    /** Steps saved before this version (or by an import) have no time: they go to the untimed slot. */
    @Test
    fun aTotalWithoutSlotsIsUntimed() {
        val split = planSlotWrite(slotSum = 0, newTotal = 6_330, previousTotal = 6_327, recoveredOwed = 0)
        assertEquals(3, split.timed)
        assertEquals(6_327, split.untimed)
    }

    /** The first counter reading after a restart holds steps walked while the service was off. */
    @Test
    fun recoveredStepsAreUntimedEvenThoughTheyArriveNow() {
        val split = planSlotWrite(slotSum = 5_030, newTotal = 6_330, previousTotal = 5_030, recoveredOwed = 1_297)
        assertEquals(3, split.timed)
        assertEquals(1_297, split.untimed)
        assertEquals(1_297, split.recoveredUsed)
    }

    @Test
    fun nothingIsAddedWhenSlotsAlreadyMatch() {
        assertEquals(SlotSplit(0, 0, 0), planSlotWrite(slotSum = 500, newTotal = 500, previousTotal = 480, recoveredOwed = 10))
        assertEquals(SlotSplit(0, 0, 0), planSlotWrite(slotSum = 600, newTotal = 500, previousTotal = 500, recoveredOwed = 0))
    }

    /** One source of truth: after every write the slots add up to the saved total. */
    @Test
    fun slotsAlwaysAddUpToTheSavedTotal() {
        var slotSum = 0
        var stored = 4_000 // saved before this version
        var owed = 250
        for (newTotal in listOf(4_010, 4_400, 4_400, 4_420, 5_000)) {
            val split = planSlotWrite(slotSum, newTotal, stored, owed)
            slotSum += split.timed + split.untimed
            owed -= split.recoveredUsed
            stored = newTotal
            assertEquals(newTotal, slotSum)
        }
    }

    @Test
    fun rollbackTakesTheRideStepsFromItsStartOnward() {
        val slots = mapOf(100 to 500, 200 to 300, 201 to 200, UNTIMED_SLOT to 1_000)
        val changed = removeSteps(slots, fromSlot = 200, amount = 400)
        assertEquals(mapOf(201 to 0, 200 to 100), changed)
    }

    @Test
    fun rollbackFallsBackToEarlierSlotsThenUntimed() {
        val slots = mapOf(100 to 50, 200 to 30, UNTIMED_SLOT to 1_000)
        val changed = removeSteps(slots, fromSlot = 200, amount = 100)
        assertEquals(mapOf(200 to 0, 100 to 0, UNTIMED_SLOT to 980), changed)
        val after = slots + changed
        assertEquals(slots.values.sum() - 100, after.values.sum())
    }

    @Test
    fun totalsGroupSlotsAndLeaveOutUntimed() {
        val slots = mapOf(0 to 10, 1 to 20, 2 to 30, 3 to 40, 12 to 5, UNTIMED_SLOT to 999)
        val quarters = quarterHourTotals(slots)
        assertEquals(96, quarters.size)
        assertEquals(60, quarters[0])
        assertEquals(40, quarters[1])
        val hours = hourTotals(slots)
        assertEquals(24, hours.size)
        assertEquals(100, hours[0])
        assertEquals(5, hours[1])
        assertEquals(999, untimedSteps(slots))
    }

    @Test
    fun heatLevelsFollowTheThresholds() {
        assertEquals(Heat.NONE, quarterHeat(0))
        assertEquals(Heat.LIGHT, quarterHeat(QUARTER_MODERATE - 1))
        assertEquals(Heat.MODERATE, quarterHeat(QUARTER_MODERATE))
        assertEquals(Heat.BRISK, quarterHeat(QUARTER_BRISK))
        assertEquals(Heat.PEAK, quarterHeat(QUARTER_PEAK))
        assertEquals(Heat.LIGHT, hourHeat(1))
        assertEquals(Heat.PEAK, hourHeat(HOUR_PEAK + 1))
    }

    /** The owner's example: 20 minutes in the morning and an hour in the evening. */
    @Test
    fun findsAMorningAndAnEveningWalk() {
        val slots = HashMap<Int, Int>()
        for (s in 0 until 4) slots[7 * 12 + 2 + s] = 520 // 07:10-07:30
        for (s in 0 until 12) slots[17 * 12 + 9 + s] = 525 // 17:45-18:45
        slots[17 * 12 + 9 + 5] = 40 // a pause at a crossing inside the evening walk
        slots[3] = 20 // moving about at night: not a walk
        val walks = findWalks(slots)
        assertEquals(2, walks.size)
        assertEquals("07:10", clockOf(walks[0].startMinute))
        assertEquals("07:30", clockOf(walks[0].endMinute))
        assertEquals(20, walks[0].minutes)
        assertEquals("Morning", periodOfDay(walks[0].startMinute))
        assertEquals("17:45", clockOf(walks[1].startMinute))
        assertEquals(60, walks[1].minutes)
        assertEquals(11 * 525 + 40, walks[1].steps)
        assertEquals("Evening", periodOfDay(walks[1].startMinute))
    }

    @Test
    fun aLongQuietGapSplitsWalksAndShortBurstsAreDropped() {
        val slots = mapOf(100 to 400, 103 to 400, 200 to 160)
        val walks = findWalks(slots)
        assertEquals(2, walks.size)
        assertTrue(walks.none { it.startSlot == 200 })
    }

    /** Tuned on the owner's 2026-11-22 walks: a 7-minute walk at about 50 steps a minute is listed. */
    @Test
    fun aSlowShortWalkIsListed() {
        val slots = mapOf(14 * 12 + 5 to 170, 14 * 12 + 6 to 104)
        val walks = findWalks(slots)
        assertEquals(1, walks.size)
        assertEquals("14:25", clockOf(walks[0].startMinute))
        assertEquals(274, walks[0].steps)
    }

    @Test
    fun busiestHourAcrossDays() {
        val a = IntArray(24).also { it[17] = 3_000; it[7] = 1_000 }
        val b = IntArray(24).also { it[17] = 2_000; it[8] = 4_000 }
        assertEquals(17, busiestHour(listOf(a, b)))
        assertNull(busiestHour(listOf(IntArray(24))))
    }

    @Test
    fun clockFormatsMinutes() {
        assertEquals("00:00", clockOf(0))
        assertEquals("17:45", clockOf(17 * 60 + 45))
        assertEquals("24:00", clockOf(1_440))
    }
}
