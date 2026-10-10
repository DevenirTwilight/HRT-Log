# P1 functional release acceptance ONLY (applied by acceptance.init.gradle in mode=functional).
# Keeps the project's own classes and members so in-process instrumentation can call them after R8.
# Third-party libraries, resource shrinking and ABI filtering stay exactly as in the scenario.
-keep class net.plainnotes.** { *; }
