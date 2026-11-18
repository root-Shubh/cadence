package com.stepwise.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: StepDayEntity)

    @Query("SELECT * FROM step_days WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getByDate(dateIso: String): StepDayEntity?

    @Query("SELECT * FROM step_days WHERE dateIso BETWEEN :fromIso AND :toIso ORDER BY dateIso ASC")
    suspend fun getRange(fromIso: String, toIso: String): List<StepDayEntity>

    @Query("DELETE FROM step_days")
    suspend fun deleteAll()

    @Query("SELECT * FROM step_days WHERE dateIso BETWEEN :fromIso AND :toIso ORDER BY dateIso ASC")
    fun getRangeFlow(fromIso: String, toIso: String): Flow<List<StepDayEntity>>
}
