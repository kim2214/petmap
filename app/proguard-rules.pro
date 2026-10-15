# ===== PetMap R8/ProGuard 규칙 =====

# 디버깅용 스택트레이스 라인 보존
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ----- Firebase (Crashlytics · Analytics) -----
# ComponentRegistrar 구현체는 매니페스트 메타데이터로 선언되고 리플렉션(기본 생성자)으로
# 인스턴스화된다. R8 이 기본 생성자를 지우면 로그에 "Invalid component registrar" 만 남기고
# Crashlytics 가 조용히 등록되지 않아 릴리스 크래시가 수집되지 않는다.
-keepnames class com.google.firebase.components.ComponentRegistrar
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# ----- Naver Map SDK -----
# SDK 가 consumer rules(proguard.txt)를 동봉한다: @NativeApi·native 메서드(JNI), LatLng/LatLngBounds 생성자,
# TypefaceFactory·OkHttpClientProvider 구현체, Gson. 그래서 전체 keep 없이 축소·난독화되게 둔다.
-dontwarn com.naver.maps.**

# ----- 도메인 enum (DB 에 name() 저장 / valueOf 로 복원) -----
-keepclassmembers enum com.kimdev.petmap.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Hilt / Room 은 각 라이브러리의 consumer rules 로 처리됨
