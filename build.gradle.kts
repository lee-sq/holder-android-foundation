import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

val hardwareModules = listOf(
    ":hardware:base",
    ":hardware:cabinet",
    ":hardware:camera",
    ":hardware:scale",
    ":hardware:temperature",
)

val cabinetSdkModules = listOf(
    ":hardware:cabinet",
)

val scaleSdkModules = listOf(
    ":hardware:scale",
)

val cameraSdkModules = listOf(
    ":hardware:camera",
    ":hardware:camera:compose",
    ":hardware:camera:face-mlkit",
)

val sharedHardwareVendorModules = listOf(
    ":hardware:vendor",
)

val publicSdkModules = (
    listOf(":core") +
        cabinetSdkModules +
        cameraSdkModules +
        scaleSdkModules
    ).distinct()

val localRepoPublishModules = (
    publicSdkModules +
        sharedHardwareVendorModules
    ).distinct()

fun Project.loadModuleVersion(): String {
    val props = Properties().apply {
        file("version.properties").inputStream().use(::load)
    }
    return props.getProperty("VERSION_NAME")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    delete(
        file("core/ui/build"),
        file("core/network/build"),
        file("core/platform/build"),
        file("core/navigation/build"),
    )
}

tasks.register("printHardwareVersions") {
    dependsOn(hardwareModules.map { "$it:printModuleVersion" })
}

tasks.register("assembleHardwareRelease") {
    dependsOn(hardwareModules.map { "$it:assembleRelease" })
}

tasks.register("assembleCameraSdkRelease") {
    dependsOn(cameraSdkModules.map { "$it:assembleRelease" })
}

tasks.register("printCameraSdkVersions") {
    dependsOn(cameraSdkModules.map { "$it:printModuleVersion" })
}

tasks.register("assembleCabinetSdkRelease") {
    dependsOn(":hardware:cabinet:assembleRelease")
}

tasks.register("printCabinetSdkVersion") {
    dependsOn(cabinetSdkModules.map { "$it:printModuleVersion" })
}

tasks.register("assembleScaleSdkRelease") {
    dependsOn(":hardware:scale:assembleRelease")
}

tasks.register("printScaleSdkVersion") {
    dependsOn(scaleSdkModules.map { "$it:printModuleVersion" })
}

tasks.register("printCoreVersion") {
    doLast {
        println("root -> ${rootProject.loadModuleVersion()}")
    }
}

tasks.register("printCoreSdkVersion") {
    dependsOn(":core:printModuleVersion")
}

tasks.register("assembleCoreSdkRelease") {
    dependsOn(":core:assembleRelease")
}

tasks.register<Delete>("cleanLocalMavenRepo") {
    delete(layout.projectDirectory.dir("dist/repo"))
}

tasks.register("printFoundationPublicationCoordinates") {
    group = "publishing"
    description = "Prints the Maven coordinates of all public SDK modules."
    dependsOn(publicSdkModules.map { "$it:printPublicationCoordinate" })
}

tasks.register("publishFoundationToLocalRepo") {
    group = "publishing"
    description = "Publishes all public SDK modules to the local Maven repository under dist/repo."
    dependsOn(localRepoPublishModules.map { "$it:publishToLocalFoundationRepo" })
    doLast {
        println("local repo -> ${layout.projectDirectory.dir("dist/repo").asFile.absolutePath}")
    }
}
