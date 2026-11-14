plugins {
    id("livecast.kmp.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

fun org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler.implementation(
    dependency: Provider<MinimalExternalModuleDependency>,
    configure: Action<org.gradle.api.artifacts.ExternalModuleDependency>
) {
    val dep = project.dependencies.create(dependency.get()) as org.gradle.api.artifacts.ExternalModuleDependency
    configure.execute(dep)
    implementation(dep)
}

fun org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler.implementation(
    dependency: org.gradle.accessors.dm.LibrariesForLibs.OkhttpLibraryAccessors,
    configure: Action<org.gradle.api.artifacts.ExternalModuleDependency>
) {
    implementation(dependency.asProvider(), configure)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.protocol)
            implementation(projects.transport)
            implementation(projects.designsystem)
            implementation(projects.auth)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.android.bom))
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.auth)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp) {
                version { strictly(libs.versions.okhttp.get()) }
            }
            implementation(libs.okhttp.android) {
                version { strictly(libs.versions.okhttp.get()) }
            }
        }

        desktopMain.dependencies {
            implementation(projects.firestoreGrpc)
            implementation(libs.ktor.client.cio)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.dhananjay.livecast.client.ui"
}
