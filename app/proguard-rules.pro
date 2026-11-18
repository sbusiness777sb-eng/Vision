# Preserve anonymized source line info for crash diagnostics without leaking local paths
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room persistence entities and generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

# Firebase AI Logic & App Check
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.android.recaptcha.**
