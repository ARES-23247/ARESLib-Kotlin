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
    implementation("com.github.FRC-For-FTC-Tools:nt-self-impl:0.0.2")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    testImplementation(kotlin("test"))
    compileOnly("edu.wpi.first.wpilibj:wpilibj-java:2026.2.1")
    compileOnly("edu.wpi.first.hal:hal-java:2026.2.1")
    compileOnly("edu.wpi.first.wpimath:wpimath-java:2026.2.1")
    compileOnly("edu.wpi.first.wpiutil:wpiutil-java:2026.2.1")
    compileOnly("com.ctre.phoenix6:wpiapi-java:26.1.1")
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
            artifactId = "core"
            version = "1.0-SNAPSHOT"
        }
    }
}
