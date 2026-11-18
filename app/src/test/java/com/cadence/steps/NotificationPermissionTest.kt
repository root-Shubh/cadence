package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionTest {
    @Test
    fun belowAndroid13AlwaysSkips() {
        assertEquals(NotificationAction.Skip, decideNotificationAction(31, granted = false))
        assertEquals(NotificationAction.Skip, decideNotificationAction(32, granted = false))
        assertEquals(NotificationAction.Skip, decideNotificationAction(26, granted = true))
    }

    @Test
    fun android13AndLaterRequestWhenNotGranted() {
        assertEquals(NotificationAction.Request, decideNotificationAction(33, granted = false))
        assertEquals(NotificationAction.Request, decideNotificationAction(34, granted = false))
        assertEquals(NotificationAction.Request, decideNotificationAction(35, granted = false))
    }

    @Test
    fun grantedSkipsOnAndroid13AndLater() {
        assertEquals(NotificationAction.Skip, decideNotificationAction(33, granted = true))
        assertEquals(NotificationAction.Skip, decideNotificationAction(34, granted = true))
    }
}
