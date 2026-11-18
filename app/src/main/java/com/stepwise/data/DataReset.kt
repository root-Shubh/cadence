package com.stepwise.data

/**
 * Deletion actions. Profile deletion and history deletion are independent:
 * deleting the profile never touches step history, and vice versa.
 */
class DataReset(
    private val profileStore: ProfileStore,
    private val dao: StepDayDao,
) {

    /** Removes the saved profile only. Step history is kept. */
    suspend fun deleteProfile() {
        profileStore.clear()
    }

    /** Removes all recorded step days only. The profile is kept. */
    suspend fun deleteAllRecordedData() {
        dao.deleteAll()
    }
}
