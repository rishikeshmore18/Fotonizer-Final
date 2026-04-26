# ProGuard rules for PhotoApp10 Production Release
# Add project specific ProGuard rules here.

# Keep all classes in the main package
-keep class com.example.photoapp10.** { *; }

# Keep all data classes
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Database
-keep class * extends androidx.room.Dao

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Google Drive API classes
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }

# Keep serialization classes
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class *

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep Coil image loading
-keep class coil.** { *; }

# Keep Timber logging
-keep class timber.log.** { *; }

# Keep Retrofit classes
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep Paging
-keep class androidx.paging.** { *; }

# Keep Navigation
-keep class androidx.navigation.** { *; }

# Keep security crypto
-keep class androidx.security.crypto.** { *; }

# Keep Bugsnag
-keep class com.bugsnag.** { *; }

# Remove debug logs in release
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep R class
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# Keep BuildConfig
-keep class **.BuildConfig

# Remove unused resources
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.api.**
-dontwarn com.google.apis.**
-dontwarn kotlinx.serialization.**
-dontwarn androidx.compose.**
-dontwarn androidx.camera.**
-dontwarn coil.**
-dontwarn timber.log.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn androidx.work.**
-dontwarn androidx.datastore.**
-dontwarn androidx.paging.**
-dontwarn androidx.navigation.**
-dontwarn androidx.security.crypto.**
-dontwarn com.bugsnag.**

# Suppress optional Apache HTTP/JNDI/GSS classes referenced by transitive libs.
# These classes are not used on Android runtime paths for this app.
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# Optimization
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
