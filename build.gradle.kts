import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea("2026.2.4")
        testFramework(TestFrameworkType.Platform)
        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241" // Compatible desde IntelliJ 2024.1 en adelante
            untilBuild = provider { null } // Sin límite superior de versión
        }
    }
}