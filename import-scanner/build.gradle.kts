plugins {
    kotlin("jvm") version "1.6.10"
    java
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
}

tasks.getByName<Test>("test") {
useJUnitPlatform()
}