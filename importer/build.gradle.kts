plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { implementation(project(":core:domain")); compileOnly(libs.org.json); testImplementation(libs.org.json); testImplementation(libs.junit); testImplementation(libs.sqlite.jdbc); testRuntimeOnly(libs.junit.launcher) }
tasks.test { useJUnitPlatform() }
