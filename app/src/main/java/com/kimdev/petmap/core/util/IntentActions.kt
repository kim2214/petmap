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
import com.kimdev.petmap.core.review.InAppReview
import com.kimdev.petmap.data.local.DirectionsApp
import com.kimdev.petmap.data.local.DirectionsAppStore
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.ui.navigation.Routes

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
    // 장소 공유도 긍정 시그널 → 인앱 리뷰 요청 조건으로 쌓는다
    InAppReview.recordPositiveSignal(this)
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
        // 펫맵이 설치된 기기에서는 이 링크로 앱의 상세 화면이 바로 열린다
        appendLine(getString(R.string.share_app_link_format, Routes.detailDeepLink(place.id)))
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
    // 기본 스킴은 http 를 유지한다. DB 의 스킴 없는 홈페이지 728건 중엔 HTTPS 를 아예
    // 받지 않는 관공서 사이트가 실존하고(https 는 http 로 폴백되지 않음),
    // http 는 서버가 지원하면 https 로 리다이렉트되므로 열리는 쪽이 항상 http 다.
    val normalized = if (url.startsWith("http", ignoreCase = true)) url else "http://$url"
    safeStart(Intent(Intent.ACTION_VIEW, normalized.toUri()))
}

/**
 * 길찾기: 설정에서 고른 지도 앱(기본 네이버 지도)으로 자동차 경로를 연다(반려동물 동반 이동은 차량이 기본). 그 앱이 없으면 다른 앱,
 * 그마저 없으면 기기 기본 지도(geo:), 그것도 없으면 웹 지도로 폴백한다 — 이름 검색이 아니라 좌표를
 * 넘겨 목적지가 특정되게 한다. 네이버 딥링크는 appname 파라미터(호출 앱 패키지)를 요구한다.
 * 카카오맵은 출발지(sp)를 생략하면 현재 위치에서 출발한다.
 */
fun Context.openDirections(place: Place) {
    // 길찾기 실행은 앱이 실제 도움이 된 순간 → 인앱 리뷰 요청 조건으로 쌓는다
    InAppReview.recordPositiveSignal(this)
    val name = Uri.encode(place.name)
    val naver = Intent(
        Intent.ACTION_VIEW,
        "nmap://route/car?dlat=${place.lat}&dlng=${place.lng}&dname=$name&appname=$packageName".toUri(),
    ).setPackage(NAVER_MAP_PACKAGE)
    val kakao = Intent(
        Intent.ACTION_VIEW,
        "kakaomap://route?ep=${place.lat},${place.lng}&by=CAR".toUri(),
    ).setPackage(KAKAO_MAP_PACKAGE)
    val preferred = DirectionsAppStore.readStored(this)
    val apps = if (preferred == DirectionsApp.KAKAO) listOf(kakao, naver) else listOf(naver, kakao)
    val geo = Intent(
        Intent.ACTION_VIEW,
        "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}($name)".toUri(),
    )
    for (intent in apps + geo) {
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // 설치 안 됨 → 다음 후보로
        }
    }
    val web = if (preferred == DirectionsApp.KAKAO) {
        "https://map.kakao.com/link/to/$name,${place.lat},${place.lng}"
    } else {
        "https://www.google.com/maps/dir/?api=1&destination=${place.lat},${place.lng}"
    }
    openUrl(web)
}

private const val NAVER_MAP_PACKAGE = "com.nhn.android.nmap"
private const val KAKAO_MAP_PACKAGE = "net.daum.android.map"

/** 리뷰·사진을 볼 외부 지도 서비스 (자체 리뷰가 없으므로 장소 검색으로 넘긴다) */
enum class ReviewProvider { NAVER, KAKAO }

/**
 * 리뷰 검색어: 도로명주소 앞 두 토큰(시/도 시/군/구) + 상호.
 * 상호만 넘기면 체인점·동명 업소가 섞여 다른 지점의 리뷰를 보게 되므로 지역으로 좁힌다.
 */
fun reviewSearchQuery(place: Place): String {
    val area = place.roadAddress.trim().split(Regex("\\s+")).take(2).joinToString(" ")
    return listOf(area, place.name.trim()).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * 네이버 지도/카카오맵에서 장소 검색 결과 열기 — 리뷰·사진은 그쪽 장소 페이지에서 본다.
 * 앱이 설치돼 있으면 앱 딥링크, 없으면 모바일 웹으로 폴백한다(API 30+ 패키지 가시성 선언 없이
 * startActivity 실패로 미설치를 판정한다).
 */
fun Context.openPlaceReviews(place: Place, provider: ReviewProvider) {
    val q = Uri.encode(reviewSearchQuery(place))
    val (deepLink, appPackage, webUrl) = when (provider) {
        ReviewProvider.NAVER -> Triple(
            "nmap://search?query=$q&appname=$packageName",
            "com.nhn.android.nmap",
            "https://m.map.naver.com/search2/search.naver?query=$q",
        )
        ReviewProvider.KAKAO -> Triple(
            "kakaomap://search?q=$q",
            "net.daum.android.map",
            "https://m.map.kakao.com/actions/searchView?q=$q",
        )
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, deepLink.toUri()).setPackage(appPackage))
    } catch (e: ActivityNotFoundException) {
        safeStart(Intent(Intent.ACTION_VIEW, webUrl.toUri()))
    }
}

/** 공유 문구용 반려동물 정보 요약 (예: "소형견 · 실내 가능 · 실외 불가") */
private fun Context.petSummary(place: Place): String? {
    val parts = buildList {
        place.petInfo.allowedPetSize?.let { add(it) }
        // null(정보 없음)은 생략 — "불가"로 잘못 전달하지 않는다
        place.petInfo.indoorAllowed?.let { add(getString(if (it) R.string.pet_indoor_allowed else R.string.pet_indoor_not_allowed)) }
        place.petInfo.outdoorAllowed?.let { add(getString(if (it) R.string.pet_outdoor_allowed else R.string.pet_outdoor_not_allowed)) }
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
