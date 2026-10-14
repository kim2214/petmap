package com.kimdev.petmap.core.common

object Constants {
    // 지도 기본 카메라 위치 (서울시청)
    const val DEFAULT_LAT = 37.5666102
    const val DEFAULT_LNG = 126.9783881
    const val DEFAULT_ZOOM = 12.0

    // 문의·정보 오류 신고 수신 메일 (설정 화면·상세 화면 공용)
    const val CONTACT_EMAIL = "kimdev0821@gmail.com"

    /**
     * 원격 장소 데이터 배포 위치 (GitHub Pages, 별도 리포 kim2214/kim2214.github.io).
     * 앱은 `<이 경로><DB identity_hash>/manifest.json` 을 읽는다 — 스키마가 같은 앱만 같은 파일을 받는다.
     * 게시: tools/publish_db.py
     */
    const val DATA_UPDATE_BASE_URL = "https://kim2214.github.io/petmap/data/"
}
