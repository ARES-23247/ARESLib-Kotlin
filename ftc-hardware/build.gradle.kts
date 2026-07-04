plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "com.areslib"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://repo.dairy.foundation/releases")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    compileOnly(project(":ftc-mocks"))
    testImplementation(project(":ftc-mocks"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.areslib"
            artifactId = "ftc-hardware"
            version = "1.0-SNAPSHOT"
        }
    }
}
