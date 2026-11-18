# Keep Room entities & Kotlinx Serialization models/routes
-keepattributes *Annotation*,InnerClasses,Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class com.auraplayer.app.ui.navigation.** { *; }
-keep,includedescriptorclasses class com.auraplayer.app.data.backup.** { *; }
-keep class com.auraplayer.app.data.local.db.** { *; }

# Keep reflectively loaded Media3 FFmpeg extension & JNI methods
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class androidx.media3.decoder.ffmpeg.** {
    native <methods>;
}
