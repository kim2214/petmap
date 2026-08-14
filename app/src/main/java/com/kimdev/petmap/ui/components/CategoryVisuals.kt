package com.kimdev.petmap.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsTennis
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.ui.theme.LocalIsDarkTheme

/** 카테고리 표시 라벨 리소스 (도메인 enum → UI 문자열). */
val PlaceCategory.labelRes: Int
    @StringRes get() = when (this) {
        PlaceCategory.HOSPITAL -> R.string.category_hospital
        PlaceCategory.PHARMACY -> R.string.category_pharmacy
        PlaceCategory.SHOP -> R.string.category_shop
        PlaceCategory.GROOMING -> R.string.category_grooming
        PlaceCategory.CAFE -> R.string.category_cafe
        PlaceCategory.RESTAURANT -> R.string.category_restaurant
        PlaceCategory.ACCOMMODATION -> R.string.category_accommodation
        PlaceCategory.CULTURE -> R.string.category_culture
        PlaceCategory.TRAVEL -> R.string.category_travel
        PlaceCategory.CARE -> R.string.category_care
        PlaceCategory.SPORTS -> R.string.category_sports
        PlaceCategory.ETC -> R.string.category_etc
    }

/**
 * 카테고리별 고유 색상 원본 팔레트 (마커 비트맵용).
 * 지도 위 핀은 흰 외곽선이 있어 야간 지도에서도 이 원색으로 충분하다.
 * 컴포지션 밖(비트맵 렌더링)에서도 쓸 수 있도록 비-컴포저블로 둔다.
 */
val PlaceCategory.markerColor: Color
    get() = when (this) {
        PlaceCategory.HOSPITAL -> Color(0xFF3D8BD4)      // 파랑
        PlaceCategory.PHARMACY -> Color(0xFF8B5CD6)      // 보라
        PlaceCategory.SHOP -> Color(0xFFEC6FA0)          // 핑크
        PlaceCategory.GROOMING -> Color(0xFF18B7A8)      // 청록
        PlaceCategory.CAFE -> Color(0xFFF2913C)          // 주황
        PlaceCategory.RESTAURANT -> Color(0xFFE5663F)    // 코랄
        PlaceCategory.ACCOMMODATION -> Color(0xFF5B6CD6) // 인디고
        PlaceCategory.CULTURE -> Color(0xFFD9A23B)       // 골드
        PlaceCategory.TRAVEL -> Color(0xFF1FAE8A)        // 그린틸
        PlaceCategory.CARE -> Color(0xFF9B6BD6)          // 바이올렛
        PlaceCategory.SPORTS -> Color(0xFFE04F5F)        // 체리레드
        PlaceCategory.ETC -> Color(0xFF6E8E78)           // 그린그레이
    }

/**
 * UI 용 카테고리 색 (아이콘/태그/칩). 중간 명도 원색은 다크 배경에서 대비가 떨어지므로
 * 다크 모드에선 흰색 쪽으로 밝힌 변형을 쓴다.
 */
val PlaceCategory.color: Color
    @Composable get() =
        if (LocalIsDarkTheme.current) lerp(markerColor, Color.White, 0.28f) else markerColor

/** 아이콘 아바타 배경용 연한 틴트 (해당 카테고리 색의 옅은 버전) */
val PlaceCategory.softColor: Color
    @Composable get() = color.copy(alpha = if (LocalIsDarkTheme.current) 0.22f else 0.16f)

/** 카테고리별 대표 아이콘 (카드/상세 아바타용) */
val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.HOSPITAL -> Icons.Filled.LocalHospital
        PlaceCategory.PHARMACY -> Icons.Filled.Medication
        PlaceCategory.SHOP -> Icons.Filled.ShoppingBag
        PlaceCategory.GROOMING -> Icons.Filled.ContentCut
        PlaceCategory.CAFE -> Icons.Filled.LocalCafe
        PlaceCategory.RESTAURANT -> Icons.Filled.Restaurant
        PlaceCategory.ACCOMMODATION -> Icons.Filled.Hotel
        PlaceCategory.CULTURE -> Icons.Filled.Museum
        PlaceCategory.TRAVEL -> Icons.Filled.Luggage
        PlaceCategory.CARE -> Icons.Filled.Pets
        PlaceCategory.SPORTS -> Icons.Filled.SportsTennis
        PlaceCategory.ETC -> Icons.Filled.Pets
    }
