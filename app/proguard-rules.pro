# ONNX Runtime loads its native session provider reflectively.
-keep class ai.onnxruntime.** { *; }

# PDFBox-Android resolves font and resource classes by name.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# WorkManager writes a worker's class name into its own database when work is
# enqueued and reads it back to construct the worker, possibly after an app
# update. R8 is free to choose a different short name in the next build, which
# would leave that pending work unrunnable. Keeping the names — not the members —
# costs a few bytes and makes them stable across releases.
-keepnames class * extends androidx.work.ListenableWorker
