# Retain annotations and DTOs
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Faisal TV DTOs and API models
-keep class com.faisal.tv.data.api.** { *; }

# Gson rules
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Retrofit rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-dontwarn com.bumptech.glide.**

# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
