package com.kimdev.petmap.data.mapper

import com.kimdev.petmap.data.local.entity.FavoriteEntity
import com.kimdev.petmap.data.local.entity.PlaceEntity
import com.kimdev.petmap.data.remote.dto.PlaceDto
import com.kimdev.petmap.domain.model.PetInfo
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory

/** 공공데이터의 "가능 여부" 문자열을 Boolean 으로 변환 (도메인/엔티티 경로 공통) */
private fun String?.toBool(): Boolean =
    this?.let { it == "Y" || it == "가능" || it.equals("true", true) } ?: false

/** 좌표 + 이름으로 안정적인 id 생성 (데이터셋에 고유 id 가 없을 때) */
private fun stableId(name: String, lat: Double, lng: Double) = "${name}_${lat}_${lng}"

/** 원격 DTO → 도메인 모델 */
fun PlaceDto.toDomain(): Place? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    val placeName = name ?: return null
    val road = roadAddress ?: lotAddress.orEmpty()
    return Place(
        id = stableId(placeName, lat, lng),
        name = placeName,
        category = PlaceCategory.fromRaw(category),
        roadAddress = road,
        lotAddress = lotAddress.orEmpty(),
        lat = lat,
        lng = lng,
        phone = phone?.takeIf { it.isNotBlank() },
        operatingTime = operatingTime?.takeIf { it.isNotBlank() },
        closedDays = closedDays?.takeIf { it.isNotBlank() },
        homepage = homepage?.takeIf { it.isNotBlank() },
        petInfo = PetInfo(
            allowedPetSize = allowedPetSize?.takeIf { it.isNotBlank() },
            restriction = restriction?.takeIf { it.isNotBlank() },
            indoorAllowed = indoorAllowed.toBool(),
            outdoorAllowed = outdoorAllowed.toBool(),
        ),
    )
}

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

/** 원격 DTO → 로컬 places Entity (하이브리드 갱신용) */
fun PlaceDto.toEntity(): PlaceEntity? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    val placeName = name ?: return null
    return PlaceEntity(
        id = stableId(placeName, lat, lng),
        name = placeName,
        category = PlaceCategory.fromRaw(category).name,
        roadAddress = roadAddress ?: lotAddress.orEmpty(),
        lotAddress = lotAddress.orEmpty(),
        lat = lat,
        lng = lng,
        phone = phone?.takeIf { it.isNotBlank() },
        operatingTime = operatingTime?.takeIf { it.isNotBlank() },
        closedDays = closedDays?.takeIf { it.isNotBlank() },
        homepage = homepage?.takeIf { it.isNotBlank() },
        allowedPetSize = allowedPetSize?.takeIf { it.isNotBlank() },
        restriction = restriction?.takeIf { it.isNotBlank() },
        indoorAllowed = indoorAllowed.toBool(),
        outdoorAllowed = outdoorAllowed.toBool(),
    )
}

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
