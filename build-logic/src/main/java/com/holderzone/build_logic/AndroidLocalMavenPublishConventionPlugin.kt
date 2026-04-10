package com.holderzone.build_logic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import java.io.File

/**
 * Android Library 本地 Maven 发布约定插件。
 *
 * 该插件的目标不是把所有模块都发布出去，而是让“已经具备 SDK 交付意义”的模块，
 * 能稳定地输出到仓库根目录下的 `dist/repo`，并保留完整的 Maven 元数据，
 * 供其他本地仓库直接通过 `maven { url = uri(...) }` 方式接入。
 */
class AndroidLocalMavenPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    publishing {
                        singleVariant("release") {
                            withSourcesJar()
                        }
                    }
                }

                afterEvaluate {
                    configureFoundationLocalMavenPublishing()
                }
            }
        }
    }
}

private fun Project.configureFoundationLocalMavenPublishing() {
    val publicationName = "release"
    val repositoryName = "foundationLocal"
    val publishTaskName = "publish${publicationName.capitalized()}PublicationTo${repositoryName.capitalized()}Repository"
    val publishToMavenLocalTaskName = "publish${publicationName.capitalized()}PublicationToMavenLocal"
    val coordinate = "${resolvePublicationGroupId()}:${resolvePublicationArtifactId()}:${version}"
    val normalizeFoundationLocalTask = registerNormalizePublishedAarTask(
        taskName = "normalize${publicationName.capitalized()}PublicationFor${repositoryName.capitalized()}Repository",
        repositoryDir = resolveLocalMavenRepoDir(),
    )
    val normalizeMavenLocalTask = registerNormalizePublishedAarTask(
        taskName = "normalize${publicationName.capitalized()}PublicationForMavenLocal",
        repositoryDir = resolveMavenLocalRepoDir(),
    )

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = repositoryName
                url = resolveLocalMavenRepoDir().toURI()
            }
        }

        publications {
            if (findByName(publicationName) == null) {
                create<MavenPublication>(publicationName) {
                    groupId = resolvePublicationGroupId()
                    artifactId = resolvePublicationArtifactId()
                    version = project.version.toString()
                    from(project.components.getByName("release"))

                    pom {
                        name.set(artifactId)
                        description.set("Published from ${project.path} in holder-android-foundation.")
                    }
                }
            }
        }
    }

    tasks.matching { it.name == publishTaskName }.configureEach {
        finalizedBy(normalizeFoundationLocalTask)
    }
    tasks.matching { it.name == publishToMavenLocalTaskName }.configureEach {
        finalizedBy(normalizeMavenLocalTask)
    }

    tasks.register("printPublicationCoordinate") {
        group = "publishing"
        description = "Prints the Maven coordinate and local repository path for this module."
        doLast {
            println("${project.path} -> $coordinate")
            println("repo -> ${resolveLocalMavenRepoDir().absolutePath}")
        }
    }

    tasks.register("publishToLocalFoundationRepo") {
        group = "publishing"
        description = "Publishes this module to the root local Maven repository."
        dependsOn(publishTaskName)
    }
}

private fun Project.registerNormalizePublishedAarTask(
    taskName: String,
    repositoryDir: File,
) = tasks.register(taskName) {
    group = "publishing"
    description = "Creates an unclassified AAR alias next to the published release AAR for JitPack consumers."
    doLast {
        val artifactId = resolvePublicationArtifactId()
        val versionName = version.toString()
        val publicationDir = repositoryDir
            .resolve(resolvePublicationGroupId().replace('.', '/'))
            .resolve(artifactId)
            .resolve(versionName)

        val releaseAar = publicationDir.resolve("$artifactId-$versionName-release.aar")
        val plainAar = publicationDir.resolve("$artifactId-$versionName.aar")
        if (!releaseAar.exists()) {
            logger.info("skip AAR alias for ${project.path}, source file missing: ${releaseAar.absolutePath}")
            return@doLast
        }

        releaseAar.copyTo(target = plainAar, overwrite = true)
        logger.lifecycle("normalized AAR alias -> ${plainAar.absolutePath}")
    }
}
