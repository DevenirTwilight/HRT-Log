pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "PlainNotes"
include(":core:domain", ":pk-engine", ":importer")
if (!providers.gradleProperty("jvmOnly").isPresent) { include(":app", ":core:data", ":core:reminder", ":core:ui") }
