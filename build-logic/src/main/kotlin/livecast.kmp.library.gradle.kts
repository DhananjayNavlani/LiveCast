import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    jvmToolchain(25)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
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
