package com.kimdev.petmap.domain.repository

import com.kimdev.petmap.domain.model.DataUpdateResult

/**
 * 원격(GitHub Pages)에 게시된 장소 데이터가 내장 데이터보다 새로우면 내려받아 교체한다.
 * 앱 업데이트 없이 장소 데이터를 갱신하는 경로 — 즐겨찾기·메모는 건드리지 않는다.
 */
interface PlaceDataUpdater {
    /** 확인 → (새 데이터면) 다운로드·검증·교체. 동시에 여러 번 불려도 한 번씩 순서대로 실행된다 */
    suspend fun update(): DataUpdateResult
}
