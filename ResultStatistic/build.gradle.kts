import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.6.10"
    java
    id("com.github.johnrengelman.shadow").version("7.1.2")
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
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
