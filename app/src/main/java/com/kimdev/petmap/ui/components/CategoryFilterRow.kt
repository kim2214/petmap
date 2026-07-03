package com.kimdev.petmap.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kimdev.petmap.domain.model.PlaceCategory

/**
 * 카테고리 다중 선택 칩.
 * - "전체"는 선택 해제(빈 셋)를 의미하며, 탭 시 onClearAll 호출.
 * - 개별 카테고리 탭 시 onToggle 로 추가/제거.
 */
@Composable
fun CategoryFilterRow(
    selected: Set<PlaceCategory>,
    onToggle: (PlaceCategory) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = selected.isEmpty(),
            onClick = onClearAll,
            label = { Text("전체") },
            modifier = Modifier.padding(end = 8.dp),
        )
        PlaceCategory.entries.forEach { category ->
            val isOn = category in selected
            FilterChip(
                selected = isOn,
                onClick = { onToggle(category) },
                label = { Text(category.label) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    iconColor = category.color,
                    selectedContainerColor = category.color,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isOn,
                    borderColor = category.color.copy(alpha = 0.35f),
                    selectedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
