-dontobfuscate
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,RuntimeVisibleAnnotations,AnnotationDefault

-keep class zuiwan.** { *; }
-keepclassmembers class zuiwan.tube.TokenBridge {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class * {
    void resume(...);
    void resumeWithException(...);
}

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclasseswithmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclasseswithmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class com.metrolist.innertubex.** { *; }
-keep class com.metrolist.quackjs.** { *; }
-keep class coil3.** { *; }
-dontwarn coil3.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn dev.chrisbanes.haze.**

-dontwarn javax.servlet.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn java.beans.**
