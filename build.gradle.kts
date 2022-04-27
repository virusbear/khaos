import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10" apply false
}

subprojects {
    group = "com.virusbear"
    version = "0.0.1"

    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "16"
        }
    }
}