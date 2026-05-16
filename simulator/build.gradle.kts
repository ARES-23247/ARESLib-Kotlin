plugins {
    kotlin("jvm") version "1.9.23"
    application
}

repositories {
    mavenCentral()
    maven("https://frcmaven.wpi.edu/artifactory/release/")
}

application {
    mainClass.set("com.areslib.sim.DesktopSimLauncher")
}

dependencies {
    implementation(project(":core"))
    
    // Dyn4j Physics Engine
    implementation("org.dyn4j:dyn4j:4.2.2")

    // WPILib Desktop Simulation native dependencies for NT4 and DataLog
    val wpiVersion = "2024.3.2"
    val platform = "windowsx86-64"

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
}

kotlin {
    jvmToolchain(17)
}
