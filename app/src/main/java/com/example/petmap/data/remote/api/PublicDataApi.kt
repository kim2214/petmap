package com.example.petmap.data.remote.api

import com.example.petmap.data.remote.dto.PlaceResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 공공데이터포털 반려동물 동반가능 시설 OpenAPI.
 * 실제 데이터셋 경로(uddi)와 파라미터는 사용하는 API 문서에 맞게 수정하세요.
 */
interface PublicDataApi {

    @GET("15111389/v1/uddi:실제-데이터셋-UUID")
    suspend fun getPetFriendlyPlaces(
        @Query("serviceKey") serviceKey: String,
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 1000,
    ): PlaceResponse
}
