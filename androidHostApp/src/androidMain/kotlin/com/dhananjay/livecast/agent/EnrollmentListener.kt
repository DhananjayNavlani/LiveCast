package com.dhananjay.livecast.agent

interface EnrollmentListener {
    suspend fun onEnrolled(deviceId: String, groupId: String)
}
