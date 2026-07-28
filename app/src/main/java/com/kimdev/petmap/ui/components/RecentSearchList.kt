package com.kimdev.petmap.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kimdev.petmap.R

/**
 * 최근 검색어 목록(헤더 + 항목별 삭제 + 전체 삭제).
 *
 * 목록 화면은 전체 영역에, 지도 화면은 검색바 아래 카드(Surface) 안에 넣어 쓴다.
 * 여백만 다르고 구조는 같아 파라미터로 흡수했다.
 */
@Composable
fun RecentSearchList(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    itemVerticalPadding: Dp = 12.dp,
) {
    LazyColumn(modifier = modifier) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = horizontalPadding, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.recent_search),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.clear_all)) }
            }
        }
        items(recents, key = { it }) { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(term) }
                    .padding(
                        start = horizontalPadding,
                        end = 8.dp,
                        top = itemVerticalPadding,
                        bottom = itemVerticalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    term,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemove(term) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
