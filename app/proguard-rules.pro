# NJOCR ProGuard Rules

# 1. 核心 JNI 与 Native 保持
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.wubugncnn.njocr.OCRService {
    private external <methods>;
}
-keep class com.wubugncnn.njocr.** { *; }

# 2. 序列化模型保持 (防止 JSON 解析失败)
-keep class com.wubugncnn.njocr.OCRBaseResponse { *; }
-keep class com.wubugncnn.njocr.OCRResultType2 { *; }
-keep class com.wubugncnn.njocr.OCRResultType3 { *; }
-keep class com.wubugncnn.njocr.OCRRequest { *; }
-keep class com.wubugncnn.njocr.OCRResponse { *; }

# 3. Ktor / Netty 核心保持
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter

# 4. 【核心修复】忽略 Netty/Ktor 的可选依赖警告 (解决 R8 编译报错)
# 这些类在 Android 环境中通常不存在，且不影响识别功能
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn java.lang.management.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.jboss.marshalling.**
-dontwarn org.slf4j.impl.**
-dontwarn reactor.blockhound.**
-dontwarn sun.security.x509.**
-dontwarn org.osgi.annotation.bundle.**

# 5. 保持反射用到的类名
-keepnames class io.netty.handler.codec.http.HttpObjectEncoder
-keepnames class io.netty.handler.codec.http.HttpObjectDecoder
