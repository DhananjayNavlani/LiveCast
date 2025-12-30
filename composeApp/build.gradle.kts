import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.lumo)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.swiftklib)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(compose.components.resources)
            implementation(libs.androidx.material3.compose)
            implementation(libs.material.icons.extended)
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.bundles.webrtc.android)
            implementation(libs.stream.log.android)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.runtime.compose.android)

            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.coil.network)

            //workmanager
            implementation(libs.androidx.work)

            implementation(project.dependencies.platform(libs.firebase.android.bom))
            implementation(libs.bundles.firebase.android)

            // Google Sign-In with Credential Manager
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.google.id)

            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.koin.androidx.work)
            
            // Android-specific dependencies (web is now a separate React module)
            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.coil.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.coil.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        iosMain.dependencies {
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.coil.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}

android {
    namespace = "com.dhananjay.livecast"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                val fileName = "livecast-${variant.versionName}.apk"
                outputFileName = fileName
            }
        }
    }

    defaultConfig {
        applicationId = "com.dhananjay.livecast"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        
        // Support dynamic version from CI/CD
        val versionNameFromProperty = project.findProperty("version.name") as String?
        versionCode = versionNameFromProperty?.let {
            // Generate version code from version name (e.g., 1.0.1 -> 10001)
            val parts = it.split(".")
            if (parts.size >= 3) {
                parts[0].toIntOrNull()?.times(10000)?.plus(
                    parts[1].toIntOrNull()?.times(100) ?: 0
                )?.plus(parts[2].toIntOrNull() ?: 0) ?: 1
            } else 1
        } ?: 1
        versionName = versionNameFromProperty ?: "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Signing configuration
    signingConfigs {
        create("release") {
            // Support signing from CI/CD environment
            val keystoreFile = project.findProperty("android.injected.signing.store.file") as String?
            val keystorePassword = project.findProperty("android.injected.signing.store.password") as String?
            val keyAlias = project.findProperty("android.injected.signing.key.alias") as String?
            val keyPassword = project.findProperty("android.injected.signing.key.password") as String?
            
            if (keystoreFile != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }
    
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        
        getByName("release") {
            isMinifyEnabled = false
            isDebuggable = false
            
            // Apply signing config if available
            if (signingConfigs.findByName("release")?.storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    lint {
        disable += "NullSafeMutableLiveData"
    }
}


compose.desktop {
    application {
        mainClass = "com.dhananjay.livecast.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.dhananjay.livecast"
            packageVersion = "1.0.0"
        }
    }
}

swiftklib {
    create("FirebaseAuthIOS") {
        path = file("src/nativeInterop/swiftklib/FirebaseAuthIOS")
        packageName = "com.dhananjay.livecast.auth.ios"
        minIos = 15
    }
}


