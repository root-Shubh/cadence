package com.stepwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StepDayEntity::class], version = 1, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {

    abstract fun stepDayDao(): StepDayDao

    companion object {
        private const val DB_NAME = "stepwise.db"

        @Volatile
        private var INSTANCE: StepDatabase? = null

        fun getInstance(context: Context): StepDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    DB_NAME,
                ).build().also { INSTANCE = it }
            }
    }
}
