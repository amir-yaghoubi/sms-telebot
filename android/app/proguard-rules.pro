# Keep project classes
-keep class com.teslor.sms_telebot.** { *; }

# Keep mail/activation classes
-keep class com.sun.mail.** { *; }
-keep class jakarta.mail.** { *; }
-keep class com.sun.activation.** { *; }
-keep class jakarta.activation.** { *; }
-dontwarn com.sun.mail.**
-dontwarn jakarta.mail.**

# Keep okhttp3/okio classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Suppress warnings about missing annotations
-dontwarn com.google.errorprone.annotations.**

# Tell R8 not to modify resource files used for protocol lookup
-adaptresourcefilecontents META-INF/javamail.providers
-adaptresourcefilecontents META-INF/mailcap
