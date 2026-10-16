plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }
android {
    namespace = "net.plainnotes.app.data"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Optional: -ProbolectricDir=<dir with android-all jars> runs Robolectric offline (sandboxes without direct Maven access).
        unitTests.all { t -> providers.gradleProperty("robolectricDir").orNull?.let { t.systemProperty("robolectric.offline", "true"); t.systemProperty("robolectric.dependency.dir", it) } }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:domain")); implementation(project(":importer")); implementation(libs.bouncycastle)
    implementation(libs.core.ktx)
    implementation(libs.coroutines)
    implementation(libs.room.runtime); implementation(libs.room.ktx); implementation(libs.sqlcipher); implementation(libs.sqlite)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler)
    testImplementation(libs.junit4); testImplementation(libs.robolectric); testImplementation(libs.android.test.core)
    androidTestImplementation(libs.android.test.core); androidTestImplementation(libs.android.test.runner); androidTestImplementation(libs.room.testing); androidTestImplementation(libs.junit4)
    // SECURITY-GATE2 standalone synthetic SQLCipher/FS experiment. Never runtime/release.
    androidTestImplementation(project(":attachment-poc"))
    androidTestImplementation(libs.tink.android)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
android.sourceSets.getByName("androidTest").apply { assets.srcDir("$projectDir/schemas"); java.srcDirs("src/historyTest/java", "src/medicalTest/java", "src/attachmentTest/java") }
android.sourceSets.getByName("test").apply { assets.srcDir("$projectDir/schemas"); java.srcDirs("src/historyTest/java", "src/medicalTest/java", "src/attachmentTest/java") }
android.sourceSets.getByName("androidTest").java.srcDir("../../tools/attachment-poc/src/contractTest/kotlin")

// ANSM-03B0: compile only the read-only shadow adapter; its measurement API now comes from domain main.
// Explicit includes keep JUnit5 tests out of Android/JUnit4; no source is added to main or release.
val ansm03b0TestSources = tasks.register<Sync>("prepareAnsm03b0TestSources") {
    from(rootProject.layout.projectDirectory.dir("core/domain/src/test/kotlin")) {
        include("net/plainnotes/app/domain/shadow/ShadowMeasurementAllocator.kt")
    }
    into(layout.buildDirectory.dir("generated/ansm03b0TestSources"))
}
listOf("test", "androidTest").forEach { name ->
    android.sourceSets.getByName(name).java.srcDir("src/test/shared/java")
    android.sourceSets.getByName(name).java.srcDir(layout.buildDirectory.dir("generated/ansm03b0TestSources").get().asFile)
}
tasks.configureEach {
    if (name in setOf("kspDebugUnitTestKotlin", "compileDebugUnitTestKotlin", "kspDebugAndroidTestKotlin", "compileDebugAndroidTestKotlin") ||
        name.contains("lint", ignoreCase = true)) {
        dependsOn(ansm03b0TestSources)
    }
}
