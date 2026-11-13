package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.User

enum class ClientGate {
    SIGN_IN,
    GUEST,
    VERIFY_EMAIL,
    READY
}

fun clientGate(user: User?): ClientGate {
    if (user == null) return ClientGate.SIGN_IN
    if (user.isAnonymous || user.email.isNullOrBlank()) return ClientGate.GUEST
    if (!user.emailVerified) return ClientGate.VERIFY_EMAIL
    return ClientGate.READY
}
