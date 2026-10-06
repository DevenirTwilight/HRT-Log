plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { compileOnly(libs.org.json); testImplementation(libs.junit); testImplementation(libs.org.json); testRuntimeOnly(libs.junit.launcher) }
tasks.test { useJUnitPlatform() }
