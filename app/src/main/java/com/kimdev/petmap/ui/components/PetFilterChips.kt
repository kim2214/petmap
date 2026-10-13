package com.kimdev.petmap.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.model.PetFilter

/** 반려동물 동반 조건 칩 라벨 (내 아이 칩은 체중이 있으면 [PetFilterChips] 가 체중을 붙여 그린다) */
val PetFilter.labelRes: Int
    @StringRes get() = when (this) {
        PetFilter.ANY_SIZE -> R.string.filter_any_size
        PetFilter.INDOOR -> R.string.pet_indoor_allowed
        PetFilter.OUTDOOR -> R.string.pet_outdoor_allowed
        PetFilter.MY_PET -> R.string.filter_my_pet
        PetFilter.LARGE_DOG -> R.string.filter_large_dog
        PetFilter.PARKING -> R.string.filter_parking
    }

/**
 * 반려동물 동반 조건 칩 묶음 (내 아이 가능 / 대형견 가능 / 모든 크기 / 실내 가능 / 실외 가능 / 주차 가능). 여러 개 켜면 AND.
 * 목록·지도가 같은 칩을 쓴다. 호출 측 Row 안에 놓는다(스크롤·간격은 호출 측이 정한다).
 *
 * "내 아이 가능"은 체중([myPetKg])이 등록돼 있어야 뜻이 있다 — 미등록 상태에서 누르면 토글 대신
 * [onNeedWeight] 로 등록 다이얼로그를 띄우게 한다.
 */
@Composable
fun PetFilterChips(
    selected: Set<PetFilter>,
    onToggle: (PetFilter) -> Unit,
    myPetKg: Double? = null,
    onNeedWeight: () -> Unit = {},
    chipModifier: Modifier = Modifier,
) {
    // 개인화 칩을 맨 앞에 — 등록해 두면 가장 자주 쓰는 조건이다. 대형견은 체중 미등록 보호자의 빠른 조건이라 그다음.
    val order = PetFilter.CHIP_ORDER
    order.forEach { filter ->
        val isOn = filter in selected
        val label = if (filter == PetFilter.MY_PET && myPetKg != null) {
            stringResource(R.string.filter_my_pet_format, formatPetWeight(myPetKg))
        } else {
            stringResource(filter.labelRes)
        }
        FilterChip(
            selected = isOn,
            onClick = {
                if (filter == PetFilter.MY_PET && myPetKg == null) onNeedWeight() else onToggle(filter)
            },
            label = { Text(label) },
            leadingIcon = when {
                isOn -> {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                }
                filter == PetFilter.MY_PET -> {
                    { Icon(Icons.Filled.Pets, contentDescription = null) }
                }
                else -> null
            },
            modifier = chipModifier,
        )
    }
}
