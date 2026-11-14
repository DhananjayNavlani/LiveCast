package com.dhananjay.livecast.server.firestore

import com.dhananjay.livecast.protocol.GroupRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirestoreRestDirectoryEmulatorTest {

    private val emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST")
    private val projectId = "demo-livecast"

    private suspend fun seedDocument(client: HttpClient, path: String, fieldsJson: String) {
        val url = "http://$emulatorHost/v1/projects/$projectId/databases/(default)/documents/$path"
        client.patch(url) {
            header(HttpHeaders.Authorization, "Bearer owner")
            contentType(ContentType.Application.Json)
            setBody("""{"fields": $fieldsJson}""")
        }
    }

    @Test
    fun groupOfReturnsGroupId() = runTest {
        if (emulatorHost.isNullOrBlank()) return@runTest
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val directory = FirestoreRestDirectory(client, projectId, tokenProvider = null, emulatorHost = emulatorHost)

        seedDocument(client, "devices/test-dev-enrolled", """{"groupId": {"stringValue": "g-enrolled"}}""")
        val group = directory.groupOf("test-dev-enrolled")
        assertEquals("g-enrolled", group)
    }

    @Test
    fun groupOfNullWhenUnenrolled() = runTest {
        if (emulatorHost.isNullOrBlank()) return@runTest
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val directory = FirestoreRestDirectory(client, projectId, tokenProvider = null, emulatorHost = emulatorHost)

        seedDocument(client, "devices/test-dev-unenrolled", """{"groupId": {"nullValue": null}}""")
        val group = directory.groupOf("test-dev-unenrolled")
        assertNull(group)
    }

    @Test
    fun groupOfNullWhenMissing() = runTest {
        if (emulatorHost.isNullOrBlank()) return@runTest
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val directory = FirestoreRestDirectory(client, projectId, tokenProvider = null, emulatorHost = emulatorHost)

        val group = directory.groupOf("missing-device-${System.currentTimeMillis()}")
        assertNull(group)
    }

    @Test
    fun roleOfReturnsRole() = runTest {
        if (emulatorHost.isNullOrBlank()) return@runTest
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val directory = FirestoreRestDirectory(client, projectId, tokenProvider = null, emulatorHost = emulatorHost)

        seedDocument(client, "groups/g-test/members/member@example.com", """{"role": {"stringValue": "supervisor"}}""")
        val role = directory.roleOf("g-test", "member@example.com")
        assertEquals(GroupRole.SUPERVISOR, role)
    }

    @Test
    fun roleOfNullWhenNotMember() = runTest {
        if (emulatorHost.isNullOrBlank()) return@runTest
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val directory = FirestoreRestDirectory(client, projectId, tokenProvider = null, emulatorHost = emulatorHost)

        val role = directory.roleOf("g-test", "nonexistent@example.com")
        assertNull(role)
    }

    @Test
    fun pushTokenOfReturnsToken() = runTest {
        if (emulatorHost.isNullOrBlank()) return@runTest
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val directory = FirestoreRestDirectory(client, projectId, tokenProvider = null, emulatorHost = emulatorHost)

        seedDocument(client, "devices/test-dev-push/private/push", """{"fcmToken": {"stringValue": "fcm-tok-123"}}""")
        val token = directory.pushTokenOf("test-dev-push")
        assertEquals("fcm-tok-123", token)
    }

    @Test
    fun pushTokenOfNullWhenMissing() = runTest {
        if (emulatorHost.isNullOrBlank()) return@runTest
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val directory = FirestoreRestDirectory(client, projectId, tokenProvider = null, emulatorHost = emulatorHost)

        val token = directory.pushTokenOf("no-push-device-${System.currentTimeMillis()}")
        assertNull(token)
    }
}
