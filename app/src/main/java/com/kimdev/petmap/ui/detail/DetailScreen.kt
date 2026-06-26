package com.kimdev.petmap.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.core.util.dialPhone
import com.kimdev.petmap.core.util.openNaverDirections
import com.kimdev.petmap.core.util.openUrl
import com.kimdev.petmap.core.util.sharePlace
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.util.OpeningHours
import com.kimdev.petmap.ui.components.color
import com.kimdev.petmap.ui.components.icon
import com.kimdev.petmap.ui.components.softColor
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val place = state.place

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "상세", maxLines = 1) },
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
                                contentDescription = if (place.isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
                                tint = if (place.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            place == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("장소를 찾을 수 없습니다") }
            else -> DetailContent(
                place = place,
                onShowOnMap = {
                    viewModel.showOnMap()
                    onShowOnMap()
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    place: Place,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Header(place)

        Column(modifier = Modifier.padding(16.dp)) {
            // 액션: 길찾기(주) + 전화/공유(보조)
            Button(
                onClick = { context.openNaverDirections(place) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Directions, contentDescription = null)
                Text("길찾기", modifier = Modifier.padding(start = 6.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                place.phone?.let { phone ->
                    FilledTonalButton(onClick = { context.dialPhone(phone) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Phone, contentDescription = null)
                        Text("전화", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                FilledTonalButton(onClick = { context.sharePlace(place) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("공유", modifier = Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(
                onClick = onShowOnMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Text("지도에서 보기", modifier = Modifier.padding(start = 6.dp))
            }

            SectionTitle("위치")
            LocationMiniMap(place)

            SectionTitle("정보")
            val accent = place.category.color
            InfoRow(Icons.Filled.Place, place.roadAddress, tint = accent)
            place.operatingTime?.let { InfoRow(Icons.Filled.Schedule, it, tint = accent) }
            place.closedDays?.let { InfoRow(Icons.Filled.CalendarMonth, "휴무 $it", tint = accent) }
            place.phone?.let { InfoRow(Icons.Filled.Phone, it, tint = accent) }
            place.homepage?.let { hp ->
                InfoRow(
                    Icons.Filled.Language,
                    hp,
                    valueColor = MaterialTheme.colorScheme.primary,
                    underline = true,
                    tint = accent,
                    onClick = { context.openUrl(hp) },
                )
            }

            SectionTitle("반려동물 동반 정보")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                place.petInfo.allowedPetSize?.let { Pill("🐾 $it", highlight = true) }
                Pill(if (place.petInfo.indoorAllowed) "실내 가능" else "실내 불가", highlight = place.petInfo.indoorAllowed)
                Pill(if (place.petInfo.outdoorAllowed) "실외 가능" else "실외 불가", highlight = place.petInfo.outdoorAllowed)
                place.petInfo.restriction?.let { Pill(it, highlight = false) }
            }
        }
    }
}

@Composable
private fun Header(place: Place) {
    val open = OpeningHours.isOpenNow(place.operatingTime, place.closedDays, LocalDateTime.now())
    Surface(
        color = place.category.softColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    place.category.icon,
                    contentDescription = place.category.label,
                    tint = place.category.color,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                place.category.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (open != null) {
                Surface(
                    color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (open) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        if (open) "지금 영업중" else "영업종료",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
private fun LocationMiniMap(place: Place) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(LatLng(place.lat, place.lng), 15.0)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = "${place.name} 위치 지도" },
    ) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isNightModeEnabled = isSystemInDarkTheme()),
            // 미리보기용: 제스처/버튼 비활성 (네이버 로고는 약관상 노출 유지)
            uiSettings = MapUiSettings(
                isScrollGesturesEnabled = false,
                isZoomGesturesEnabled = false,
                isTiltGesturesEnabled = false,
                isRotateGesturesEnabled = false,
                isStopGesturesEnabled = false,
                isZoomControlEnabled = false,
                isScaleBarEnabled = false,
                isLocationButtonEnabled = false,
            ),
        ) {
            Marker(
                state = MarkerState(position = LatLng(place.lat, place.lng)),
                iconTintColor = place.category.color,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    underline: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            textDecoration = if (underline) TextDecoration.Underline else null,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun Pill(text: String, highlight: Boolean) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
