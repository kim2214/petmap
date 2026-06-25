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
import androidx.compose.ui.graphics.vector.ImageVector
import com.kimdev.petmap.domain.model.PlaceCategory

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
