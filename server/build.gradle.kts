plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
}

application {
    mainClass.set("com.dhananjay.livecast.server.ApplicationKt")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}
