package com.dhananjay.livecast.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ServerConfigTest {

    @Test
    fun defaultsPort8080() {
        val config = ServerConfig.fromEnv(mapOf("FIREBASE_PROJECT_ID" to "my-proj"))
        assertEquals("my-proj", config.projectId)
        assertEquals(8080, config.port)
        assertNull(config.credentialsJson)
        assertNull(config.credentialsPath)
        assertNull(config.firestoreEmulatorHost)
        assertEquals(emptyList(), config.corsOrigins)
    }

    @Test
    fun missingProjectIdFails() {
        assertFailsWith<IllegalStateException> {
            ServerConfig.fromEnv(emptyMap())
        }
    }

    @Test
    fun emulatorHostRead() {
        val config = ServerConfig.fromEnv(
            mapOf(
                "FIREBASE_PROJECT_ID" to "my-proj",
                "FIRESTORE_EMULATOR_HOST" to "localhost:8085"
            )
        )
        assertEquals("localhost:8085", config.firestoreEmulatorHost)
    }

    @Test
    fun corsListSplit() {
        val config = ServerConfig.fromEnv(
            mapOf(
                "FIREBASE_PROJECT_ID" to "my-proj",
                "CORS_ALLOWED_ORIGINS" to "https://app.livecast.com, http://localhost:3000 , "
            )
        )
        assertEquals(listOf("https://app.livecast.com", "http://localhost:3000"), config.corsOrigins)
    }
}
