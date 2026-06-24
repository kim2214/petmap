package com.kimdev.petmap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * odcloud OpenAPI 표준 응답 형태:
 * { "page":1, "perPage":10, "totalCount":..., "currentCount":..., "data":[ {...} ] }
 *
 * 필드명은 "한국문화정보원 전국 반려동물 동반가능 문화시설" 데이터셋 기준 예시.
 * 실제 사용 데이터셋의 컬럼명에 맞게 @SerialName 을 조정하세요.
 */
@Serializable
data class PlaceResponse(
    val page: Int = 0,
    val perPage: Int = 0,
    val totalCount: Int = 0,
    val currentCount: Int = 0,
    val data: List<PlaceDto> = emptyList(),
)

@Serializable
data class PlaceDto(
    @SerialName("시설명") val name: String? = null,
    @SerialName("카테고리3") val category: String? = null,
    @SerialName("도로명주소") val roadAddress: String? = null,
    @SerialName("지번주소") val lotAddress: String? = null,
    @SerialName("위도") val latitude: Double? = null,
    @SerialName("경도") val longitude: Double? = null,
    @SerialName("전화번호") val phone: String? = null,
    @SerialName("운영시간") val operatingTime: String? = null,
    @SerialName("휴무일") val closedDays: String? = null,
    @SerialName("홈페이지") val homepage: String? = null,
    @SerialName("입장가능동물크기") val allowedPetSize: String? = null,
    @SerialName("반려동물제한사항") val restriction: String? = null,
    @SerialName("실내동반가능여부") val indoorAllowed: String? = null,
    @SerialName("실외동반가능여부") val outdoorAllowed: String? = null,
)
