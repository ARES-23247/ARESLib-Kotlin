plugins {
    kotlin("jvm") version "1.9.23" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("org.jetbrains.dokka") version "1.9.20"
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
    }
}

subprojects {
    if (name != "tools" && name != "FtcRobotController") {
        apply(plugin = "org.jetbrains.dokka")
    }
}
