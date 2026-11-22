package com.cadence.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Version 1 to 2: adds profileId (existing rows become 'default') and rebuilds the table so
 * the primary key is (profileId, dateIso). SQLite cannot change a primary key in place.
 */
val STEP_DB_MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS step_days_new (" +
                "profileId TEXT NOT NULL DEFAULT 'default', " +
                "dateIso TEXT NOT NULL, " +
                "steps INTEGER NOT NULL, " +
                "distanceM REAL NOT NULL, " +
                "activeKcal REAL NOT NULL, " +
                "bmrKcal REAL NOT NULL, " +
                "rmrKcal REAL NOT NULL, " +
                "totalKcal REAL NOT NULL, " +
                "PRIMARY KEY(profileId, dateIso))",
        )
        db.execSQL(
            "INSERT INTO step_days_new (profileId, dateIso, steps, distanceM, activeKcal, bmrKcal, rmrKcal, totalKcal) " +
                "SELECT 'default', dateIso, steps, distanceM, activeKcal, bmrKcal, rmrKcal, totalKcal FROM step_days",
        )
        db.execSQL("DROP TABLE step_days")
        db.execSQL("ALTER TABLE step_days_new RENAME TO step_days")
    }
}

/** SQL that creates the step_slots table (STEPW-63). Kept here so the migration matches the entity. */
const val CREATE_STEP_SLOTS_SQL: String =
    "CREATE TABLE IF NOT EXISTS step_slots (" +
        "profileId TEXT NOT NULL, " +
        "dateIso TEXT NOT NULL, " +
        "slot INTEGER NOT NULL, " +
        "steps INTEGER NOT NULL, " +
        "PRIMARY KEY(profileId, dateIso, slot))"

/** Version 4 to 5: adds steps by time of day. Additive, so the day history is kept. */
val STEP_DB_MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_STEP_SLOTS_SQL)
    }
}

@Database(entities = [StepDayEntity::class, StepSlotEntity::class], version = 5, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {

    abstract fun stepDayDao(): StepDayDao

    companion object {
        private const val DB_NAME = "cadence.db"

        @Volatile
        private var INSTANCE: StepDatabase? = null

        fun getInstance(context: Context): StepDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    DB_NAME,
                ).addMigrations(STEP_DB_MIGRATION_1_2, STEP_DB_MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
