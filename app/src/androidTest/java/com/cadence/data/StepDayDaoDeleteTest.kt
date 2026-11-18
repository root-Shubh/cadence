package com.cadence.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StepDayDaoDeleteTest {

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

    private fun day(iso: String, steps: Int, profileId: String = DEFAULT_PROFILE_ID) = StepDayEntity(
        dateIso = iso,
        steps = steps,
        distanceM = steps * 0.7,
        activeKcal = 10.0,
        bmrKcal = 1500.0,
        rmrKcal = 1400.0,
        totalKcal = 1510.0,
        profileId = profileId,
    )

    @Test
    fun deleteAllRemovesOnlyThatProfilesRows() = runBlocking {
        dao.upsert(day("2026-03-01", 1000))
        dao.upsert(day("2026-03-02", 2000))
        dao.upsert(day("2026-03-01", 3000, profileId = "other"))

        dao.deleteAll(DEFAULT_PROFILE_ID)

        assertTrue(dao.getRange(DEFAULT_PROFILE_ID, "2026-03-01", "2026-03-31").isEmpty())
        assertTrue(dao.getRange("other", "2026-03-01", "2026-03-31").size == 1)
    }

    @Test
    fun deleteAllForAllProfilesRemovesEveryRow() = runBlocking {
        dao.upsert(day("2026-03-01", 1000))
        dao.upsert(day("2026-03-01", 3000, profileId = "other"))

        dao.deleteAllForAllProfiles()

        assertTrue(dao.getRange("other", "2026-03-01", "2026-03-31").isEmpty())
        assertTrue(dao.getRange(DEFAULT_PROFILE_ID, "2026-03-01", "2026-03-31").isEmpty())
    }
}
