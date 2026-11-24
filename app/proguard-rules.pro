# Add project-specific ProGuard/R8 rules here. ML Kit and Compose ship consumer rules.

# Tesseract JNI entry points and native bindings.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
