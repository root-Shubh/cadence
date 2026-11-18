package com.cadence.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePointDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: RoutePointEntity)

    @Query(
        "SELECT * FROM route_points WHERE profileId = :profileId AND dateIso = :dateIso " +
            "ORDER BY timeMillis ASC",
    )
    fun getDayFlow(profileId: String, dateIso: String): Flow<List<RoutePointEntity>>
}
