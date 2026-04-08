plugins {
    id("com.holderzone.android.hardware.library")
    id("com.holderzone.android.local.maven.publish")
}

android {
    namespace = "com.holderzone.hardware.vendor"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets {
        named("main") {
            jniLibs.srcDirs("libs/serialport/jni")
        }
    }
}

dependencies {
    api(files("libs/vendor-core.jar"))
    api(files("libs/jwsapi/jws.jar"))
}
