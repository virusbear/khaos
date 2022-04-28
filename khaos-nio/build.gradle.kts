plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":khaos-core"))

    implementation("io.github.microutils:kotlin-logging:2.1.21")
}