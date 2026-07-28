# ===== PetMap R8/ProGuard 규칙 =====

# 디버깅용 스택트레이스 라인 보존
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ----- Naver Map SDK -----
-keep class com.naver.maps.** { *; }
-dontwarn com.naver.maps.**

# ----- 도메인 enum (DB 에 name() 저장 / valueOf 로 복원) -----
-keepclassmembers enum com.kimdev.petmap.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Hilt / Room 은 각 라이브러리의 consumer rules 로 처리됨
