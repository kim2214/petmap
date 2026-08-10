package com.kimdev.petmap.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 즐겨찾기 + 원본 장소 조인 결과.
 * favorites 는 표시용 최소 스냅샷만 저장하므로, 영업시간·반려동물 정보까지 보여주려면
 * places 원본 행이 필요하다. 데이터 갱신으로 원본이 사라진 즐겨찾기는 place 가 null 이며
 * 스냅샷으로 폴백해 목록에서 계속 보이게 한다.
 */
data class FavoriteWithPlace(
    @Embedded val favorite: FavoriteEntity,
    @Relation(parentColumn = "id", entityColumn = "id")
    val place: PlaceEntity?,
)
