plugins { alias(libs.plugins.android.app); alias(libs.plugins.kotlin.android); alias(libs.plugins.compose); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }

// Build provenance (audit B1): the exact source commit is compiled into every APK so an installed build can be traced.
// Override with -PsourceSha=<40 hex> (and optionally -PsourceCommitTimeUtc) only for trusted injection, e.g. a source tarball.
// The time is the commit time, not the build time, so the same source always produces the same values.
fun gitOutput(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", *args); environment("TZ", "UTC"); isIgnoreExitValue = true }.let { out ->
        if (out.result.get().exitValue == 0) out.standardOutput.asText.get().trim() else null
    }
}.getOrNull()?.takeIf { it.isNotEmpty() }
val sourceSha: String = providers.gradleProperty("sourceSha").orNull ?: gitOutput("rev-parse", "HEAD") ?: "unknown"
val sourceCommitTimeUtc: String = providers.gradleProperty("sourceCommitTimeUtc").orNull
    ?: gitOutput("show", "-s", "--format=%cd", "--date=format-local:%Y-%m-%dT%H:%M:%SZ", "HEAD")?.takeIf { sourceSha != "unknown" } ?: "unknown"
val sourceDirty: Boolean = providers.gradleProperty("sourceSha").orNull == null && gitOutput("status", "--porcelain", "--untracked-files=no") != null
require(sourceSha == "unknown" || Regex("[0-9a-f]{40}").matches(sourceSha)) { "sourceSha must be a full 40-character lowercase commit SHA" }

android {
    namespace = "net.plainnotes.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26; applicationId = "net.plainnotes.app"; targetSdk = 37; versionCode = 25; versionName = "0.2.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SOURCE_SHA", "\"$sourceSha\""); buildConfigField("String", "SOURCE_COMMIT_TIME_UTC", "\"$sourceCommitTimeUtc\""); buildConfigField("boolean", "SOURCE_DIRTY", sourceDirty.toString()) }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Optional: -ProbolectricDir=<dir with android-all jars> runs Robolectric offline (sandboxes without direct Maven access).
        unitTests.all { t ->
            if(System.getenv("BUTTON_AUDIT")=="1")t.outputs.upToDateWhen { false }
            providers.gradleProperty("robolectricDir").orNull?.let { t.systemProperty("robolectric.offline", "true"); t.systemProperty("robolectric.dependency.dir", it) } }
    }
    // Debug-only key kept in the repo so every build (local or CI) signs debug APKs the same way and installs over the last one.
    // It must never sign a release meant for distribution.
    signingConfigs { getByName("debug") { storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    flavorDimensions += "distribution"
    productFlavors { create("full") { dimension = "distribution" } }
    // 2026-10-10 (REQUIREMENTS §54, P1 scenario F): unused resources are removed and native libraries are stored deflated
    // (extracted at install), keeping every ABI: universal APK ~23.5 MB -> ~12.7 MB. Verified by release acceptance.
    buildTypes { getByName("release") { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures { compose = true; buildConfig = true }
}
// A release that cannot name its source commit is not traceable: fail instead of shipping "unknown".
val verifyReleaseProvenance by tasks.registering {
    val sha = sourceSha
    doLast { check(sha != "unknown") { "Release build has no source commit: build from a git checkout or pass -PsourceSha=<40 hex>" } }
}
tasks.configureEach { if (name == "preFullReleaseBuild") dependsOn(verifyReleaseProvenance) }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:domain")); implementation(project(":pk-engine")); implementation(project(":importer"))
    implementation(libs.core.ktx); implementation(libs.appcompat); implementation(libs.biometric); implementation(libs.bouncycastle)
    implementation(libs.coroutines)
    implementation(project(":core:data")); implementation(project(":core:reminder")); implementation(project(":core:ui"))
    implementation(libs.work); implementation(libs.activity.compose); implementation(libs.lifecycle.compose); implementation(libs.lifecycle.viewmodel)
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material); implementation(libs.compose.icons); implementation(libs.compose.preview); debugImplementation(libs.compose.tooling)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler)
    testImplementation(libs.room.runtime); testImplementation(libs.room.ktx); testImplementation(libs.sqlite)
    testImplementation(libs.junit4); testImplementation(libs.org.json); testImplementation(libs.robolectric); testImplementation(libs.android.test.core)
    testImplementation(platform(libs.compose.bom)); testImplementation(libs.compose.ui.test); debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.android.test.runner); androidTestImplementation(libs.android.test.core); androidTestImplementation(libs.junit4)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.ui.test)
}
