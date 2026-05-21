# Keep Android/WorkManager entrypoints
-keep class com.teslor.sms_telebot.BootReceiver { *; }
-keep class com.teslor.sms_telebot.CallReceiver { *; }
-keep class com.teslor.sms_telebot.ForegroundService { *; }
-keep class com.teslor.sms_telebot.MainActivity { *; }
-keep class com.teslor.sms_telebot.SmsReceiver { *; }
-keep class com.teslor.sms_telebot.SmsTelebotApp { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

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
