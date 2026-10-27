# Keep kotlinx.serialization serializers
-keepclassmembers class com.yh.schedule.** {
    *** Companion;
}
-keepclasseswithmembers class com.yh.schedule.** {
    kotlinx.serialization.KSerializer serializer(...);
}
