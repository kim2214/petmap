package com.kimdev.petmap.core.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.model.Place

/** 이 앱의 설정 화면 열기 (권한 영구 거부 시 안내용) */
fun Context.openAppSettings() {
    safeStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    )
}

/** 전화 앱 열기(다이얼러) */
fun Context.dialPhone(phone: String) {
    val number = phone.filter { it.isDigit() || it == '+' }
    safeStart(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
}

/** 장소 정보 공유 */
fun Context.sharePlace(place: Place) {
    val text = buildString {
        appendLine(place.name)
        appendLine(place.roadAddress)
        place.phone?.let { appendLine(getString(R.string.share_phone_format, it)) }
        place.operatingTime?.let { appendLine(getString(R.string.share_operating_format, it)) }
    }.trim()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, place.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    safeStart(Intent.createChooser(send, getString(R.string.action_share)))
}

/** 메일 앱으로 문의 보내기 */
fun Context.sendEmail(address: String, subject: String = "") {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()).apply {
        if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    safeStart(intent)
}

/** 외부 브라우저로 URL 열기 */
fun Context.openUrl(url: String) {
    val normalized = if (url.startsWith("http", ignoreCase = true)) url else "http://$url"
    safeStart(Intent(Intent.ACTION_VIEW, normalized.toUri()))
}

/**
 * 네이버 지도 앱으로 도보 길찾기. 미설치 시 네이버 지도 웹으로 폴백.
 * 네이버 딥링크는 appname 파라미터(호출 앱 패키지)를 요구한다.
 */
fun Context.openNaverDirections(place: Place) {
    val name = Uri.encode(place.name)
    val deepLink = "nmap://route/walk?dlat=${place.lat}&dlng=${place.lng}" +
        "&dname=$name&appname=$packageName"
    val appIntent = Intent(Intent.ACTION_VIEW, deepLink.toUri())
        .setPackage("com.nhn.android.nmap")
    try {
        startActivity(appIntent)
    } catch (e: ActivityNotFoundException) {
        // 네이버 지도 미설치 → 웹 지도 검색으로 대체
        openUrl("https://map.naver.com/p/search/$name")
    }
}

/** 텍스트를 클립보드에 복사. Android 13+ 는 시스템이 복사 UI 를 띄우므로 토스트를 생략한다. */
fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
    }
}

private fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.toast_no_app), Toast.LENGTH_SHORT).show()
    }
}
