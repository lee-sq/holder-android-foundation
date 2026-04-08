package com.holderzone.build_logic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension

class AndroidScaleFamilyLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.holderzone.android.library")

            val versionInfo = loadScaleFamilyVersionInfo()
            val artifactId = deriveFamilyArtifactId(
                familyRootPath = ":hardware:scale",
                rootArtifactId = versionInfo.artifactId ?: "hardware-scale",
            )
            version = versionInfo.versionName
            extensions.extraProperties["moduleVersionCode"] = versionInfo.versionCode
            extensions.extraProperties["scaleFamilyVersionName"] = versionInfo.versionName
            extensions.extraProperties["artifactId"] = artifactId
            extensions.findByType(BasePluginExtension::class.java)?.archivesName?.set(
                "$artifactId-${versionInfo.versionName}"
            )

            tasks.register("printModuleVersion") {
                group = "versioning"
                description = "Prints the module artifact and version information."
                doLast {
                    println("${project.path} -> $artifactId:${versionInfo.versionName} (${versionInfo.versionCode})")
                }
            }
        }
    }
}
