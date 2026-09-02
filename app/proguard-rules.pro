# ONNX Runtime loads its native session provider reflectively.
-keep class ai.onnxruntime.** { *; }

# PDFBox-Android resolves font and resource classes by name.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
