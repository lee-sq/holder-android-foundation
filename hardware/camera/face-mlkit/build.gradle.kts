import org.gradle.api.plugins.BasePluginExtension

plugins {
    id("com.holderzone.android.camera.family.library")
    id("com.holderzone.android.local.maven.publish")
}

android {
    namespace = "com.holderzone.hardware.camera.face.mlkit"
}

extensions.configure<BasePluginExtension> {
    archivesName.set("hardware-camera-face-mlkit-${project.version}")
}

dependencies {
    api(project(":hardware:camera"))
    api(libs.google.mlkit.face.detection)
}
