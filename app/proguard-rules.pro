# 增量合并：JNI 符号依赖类名/方法名，不能被 R8 改名
-keep class com.xah.bsdiffs.BsdiffJNI { *; }

# Keep kotlinx.serialization serializers
-keepclassmembers class com.yh.schedule.** {
    *** Companion;
}
-keepclasseswithmembers class com.yh.schedule.** {
    kotlinx.serialization.KSerializer serializer(...);
}
