package com.kimdev.petmap.domain.model

import java.time.LocalDate

/** 원격 장소 데이터 갱신 시도 결과 */
sealed interface DataUpdateResult {
    /** 게시된 데이터가 없거나(이 스키마용 매니페스트 없음) 이미 같은/더 새 데이터를 갖고 있음 */
    data object UpToDate : DataUpdateResult

    /** 새 데이터로 교체함 */
    data class Updated(val dataDate: LocalDate, val placeCount: Int) : DataUpdateResult

    /** 네트워크·검증·적용 실패. 기존 데이터는 그대로 남는다 */
    data class Failed(val cause: Throwable) : DataUpdateResult
}
