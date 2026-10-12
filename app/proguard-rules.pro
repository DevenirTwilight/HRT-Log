-keep class net.zetetic.database.sqlcipher.** { *; }
# Audit B1: keep the source commit in release dex so a delivered APK can be traced to its exact source even before any UI shows it.
-keep class net.plainnotes.app.BuildConfig { public static final java.lang.String SOURCE_SHA; public static final java.lang.String SOURCE_COMMIT_TIME_UTC; public static final boolean SOURCE_DIRTY; public static final java.lang.String SOURCE_ORIGIN; }
