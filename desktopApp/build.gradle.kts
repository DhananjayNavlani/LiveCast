plugins {
    kotlin("jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(projects.client)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.dhananjay.livecast.client.desktop.MainKt"
    }
}
