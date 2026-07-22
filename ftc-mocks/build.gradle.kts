plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "com.areslib"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core"))
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.github.ARES-23247.ARESLib-Kotlin"
            artifactId = "ftc-mocks"
            version = "master-SNAPSHOT"
        }
    }
}
