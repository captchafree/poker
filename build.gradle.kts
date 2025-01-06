plugins {
    kotlin("jvm") version "2.0.21"
    // application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.tripleslate"
version = "1.0-SNAPSHOT"

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "com.tripleslate.poker.MainKt" // Ensures the main class is set correctly
        )
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.1")

    // optional support for rendering markdown in help messages
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")
}
