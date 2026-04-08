package com.holderzone.build_logic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import java.io.File
import java.util.Properties

val Project.libs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureKotlinAndroid() {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

internal fun Project.defaultNamespace(): String {
    val suffix = path.removePrefix(":").replace(":", ".").replace("-", ".")
    return "${libs.findVersion("namespace").get()}.$suffix"
}

internal fun Project.configureLibraryDefaults() {
    extensions.configure<LibraryExtension> {
        compileSdk = libs.findVersion("compileSdk").get().toString().toInt()
        namespace = defaultNamespace()
        defaultConfig {
            minSdk = libs.findVersion("minSdk").get().toString().toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = org.gradle.api.JavaVersion.VERSION_11
            targetCompatibility = org.gradle.api.JavaVersion.VERSION_11
        }
        buildFeatures {
            buildConfig = true
        }
    }
}

internal fun Project.configureSampleAppDefaults() {
    extensions.configure<ApplicationExtension> {
        compileSdk = libs.findVersion("compileSdk").get().toString().toInt()
        namespace = defaultNamespace()
        defaultConfig {
            applicationId = defaultNamespace()
            minSdk = libs.findVersion("minSdk").get().toString().toInt()
            targetSdk = libs.findVersion("targetSdk").get().toString().toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            vectorDrawables.useSupportLibrary = true
            val versionInfo = loadVersionInfo(rootProject.file("version.properties"))
            versionCode = versionInfo.versionCode
            versionName = versionInfo.versionName
        }
        compileOptions {
            sourceCompatibility = org.gradle.api.JavaVersion.VERSION_11
            targetCompatibility = org.gradle.api.JavaVersion.VERSION_11
        }
        buildFeatures {
            buildConfig = true
        }
    }
}

internal data class ModuleVersionInfo(
    val versionName: String,
    val versionCode: Int,
    val artifactId: String? = null,
)

internal fun Project.loadVersionInfo(file: File): ModuleVersionInfo {
    val properties = Properties().apply {
        file.inputStream().use(::load)
    }
    return ModuleVersionInfo(
        versionName = properties.getProperty("VERSION_NAME"),
        versionCode = properties.getProperty("VERSION_CODE").toInt(),
        artifactId = properties.getProperty("ARTIFACT_ID"),
    )
}

internal fun Project.loadCameraFamilyVersionInfo(): ModuleVersionInfo {
    return loadVersionInfo(rootProject.file("hardware/camera/version.properties"))
}

internal fun Project.loadCabinetFamilyVersionInfo(): ModuleVersionInfo {
    return loadVersionInfo(rootProject.file("hardware/cabinet/version.properties"))
}

internal fun Project.loadScaleFamilyVersionInfo(): ModuleVersionInfo {
    return loadVersionInfo(rootProject.file("hardware/scale/version.properties"))
}

internal fun Project.resolvePublicationGroupId(): String {
    return providers.gradleProperty("foundation.publish.group").orNull ?: "com.holderzone"
}

internal fun Project.resolveLocalMavenRepoDir(): File {
    val relativePath = providers.gradleProperty("foundation.publish.repoDir").orNull ?: "dist/repo"
    return rootProject.file(relativePath)
}

internal fun Project.resolvePublicationArtifactId(): String {
    val extraArtifactId = extensions.extraProperties
        .takeIf { it.has("artifactId") }
        ?.get("artifactId")
        ?.toString()
        ?.takeIf { it.isNotBlank() }
    if (extraArtifactId != null) {
        return extraArtifactId
    }

    val archiveName = extensions.findByType(BasePluginExtension::class.java)
        ?.archivesName
        ?.orNull
        ?.takeIf { it.isNotBlank() }
    if (archiveName != null) {
        val suffix = "-${version}"
        return archiveName.removeSuffix(suffix)
    }

    return name
}

internal fun Project.deriveFamilyArtifactId(
    familyRootPath: String,
    rootArtifactId: String,
): String {
    val moduleSuffix = path
        .removePrefix(familyRootPath)
        .removePrefix(":")
        .takeIf { it.isNotBlank() }
        ?.replace(":", "-")

    return if (moduleSuffix == null) {
        rootArtifactId
    } else {
        "$rootArtifactId-$moduleSuffix"
    }
}

internal fun String.capitalized(): String {
    return replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase()
        } else {
            char.toString()
        }
    }
}
