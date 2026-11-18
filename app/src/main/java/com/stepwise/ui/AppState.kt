package com.stepwise.ui

/** Chooses the status line shown on the Today screen. Pure function, no Android types. */
fun todayStatusText(profileMissing: Boolean, trackingActive: Boolean): String = when {
    profileMissing && !trackingActive -> "Add your profile and start counting to track today"
    profileMissing -> "Add your profile to see calories"
    !trackingActive -> "Start counting to track today"
    else -> "Counting today"
}
