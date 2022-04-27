val logback_version: String by project

plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization") version "1.6.10"
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
}

application {
    mainClass.set("com.virusbear.ApplicationKt")

    applicationDefaultJvmArgs = listOf("-XX:+UseZGC")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.github.microutils:kotlin-logging:2.1.21")

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-hocon:1.3.2")

    implementation("com.virusbear.metrix:metrix-micrometer:0.0.2")
}