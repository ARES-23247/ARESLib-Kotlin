plugins {
    kotlin("jvm") version "1.9.23"
}

group = "com.areslib"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":core"))
}

kotlin {
    jvmToolchain(17)
}
