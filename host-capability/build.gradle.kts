plugins {
    id("livecast.android.library")
}

android {
    namespace = "com.dhananjay.livecast.host.capability"
}

dependencies {
    implementation(projects.core)
    implementation(projects.protocol)
}
