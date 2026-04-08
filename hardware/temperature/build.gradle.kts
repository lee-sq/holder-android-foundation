plugins {
    id("com.holderzone.android.hardware.library")
}

android {
    namespace = "com.holderzone.temperature"
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
