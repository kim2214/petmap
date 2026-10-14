package com.kimdev.petmap.data.remote

import org.json.JSONObject
import java.time.LocalDate

/**
 * 원격 데이터 매니페스트 (`<base>/<identity_hash>/manifest.json`, tools/publish_db.py 가 생성).
 *
 * ```json
 * {"dataDate": "2026-10-14", "file": "petmap-2026-10-14.db.gz",
 *  "sha256": "<gz 파일의 SHA-256 hex>", "size": 5321112, "placeCount": 24700}
 * ```
 */
data class DataManifest(
    /** 데이터 기준일. 내 DB 의 meta.data_date 보다 늦을 때만 받는다 */
    val dataDate: LocalDate,
    /** 매니페스트와 같은 디렉터리의 gzip DB 파일 이름 */
    val file: String,
    val sha256: String,
    /** gz 파일 크기(바이트) — 다운로드 상한·무결성 확인용 */
    val size: Long,
    val placeCount: Int,
) {
    companion object {
        /** 원격 DB 크기 상한 (현재 gz 약 5MB) — 잘못 게시된 거대 파일을 받지 않는다 */
        const val MAX_SIZE = 50L * 1024 * 1024

        private val FILE_NAME = Regex("[A-Za-z0-9._-]+")
        private val SHA256_HEX = Regex("[0-9a-f]{64}")

        /** 형식이 어긋나면 [IllegalArgumentException] (파싱 오류 포함) */
        fun parse(json: String): DataManifest {
            val o = runCatching { JSONObject(json) }
                .getOrElse { throw IllegalArgumentException("매니페스트 JSON 파싱 실패", it) }
            val manifest = runCatching {
                DataManifest(
                    dataDate = LocalDate.parse(o.getString("dataDate")),
                    file = o.getString("file"),
                    sha256 = o.getString("sha256").lowercase(),
                    size = o.getLong("size"),
                    placeCount = o.getInt("placeCount"),
                )
            }.getOrElse { throw IllegalArgumentException("매니페스트 필드 누락/형식 오류", it) }
            // 파일 이름은 URL 경로에 그대로 붙으므로 경로 이동(../)·다른 호스트를 막는다
            require(FILE_NAME.matches(manifest.file) && ".." !in manifest.file) { "잘못된 파일 이름: ${manifest.file}" }
            require(SHA256_HEX.matches(manifest.sha256)) { "잘못된 sha256" }
            require(manifest.size in 1..MAX_SIZE) { "허용 범위를 벗어난 크기: ${manifest.size}" }
            require(manifest.placeCount > 0) { "placeCount 가 0" }
            return manifest
        }
    }

    /** 내 데이터 기준일([local], 모르면 null)보다 새 데이터인가 */
    fun isNewerThan(local: LocalDate?): Boolean = local == null || dataDate.isAfter(local)
}
