import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `kotlin-dsl`
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val kotlinVersion = libs.findVersion("kotlin").get().requiredVersion
val agpVersion = libs.findVersion("agp").get().requiredVersion

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("com.android.tools.build:gradle:$agpVersion")
}
