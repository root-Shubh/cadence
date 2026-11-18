package com.cadence.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: StepDayEntity)

    @Query("SELECT * FROM step_days WHERE profileId = :profileId AND dateIso = :dateIso LIMIT 1")
    suspend fun getByDate(profileId: String, dateIso: String): StepDayEntity?

    @Query(
        "SELECT * FROM step_days WHERE profileId = :profileId AND dateIso BETWEEN :fromIso AND :toIso " +
            "ORDER BY dateIso ASC",
    )
    suspend fun getRange(profileId: String, fromIso: String, toIso: String): List<StepDayEntity>

    @Query(
        "SELECT * FROM step_days WHERE profileId = :profileId AND dateIso BETWEEN :fromIso AND :toIso " +
            "ORDER BY dateIso ASC",
    )
    fun getRangeFlow(profileId: String, fromIso: String, toIso: String): Flow<List<StepDayEntity>>

    /** Deletes one profile's history. */
    @Query("DELETE FROM step_days WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: String)

    /** Deletes one profile's history. Same effect as [deleteAll]; named for profile deletion. */
    @Query("DELETE FROM step_days WHERE profileId = :profileId")
    suspend fun deleteProfileHistory(profileId: String)

    /** Deletes every profile's history. Used only by the all-profiles reset. */
    @Query("DELETE FROM step_days")
    suspend fun deleteAllForAllProfiles()
}
