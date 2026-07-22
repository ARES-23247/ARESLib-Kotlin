plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "com.areslib"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://frcmaven.wpi.edu/artifactory/release/")
    maven("https://maven.ctr-electronics.com/release/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.code.gson:gson:2.10.1")
    api("org.java-websocket:Java-WebSocket:1.5.3") // transitive dep of NT4Server (extends WebSocketServer)
    implementation("org.msgpack:msgpack-core:0.9.3") // Add msgpack core because it was inside nt-self-impl!
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.7.1") // needed for NT4Server, using older version for Android 7.1.1 (API 25) compatibility
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    api("org.nanohttpd:nanohttpd:2.3.1")
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
            groupId = "com.github.ARES-23247.ARESLib-Kotlin"
            artifactId = "core"
            version = "master-SNAPSHOT"
        }
    }
}
