import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "com.dhananjay.livecast.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val localProps = Properties().apply {
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { load(it) }
        }
    }
    val livecastServerUrl: String = (project.findProperty("livecast.serverUrl") as? String)
        ?: localProps.getProperty("livecast.serverUrl")
        ?: ""

    defaultConfig {
        applicationId = "com.dhananjay.livecast.client"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "LIVECAST_SERVER_URL", "\"${livecastServerUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = false
            isDebuggable = false
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:5.4.0")
        force("com.squareup.okhttp3:okhttp-android:5.4.0")
    }
}

dependencies {
    implementation(projects.client)
    implementation(projects.auth)
    implementation(projects.core)
    implementation(projects.protocol)
    implementation(projects.transport)

    implementation(project.dependencies.platform(libs.firebase.android.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.koin.android)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.uiToolingPreview)
    implementation(libs.androidx.activity.compose)
}
