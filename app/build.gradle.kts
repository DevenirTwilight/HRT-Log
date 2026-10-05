plugins { alias(libs.plugins.android.app); alias(libs.plugins.kotlin.android); alias(libs.plugins.compose); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }
android {
    namespace = "net.plainnotes.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26; applicationId = "net.plainnotes.app"; targetSdk = 37; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    flavorDimensions += "distribution"
    productFlavors { create("full") { dimension = "distribution" }; create("play") { dimension = "distribution" } }
    buildTypes { getByName("release") { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines)
    implementation(project(":core:data")); implementation(project(":core:reminder")); implementation(project(":core:ui"))
    implementation(libs.work); implementation(libs.activity.compose); implementation(libs.lifecycle.compose); implementation(libs.lifecycle.viewmodel)
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material); implementation(libs.compose.preview); debugImplementation(libs.compose.tooling)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler)
    testImplementation(libs.junit4); testImplementation(libs.robolectric); testImplementation(libs.android.test.core)
    androidTestImplementation(libs.android.test.runner)
}
