package com.kimdev.petmap.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.model.PetFilter

/** 반려동물 동반 조건 칩 라벨 */
val PetFilter.labelRes: Int
    @StringRes get() = when (this) {
        PetFilter.ANY_SIZE -> R.string.filter_any_size
        PetFilter.INDOOR -> R.string.pet_indoor_allowed
        PetFilter.OUTDOOR -> R.string.pet_outdoor_allowed
    }

/**
 * 반려동물 동반 조건 칩 묶음 (모든 크기 / 실내 가능 / 실외 가능). 여러 개 켜면 AND.
 * 목록·지도가 같은 칩을 쓴다. 호출 측 Row 안에 놓는다(스크롤·간격은 호출 측이 정한다).
 */
@Composable
fun PetFilterChips(
    selected: Set<PetFilter>,
    onToggle: (PetFilter) -> Unit,
    chipModifier: Modifier = Modifier,
) {
    PetFilter.entries.forEach { filter ->
        val isOn = filter in selected
        FilterChip(
            selected = isOn,
            onClick = { onToggle(filter) },
            label = { Text(stringResource(filter.labelRes)) },
            leadingIcon = if (isOn) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
            } else {
                null
            },
            modifier = chipModifier,
        )
    }
}
