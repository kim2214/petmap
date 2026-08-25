package com.kimdev.petmap.domain.model

import java.time.LocalDate

/** 내장 장소 데이터의 메타 정보 (설정 화면 표시용) */
data class DataInfo(
    /** 데이터 기준일. 에셋에 meta 가 없거나 파싱 실패 시 null */
    val dataDate: LocalDate?,
    /** 수록 장소 수 */
    val placeCount: Int,
)
