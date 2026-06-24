package com.example.petmap.data.mapper

import com.example.petmap.data.local.entity.FavoriteEntity
import com.example.petmap.data.remote.dto.PlaceDto
import com.example.petmap.domain.model.PetInfo
import com.example.petmap.domain.model.Place
import com.example.petmap.domain.model.PlaceCategory

private fun String?.toBool(): Boolean =
    this?.let { it == "Y" || it == "가능" || it.equals("true", true) } ?: false

/** 원격 DTO → 도메인 모델 */
fun PlaceDto.toDomain(): Place? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    val placeName = name ?: return null
    val road = roadAddress ?: lotAddress.orEmpty()
    return Place(
        // 좌표 + 이름으로 안정적인 id 생성 (데이터셋에 고유 id 가 없을 때)
        id = "${placeName}_${lat}_${lng}",
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
