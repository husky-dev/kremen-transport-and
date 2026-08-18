# kotlinx.serialization generates a companion `serializer()` per @Serializable class and looks
# the companions up reflectively. R8's shrinker cannot see those uses, so without these the
# release build fails to decode every payload at runtime while the debug build is fine.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.krementransport.**$$serializer { *; }
-keepclassmembers class com.krementransport.** {
    *** Companion;
}
-keepclasseswithmembers class com.krementransport.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp references these optional platform integrations; they are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
