# ===== PetMap R8/ProGuard 규칙 =====

# 디버깅용 스택트레이스 라인 보존
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ----- kotlinx.serialization -----
# @Serializable 클래스의 생성된 시리얼라이저/Companion 보존
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static kotlinx.serialization.KSerializer serializer(...);
}
# 네트워크 DTO 는 직렬화에 사용되므로 통째로 보존
-keep @kotlinx.serialization.Serializable class com.kimdev.petmap.data.remote.dto.** { *; }

# ----- Retrofit / OkHttp -----
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface com.kimdev.petmap.data.remote.api.** { *; }
-keep class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ----- Naver Map SDK -----
-keep class com.naver.maps.** { *; }
-dontwarn com.naver.maps.**

# ----- 도메인 enum (DB 에 name() 저장 / valueOf 로 복원) -----
-keepclassmembers enum com.kimdev.petmap.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Hilt / Room 은 각 라이브러리의 consumer rules 로 처리됨
