plugins {
    id("com.holderzone.android.camera.family.library")
}

android {
    namespace = "com.holderzone.hardware.camera"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
