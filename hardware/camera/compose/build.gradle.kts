import org.gradle.api.plugins.BasePluginExtension

plugins {
    id("com.holderzone.android.camera.family.library")
    id("com.holderzone.android.compose")
    id("com.holderzone.android.local.maven.publish")
}

android {
    namespace = "com.holderzone.hardware.camera.compose"
}

extensions.configure<BasePluginExtension> {
    archivesName.set("hardware-camera-compose-${project.version}")
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(project(":hardware:camera"))
}
