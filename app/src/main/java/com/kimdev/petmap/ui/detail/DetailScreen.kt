package com.kimdev.petmap.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.compose.rememberUpdatedMarkerState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.core.util.copyToClipboard
import com.kimdev.petmap.core.util.dialPhone
import com.kimdev.petmap.core.util.openNaverDirections
import com.kimdev.petmap.core.util.openUrl
import com.kimdev.petmap.core.util.sharePlace
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.util.OpeningHours
import com.kimdev.petmap.ui.components.color
import com.kimdev.petmap.ui.components.icon
import com.kimdev.petmap.ui.components.labelRes
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
                title = { Text(place?.name ?: stringResource(R.string.detail_title_fallback), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (place != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (place.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(if (place.isFavorite) R.string.favorite_remove else R.string.favorite_add),
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
            place == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.detail_not_found)) }
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
                Text(stringResource(R.string.action_directions), modifier = Modifier.padding(start = 6.dp))
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
                        Text(stringResource(R.string.action_phone), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                FilledTonalButton(onClick = { context.sharePlace(place) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(stringResource(R.string.action_share), modifier = Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(
                onClick = onShowOnMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Text(stringResource(R.string.detail_show_on_map), modifier = Modifier.padding(start = 6.dp))
            }

            SectionTitle(stringResource(R.string.section_location))
            LocationMiniMap(place, onClick = onShowOnMap)

            SectionTitle(stringResource(R.string.section_info))
            val accent = place.category.color
            val addressLabel = stringResource(R.string.clipboard_label_address)
            // 주소는 탭으로 복사 (숙소 예약·메시지 공유 등에 붙여넣기)
            InfoRow(
                Icons.Filled.Place,
                place.roadAddress,
                tint = accent,
                onClick = { context.copyToClipboard(addressLabel, place.roadAddress) },
            )
            place.operatingTime?.let { InfoRow(Icons.Filled.Schedule, it, tint = accent) }
            place.closedDays?.let { InfoRow(Icons.Filled.CalendarMonth, stringResource(R.string.closed_days_format, it), tint = accent) }
            // 상단 전화 버튼과 별개로, 번호 행 자체도 탭하면 다이얼로 연결 (홈페이지 행과 동작 일관)
            place.phone?.let { phone ->
                InfoRow(Icons.Filled.Phone, phone, tint = accent, onClick = { context.dialPhone(phone) })
            }
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

            SectionTitle(stringResource(R.string.section_pet_info))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                place.petInfo.allowedPetSize?.let { Pill(stringResource(R.string.pet_size_format, it), highlight = true) }
                Pill(
                    stringResource(if (place.petInfo.indoorAllowed) R.string.pet_indoor_allowed else R.string.pet_indoor_not_allowed),
                    highlight = place.petInfo.indoorAllowed,
                )
                Pill(
                    stringResource(if (place.petInfo.outdoorAllowed) R.string.pet_outdoor_allowed else R.string.pet_outdoor_not_allowed),
                    highlight = place.petInfo.outdoorAllowed,
                )
                place.petInfo.restriction?.let { Pill(it, highlight = false) }
            }
        }
    }
}

@Composable
private fun Header(place: Place) {
    // 재구성마다 정규식 파싱을 반복하지 않도록 영업 여부를 캐시(초 단위 정확도는 불필요).
    val open = remember(place.operatingTime, place.closedDays) {
        OpeningHours.isOpenNow(place.operatingTime, place.closedDays, LocalDateTime.now())
    }
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
                    contentDescription = stringResource(place.category.labelRes),
                    tint = place.category.color,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                stringResource(place.category.labelRes),
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
                        stringResource(if (open) R.string.label_open_now else R.string.label_closed),
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
private fun LocationMiniMap(place: Place, onClick: () -> Unit) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(LatLng(place.lat, place.lng), 15.0)
    }
    // 시스템 설정이 아니라 앱에 적용된 테마(설정에서 강제 가능)에 맞춰 야간 모드를 켠다.
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mapContentDesc = stringResource(R.string.detail_map_content_desc_format, place.name)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = mapContentDesc },
    ) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isNightModeEnabled = isDarkTheme),
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
                // rememberUpdatedMarkerState: 즐겨찾기 토글로 place 인스턴스가 바뀔 때마다
                // MarkerState 를 새로 만들면 마커 오버레이가 재생성되며 깜빡인다.
                state = rememberUpdatedMarkerState(LatLng(place.lat, place.lng)),
                iconTintColor = place.category.color,
            )
        }
        // 제스처가 모두 꺼진 미리보기 지도 → 탭하면 본 지도에서 열리게 전체를 클릭 영역으로 덮는다
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClickLabel = stringResource(R.string.detail_show_on_map)) { onClick() },
        )
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
            // 탭 가능한 행(주소 복사·전화·홈페이지)의 접근성 최소 터치 타깃 확보
            .heightIn(min = 48.dp)
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
