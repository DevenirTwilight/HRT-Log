plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.compose) }
android {
    namespace = "net.plainnotes.app.ui"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines)
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material); implementation(libs.compose.preview); debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit4); testImplementation(libs.robolectric); testImplementation(libs.android.test.core)
    androidTestImplementation(libs.android.test.runner)
}
