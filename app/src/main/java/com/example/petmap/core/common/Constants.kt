package com.example.petmap.core.common

object Constants {
    /**
     * 공공데이터포털(odcloud) 베이스 URL.
     * 예: 한국문화정보원 "전국 반려동물 동반가능 문화시설" 표준데이터 OpenAPI.
     * 실제 사용하는 데이터셋의 엔드포인트로 교체하세요.
     */
    const val PUBLIC_DATA_BASE_URL = "https://api.odcloud.kr/api/"

    // 지도 기본 카메라 위치 (서울시청)
    const val DEFAULT_LAT = 37.5666102
    const val DEFAULT_LNG = 126.9783881
    const val DEFAULT_ZOOM = 12.0
}
