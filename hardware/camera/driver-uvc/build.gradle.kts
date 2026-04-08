plugins {
    id("com.holderzone.android.camera.family.library")
}

android {
    namespace = "com.holderzone.hardware.camera"

    defaultConfig {
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets {
        named("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

dependencies {
    implementation(project(":hardware:camera:api"))
}
