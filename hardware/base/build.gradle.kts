plugins {
    id("com.holderzone.android.hardware.library")
}

android {
    namespace = "com.holderzone.hardware.base"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
