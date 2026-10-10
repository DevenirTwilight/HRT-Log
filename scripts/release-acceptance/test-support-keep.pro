# P1 release acceptance ONLY, applied to the app in BOTH modes by acceptance.init.gradle.
# AGP strips from the test APK every library the app already ships, so the instrumentation runner and test code
# resolve these classes from the app's dex. R8 removes or inlines unused library code there (first CI run:
# NoClassDefFoundError androidx.tracing.Trace in AndroidJUnitRunner.onCreate). Keeping them changes no app
# behaviour (same library code, just not removed/inlined) and touches no resources or native libraries;
# reports therefore call mode=exact "production R8 + test-runner support keeps", not byte-identical.
-keep class androidx.tracing.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
-keep class kotlin.** { *; }
