plugins {
    id("com.holderzone.android.camera.family.library")
}

android {
    namespace = "com.holderzone.hardware.camera"
}

dependencies {
    implementation(project(":hardware:camera:api"))
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.camera.view)
}
