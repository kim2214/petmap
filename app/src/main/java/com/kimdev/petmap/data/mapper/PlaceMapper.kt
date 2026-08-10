package com.kimdev.petmap.data.mapper

import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.FavoriteWithPlace
import com.kimdev.petmap.data.local.entity.PlaceEntity
import com.kimdev.petmap.domain.model.PetInfo
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory

/** 로컬 places Entity → 도메인 (category 컬럼엔 PlaceCategory.name 이 저장돼 있음) */
fun PlaceEntity.toDomain(): Place = Place(
    id = id,
    name = name,
    category = runCatching { PlaceCategory.valueOf(category) }.getOrDefault(PlaceCategory.ETC),
    roadAddress = roadAddress,
    lotAddress = lotAddress,
    lat = lat,
    lng = lng,
    phone = phone,
    operatingTime = operatingTime,
    closedDays = closedDays,
    homepage = homepage,
    petInfo = PetInfo(allowedPetSize, restriction, indoorAllowed, outdoorAllowed),
)

/** 도메인 → 로컬 즐겨찾기 Entity */
fun Place.toFavoriteEntity(): FavoriteEntity = FavoriteEntity(
    id = id,
    name = name,
    category = category.name,
    roadAddress = roadAddress,
    lat = lat,
    lng = lng,
    phone = phone,
)

/**
 * 즐겨찾기 조인 결과 → 도메인.
 * places 원본이 있으면 영업시간·반려동물 정보까지 완성된 모델을,
 * 데이터 갱신으로 원본이 사라졌으면 저장 시점 스냅샷(최소 정보)을 돌려준다.
 */
fun FavoriteWithPlace.toDomain(): Place =
    place?.toDomain()?.copy(isFavorite = true) ?: favorite.toDomain()

/** 즐겨찾기 Entity → 도메인 (목록 표시용 최소 정보) */
fun FavoriteEntity.toDomain(): Place = Place(
    id = id,
    name = name,
    category = runCatching { PlaceCategory.valueOf(category) }.getOrDefault(PlaceCategory.ETC),
    roadAddress = roadAddress,
    lotAddress = "",
    lat = lat,
    lng = lng,
    phone = phone,
    operatingTime = null,
    closedDays = null,
    homepage = null,
    petInfo = PetInfo(null, null, indoorAllowed = false, outdoorAllowed = false),
    isFavorite = true,
)
