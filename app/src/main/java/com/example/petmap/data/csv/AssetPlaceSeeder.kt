package com.example.petmap.data.csv

import android.content.Context
import android.util.Log
import com.example.petmap.data.local.dao.PlaceDao
import com.example.petmap.data.local.entity.PlaceEntity
import com.example.petmap.domain.model.PlaceCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱에 내장된 gzip CSV(assets/places.csv.gz)를 파싱해 Room 의 places 테이블에 1회 시딩한다.
 * 데이터셋 컬럼이 바뀌어도 헤더명 기반 매핑이라 견고하다.
 */
@Singleton
class AssetPlaceSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val placeDao: PlaceDao,
) {
    private val mutex = Mutex()

    /** places 가 비어 있으면 에셋에서 시딩. 동시 호출은 직렬화되며 재확인한다. */
    suspend fun seedIfEmpty() {
        if (placeDao.count() > 0) return
        mutex.withLock {
            if (placeDao.count() > 0) return
            seed()
        }
    }

    suspend fun seed(): Int = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        var headerIndex: Map<String, Int>? = null
        val all = ArrayList<PlaceEntity>(80_000)

        try {
            context.assets.open(ASSET_NAME).use { raw ->
                BufferedReader(InputStreamReader(raw, Charsets.UTF_8), 1 shl 16).use { reader ->
                    CsvReader.forEachRecord(reader) { record ->
                        val idx = headerIndex
                        if (idx == null) {
                            // 첫 행 = 헤더. BOM 제거 후 인덱스 맵 구성.
                            headerIndex = record.mapIndexed { i, h -> h.removePrefix("﻿").trim() to i }.toMap()
                            return@forEachRecord
                        }
                        toEntity(record, idx)?.let { all.add(it) }
                    }
                }
            }
            // 원본 데이터셋에 동일 레코드가 대량 중복되어 있어 id(이름+좌표) 기준으로 정제한다.
            val deduped = all.distinctBy { it.id }
            deduped.chunked(BATCH).forEach { placeDao.upsertAll(it) }
            Log.i(
                TAG,
                "Seeded ${deduped.size} unique places (parsed ${all.size}) in ${System.currentTimeMillis() - started}ms",
            )
        } catch (e: Exception) {
            // 시딩 실패가 앱 전체를 죽이지 않도록 방어 (데이터는 비어 있게 됨)
            Log.e(TAG, "Seeding failed", e)
        }
        all.size
    }

    private fun toEntity(r: List<String>, idx: Map<String, Int>): PlaceEntity? {
        fun col(name: String): String? = idx[name]?.let { r.getOrNull(it)?.trim() }
        fun meaningful(s: String?): String? =
            s?.takeIf { it.isNotEmpty() && it !in BLANK_VALUES }

        val name = col("시설명")?.takeIf { it.isNotEmpty() } ?: return null
        val lat = col("위도")?.toDoubleOrNull() ?: return null
        val lng = col("경도")?.toDoubleOrNull() ?: return null
        val road = meaningful(col("도로명주소")) ?: col("지번주소").orEmpty()

        return PlaceEntity(
            id = "${name}_${lat}_${lng}",
            name = name,
            category = PlaceCategory.fromRaw(col("카테고리3")).name,
            roadAddress = road,
            lotAddress = col("지번주소").orEmpty(),
            lat = lat,
            lng = lng,
            phone = meaningful(col("전화번호")),
            operatingTime = meaningful(col("운영시간")),
            closedDays = meaningful(col("휴무일")),
            homepage = meaningful(col("홈페이지")),
            allowedPetSize = meaningful(col("입장 가능 동물 크기")),
            restriction = meaningful(col("반려동물 제한사항")),
            indoorAllowed = col("장소(실내) 여부") == "Y",
            outdoorAllowed = col("장소(실외)여부") == "Y",
        )
    }

    companion object {
        private const val TAG = "AssetPlaceSeeder"
        private const val ASSET_NAME = "places.csv"
        private const val BATCH = 1000
        private val BLANK_VALUES = setOf("정보없음", "해당없음", "없음", "-")
    }
}
