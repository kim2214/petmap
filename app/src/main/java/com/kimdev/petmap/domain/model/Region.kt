package com.kimdev.petmap.domain.model

/**
 * 지역 브라우징 단위. 주소 앞 토큰(시/도, 시/군/구)으로 장소를 묶는다 — 별도 컬럼 없이
 * `roadAddress LIKE prefix%` 로 걸러지도록 [prefix] 를 만든다.
 * 시/도 명칭은 build_db.py 가 현행 공식 이름으로 정규화해 두었다(강원특별자치도 등).
 */
data class Region(
    /** 예: "경기도", "전남광주통합특별시" */
    val sido: String,
    /** 예: "고양시", "북구". null 이면 시/도 전체 */
    val sigungu: String? = null,
) {
    /** SQL LIKE 접두어. 뒤에 공백을 붙여 "경기도 고양시"가 "경기도 고양시청로…" 같은 다른 토큰과 섞이지 않게 한다 */
    val prefix: String
        get() = if (sigungu == null) "$sido " else "$sido $sigungu "

    /** "경기 고양시", "서울" 처럼 짧게 */
    val shortLabel: String
        get() = listOfNotNull(shortSido(sido), sigungu).joinToString(" ")

    companion object {
        private val SHORT = mapOf(
            "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천",
            "대전광역시" to "대전", "울산광역시" to "울산", "세종특별자치시" to "세종", "경기도" to "경기",
            "강원특별자치도" to "강원", "충청북도" to "충북", "충청남도" to "충남", "전북특별자치도" to "전북",
            "전남광주통합특별시" to "전남·광주", "경상북도" to "경북", "경상남도" to "경남", "제주특별자치도" to "제주",
            // 정규화 전 표기가 남아 있을 때의 폴백
            "강원도" to "강원", "전라북도" to "전북", "전라남도" to "전남", "광주광역시" to "광주",
        )

        /** 지역 선택 화면의 시/도 표시 순서(북→남, 서→동 대략). 목록에 없는 이름은 뒤에 붙인다 */
        val SIDO_ORDER = listOf(
            "서울특별시", "경기도", "인천광역시", "강원특별자치도", "충청북도", "충청남도", "세종특별자치시",
            "대전광역시", "전북특별자치도", "전남광주통합특별시", "경상북도", "대구광역시", "경상남도",
            "울산광역시", "부산광역시", "제주특별자치도",
        )

        fun shortSido(sido: String): String = SHORT[sido] ?: sido

        /** 시/군/구로 볼 수 있는 토큰인가 (세종은 읍·면·도로명이 바로 와서 하위 단계가 없다) */
        fun isSigunguToken(token: String): Boolean =
            token.length >= 2 && (token.endsWith("시") || token.endsWith("군") || token.endsWith("구"))
    }
}

/** 지역별 장소 개수 (지역 선택 화면). [sigungu] 가 null 인 행은 시/군/구로 볼 수 없는 토큰을 합친 것 */
data class RegionCount(
    val sido: String,
    val sigungu: String?,
    val count: Int,
)
