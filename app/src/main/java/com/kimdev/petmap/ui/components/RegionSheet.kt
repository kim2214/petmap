package com.kimdev.petmap.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.model.Region
import com.kimdev.petmap.domain.model.RegionCount

/**
 * 지역 선택 바텀시트: 시/도 칩 → 시/군/구 목록(가나다순, 개수 표시) 2단계.
 * 시/군/구 단계가 없는 시/도(세종)는 시/도를 누르면 바로 선택된다.
 * [counts] 가 비어 있으면 로딩 표시.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegionSheet(
    current: Region?,
    counts: List<RegionCount>,
    onSelect: (Region?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 현재 선택된 시/도가 있으면 그 시/군/구 단계부터 보여준다
    var sido by rememberSaveable { mutableStateOf(current?.sido) }
    val bySido = counts.groupBy { it.sido }
    val sidoOrder = Region.SIDO_ORDER.filter { it in bySido } + bySido.keys.filter { it !in Region.SIDO_ORDER }.sorted()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val currentSido = sido
                if (currentSido != null) {
                    IconButton(onClick = { sido = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.region_back))
                    }
                }
                Text(
                    text = currentSido ?: stringResource(R.string.region_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (current != null) {
                    TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.action_region_off)) }
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                counts.isEmpty() -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                sido == null -> {
                    Text(
                        stringResource(R.string.region_pick_sido),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sidoOrder.forEach { name ->
                            val total = bySido.getValue(name).sumOf { it.count }
                            val hasSigungu = bySido.getValue(name).any { it.sigungu != null }
                            FilterChip(
                                selected = current?.sido == name,
                                onClick = { if (hasSigungu) sido = name else onSelect(Region(name)) },
                                label = { Text("${Region.shortSido(name)} ${formatCount(total)}") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                else -> {
                    val selected = sido!!
                    val rows = bySido[selected].orEmpty()
                    val total = rows.sumOf { it.count }
                    val sigungus = rows.filter { it.sigungu != null }.sortedBy { it.sigungu }
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        item(key = "all") {
                            RegionRowItem(
                                label = stringResource(R.string.region_all_format, Region.shortSido(selected)),
                                count = total,
                                selected = current == Region(selected),
                                onClick = { onSelect(Region(selected)) },
                            )
                        }
                        items(sigungus, key = { it.sigungu!! }) { row ->
                            RegionRowItem(
                                label = row.sigungu!!,
                                count = row.count,
                                selected = current == Region(selected, row.sigungu),
                                onClick = { onSelect(Region(selected, row.sigungu)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRowItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatCount(count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatCount(count: Int): String = stringResource(R.string.region_count_format, "%,d".format(count))
