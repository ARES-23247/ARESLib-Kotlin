plugins {
    kotlin("jvm") version "1.9.23" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("org.jetbrains.dokka") version "1.9.20"
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.0")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://frcmaven.wpi.edu/artifactory/release/")
        maven("https://maven.ctr-electronics.com/release/")
        maven("https://jitpack.io")
        maven("https://repo.dairy.foundation/releases")
    }
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.23")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23")
        }
    }
}

subprojects {
    if (name != "tools" && name != "FtcRobotController") {
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}
