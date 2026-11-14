package com.dhananjay.livecast.server.auth

import com.dhananjay.livecast.server.Clock
import com.dhananjay.livecast.server.PublicKeySource
import com.dhananjay.livecast.server.UpstreamException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import kotlin.coroutines.cancellation.CancellationException

class GoogleCertPublicKeySource(
    private val httpClient: HttpClient,
    private val clock: Clock,
    private val certsUrl: String = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
) : PublicKeySource {

    private val mutex = Mutex()
    @Volatile
    private var cachedKeys: Map<String, RSAPublicKey>? = null
    @Volatile
    private var cachedUntilMs: Long = 0L

    private val json = Json { ignoreUnknownKeys = true }
    private val certificateFactory = CertificateFactory.getInstance("X.509")

    override suspend fun keys(): Map<String, RSAPublicKey> {
        val now = clock.nowMs()
        cachedKeys?.let {
            if (now < cachedUntilMs) return it
        }

        return mutex.withLock {
            val lockedNow = clock.nowMs()
            cachedKeys?.let {
                if (lockedNow < cachedUntilMs) return it
            }

            try {
                val response = httpClient.get(certsUrl)
                if (!response.status.isSuccess()) {
                    throw UpstreamException("Failed to fetch Google public certs: ${response.status}")
                }

                val cacheControl = response.headers[HttpHeaders.CacheControl]
                val maxAgeSeconds = cacheControl?.let { header ->
                    Regex("""max-age=(\d+)""").find(header)?.groupValues?.get(1)?.toLongOrNull()
                } ?: 3600L

                val bodyText = response.bodyAsText()
                val jsonObject = json.parseToJsonElement(bodyText).jsonObject

                val parsedKeys = mutableMapOf<String, RSAPublicKey>()
                for ((kid, element) in jsonObject) {
                    val pem = element.jsonPrimitive.content
                    val cert = certificateFactory.generateCertificate(
                        ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8))
                    ) as? X509Certificate
                    (cert?.publicKey as? RSAPublicKey)?.let { rsaKey ->
                        parsedKeys[kid] = rsaKey
                    }
                }

                cachedKeys = parsedKeys
                cachedUntilMs = lockedNow + (maxAgeSeconds * 1000L)
                parsedKeys
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is UpstreamException) throw e
                throw UpstreamException("Error fetching or parsing Google public certs", e)
            }
        }
    }
}
