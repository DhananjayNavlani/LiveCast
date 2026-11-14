package com.dhananjay.livecast.agent

import com.dhananjay.livecast.protocol.AppEventType

data class InstalledApp(
    val packageName: String,
    val label: String,
    val firstInstallTimeMs: Long
)

data class PackageChange(
    val packageName: String,
    val added: Boolean,
    val replacing: Boolean,
    val atEpochMs: Long
)

data class NewAppEvent(
    val type: AppEventType,
    val packageName: String,
    val label: String,
    val atEpochMs: Long,
    val detectedLate: Boolean,
    val groupId: String
)

object AppChangeDiff {

    data class Result(
        val events: List<NewAppEvent>,
        val baseline: Map<String, String>
    )

    fun onStart(
        baseline: Map<String, String>?,
        installed: List<InstalledApp>,
        groupId: String,
        nowMs: Long
    ): Result {
        val currentInstalledBaseline = installed.associate { it.packageName to it.label }
        if (baseline == null) {
            return Result(events = emptyList(), baseline = currentInstalledBaseline)
        }

        val installedMap = installed.associateBy { it.packageName }
        val events = mutableListOf<NewAppEvent>()

        for (app in installed) {
            if (app.packageName !in baseline) {
                events.add(
                    NewAppEvent(
                        type = AppEventType.INSTALLED,
                        packageName = app.packageName,
                        label = app.label,
                        atEpochMs = app.firstInstallTimeMs,
                        detectedLate = false,
                        groupId = groupId
                    )
                )
            }
        }

        for ((pkg, label) in baseline) {
            if (pkg !in installedMap) {
                events.add(
                    NewAppEvent(
                        type = AppEventType.REMOVED,
                        packageName = pkg,
                        label = label,
                        atEpochMs = nowMs,
                        detectedLate = true,
                        groupId = groupId
                    )
                )
            }
        }

        return Result(events = events, baseline = currentInstalledBaseline)
    }

    fun onBroadcast(
        change: PackageChange,
        baseline: Map<String, String>,
        app: InstalledApp?,
        groupId: String
    ): Result {
        if (change.replacing) {
            return Result(events = emptyList(), baseline = baseline)
        }

        if (change.added) {
            return if (app != null && !baseline.containsKey(change.packageName)) {
                val event = NewAppEvent(
                    type = AppEventType.INSTALLED,
                    packageName = change.packageName,
                    label = app.label,
                    atEpochMs = change.atEpochMs,
                    detectedLate = false,
                    groupId = groupId
                )
                Result(
                    events = listOf(event),
                    baseline = baseline + (change.packageName to app.label)
                )
            } else {
                Result(events = emptyList(), baseline = baseline)
            }
        } else {
            val label = baseline[change.packageName]
            return if (label != null) {
                val event = NewAppEvent(
                    type = AppEventType.REMOVED,
                    packageName = change.packageName,
                    label = label,
                    atEpochMs = change.atEpochMs,
                    detectedLate = false,
                    groupId = groupId
                )
                Result(
                    events = listOf(event),
                    baseline = baseline - change.packageName
                )
            } else {
                Result(events = emptyList(), baseline = baseline)
            }
        }
    }
}
