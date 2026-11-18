package com.cadence.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StepDayDaoTest {

    private lateinit var db: StepDatabase
    private lateinit var dao: StepDayDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StepDatabase::class.java).build()
        dao = db.stepDayDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun day(iso: String, steps: Int) = StepDayEntity(
        dateIso = iso,
        steps = steps,
        distanceM = steps * 0.7,
        activeKcal = 10.0,
        bmrKcal = 1500.0,
        rmrKcal = 1400.0,
        totalKcal = 1510.0,
    )

    @Test
    fun upsertTwiceOnSameDateKeepsOneRow() = runBlocking {
        dao.upsert(day("2026-03-01", 1000))
        dao.upsert(day("2026-03-01", 2000))

        assertEquals(2000, dao.getByDate(DEFAULT_PROFILE_ID, "2026-03-01")?.steps)
        assertEquals(1, dao.getRange(DEFAULT_PROFILE_ID, "2026-03-01", "2026-03-01").size)
    }

    @Test
    fun rangeQueryReturnsSortedRowsInsideRangeOnly() = runBlocking {
        dao.upsert(day("2026-03-03", 3))
        dao.upsert(day("2026-03-01", 1))
        dao.upsert(day("2026-03-02", 2))
        dao.upsert(day("2026-02-28", 0))
        dao.upsert(day("2026-03-04", 4))

        val rows = dao.getRange(DEFAULT_PROFILE_ID, "2026-03-01", "2026-03-03")

        assertEquals(listOf("2026-03-01", "2026-03-02", "2026-03-03"), rows.map { it.dateIso })
    }

    @Test
    fun sameDateForTwoProfilesIsTwoRows() = runBlocking {
        dao.upsert(day("2026-03-01", 1))
        dao.upsert(day("2026-03-01", 9).copy(profileId = "other"))

        assertEquals(1, dao.getByDate(DEFAULT_PROFILE_ID, "2026-03-01")?.steps)
        assertEquals(9, dao.getByDate("other", "2026-03-01")?.steps)
    }
}
