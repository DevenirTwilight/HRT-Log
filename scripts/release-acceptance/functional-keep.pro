# P1 functional release acceptance ONLY (applied by acceptance.init.gradle in mode=functional, on top of
# test-support-keep.pro). Keeps the project's own classes and the libraries in-process tests call directly
# (Room, coroutines, Compose UI test hooks, lifecycle/activity, Hilt), so tests can drive them after R8.
# Resource shrinking and ABI filtering stay exactly as in the scenario.
-keep class net.plainnotes.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.core.** { *; }
-keep class dagger.** { *; }
-keep class androidx.test.** { *; }
-keep class androidx.collection.** { *; }
