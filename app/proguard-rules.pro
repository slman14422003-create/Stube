-keepattributes *Annotation*
-dontwarn java.lang.invoke.StringConcatFactory

# NewPipeExtractor + Rhino + jsoup
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.protobuf.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.lang.model.**

# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.**

# OkHttp / Okio (مزوّدات TLS الاختيارية غير موجودة على أندرويد)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# أداء: حذف سجلات التصحيح من نسخة الإصدار وتحسين أعمق
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Kotlin coroutines
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.**
