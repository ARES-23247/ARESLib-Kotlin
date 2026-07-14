plugins {
    kotlin("jvm")
    application
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://frcmaven.wpi.edu/artifactory/release/")
    maven("https://jitpack.io")
}

application {
    mainClass.set("com.areslib.sim.DesktopSimLauncher")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ftc-hardware"))
    implementation(project(":ftc-mocks"))
    
    // Dyn4j Physics Engine
    implementation("org.dyn4j:dyn4j:4.2.2")

    // JSON Parser
    implementation("com.google.code.gson:gson:2.10.1")

    // WPILib Desktop Simulation native dependencies for NT4 and DataLog
    val wpiVersion = "2024.3.2"
    val osName = System.getProperty("os.name").lowercase()
    val platform = when {
        osName.contains("windows") -> "windowsx86-64"
        osName.contains("mac") -> "osxuniversal"
        osName.contains("linux") -> "linuxx86-64"
        else -> "windowsx86-64"
    }

    implementation("edu.wpi.first.wpilibj:wpilibj-java:$wpiVersion")
    implementation("edu.wpi.first.cameraserver:cameraserver-java:$wpiVersion")
    implementation("edu.wpi.first.wpinet:wpinet-java:$wpiVersion")
    implementation("edu.wpi.first.wpinet:wpinet-jni:$wpiVersion:$platform")
    implementation("edu.wpi.first.ntcore:ntcore-java:$wpiVersion")
    implementation("edu.wpi.first.ntcore:ntcore-jni:$wpiVersion:$platform")
    
    implementation("edu.wpi.first.wpiutil:wpiutil-java:$wpiVersion")
    implementation("edu.wpi.first.wpiutil:wpiutil-jni:$wpiVersion:$platform")
    
    implementation("edu.wpi.first.wpimath:wpimath-java:$wpiVersion")
    implementation("edu.wpi.first.wpimath:wpimath-jni:$wpiVersion:$platform")
    
    implementation("edu.wpi.first.hal:hal-java:$wpiVersion")
    implementation("edu.wpi.first.hal:hal-jni:$wpiVersion:$platform")
    
    // Slf4j for logging (optional, usually good to have)
    implementation("org.slf4j:slf4j-simple:2.0.12")
    
    // LWJGL Core
    implementation("org.lwjgl:lwjgl:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-macos")
    
    // LWJGL GLFW for robust cross-platform Gamepad support (auto-extracts natives)
    implementation("org.lwjgl:lwjgl-glfw:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.3:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.3:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.3:natives-macos")
    
    // Reflections for finding OpModes
    implementation("org.reflections:reflections:0.10.2")
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

val javaToolchains = project.extensions.getByType<JavaToolchainService>()

tasks.named<JavaExec>("run") {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
    if (project.hasProperty("appArgs")) {
        args(project.property("appArgs").toString().split(" "))
    }
}

tasks.register<JavaExec>("runFakeController") {
    group = "application"
    mainClass.set("com.areslib.sim.infra.FakeControllerClient")
    classpath = sourceSets.main.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}

tasks.register<JavaExec>("runVerification") {
    group = "application"
    mainClass.set("com.areslib.sim.VerificationAppKt")
    classpath = sourceSets.main.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.areslib"
            artifactId = "simulator"
            version = "1.0-SNAPSHOT"
        }
    }
}
