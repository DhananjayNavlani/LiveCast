package com.dhananjay.livecast.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.dhananjay.livecast.cast.data.services.AccessibilityService

object ReEnableIntents {
    const val ACTION_REENABLE_ACCESSIBILITY = "com.dhananjay.livecast.action.REENABLE_ACCESSIBILITY"

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    fun shouldHandleReEnable(action: String?, restored: Boolean, launchedFromHistory: Boolean): Boolean {
        return action == ACTION_REENABLE_ACCESSIBILITY && !restored && !launchedFromHistory
    }

    fun highlightExtras(flattenedComponent: String): Map<String, String> {
        return mapOf(EXTRA_FRAGMENT_ARG_KEY to flattenedComponent)
    }

    fun accessibilitySettings(context: Context): Intent {
        val component = ComponentName(context, AccessibilityService::class.java).flattenToString()
        val extras = highlightExtras(component)
        val argsBundle = Bundle().apply {
            putString(EXTRA_FRAGMENT_ARG_KEY, component)
        }
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            extras.forEach { (k, v) -> putExtra(k, v) }
            putExtra(EXTRA_SHOW_FRAGMENT_ARGS, argsBundle)
        }
    }

    fun appDetails(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun appNotificationSettings(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
