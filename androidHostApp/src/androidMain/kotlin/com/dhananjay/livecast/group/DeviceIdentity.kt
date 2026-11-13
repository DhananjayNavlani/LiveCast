package com.dhananjay.livecast.group

import com.google.firebase.auth.FirebaseAuth

interface DeviceIdentity {
    val deviceId: String?
}

class FirebaseDeviceIdentity(
    private val auth: FirebaseAuth
) : DeviceIdentity {
    override val deviceId: String?
        get() = auth.currentUser?.uid
}
