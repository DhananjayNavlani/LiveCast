package com.dhananjay.livecast.cast.utils

import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object Constants {
    const val ACTION_SEND_EVENT= "action_send_event"
    const val DATA_CHANNEL_KEY = "data_channel_key"
    const val WORK_DEVICE_ONLINE = "WORK_DEVICE_ONLINE"
    const val KEY_IS_ONLINE = "isOnline"
    const val CHANNEL_ID_APP = "app_updates"
    const val GROUP_KEY_APP = "app_updates_group"
    const val ACTION_START_SCREEN_SHARING = "action_start_screen_sharing"
    const val ACTION_STOP_SCREEN_SHARING = "action_stop_screen_sharing"
    const val EXTRA_ON_CAPTURE_SUCCESS = "on_capture_success"
    const val EXTRA_IS_VIEWER = "is_viewer"

    val PREF_LOGIN_STATUS = booleanPreferencesKey("login_status")
    val PREF_USER = stringPreferencesKey("user")
    val DEVICE_ID by lazy {
        "${Build.FINGERPRINT}_${Build.DEVICE}_${Build.MANUFACTURER}".hashCode()
    }
    val DEVICE_NAME by lazy {
        "${Build.DEVICE}_${Build.MANUFACTURER}_${Build.MODEL}"
    }
}