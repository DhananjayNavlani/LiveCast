import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

val libs = extensions.findByType<VersionCatalogsExtension>()?.named("libs")
val androidCompileSdk = libs?.findVersion("android-compileSdk")?.orElse(null)?.requiredVersion?.toIntOrNull()
    ?: libs?.findVersion("android.compileSdk")?.orElse(null)?.requiredVersion?.toIntOrNull()
    ?: 36
val androidMinSdk = libs?.findVersion("android-minSdk")?.orElse(null)?.requiredVersion?.toIntOrNull()
    ?: libs?.findVersion("android.minSdk")?.orElse(null)?.requiredVersion?.toIntOrNull()
    ?: 26

android {
    compileSdk = androidCompileSdk
    defaultConfig {
        minSdk = androidMinSdk
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
