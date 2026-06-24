package com.kimdev.petmap.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.core.util.dialPhone
import com.kimdev.petmap.core.util.openNaverDirections
import com.kimdev.petmap.core.util.openUrl
import com.kimdev.petmap.core.util.sharePlace
import com.kimdev.petmap.domain.model.Place

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val place = state.place

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "상세") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (place != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (place.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "즐겨찾기",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(padding))
            place == null -> Text("장소를 찾을 수 없습니다", Modifier.padding(padding).padding(16.dp))
            else -> DetailContent(place, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailContent(place: Place, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 액션 버튼: 전화 / 길찾기 / 공유
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            place.phone?.let { phone ->
                FilledTonalButton(onClick = { context.dialPhone(phone) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Phone, contentDescription = null)
                    Text("전화", modifier = Modifier.padding(start = 4.dp))
                }
            }
            FilledTonalButton(onClick = { context.openNaverDirections(place) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Directions, contentDescription = null)
                Text("길찾기", modifier = Modifier.padding(start = 4.dp))
            }
            FilledTonalButton(onClick = { context.sharePlace(place) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text("공유", modifier = Modifier.padding(start = 4.dp))
            }
        }

        InfoRow("카테고리", place.category.label)
        InfoRow("도로명주소", place.roadAddress)
        place.phone?.let { InfoRow("전화", it) }
        place.operatingTime?.let { InfoRow("운영시간", it) }
        place.closedDays?.let { InfoRow("휴무일", it) }
        place.homepage?.let { homepage ->
            Column(modifier = Modifier.padding(vertical = 4.dp).clickable { context.openUrl(homepage) }) {
                Text("홈페이지", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    homepage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("반려동물 정보", style = MaterialTheme.typography.titleMedium)
        place.petInfo.allowedPetSize?.let { InfoRow("입장 가능 크기", it) }
        place.petInfo.restriction?.let { InfoRow("제한사항", it) }
        InfoRow("실내 동반", if (place.petInfo.indoorAllowed) "가능" else "불가")
        InfoRow("실외 동반", if (place.petInfo.outdoorAllowed) "가능" else "불가")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
