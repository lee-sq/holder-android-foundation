import org.gradle.api.plugins.BasePluginExtension

plugins {
    id("com.holderzone.android.hardware.library")
    id("com.holderzone.android.local.maven.publish")
}

android {
    namespace = "com.holderzone.hardware.scale"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets {
        named("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/java")
        }
    }
}

extensions.configure<BasePluginExtension> {
    archivesName.set("hardware-scale-${project.version}")
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    implementation(project(":hardware:vendor"))
    implementation(files("libs/jw/jwbaselib.jar"))
    implementation(files("libs/jw/baijieapi_1.2.jar"))
    implementation("com.github.xuexiangjys:RxUtil2:1.2.1")
    implementation("com.github.xuexiangjys.XUtil:xutil-core:2.0.0")
    implementation("com.github.xuexiangjys.XUtil:xutil-sub:1.1.8")
    implementation("com.alibaba:fastjson:1.2.62")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
}
