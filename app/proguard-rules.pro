# R8 full mode is on (see gradle.properties).

# --- PdfBox-Android ------------------------------------------------------
# PdfBox resolves parsers, filters and font handlers reflectively by class name,
# so its internals cannot be renamed or stripped. It also carries optional
# references to desktop/Bouncy Castle classes that are absent on Android; those
# are warnings, not errors.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# --- jipp ----------------------------------------------------------------
# IPP attribute types are looked up reflectively when decoding a printer's reply.
-keep class com.hp.jipp.** { *; }
-dontwarn com.hp.jipp.**

# --- Kotlin serialisation ------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.abhishekcs194.printdeck.** {
    *** Companion;
}

# --- Release log hygiene -------------------------------------------------
# Document names and file paths must never reach logcat in a release build.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
