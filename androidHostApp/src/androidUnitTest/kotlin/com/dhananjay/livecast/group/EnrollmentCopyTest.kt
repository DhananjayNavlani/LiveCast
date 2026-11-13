package com.dhananjay.livecast.group

import kotlin.test.Test
import kotlin.test.assertEquals

class EnrollmentCopyTest {

    @Test
    fun confirmCopy() {
        val expected = "This phone will be supervised by Family: Mum, Dad. Only a group owner can remove supervision."
        val actual = EnrollmentCopy.confirm("Family", listOf("Mum", "Dad"))
        assertEquals(expected, actual)
    }

    @Test
    fun notMemberCopy() {
        val expected = "Ask your group owner to add kid@x.com as a device."
        val actual = EnrollmentCopy.notMember("kid@x.com")
        assertEquals(expected, actual)
    }
}
