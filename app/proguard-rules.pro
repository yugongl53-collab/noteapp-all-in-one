# Proguard rules for NoteApp (一站笔记)
# 保留 Kotlinx 序列化与 Room 注解生成的类
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# kotlinx.serialization
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
