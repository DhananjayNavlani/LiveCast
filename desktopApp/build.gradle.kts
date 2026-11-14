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
    implementation(projects.auth)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main on the desktop JVM (viewModelScope); same version family as kotlinx-coroutines-core.
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.dhananjay.livecast.client.desktop.MainKt"
    }
}
