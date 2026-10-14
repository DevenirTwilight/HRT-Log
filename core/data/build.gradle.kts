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
