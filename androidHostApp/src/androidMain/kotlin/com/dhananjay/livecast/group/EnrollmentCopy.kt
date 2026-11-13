package com.dhananjay.livecast.group

object EnrollmentCopy {
    fun confirm(groupName: String, supervisorNames: List<String>): String {
        val names = supervisorNames.joinToString(", ")
        return "This phone will be supervised by $groupName: $names. Only a group owner can remove supervision."
    }

    fun notMember(email: String): String {
        return "Ask your group owner to add $email as a device."
    }

    const val NO_EMAIL = "Sign in with an email account to enroll this phone."
    const val NOT_VERIFIED = "Not verified yet. Open the link in your email, then try again."
}
