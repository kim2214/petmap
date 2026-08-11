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

/** 전화 앱 열기(다이얼러). 공공데이터엔 "02-123-4567, 010-1111-2222"처럼 번호가 여러 개인 값이 있어 첫 번째만 건다. */
fun Context.dialPhone(phone: String) {
    val first = phone.split(',', '/', ';').firstOrNull { it.any(Char::isDigit) } ?: phone
    val number = first.filter { it.isDigit() || it == '+' }
    safeStart(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
}

/** 장소 정보 공유 */
fun Context.sharePlace(place: Place) {
    val text = buildString {
        appendLine(place.name)
        appendLine(place.roadAddress)
        place.phone?.let { appendLine(getString(R.string.share_phone_format, it)) }
        place.operatingTime?.let { appendLine(getString(R.string.share_operating_format, it)) }
        place.closedDays?.let { appendLine(getString(R.string.share_closed_format, it)) }
        petSummary(place)?.let { appendLine(getString(R.string.share_pet_format, it)) }
        // 받은 사람이 바로 위치를 열 수 있도록 좌표 링크 동봉(지도 앱이 무엇이든 열리는 표준 링크)
        appendLine(
            getString(
                R.string.share_map_format,
                "https://www.google.com/maps/search/?api=1&query=${place.lat},${place.lng}",
            )
        )
    }.trim()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, place.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    safeStart(Intent.createChooser(send, getString(R.string.action_share)))
}

/** 메일 앱으로 문의 보내기. [body] 를 주면 본문이 미리 채워진다(정보 오류 신고 등). */
fun Context.sendEmail(address: String, subject: String = "", body: String = "") {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()).apply {
        if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
        if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
    }
    safeStart(intent)
}

/** 외부 브라우저로 URL 열기 */
fun Context.openUrl(url: String) {
    val normalized = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
    safeStart(Intent(Intent.ACTION_VIEW, normalized.toUri()))
}

/**
 * 네이버 지도 앱으로 자동차 길찾기(반려동물 동반 이동은 차량이 기본).
 * 미설치 시 좌표 geo: 인텐트로 설치된 다른 지도 앱(카카오맵·구글맵 등)에 위임하고,
 * 그마저 없으면 웹 지도로 폴백한다 — 이름 검색이 아니라 좌표를 넘겨 목적지가 특정되게 한다.
 * 네이버 딥링크는 appname 파라미터(호출 앱 패키지)를 요구한다.
 */
fun Context.openNaverDirections(place: Place) {
    val name = Uri.encode(place.name)
    val deepLink = "nmap://route/car?dlat=${place.lat}&dlng=${place.lng}" +
        "&dname=$name&appname=$packageName"
    val appIntent = Intent(Intent.ACTION_VIEW, deepLink.toUri())
        .setPackage("com.nhn.android.nmap")
    try {
        startActivity(appIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // 네이버 지도 미설치 → 아래 폴백으로
    }
    val geo = Intent(
        Intent.ACTION_VIEW,
        "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}($name)".toUri(),
    )
    try {
        startActivity(geo)
    } catch (e: ActivityNotFoundException) {
        openUrl("https://www.google.com/maps/dir/?api=1&destination=${place.lat},${place.lng}")
    }
}

/** 공유 문구용 반려동물 정보 요약 (예: "소형견 · 실내 가능 · 실외 불가") */
private fun Context.petSummary(place: Place): String? {
    val parts = buildList {
        place.petInfo.allowedPetSize?.let { add(it) }
        add(getString(if (place.petInfo.indoorAllowed) R.string.pet_indoor_allowed else R.string.pet_indoor_not_allowed))
        add(getString(if (place.petInfo.outdoorAllowed) R.string.pet_outdoor_allowed else R.string.pet_outdoor_not_allowed))
    }
    return parts.joinToString(" · ").ifBlank { null }
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
