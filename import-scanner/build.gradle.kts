plugins {
    kotlin("jvm") version "1.6.10"
    java
    id("org.jetbrains.kotlin.plugin.serialization") version "1.6.20-RC"
}

sourceSets {
    java {
        sourceSets {
            main {
                java {
                    srcDir("src/main/gen")
                }
            }
        }
    }
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    implementation("org.antlr:antlr4-runtime:4.9.3")

    implementation("io.ktor:ktor-client-core:1.6.8")
    implementation("io.ktor:ktor-client-cio:1.6.8")
    implementation("io.ktor:ktor-client-serialization:1.6.8")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}