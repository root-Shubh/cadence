package com.cadence.data

import org.junit.Assert.assertTrue
import org.junit.Test

class FreshInstallTest {

    @Test
    fun freshInstallPrefFilesListsAllSixStateFiles() {
        val files = freshInstallPrefFiles()
        listOf(
            "cadence_profile",
            "cadence_goals",
            "cadence_theme",
            "cadence_privacy",
            "cadence_onboarding",
            "cadence_tracker",
        ).forEach { assertTrue("missing $it", files.contains(it)) }
    }
}
