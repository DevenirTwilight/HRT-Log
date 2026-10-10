# P1 release acceptance: rules for the instrumentation (test) APK only, never for the app APK.
# Test classes are entry points for the runner; references are rewritten with the app's R8 mapping by AGP.
-keep class net.plainnotes.** { *; }
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-dontwarn **
