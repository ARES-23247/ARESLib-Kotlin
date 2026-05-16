plugins {
    kotlin("jvm") version "1.9.23"
}

group = "com.areslib"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://frcmaven.wpi.edu/artifactory/release/")
    maven("https://maven.ctr-electronics.com/release/")
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("edu.wpi.first.wpilibj:wpilibj-java:2024.3.2")
    compileOnly("edu.wpi.first.hal:hal-java:2024.3.2")
    compileOnly("edu.wpi.first.wpimath:wpimath-java:2024.3.2")
    compileOnly("edu.wpi.first.wpiutil:wpiutil-java:2024.3.2")
    compileOnly("com.ctre.phoenix6:wpiapi-java:24.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
