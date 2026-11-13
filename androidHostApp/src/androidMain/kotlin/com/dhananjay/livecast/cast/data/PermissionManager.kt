package com.dhananjay.livecast.cast.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.dhananjay.livecast.cast.data.services.AccessibilityService

class PermissionManager(
    private val context: Context,
) {
    private val accessibilityManager by lazy {
        context.getSystemService(AccessibilityManager::class.java)
    }

    fun isAccessibilityEnabled() = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_GENERIC
    ).any {
        it.resolveInfo.serviceInfo.run { "$packageName/$name" } == "${context.packageName}/${AccessibilityService::class.java.name}"
    }
}