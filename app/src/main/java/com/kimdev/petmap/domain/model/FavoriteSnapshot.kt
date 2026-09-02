package com.kimdev.petmap.domain.model

/**
 * 즐겨찾기 삭제 직전 상태. [com.kimdev.petmap.domain.repository.PlaceRepository.restoreFavorite] 에
 * 그대로 넘기면 메모·추가 시각까지 원래대로 되살아난다(실행취소).
 *
 * 즐겨찾기를 해제하면 favorites 행이 통째로 지워져 메모도 함께 사라지므로, 토글로 다시 담는 것과
 * 이 스냅샷 복원은 결과가 다르다.
 */
data class FavoriteSnapshot(
    /** 삭제 시점의 장소 (memo 포함) */
    val place: Place,
    /** 원래 추가 시각(epoch millis) — 목록 순서 보존용 */
    val addedAt: Long,
)
