package com.stepwise.data

/**
 * Deletion actions. Profile deletion and history deletion are independent:
 * deleting the profile never touches step history, and vice versa.
 */
class DataReset(
    private val profileStore: ProfileStore,
    private val dao: StepDayDao,
) {

    /**
     * Removes the active profile only. Its step history is kept. With no profile saved,
     * nothing is deleted.
     */
    suspend fun deleteProfile() {
        val activeId = profileStore.activeProfileId() ?: return
        profileStore.deleteProfile(activeId)
    }

    /** Removes all recorded step days of the active profile only. Profiles are kept. */
    suspend fun deleteAllRecordedData() {
        dao.deleteAll(activeProfileIdOrDefault())
    }

    /** Removes recorded step days of every profile. Only for a second, explicit confirmation. */
    suspend fun deleteAllRecordedDataForEveryone() {
        dao.deleteAllForAllProfiles()
    }

    private fun activeProfileIdOrDefault(): String =
        profileStore.activeProfileId() ?: DEFAULT_PROFILE_ID
}
