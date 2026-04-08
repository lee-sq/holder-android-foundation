import java.util.Properties
import org.gradle.api.plugins.BasePluginExtension

plugins {
    id("com.holderzone.android.library")
    id("com.holderzone.android.compose")
    id("com.holderzone.android.local.maven.publish")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val coreVersionName = versionProperties.getProperty("VERSION_NAME")
val coreVersionCode = versionProperties.getProperty("VERSION_CODE").toInt()

version = coreVersionName

android {
    namespace = "com.holderzone.core.ui"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        named("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.setSrcDirs(
                listOf(
                    "ui/src/main/java",
                    "network/src/main/java",
                    "platform/src/main/java",
                )
            )
            res.setSrcDirs(listOf("ui/src/main/res"))
        }
        named("test") {
            java.setSrcDirs(
                listOf(
                    "ui/src/test/java",
                    "network/src/test/java",
                    "platform/src/test/java",
                )
            )
        }
        named("androidTest") {
            java.setSrcDirs(
                listOf(
                    "ui/src/androidTest/java",
                    "network/src/androidTest/java",
                    "platform/src/androidTest/java",
                )
            )
        }
    }
}

extensions.configure<BasePluginExtension> {
    archivesName.set("foundation-core-sdk-${project.version}")
}

tasks.register("printModuleVersion") {
    group = "versioning"
    description = "Prints the core sdk artifact and version information."
    doLast {
        println("${project.path} -> foundation-core-sdk:$coreVersionName ($coreVersionCode)")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "platform/libs", "include" to listOf("*.jar"))))

    // BaseActivity is part of the public API and extends AppCompatActivity.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.appcompat)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.navigation.compose)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.mmkv)
    implementation(libs.hilt.android)
    implementation(libs.hilt.worker)
    implementation(libs.work.runtime.ktx)
    implementation(libs.blankj.utils) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation(libs.github.voice)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.google.gson)
    implementation(libs.github.logbook) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation(libs.logger)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.permissionx)
    implementation(libs.google.accompanist.systemuicontroller)

    ksp(libs.hilt.android.compiler)
}
