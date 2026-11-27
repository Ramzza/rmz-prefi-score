-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class hu.rmz.prefiscore.**$$serializer { *; }
-keepclassmembers class hu.rmz.prefiscore.** { *** Companion; }
-keepclasseswithmembers class hu.rmz.prefiscore.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Anthropic Java SDK (Jackson alapú, reflexiót használ) ---
-keepattributes Signature, Exceptions, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
    @com.fasterxml.jackson.annotation.* <init>(...);
}
-dontwarn com.fasterxml.jackson.**
-dontwarn com.github.victools.**
-dontwarn io.swagger.**
-dontwarn io.opentelemetry.**
-dontwarn com.standardwebhooks.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn java.beans.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-dontwarn java.lang.reflect.AnnotatedType
