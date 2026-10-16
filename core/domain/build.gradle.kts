plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { testImplementation(libs.junit); testRuntimeOnly(libs.junit.launcher) }
tasks.test { useJUnitPlatform() }

// Keep the frozen 03A prototype as evidence, but all original tests now resolve the production API.
// A renamed generated copy exists only for differential tests, never in the runtime JAR.
val prepareMeasurementOracle by tasks.registering {
    val source = layout.projectDirectory.file("src/test/kotlin/net/plainnotes/app/domain/measurement/MeasurementCore.kt")
    val output = layout.buildDirectory.file("generated/measurementOracle/net/plainnotes/app/domain/prototype/measurement/MeasurementCore.kt")
    inputs.file(source); outputs.file(output)
    doLast {
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(source.asFile.readText().replace(
                "package net.plainnotes.app.domain.measurement", "package net.plainnotes.app.domain.prototype.measurement"))
        }
    }
}
kotlin.sourceSets.named("test") {
    kotlin.exclude("net/plainnotes/app/domain/measurement/MeasurementCore.kt")
    kotlin.srcDir(layout.buildDirectory.dir("generated/measurementOracle"))
}
tasks.named("compileTestKotlin") { dependsOn(prepareMeasurementOracle) }
