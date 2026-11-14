package com.dhananjay.livecast.server.auth

import com.dhananjay.livecast.server.Clock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleCertPublicKeySourceTest {

    private class FakeClock(var now: Long = 1_000_000_000L) : Clock {
        override fun nowMs(): Long = now
    }

    private val sampleCert = """
        -----BEGIN CERTIFICATE-----
        MIICmjCCAYICCQDc9I1OWHUVoTANBgkqhkiG9w0BAQsFADAPMQ0wCwYDVQQDDARU
        ZXN0MB4XDTI2MTExNDA1MDMyNVoXDTI3MTExNDA1MDMyNVowDzENMAsGA1UEAwwE
        VGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMBF1Ty7BFqKuUxX
        bx+xzenandGCNRiYjU8VNRWRUmfzpCeJhVk2k+J24F1zNkMJaPrWVO87U2ubqu/P
        Oq/AYRn58Maw5YvnGaJ6ufVcAFIBC16+B2x/t2k+qQYjxw9Q9mr0DNMtE6MhLrGv
        YgQa4XAMl2WStCMroTq6c34ZFuEKxOMJlgAjimTQ2SCF/Jlir7jzoWXPFhYgqYg9
        cY7MkP5CL2yByqfIL50we+tNfkN6iIJAdEBk7Z17E3dOGNVx+P8vFg+qosZKuXzI
        g1A/21+F586CkZDbgIYmaah8Dk8naXnWBnFKf3SLAAE3TY7hNAgWpEnsW7cNrfU8
        Yv5djBcCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAQSxT+/imU6t7xdZsgr/gIU8Y
        keBiqG72q24Lh8l/7SiV38dAhBP7BVEk7E5iFYe9y+FxZaVJ+3Ng8s5tNTJU0oKR
        YJ8yPMly1ZWyiSd5WSagyA6NYG/F08j3xbHKIqkDQSTJeng6CRZV6j8ozqhRRur4
        eDutYpmQwtz7DZDxUtwOLR1PywfVI1eUeEAieoqLWQc9jh1ixb6XKsLWKNLVJijJ
        EiDbipGr5K0g4itvqXidbbe4fpfbJVR+0Zi2PPWjWCzh98m1eKjGVHES6YQIA9gC
        GSESpbh1FIGxQRHExa3ml07Z2q3kX4jo5lMEV2yU2RTOnD82Qg3TRknQZtRfPg==
        -----END CERTIFICATE-----
    """.trimIndent()

    @Test
    fun cachesUntilMaxAge() = runTest {
        val requestCount = AtomicInteger(0)
        val clock = FakeClock(now = 100_000L)

        val jsonBody = """{"k1": ${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer(), sampleCert)}}"""

        val engine = MockEngine { request ->
            requestCount.incrementAndGet()
            respond(
                content = jsonBody,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.CacheControl to listOf("public, max-age=100, must-revalidate")
                )
            )
        }

        val client = HttpClient(engine)
        val keySource = GoogleCertPublicKeySource(client, clock)

        // First call triggers request 1
        val keys1 = keySource.keys()
        assertEquals(1, requestCount.get())
        assertTrue("k1" in keys1)

        // Second call within 100s uses cache
        clock.now = 150_000L // +50s, still < 100s
        val keys2 = keySource.keys()
        assertEquals(1, requestCount.get())
        assertEquals(keys1, keys2)

        // Third call after 100s triggers request 2
        clock.now = 205_000L // +105s, expired
        val keys3 = keySource.keys()
        assertEquals(2, requestCount.get())
        assertEquals(keys1.keys, keys3.keys)
    }
}
