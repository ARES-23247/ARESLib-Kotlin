plugins {
    kotlin("jvm")
    id("edu.wpi.first.GradleRIO") version "2026.2.1"
    `maven-publish`
}

group = "com.areslib"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core"))

    // WPILib dependencies list iteration for Kotlin DSL
    wpi.java.deps.wpilib().forEach { dep ->
        implementation(dep)
    }
    
    // Vendor dependencies list iteration
    wpi.java.vendor.java().forEach { dep ->
        implementation(dep)
    }
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.areslib"
            artifactId = "frc-hardware"
            version = "1.0-SNAPSHOT"
        }
    }
}
