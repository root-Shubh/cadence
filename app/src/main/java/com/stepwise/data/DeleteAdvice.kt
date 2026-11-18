package com.stepwise.data

/**
 * Advice shown before deleting data. Returns the warning when there is recorded data to lose,
 * and an empty string when there is nothing to delete.
 */
fun deleteAdvice(hasRecordedData: Boolean): String =
    if (hasRecordedData) {
        "Export your data as CSV first. Stepwise has no cloud storage, so deleting here cannot be undone."
    } else {
        ""
    }
