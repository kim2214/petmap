package com.kimdev.petmap.data.local

import com.kimdev.petmap.domain.util.distanceMeters

/**
 * 끊긴 장소 id 를 지금 데이터의 같은 장소로 잇는 판정 (tools/build_db.py 의 carry_forward_ids ② 단계와 같은 기준).
 *
 * 장소 id 는 `이름_위도_경도` 원문이라, DB 재생성이 id 를 물려주기 전(v9 법인 표기 정제 등)에는 같은 장소의 id 가
 * 바뀌었다. 그때 공유된 링크와 즐겨찾기는 옛 id 를 들고 있다 → 옛 id 에 든(또는 즐겨찾기에 저장된) 이름·좌표로
 * "정규화 이름이 같고 [MAX_METERS] 안에서 가장 가까운 장소"를 찾는다.
 */
object SamePlaceMatcher {
    const val MAX_METERS = 150.0

    /** 옛 id 에서 (이름, 위도, 경도)를 꺼낸다. 이름에 '_' 가 있어도 뒤의 두 토큰만 좌표로 본다. 형식이 아니면 null */
    fun parseLegacyId(id: String): Triple<String, Double, Double>? {
        val lngSep = id.lastIndexOf('_').takeIf { it > 0 } ?: return null
        val latSep = id.lastIndexOf('_', lngSep - 1).takeIf { it > 0 } ?: return null
        val lat = id.substring(latSep + 1, lngSep).toDoubleOrNull() ?: return null
        val lng = id.substring(lngSep + 1).toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return Triple(id.substring(0, latSep), lat, lng)
    }

    /** build_db.py normalize_name 과 같다: 괄호 내용·법인 표기·기호 제거, 소문자 */
    fun normalizeName(name: String): String = name
        .replace(PARENS, "")
        .replace(CORP, "")
        .replace(NON_WORD, "")
        .lowercase()

    /** [MAX_METERS] 박스 안 후보 중 같은 장소로 볼 가장 가까운 것 */
    fun <T> pick(name: String, lat: Double, lng: Double, candidates: List<T>, nameOf: (T) -> String, latOf: (T) -> Double, lngOf: (T) -> Double): T? {
        val key = normalizeName(name).ifEmpty { return null }
        return candidates
            .filter { normalizeName(nameOf(it)) == key }
            .map { it to distanceMeters(lat, lng, latOf(it), lngOf(it)) }
            .filter { (_, d) -> d <= MAX_METERS }
            .minByOrNull { (_, d) -> d }
            ?.first
    }

    private val PARENS = Regex("\\(.*?\\)")
    private val CORP = Regex("\\(주\\)|㈜|주식회사")
    private val NON_WORD = Regex("[^0-9A-Za-z가-힣]")
}
