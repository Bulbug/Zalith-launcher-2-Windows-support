import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
}

// Function to determine if we're in GitHub Actions CI
fun isCi(): Boolean = System.getenv("CI")?.toBoolean() ?: false

// Determine if we should build installers (only in CI)
fun shouldBuildInstallers(): Boolean = isCi()

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
            // Only build EXE/MSI in CI (GitHub Actions)
            if (shouldBuildInstallers()) {
                targetFormats(TargetFormat.Exe, TargetFormat.Msi)
                // Let Compose plugin manage WiX installation automatically
                packageOptions {
                    // Set this to true to let the plugin download WiX if needed
                    downloadWix = true
                }
            } else {
                targetFormats(TargetFormat.AppImage) // Only AppImage locally
            }
            // the packaged app only contains the Java modules listed here (the launcher needs java.net.http)
            modules("java.net.http", "jdk.unsupported", "java.instrument")
            packageName = "Zalith2WindowsSupport"
            packageVersion = "0.5.0"
        }
    }
}