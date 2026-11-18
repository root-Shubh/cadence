package com.stepwise.data

import org.junit.Assert.assertTrue
import org.junit.Test

class FreshInstallTest {

    @Test
    fun freshInstallPrefFilesListsAllSixStateFiles() {
        val files = freshInstallPrefFiles()
        listOf(
            "stepwise_profile",
            "stepwise_goals",
            "stepwise_theme",
            "stepwise_privacy",
            "stepwise_onboarding",
            "stepwise_tracker",
        ).forEach { assertTrue("missing $it", files.contains(it)) }
    }
}
