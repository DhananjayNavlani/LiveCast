package com.dhananjay.livecast.firestore.grpc

import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvPropertiesFirestoreConfigSourceTest {

    @Test
    fun envWinsOverFile() {
        val tempDir = Files.createTempDirectory("livecast-config-test").toFile()
        try {
            val configFile = File(tempDir, "config.properties")
            Properties().apply {
                setProperty("firebase_project_id", "file-project-id")
                configFile.outputStream().use { store(it, null) }
            }

            val envMap = mapOf(
                "FIREBASE_PROJECT_ID" to "env-project-id"
            )
            val source = EnvPropertiesFirestoreConfigSource(
                configDir = tempDir,
                env = { envMap[it] }
            )

            val config = source.load()
            assertNotNull(config)
            assertEquals("env-project-id", config.projectId)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun emulatorHostParsed() {
        val tempDir = Files.createTempDirectory("livecast-config-test").toFile()
        try {
            val envMap = mapOf(
                "FIREBASE_PROJECT_ID" to "test-project",
                "FIRESTORE_EMULATOR_HOST" to "localhost:8080"
            )
            val source = EnvPropertiesFirestoreConfigSource(
                configDir = tempDir,
                env = { envMap[it] }
            )

            val config = source.load()
            assertNotNull(config)
            assertEquals("test-project", config.projectId)
            assertEquals("localhost", config.host)
            assertEquals(8080, config.port)
            assertTrue(config.plaintext)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun missingProjectIdIsNull() {
        val tempDir = Files.createTempDirectory("livecast-config-test").toFile()
        try {
            val source = EnvPropertiesFirestoreConfigSource(
                configDir = tempDir,
                env = { null }
            )

            val config = source.load()
            assertNull(config)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
