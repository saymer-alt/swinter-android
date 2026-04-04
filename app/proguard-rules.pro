# Swinter VPN - ProGuard Rules

# Keep Go library JNI interface
-keep class swinterlib.** { *; }
-keep class go.** { *; }

# Keep data models (Gson serialization)
-keep class com.swinter.vpn.data.** { *; }
-keep class com.swinter.vpn.data.model.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# MessagePack
-keep class org.msgpack.** { *; }

# Compose (handled by R8 automatically, but just in case)
-dontwarn androidx.compose.**

# VPN Service
-keep class com.swinter.vpn.service.TurnVpnService { *; }
-keep class com.swinter.vpn.receiver.** { *; }
-keep class com.swinter.vpn.widget.** { *; }
