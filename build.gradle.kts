import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
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

fun collectProjectDependencies(projectPath: String): List<String> {
    return project(projectPath).configurations
        .flatMap { configuration ->
            configuration.dependencies
                .withType(ProjectDependency::class.java)
                .map { dependency -> normalizeProjectPath(dependency.path) }
        }
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
        if (":new-vpn-daemon-jvm" in coreDependencies) {
            throw GradleException("Architecture rule violated: :new-vpn must not depend on :new-vpn-daemon-jvm.")
        }

        val daemonJvmDependencies = daemonJvmProjectDependencies.get().toSet()
        if (":new-vpn-daemon-protocol" !in daemonJvmDependencies) {
            throw GradleException("Architecture rule violated: :new-vpn-daemon-jvm must depend on :new-vpn-daemon-protocol.")
        }
        val daemonJvmUnexpected = daemonJvmDependencies - setOf(":new-vpn-daemon-protocol")
        if (daemonJvmUnexpected.isNotEmpty()) {
            throw GradleException(
                "Architecture rule violated: :new-vpn-daemon-jvm has unexpected project dependencies: $daemonJvmUnexpected"
            )
        }

        val daemonClientDependencies = daemonClientProjectDependencies.get().toSet()
        if (":new-vpn-daemon-protocol" !in daemonClientDependencies) {
            throw GradleException(
                "Architecture rule violated: :new-vpn-daemon-client-jvm must depend on :new-vpn-daemon-protocol."
            )
        }
        val daemonClientUnexpected = daemonClientDependencies - setOf(":new-vpn-daemon-protocol")
        if (daemonClientUnexpected.isNotEmpty()) {
            throw GradleException(
                "Architecture rule violated: :new-vpn-daemon-client-jvm has unexpected project dependencies: $daemonClientUnexpected"
            )
        }
    }
}

val checkArchitectureBoundaries = tasks.register<ArchitectureBoundaryCheckTask>("checkArchitectureBoundaries") {
    group = "verification"
    description = "Enforces phase 01 module dependency boundaries."
}

gradle.projectsEvaluated {
    checkArchitectureBoundaries.configure {
        coreProjectDependencies.set(collectProjectDependencies(":new-vpn"))
        daemonJvmProjectDependencies.set(collectProjectDependencies(":new-vpn-daemon-jvm"))
        daemonClientProjectDependencies.set(collectProjectDependencies(":new-vpn-daemon-client-jvm"))
    }
}

val ciNewVpnCore = tasks.register("ciNewVpnCore") {
    group = "verification"
    description = "CI entry task for :new-vpn."
    dependsOn(":new-vpn:check")
}

val ciNewVpnDaemonProtocol = tasks.register("ciNewVpnDaemonProtocol") {
    group = "verification"
    description = "CI entry task for :new-vpn-daemon-protocol."
    dependsOn(":new-vpn-daemon-protocol:check")
}

val ciNewVpnDaemonJvm = tasks.register("ciNewVpnDaemonJvm") {
    group = "verification"
    description = "CI entry task for :new-vpn-daemon-jvm."
    dependsOn(":new-vpn-daemon-jvm:check")
}

val ciNewVpnDaemonClientJvm = tasks.register("ciNewVpnDaemonClientJvm") {
    group = "verification"
    description = "CI entry task for :new-vpn-daemon-client-jvm."
    dependsOn(":new-vpn-daemon-client-jvm:check")
}

tasks.register("ciPhase01") {
    group = "verification"
    description = "Aggregate CI entry task for phase 01 scaffolding."
    dependsOn(
        checkArchitectureBoundaries,
        ciNewVpnCore,
        ciNewVpnDaemonProtocol,
        ciNewVpnDaemonJvm,
        ciNewVpnDaemonClientJvm
    )
}
