import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.1")
    implementation("androidx.compose.animation:animation:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    // JNA for Windows Credential Manager / DPAPI
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
}

tasks.withType<org.gradle.api.tasks.testing.Test> {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "launcher.MainKt"

        nativeDistributions {
            packageName = "DeskLauncher"
            packageVersion = "0.5.0"

            // Build formats suitable for this platform
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.AppImage)

            // the packaged app only contains the Java modules listed here (the launcher needs java.net.http)
            modules("java.net.http", "jdk.unsupported", "java.instrument")
        }
    }
}