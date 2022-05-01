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
    implementation("io.ktor:ktor-client-jackson:1.6.8")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.13.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.13.1")

    implementation("org.eclipse.jgit:org.eclipse.jgit:6.1.0.202203080745-r")
    implementation("io.github.g00fy2:versioncompare:1.5.0")

    implementation("commons-cli:commons-cli:1.5.0")
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
        include { it.moduleGroup.startsWith("com.fasterxml") }
        include { it.moduleGroup.startsWith("org.eclipse") }
        include { it.moduleGroup.startsWith("io.github") }
        include { it.moduleGroup.equals("commons-cli", ignoreCase = true) }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
