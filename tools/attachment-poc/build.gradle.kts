plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { implementation(project(":core:domain")); testImplementation(libs.junit4); testImplementation(libs.org.json); testImplementation(libs.bouncycastle) }
kotlin.sourceSets.getByName("test").kotlin.srcDir("src/contractTest/kotlin")
tasks.test { useJUnit() }
