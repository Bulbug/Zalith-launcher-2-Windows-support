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
}

compose.desktop {
    application {
        mainClass = "launcher.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            // the packaged app only contains the Java modules listed here (the launcher needs java.net.http)
            modules("java.net.http", "jdk.unsupported", "java.instrument")
            packageName = "Zalith2WindowsSupport"
            packageVersion = "0.5.0"
        }
    }
}