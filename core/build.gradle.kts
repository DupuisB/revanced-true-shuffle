plugins {
    kotlin("jvm") version "2.2.21"
}

repositories { mavenCentral() }

kotlin { jvmToolchain(21) }

dependencies { testImplementation(kotlin("test-junit5")); testRuntimeOnly("org.junit.platform:junit-platform-launcher") }

tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("benchmarkShuffle") {
    group = "verification"
    description = "Measure local JVM shuffle generation (not an Android benchmark)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.revanced.trueshuffle.core.ShuffleBenchmarkKt")
}
