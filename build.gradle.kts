import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.gobley.cargo).apply(false)
}

fun normalizeProjectPath(path: String): String {
    return if (path.startsWith(":")) path else ":$path"
}

fun collectResolvedProjectDependencies(projectPath: String, configurationNames: List<String>): List<String> {
    val targetProject = project(projectPath)
    val normalizedProjectPath = normalizeProjectPath(projectPath)

    return configurationNames
        .map { configurationName ->
            targetProject.configurations.findByName(configurationName)
                ?: throw GradleException(
                    "Architecture rule misconfigured: $normalizedProjectPath does not define configuration '$configurationName'."
                )
        }
        .onEach { configuration ->
            if (!configuration.isCanBeResolved) {
                throw GradleException(
                    "Architecture rule misconfigured: $normalizedProjectPath configuration '${configuration.name}' must be resolvable."
                )
            }
        }
        .flatMap { configuration ->
            configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                (component.id as? ProjectComponentIdentifier)?.projectPath
            }
        }
        .filterNot { dependencyPath -> dependencyPath == normalizedProjectPath }
        .distinct()
        .sorted()
}

abstract class ArchitectureBoundaryCheckTask : DefaultTask() {
    @get:Input
    abstract val coreProjectDependencies: ListProperty<String>

    @get:Input
    abstract val daemonJvmProjectDependencies: ListProperty<String>

    @get:Input
    abstract val daemonClientProjectDependencies: ListProperty<String>

    @TaskAction
    fun verify() {
        val coreDependencies = coreProjectDependencies.get().toSet()
        if (":wg-kotlin-daemon-jvm" in coreDependencies) {
            throw GradleException("Architecture rule violated: :wg-kotlin must not depend on :wg-kotlin-daemon-jvm.")
        }

        val daemonJvmDependencies = daemonJvmProjectDependencies.get().toSet()
        if (":wg-kotlin-daemon-protocol" !in daemonJvmDependencies) {
            throw GradleException("Architecture rule violated: :wg-kotlin-daemon-jvm must depend on :wg-kotlin-daemon-protocol.")
        }
        val daemonJvmUnexpected = daemonJvmDependencies - setOf(":wg-kotlin-daemon-protocol", ":wg-kotlin", ":wg-kotlin-daemon-client-jvm")
        if (daemonJvmUnexpected.isNotEmpty()) {
            throw GradleException(
                "Architecture rule violated: :wg-kotlin-daemon-jvm has unexpected project dependencies: $daemonJvmUnexpected"
            )
        }

        val daemonClientDependencies = daemonClientProjectDependencies.get().toSet()
        if (":wg-kotlin-daemon-protocol" !in daemonClientDependencies) {
            throw GradleException(
                "Architecture rule violated: :wg-kotlin-daemon-client-jvm must depend on :wg-kotlin-daemon-protocol."
            )
        }
        val daemonClientUnexpected = daemonClientDependencies - setOf(":wg-kotlin-daemon-protocol")
        if (daemonClientUnexpected.isNotEmpty()) {
            throw GradleException(
                "Architecture rule violated: :wg-kotlin-daemon-client-jvm has unexpected project dependencies: $daemonClientUnexpected"
            )
        }
    }
}

val checkArchitectureBoundaries = tasks.register<ArchitectureBoundaryCheckTask>("checkArchitectureBoundaries") {
    group = "verification"
    description = "Enforces phase 01 module dependency boundaries."
}

val wgKotlinMainClasspathConfigurations = listOf("jvmCompileClasspath", "jvmRuntimeClasspath")
val jvmMainClasspathConfigurations = listOf("compileClasspath", "runtimeClasspath")

gradle.projectsEvaluated {
    checkArchitectureBoundaries.configure {
        coreProjectDependencies.set(
            providers.provider {
                collectResolvedProjectDependencies(":wg-kotlin", wgKotlinMainClasspathConfigurations)
            }
        )
        daemonJvmProjectDependencies.set(
            providers.provider {
                collectResolvedProjectDependencies(":wg-kotlin-daemon-jvm", jvmMainClasspathConfigurations)
            }
        )
        daemonClientProjectDependencies.set(
            providers.provider {
                collectResolvedProjectDependencies(":wg-kotlin-daemon-client-jvm", jvmMainClasspathConfigurations)
            }
        )
    }
}

val ciWgKotlinCore = tasks.register("ciWgKotlinCore") {
    group = "verification"
    description = "CI entry task for :wg-kotlin."
    dependsOn(":wg-kotlin:check")
}

val ciWgKotlinDaemonProtocol = tasks.register("ciWgKotlinDaemonProtocol") {
    group = "verification"
    description = "CI entry task for :wg-kotlin-daemon-protocol."
    dependsOn(":wg-kotlin-daemon-protocol:check")
}

val ciWgKotlinDaemonJvm = tasks.register("ciWgKotlinDaemonJvm") {
    group = "verification"
    description = "CI entry task for :wg-kotlin-daemon-jvm."
    dependsOn(":wg-kotlin-daemon-jvm:check")
}

val ciWgKotlinDaemonClientJvm = tasks.register("ciWgKotlinDaemonClientJvm") {
    group = "verification"
    description = "CI entry task for :wg-kotlin-daemon-client-jvm."
    dependsOn(":wg-kotlin-daemon-client-jvm:check")
}

tasks.register("ciPhase01") {
    group = "verification"
    description = "Aggregate CI entry task for phase 01 scaffolding."
    dependsOn(
        checkArchitectureBoundaries,
        ciWgKotlinCore,
        ciWgKotlinDaemonProtocol,
        ciWgKotlinDaemonJvm,
        ciWgKotlinDaemonClientJvm
    )
}
