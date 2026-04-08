plugins {
    id("com.holderzone.android.sample.app")
}

android {
    namespace = "com.holderzone.samples.cabinet"
    defaultConfig {
        applicationId = "com.holderzone.samples.cabinet"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":hardware:cabinet"))
}
