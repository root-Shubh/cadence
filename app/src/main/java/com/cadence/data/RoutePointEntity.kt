package com.cadence.data

import androidx.room.Entity

/**
 * One kept GPS point of a recorded walk. Used only to draw the route; never used for steps.
 * [dateIso] is the local date of the fix in yyyy-MM-dd form.
 */
@Entity(tableName = "route_points", primaryKeys = ["profileId", "dateIso", "timeMillis"])
data class RoutePointEntity(
    val profileId: String,
    val dateIso: String,
    val timeMillis: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
)
