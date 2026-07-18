# ================================================================
# 工友通 ProGuard 优化规则
# 用于 Release 构建时的代码混淆和压缩
# ================================================================

# ========== 基本配置 ==========

# 保留行号信息便于调试
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*

# 保留内部 API（某些库需要）
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ========== Room 数据库 ==========

# 保留 Room 实体类
-keep class com.gongyoutong.app.database.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ========== 百度地图 SDK ==========

# 保留 Baidu Map SDK 类
-keep class com.baidu.** { *; }
-keep class com.baidu.mapapi.** { *; }
-keep class com.baidu.lbsyun.** { *; }

# 保留 JNI 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留自定义视图的构造方法
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ========== OkHttp ==========

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# OkHttp 保留规则
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ========== Gson ==========

# 保留 Gson 序列化/反序列化所需的类
-keepattributes Signature
-keepattributes *Annotation*

# 保留泛型信息
-keep class java.lang.reflect.Type { *; }

# 保留数据模型类（如果使用 Gson 序列化）
-keep class com.gongyoutong.app.data.** { *; }
-keep class com.gongyoutong.app.database.** { *; }

# Gson 特殊保留
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ========== Retrofit（如果有使用）==========
# -dontwarn retrofit2.**
# -keep class retrofit2.** { *; }

# ========== Kotlin 相关 ==========
# 如果使用 Kotlin

# 保留 Kotlin 反射
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# 保留 Kotlin metadata
-keep class kotlin.Metadata { *; }

# ========== Android 组件 ==========

# 保留 Application 类
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# 保留 Parcelable 实现类
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ========== 枚举 ==========

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== 其他优化 ==========

# 移除日志（在 Release 版本中）
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# 保留 R 类（资源引用）
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留原生方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========== 性能优化 ==========

# 启用优化
-optimizationpasses 5

# 允许方法内联
-allowaccessmodification

# 合并重复的类
-dontpreverify

# 保留注解
-keepattributes Annotation

# 保留异常类型（调试时有用）
-keep public class java.lang.Throwable { *; }
-keep public class java.lang.Exception { *; }

# ========== 安全性 ==========

# 不混淆包含敏感信息的类
-keep class com.gongyoutong.app.Config { *; }

# 保留混淆后的类名（便于追踪）
-repackageclasses ''
