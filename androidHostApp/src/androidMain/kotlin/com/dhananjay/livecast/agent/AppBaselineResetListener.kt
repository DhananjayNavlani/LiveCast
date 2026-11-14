package com.dhananjay.livecast.agent

class AppBaselineResetListener(
    private val baseline: AppBaselineStore
) : EnrollmentListener {

    override suspend fun onEnrolled(deviceId: String, groupId: String) {
        baseline.clear()
    }
}
