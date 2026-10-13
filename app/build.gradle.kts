plugins { alias(libs.plugins.android.app); alias(libs.plugins.kotlin.android); alias(libs.plugins.compose); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }

// Build provenance (audit B1): the source commit is compiled into every APK so an installed build can be traced.
// It identifies source, not the APK file (sha256) or the signing certificate; those are recorded separately at delivery.
//   SOURCE_ORIGIN "git": read from this project's own checkout (an enclosing unrelated repository is ignored);
//                 "declared-unverified": -PsourceSha given without a checkout to check it against;
//                 "unknown": neither. Debug builds may be "unknown"/"declared-unverified"/dirty; release builds may not.
// A -PsourceSha that disagrees with the checkout fails the build. The time is the commit time (UTC), not the build time.
fun gitOutput(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", *args); workingDir(rootDir); environment("TZ", "UTC"); isIgnoreExitValue = true }.let { out ->
        if (out.result.get().exitValue == 0) out.standardOutput.asText.get().trim() else null
    }
}.getOrNull()
val ownCheckout = gitOutput("rev-parse", "--show-toplevel")?.let { file(it).canonicalFile == rootDir.canonicalFile } == true
val gitSha = if (ownCheckout) gitOutput("rev-parse", "HEAD")?.takeIf { it.isNotEmpty() } else null
val declaredSha = providers.gradleProperty("sourceSha").orNull
require(declaredSha == null || Regex("[0-9a-f]{40}").matches(declaredSha)) { "sourceSha must be a full 40-character lowercase commit SHA" }
require(declaredSha == null || gitSha == null || declaredSha == gitSha) { "sourceSha $declaredSha does not match the checkout HEAD $gitSha" }
val sourceSha: String = gitSha ?: declaredSha ?: "unknown"
val sourceOrigin: String = when { gitSha != null -> "git"; declaredSha != null -> "declared-unverified"; else -> "unknown" }
val sourceCommitTimeUtc: String = (if (gitSha != null) gitOutput("show", "-s", "--format=%cd", "--date=format-local:%Y-%m-%dT%H:%M:%SZ", "HEAD")
    else providers.gradleProperty("sourceCommitTimeUtc").orNull)?.takeIf { it.isNotEmpty() && sourceSha != "unknown" } ?: "unknown"
// Tracked changes and untracked, non-ignored files both make the tree differ from the named commit.
val sourceDirty: Boolean = gitSha != null && !gitOutput("status", "--porcelain", "--untracked-files=normal").isNullOrEmpty()

android {
    namespace = "net.plainnotes.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26; applicationId = "net.plainnotes.app"; targetSdk = 37; versionCode = 26; versionName = "0.2.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SOURCE_SHA", "\"$sourceSha\""); buildConfigField("String", "SOURCE_COMMIT_TIME_UTC", "\"$sourceCommitTimeUtc\""); buildConfigField("boolean", "SOURCE_DIRTY", sourceDirty.toString()); buildConfigField("String", "SOURCE_ORIGIN", "\"$sourceOrigin\"") }
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
    sourceSets {
        getByName("test").java.srcDir("src/stockTest/java")
        getByName("androidTest").java.srcDir("src/stockTest/java")
    }

    buildFeatures { compose = true; buildConfig = true }
}
// A release must name a commit read from its own clean checkout; otherwise fail instead of shipping an unverifiable identity.
val verifyReleaseProvenance by tasks.registering {
    val origin = sourceOrigin; val dirty = sourceDirty; val sha = sourceSha
    doLast {
        check(origin == "git") { "Release build source is $origin ($sha): build from this project's git checkout" }
        check(!dirty) { "Release build tree has uncommitted or untracked changes relative to $sha: commit or clean them first" }
    }
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
    // Existing runtime libraries; only expose their types to the synthetic debug instrumentation host.
    debugCompileOnly(libs.room.runtime); debugCompileOnly(libs.sqlite)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler)
    testImplementation(libs.room.runtime); testImplementation(libs.room.ktx); testImplementation(libs.sqlite)
    testImplementation(libs.junit4); testImplementation(libs.org.json); testImplementation(libs.robolectric); testImplementation(libs.android.test.core)
    testImplementation(platform(libs.compose.bom)); testImplementation(libs.compose.ui.test); debugImplementation(libs.compose.ui.test.manifest)
    androidTestCompileOnly(libs.room.runtime); androidTestCompileOnly(libs.sqlite)
    androidTestImplementation(libs.android.test.runner); androidTestImplementation(libs.android.test.core); androidTestImplementation(libs.junit4)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.ui.test)
}
