// 导入Kotlin Gradle DSL中的JVM目标版本枚举
plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "com.holderzone.android.library"
            implementationClass = "com.holderzone.build_logic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "com.holderzone.android.compose"
            implementationClass = "com.holderzone.build_logic.AndroidComposeConventionPlugin"
        }
        register("androidHardwareLibrary") {
            id = "com.holderzone.android.hardware.library"
            implementationClass = "com.holderzone.build_logic.AndroidHardwareLibraryConventionPlugin"
        }
        register("androidCameraFamilyLibrary") {
            id = "com.holderzone.android.camera.family.library"
            implementationClass = "com.holderzone.build_logic.AndroidCameraFamilyLibraryConventionPlugin"
        }
        register("androidCabinetFamilyLibrary") {
            id = "com.holderzone.android.cabinet.family.library"
            implementationClass = "com.holderzone.build_logic.AndroidCabinetFamilyLibraryConventionPlugin"
        }
        register("androidScaleFamilyLibrary") {
            id = "com.holderzone.android.scale.family.library"
            implementationClass = "com.holderzone.build_logic.AndroidScaleFamilyLibraryConventionPlugin"
        }
        register("androidSampleApp") {
            id = "com.holderzone.android.sample.app"
            implementationClass = "com.holderzone.build_logic.AndroidSampleAppConventionPlugin"
        }
        register("androidLocalMavenPublish") {
            id = "com.holderzone.android.local.maven.publish"
            implementationClass = "com.holderzone.build_logic.AndroidLocalMavenPublishConventionPlugin"
        }
    }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
}
