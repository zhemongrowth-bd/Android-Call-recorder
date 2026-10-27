# Keep all app classes (JSON field names, Compose, receivers/services named in the manifest).
-keep class com.winstone.connect.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# OkHttp / Okio optional platform classes
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
