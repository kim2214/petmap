package com.kimdev.petmap.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.kimdev.petmap.domain.model.Place

/** 전화 앱 열기(다이얼러) */
fun Context.dialPhone(phone: String) {
    val number = phone.filter { it.isDigit() || it == '+' }
    safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
}

/** 장소 정보 공유 */
fun Context.sharePlace(place: Place) {
    val text = buildString {
        appendLine(place.name)
        appendLine(place.roadAddress)
        place.phone?.let { appendLine("전화: $it") }
        place.operatingTime?.let { appendLine("운영: $it") }
    }.trim()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, place.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    safeStart(Intent.createChooser(send, "공유"))
}

/** 외부 브라우저로 URL 열기 */
fun Context.openUrl(url: String) {
    val normalized = if (url.startsWith("http", ignoreCase = true)) url else "http://$url"
    safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
}

/**
 * 네이버 지도 앱으로 도보 길찾기. 미설치 시 네이버 지도 웹으로 폴백.
 * 네이버 딥링크는 appname 파라미터(호출 앱 패키지)를 요구한다.
 */
fun Context.openNaverDirections(place: Place) {
    val name = Uri.encode(place.name)
    val deepLink = "nmap://route/walk?dlat=${place.lat}&dlng=${place.lng}" +
        "&dname=$name&appname=$packageName"
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
        .setPackage("com.nhn.android.nmap")
    try {
        startActivity(appIntent)
    } catch (e: ActivityNotFoundException) {
        // 네이버 지도 미설치 → 웹 지도 검색으로 대체
        openUrl("https://map.naver.com/p/search/$name")
    }
}

private fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "실행할 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
    }
}
