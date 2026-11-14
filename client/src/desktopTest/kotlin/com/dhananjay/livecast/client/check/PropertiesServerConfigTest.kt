package com.dhananjay.livecast.client.check

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertiesServerConfigTest {

    @Test
    fun envWins() {
        val tempDir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(tempDir, "config.properties")
            val props = Properties()
            props.setProperty("livecast_server_url", "http://file:8080")
            file.outputStream().use { props.store(it, null) }

            val config = PropertiesServerConfig(tempDir, mapOf("LIVECAST_SERVER_URL" to "http://env:8080"))
            assertEquals("http://env:8080", config.baseUrl)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun fileUsedWhenNoEnv() {
        val tempDir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(tempDir, "config.properties")
            val props = Properties()
            props.setProperty("livecast_server_url", "http://file:8080")
            file.outputStream().use { props.store(it, null) }

            val config = PropertiesServerConfig(tempDir, emptyMap())
            assertEquals("http://file:8080", config.baseUrl)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun blankIsNull() {
        val tempDir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val configEnvBlank = PropertiesServerConfig(tempDir, mapOf("LIVECAST_SERVER_URL" to "   "))
            assertNull(configEnvBlank.baseUrl)

            val file = File(tempDir, "config.properties")
            val props = Properties()
            props.setProperty("livecast_server_url", "   ")
            file.outputStream().use { props.store(it, null) }

            val configFileBlank = PropertiesServerConfig(tempDir, emptyMap())
            assertNull(configFileBlank.baseUrl)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun missingFileIsNull() {
        val tempDir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val config = PropertiesServerConfig(tempDir, emptyMap())
            assertNull(config.baseUrl)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
