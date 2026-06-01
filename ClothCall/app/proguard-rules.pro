# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Room entities
-keep class com.clothcall.data.db.** { *; }

# Keep JSON
-keepclassmembers class * {
    @org.json.JSONField *;
}
