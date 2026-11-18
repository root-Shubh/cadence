package com.cadence.steps

import android.content.Context
import com.cadence.data.StepDatabase

/**
 * The one source of truth for today's step count. Today, the notification and both widgets all
 * read this. While counting, the live count is used, so every surface shows the same number at
 * the same time. Otherwise the stored total for the day is used.
 */
suspend fun currentTodaySteps(context: Context, profileId: String, dateIso: String): Int {
    if (isCountingRunning()) return LiveSteps.steps.value
    return StepDatabase.getInstance(context).stepDayDao()
        .getByDate(profileId, dateIso)?.steps ?: 0
}
