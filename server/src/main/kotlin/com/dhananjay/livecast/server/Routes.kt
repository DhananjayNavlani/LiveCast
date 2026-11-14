package com.dhananjay.livecast.server

import com.dhananjay.livecast.server.check.CheckService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.livecastModule(checks: CheckService, corsOrigins: List<String>) {
    install(ContentNegotiation) {
        json()
    }

    if (corsOrigins.isNotEmpty()) {
        install(CORS) {
            corsOrigins.forEach { origin ->
                val hostPart = origin.removePrefix("https://").removePrefix("http://")
                val schemes = if (origin.startsWith("https://")) listOf("https") else listOf("http", "https")
                allowHost(hostPart, schemes = schemes)
            }
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
        }
    }

    routing {
        get("/healthz") {
            call.respondText("OK")
        }

        post("/v1/devices/{deviceId}/checks") {
            val deviceId = call.parameters["deviceId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("status" to "missing_device_id"))
                return@post
            }
            val authHeader = call.request.header(HttpHeaders.Authorization)
            val outcome = checks.requestCheck(authHeader, deviceId)
            when (outcome) {
                is CheckOutcome.Sent -> call.respond(
                    HttpStatusCode.Accepted,
                    mapOf("checkId" to outcome.checkId)
                )
                is CheckOutcome.Unauthorized -> call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("status" to "unauthorized")
                )
                is CheckOutcome.DeviceNotFound -> call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("status" to "device_not_found")
                )
                is CheckOutcome.Forbidden -> call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("status" to "forbidden")
                )
                is CheckOutcome.RateLimited -> call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf("status" to "rate_limited")
                )
                is CheckOutcome.NoPushToken -> call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("status" to "no_push_token")
                )
                is CheckOutcome.AppRemoved -> call.respond(
                    HttpStatusCode.Gone,
                    mapOf("status" to "app_removed")
                )
                is CheckOutcome.UpstreamFailed -> call.respond(
                    HttpStatusCode.BadGateway,
                    mapOf("status" to "upstream_error")
                )
            }
        }
    }
}
