plugins {
    id("com.holderzone.android.sample.app")
}

android {
    namespace = "com.holderzone.samples.camerasdk"
    defaultConfig {
        applicationId = "com.holderzone.samples.camerasdk"
    }
}

dependencies {
    implementation(project(":hardware:camera"))
    implementation(project(":hardware:camera:compose"))
}
