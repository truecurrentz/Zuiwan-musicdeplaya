-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class zuiwan.model.** { *; }
-keep class zuiwan.data.** { *; }
-keep class zuiwan.tube.** { *; }
-keep class zuiwan.lyrics.** { *; }
-keep class com.metrolist.innertubex.** { *; }
-dontwarn com.metrolist.innertubex.**

-keep class coil3.** { *; }
-dontwarn coil3.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keepnames class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep,includedescriptorclasses class zuiwan.**$$serializer { *; }
-keepclassmembers class zuiwan.** {
    *** Companion;
}

-dontwarn org.slf4j.**
-dontwarn java.awt.**
-dontwarn javax.naming.**
