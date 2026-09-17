# Proguard / R8 rules for wangu-reader-native

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database, DAOs, and Entities
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# Preserve WebView JavascriptInterface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve core model classes and entities
-keep class org.wanshu.reader.data.personal.entity.** { *; }
-keep class org.wanshu.reader.data.content.entity.** { *; }
-keep class org.wanshu.reader.core.source.** { *; }
-keep class org.wanshu.reader.core.text.** { *; }
-keep class org.wanshu.reader.core.download.** { *; }
-keep class org.wanshu.reader.migration.LegacyMigrationBridge { *; }
-keep class org.wanshu.reader.migration.LegacyMigrationListener { *; }
