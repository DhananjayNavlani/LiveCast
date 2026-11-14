package com.dhananjay.livecast.agent

import android.app.NotificationManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationStateParserTest {

    @Test
    fun appDisabledFalse() {
        assertFalse(NotificationStateParser.canShow(appEnabled = false, channelImportance = NotificationManager.IMPORTANCE_DEFAULT))
        assertFalse(NotificationStateParser.canShow(appEnabled = false, channelImportance = null))
    }

    @Test
    fun channelBlockedFalse() {
        assertFalse(NotificationStateParser.canShow(appEnabled = true, channelImportance = NotificationManager.IMPORTANCE_NONE))
    }

    @Test
    fun channelMissingTrue() {
        assertTrue(NotificationStateParser.canShow(appEnabled = true, channelImportance = null))
    }

    @Test
    fun channelDefaultTrue() {
        assertTrue(NotificationStateParser.canShow(appEnabled = true, channelImportance = NotificationManager.IMPORTANCE_DEFAULT))
    }
}
