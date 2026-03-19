plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApiWarning()
}

dependencies {
    implementation(project(":new-vpn-daemon-protocol"))

    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
