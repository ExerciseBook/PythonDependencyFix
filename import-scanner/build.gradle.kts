import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.6.10"
    java
    id("org.jetbrains.kotlin.plugin.serialization") version "1.6.20-RC"
    id("com.github.johnrengelman.shadow").version("7.1.2")
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


tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    manifest {
        attributes("Main-Class" to "com.superexercisebook.importscanner.Main")
    }

    dependencies {
        include { it.moduleGroup.startsWith("io.ktor") }
        include { it.moduleGroup.startsWith("org.antlr") }
        include { it.moduleGroup.startsWith("org.apiguardian") }
        include { it.moduleGroup.startsWith("org.jetbrains") }
//        include { it.moduleGroup.startsWith("org.junit") }
//        include { it.moduleGroup.startsWith("org.opentest4j") }
        include { it.moduleGroup.startsWith("org.slf4j") }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
