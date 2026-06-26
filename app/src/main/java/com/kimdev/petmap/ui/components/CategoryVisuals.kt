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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kimdev.petmap.domain.model.PlaceCategory

/** 카테고리별 고유 색상 (아이콘/마커/태그용). 원색 톤으로 한눈에 구분된다. */
val PlaceCategory.color: Color
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
        PlaceCategory.ETC -> Color(0xFF6E8E78)           // 그린그레이
    }

/** 아이콘 아바타 배경용 연한 틴트 (해당 카테고리 색의 옅은 버전) */
val PlaceCategory.softColor: Color
    get() = color.copy(alpha = 0.16f)

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
        PlaceCategory.ETC -> Icons.Filled.Pets
    }
