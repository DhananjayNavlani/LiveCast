plugins {
    id("livecast.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.protocol)
        }
    }
}

android {
    namespace = "com.dhananjay.livecast.host.capability"
}
