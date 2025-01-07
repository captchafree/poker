import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.KotlinClosure2

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.tripleslate"
version = "1.0-SNAPSHOT"

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "com.tripleslate.poker.cli.MainKt"
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.1")

    // optional support for rendering markdown in help messages
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.0")
}

tasks.withType<Test> {
    testLogging {
        events = setOf(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )
    }

    afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
        if (desc.parent == null) { // will match the outermost suite
            val pass = "${Color.GREEN}${result.successfulTestCount} passed${Color.NONE}"
            val fail = "${Color.RED}${result.failedTestCount} failed${Color.NONE}"
            val skip = "${Color.YELLOW}${result.skippedTestCount} skipped${Color.NONE}"
            val type = when (val r = result.resultType) {
                TestResult.ResultType.SUCCESS -> "${Color.GREEN}$r${Color.NONE}"
                TestResult.ResultType.FAILURE -> "${Color.RED}$r${Color.NONE}"
                TestResult.ResultType.SKIPPED -> "${Color.YELLOW}$r${Color.NONE}"
            }
            val output = "Results: $type (${result.testCount} tests, $pass, $fail, $skip)"
            val startItem = "|   "
            val endItem = "   |"
            val repeatLength = startItem.length + output.length + endItem.length - 36
            println("")
            println("\n" + ("-" * repeatLength) + "\n" + startItem + output + endItem + "\n" + ("-" * repeatLength))
        }
    }))
}

operator fun String.times(x: Int): String {
    return List(x) { this }.joinToString("")
}

internal enum class Color(ansiCode: Int) {
    NONE(0), BLACK(30), RED(31), GREEN(32), YELLOW(33), BLUE(34), PURPLE(35), CYAN(36), WHITE(37);

    private val ansiString: String = "\u001B[${ansiCode}m"

    override fun toString(): String {
        return ansiString
    }
}
