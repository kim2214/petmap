package com.kimdev.petmap.data.remote

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** 원격 데이터 전송 추상화 (테스트에서 file:// 또는 페이크로 교체) */
interface DataDownloader {
    /** 텍스트를 읽는다. 대상이 없으면(HTTP 404 등) null */
    fun fetchText(url: String, maxBytes: Int): String?

    /** [dest] 로 내려받는다. [maxBytes] 를 넘으면 [IOException] */
    fun download(url: String, dest: File, maxBytes: Long)
}

/**
 * [URL.openConnection] 기반 구현 — 의존성 없이 https(GitHub Pages)와 file://(기기 테스트)을 함께 처리한다.
 * 갱신은 하루 한 번 작은 매니페스트와 가끔 수 MB 파일이 전부라 HTTP 클라이언트 라이브러리를 들이지 않는다.
 */
class UrlDataDownloader @Inject constructor() : DataDownloader {

    override fun fetchText(url: String, maxBytes: Int): String? =
        open(url)?.use { input -> String(input.readLimited(maxBytes.toLong()), Charsets.UTF_8) }

    override fun download(url: String, dest: File, maxBytes: Long) {
        val input = open(url) ?: throw FileNotFoundException(url)
        input.use { src ->
            dest.outputStream().use { out ->
                val buf = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    // 취소 확인(갱신은 runInterruptible 안에서 돈다) — 수 MB 를 끝까지 받지 않고 멈춘다
                    if (Thread.interrupted()) throw InterruptedException()
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("크기 상한 초과: $url")
                    out.write(buf, 0, n)
                }
            }
        }
    }

    /** 응답 본문 스트림. 대상 없음이면 null, 그 밖의 HTTP 오류는 [IOException] */
    private fun open(url: String): InputStream? {
        val conn = URL(url).openConnection().apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
        }
        if (conn is HttpURLConnection) {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                conn.disconnect()
                return null
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw IOException("HTTP $code: $url")
            }
        }
        return try {
            conn.getInputStream()
        } catch (_: FileNotFoundException) {
            null // file:// 대상 없음
        }
    }

    private fun InputStream.readLimited(maxBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            if (out.size() + n > maxBytes) throw IOException("크기 상한 초과")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
