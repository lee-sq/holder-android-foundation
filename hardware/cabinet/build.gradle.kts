import org.gradle.api.plugins.BasePluginExtension

plugins {
    id("com.holderzone.android.hardware.library")
    id("com.holderzone.android.local.maven.publish")
}

android {
    namespace = "com.holderzone.hardware.cabinet"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets {
        named("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/java")
            jniLibs.srcDirs("libs/star/jni")
        }
    }
}

extensions.configure<BasePluginExtension> {
    archivesName.set("hardware-cabinet-${project.version}")
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    implementation(project(":hardware:vendor"))
    implementation(files("libs/jw-sdk/jwbaselib.jar"))
    implementation(files("libs/star/xspret.jar"))
    implementation(files("libs/star/SerialportPrintSDK.jar"))

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
}
