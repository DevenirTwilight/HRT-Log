plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Robolectric android-all jars resolved by Gradle (retries, build cache) instead of Robolectric's own download at
// test time, which failed CI with MavenArtifactFetcher/FileNotFoundException. CI runs `robolectricJars` and passes
// -ProbolectricDir=build/robolectric-jars (offline mode), so a missing SDK fails loudly instead of fetching.
// Versions are Robolectric 4.16's DefaultSdkProvider coordinates for the SDKs used in @Config: 26, 28, 29, 31, 35.
// Update them together with the Robolectric version in gradle/libs.versions.toml.
// One configuration per SDK: a shared one would resolve the version conflict down to the newest jar only.
val robolectricAndroidAll = listOf("8.0.0_r4-robolectric-r1-i7", "9-robolectric-4913185-2-i7", "10-robolectric-5803371-i7",
    "12-robolectric-7732740-i7", "15-robolectric-13954326-i7")
    .map { configurations.detachedConfiguration(dependencies.create("org.robolectric:android-all-instrumented:$it")).apply { isTransitive = false } }
tasks.register<Copy>("robolectricJars") { from(robolectricAndroidAll); into(layout.buildDirectory.dir("robolectric-jars")) }
