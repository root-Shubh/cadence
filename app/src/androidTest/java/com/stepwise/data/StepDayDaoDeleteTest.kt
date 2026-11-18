package com.stepwise.data

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
    fun deleteAllRemovesEveryRow() = runBlocking {
        dao.upsert(day("2026-03-01", 1000))
        dao.upsert(day("2026-03-02", 2000))

        dao.deleteAll()

        assertTrue(dao.getRange("2026-03-01", "2026-03-31").isEmpty())
    }
}
