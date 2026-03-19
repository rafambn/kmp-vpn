plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.rafambn.kmpvpn.daemon.DaemonApplicationKt")
}

dependencies {
    implementation(project(":new-vpn-daemon-protocol"))

    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
