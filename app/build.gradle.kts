plugins { alias(libs.plugins.android.app); alias(libs.plugins.kotlin.android); alias(libs.plugins.compose); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }
android {
    namespace = "net.plainnotes.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26; applicationId = "net.plainnotes.app"; targetSdk = 37; versionCode = 10; versionName = "0.2.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Optional: -ProbolectricDir=<dir with android-all jars> runs Robolectric offline (sandboxes without direct Maven access).
        unitTests.all { t -> providers.gradleProperty("robolectricDir").orNull?.let { t.systemProperty("robolectric.offline", "true"); t.systemProperty("robolectric.dependency.dir", it) } }
    }
    // Debug-only key kept in the repo so every build (local or CI) signs debug APKs the same way and installs over the last one.
    // It must never sign a release meant for distribution.
    signingConfigs { getByName("debug") { storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    flavorDimensions += "distribution"
    productFlavors { create("full") { dimension = "distribution" } }
    buildTypes { getByName("release") { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    buildFeatures { compose = true }
}
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
