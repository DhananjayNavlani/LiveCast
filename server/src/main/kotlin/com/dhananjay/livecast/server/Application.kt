package com.dhananjay.livecast.server

import com.dhananjay.livecast.server.auth.GoogleCertPublicKeySource
import com.dhananjay.livecast.server.auth.GoogleIdTokenVerifier
import com.dhananjay.livecast.server.check.CheckService
import com.dhananjay.livecast.server.check.InMemoryCheckRateLimiter
import com.dhananjay.livecast.server.check.SystemClock
import com.dhananjay.livecast.server.check.UuidIdGenerator
import com.dhananjay.livecast.server.firestore.FirestoreRestDirectory
import com.dhananjay.livecast.server.firestore.GoogleAuthAccessTokenProvider
import com.dhananjay.livecast.server.push.FcmHttpV1PushSender
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer

fun main() {
    val config = ServerConfig.fromEnv(System.getenv())
    println("Starting LiveCast server on port ${config.port} for project ${config.projectId}")

    val tokenProvider = GoogleAuthAccessTokenProvider.create(
        config.credentialsJson,
        config.credentialsPath
    )

    if (tokenProvider == null) {
        if (!config.firestoreEmulatorHost.isNullOrBlank()) {
            println("Using Firestore emulator at ${config.firestoreEmulatorHost}. FCM has no Google credentials; checks will fail upstream.")
        } else {
            println("WARNING: No Google credentials configured. Upstream calls will fail.")
        }
    }

    val httpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    val clock = SystemClock()
    val idGenerator = UuidIdGenerator()
    val rateLimiter = InMemoryCheckRateLimiter()

    val keySource = GoogleCertPublicKeySource(httpClient, clock)
    val verifier = GoogleIdTokenVerifier(keySource, config.projectId, clock)

    val firestoreDirectory = FirestoreRestDirectory(
        httpClient = httpClient,
        projectId = config.projectId,
        tokenProvider = tokenProvider,
        emulatorHost = config.firestoreEmulatorHost
    )

    val pushSender = FcmHttpV1PushSender(
        httpClient = httpClient,
        projectId = config.projectId,
        tokenProvider = tokenProvider
    )

    val checkService = CheckService(
        verifier = verifier,
        devices = firestoreDirectory,
        members = firestoreDirectory,
        push = pushSender,
        limiter = rateLimiter,
        ids = idGenerator,
        clock = clock
    )

    embeddedServer(ServerCIO, port = config.port) {
        livecastModule(checkService, config.corsOrigins)
    }.start(wait = true)
}
