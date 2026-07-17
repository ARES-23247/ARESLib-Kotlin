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
            groupId = "com.areslib"
            artifactId = "ftc-mocks"
            version = "1.0-SNAPSHOT"
        }
    }
}
