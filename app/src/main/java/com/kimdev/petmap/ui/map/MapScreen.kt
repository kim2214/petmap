package com.kimdev.petmap.ui.map

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.R
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.util.openAppSettings
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.PlaceCard
import com.kimdev.petmap.ui.components.PlacePreviewSheet
import com.kimdev.petmap.ui.components.color
import com.kimdev.petmap.ui.components.icon
import com.kimdev.petmap.ui.components.softColor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.LocationTrackingMode
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.compose.rememberFusedLocationSource
import com.naver.maps.map.overlay.OverlayImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(
    ExperimentalNaverMapApi::class,
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class,
    FlowPreview::class,
)
@Composable
fun MapScreen(
    onPlaceClick: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 마커 탭 시 미리보기할 장소
    var previewPlace by remember { mutableStateOf<Place?>(null) }
    // 더 못 쪼개는 클러스터를 탭했을 때 펼쳐 보여줄 장소 목록
    var clusterList by remember { mutableStateOf<List<Place>?>(null) }
    // 검색창 포커스 여부 (최근 검색어 표시용)
    var searchFocused by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(
            LatLng(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG),
            Constants.DEFAULT_ZOOM,
        )
    }

    // 위치 권한 + 네이버 위치 소스(파란 점)
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )
    val locationSource = rememberFusedLocationSource()
    var trackingMode by remember { mutableStateOf(LocationTrackingMode.NoFollow) }
    val granted = locationPermissions.allPermissionsGranted
    var requestedLocationOnce by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(granted) {
        trackingMode = if (granted) LocationTrackingMode.Follow else LocationTrackingMode.NoFollow
    }

    // 카메라가 움직일 때마다 로컬 재클러스터링 + 데이터가 오래됐는지 판단("다시 검색" 노출).
    // 데이터 재조회는 자동으로 하지 않고 사용자가 버튼을 눌렀을 때만 수행한다(통신·DB 부담 ↓).
    LaunchedEffect(state.isSeeding) {
        if (state.isSeeding) return@LaunchedEffect
        snapshotFlow { cameraPositionState.position }
            .debounce(180)
            .collect { pos ->
                viewModel.onCameraMove(pos.target.latitude, pos.target.longitude, pos.zoom)
            }
    }

    // "지도에서 보기"로 들어온 포커스 대상 → 카메라 이동 + 미리보기 + 주변 재조회
    LaunchedEffect(state.focusTarget) {
        state.focusTarget?.let { target ->
            cameraPositionState.position = CameraPosition(LatLng(target.lat, target.lng), 16.0)
            previewPlace = target
            viewModel.researchHere(target.lat, target.lng, 16.0)
            viewModel.consumeFocus()
        }
    }

    // 클러스터 아이콘 캐시 (개수별). 숫자는 브랜드 폰트(나눔 ExtraBold)로 렌더링.
    val clusterTypeface = remember { ResourcesCompat.getFont(context, R.font.nanum_square_round_extrabold) }
    val iconCache = remember { mutableMapOf<Int, OverlayImage>() }
    fun clusterIcon(count: Int): OverlayImage =
        iconCache.getOrPut(count) { OverlayImage.fromBitmap(makeClusterBitmap(count, clusterTypeface)) }

    Box(modifier = Modifier.fillMaxSize()) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            locationSource = locationSource,
            properties = MapProperties(
                locationTrackingMode = if (granted) trackingMode else LocationTrackingMode.None,
                isNightModeEnabled = isSystemInDarkTheme(),
            ),
            uiSettings = MapUiSettings(isLocationButtonEnabled = false),
        ) {
            state.clusters.forEach { cluster ->
                val single = cluster.single
                if (single != null) {
                    Marker(
                        state = MarkerState(position = LatLng(single.lat, single.lng)),
                        captionText = single.name,
                        iconTintColor = single.category.color,
                        onClick = {
                            previewPlace = single
                            true
                        },
                    )
                } else {
                    Marker(
                        state = MarkerState(position = LatLng(cluster.lat, cluster.lng)),
                        icon = clusterIcon(cluster.count),
                        anchor = Offset(0.5f, 0.5f),
                        onClick = {
                            val z = cameraPositionState.position.zoom
                            // 더 줌인해도 안 쪼개지면(같은 좌표/최대 줌) 목록으로 펼친다
                            if (z >= MAX_CLUSTER_ZOOM || cluster.spanMeters() < CO_LOCATED_M) {
                                clusterList = cluster.members
                            } else {
                                cameraPositionState.position = CameraPosition(
                                    LatLng(cluster.lat, cluster.lng),
                                    (z + 2.0).coerceAtMost(MAX_CLUSTER_ZOOM),
                                )
                            }
                            true
                        },
                    )
                }
            }
        }

        // 상단 오버레이: 검색바 + (자동완성 결과 | 카테고리 칩)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("장소·주소 검색") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.clearSearch()
                            focusManager.clearFocus()
                        }) { Icon(Icons.Filled.Close, contentDescription = "지우기") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { searchFocused = it.isFocused },
            )

            when {
                // 자동완성 결과
                state.searchResults.isNotEmpty() -> Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Column(modifier = Modifier.heightIn(max = 320.dp)) {
                        state.searchResults.forEach { p ->
                            SearchResultRow(p) {
                                val q = state.searchQuery
                                cameraPositionState.position =
                                    CameraPosition(LatLng(p.lat, p.lng), 16.0)
                                previewPlace = p
                                viewModel.researchHere(p.lat, p.lng, 16.0)
                                if (q.isNotBlank()) viewModel.recordRecentSearch(q)
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                            }
                        }
                    }
                }

                // 검색했지만 결과 없음
                state.searchQuery.isNotBlank() -> Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            "'${state.searchQuery}' 검색 결과가 없어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }

                // 최근 검색어 (검색창 포커스 + 입력 비어있음)
                searchFocused && state.searchQuery.isBlank() && state.recentSearches.isNotEmpty() ->
                    RecentSearchPanel(
                        recents = state.recentSearches,
                        onPick = { viewModel.onSearchQueryChange(it) },
                        onRemove = { viewModel.removeRecentSearch(it) },
                        onClearAll = { viewModel.clearRecentSearches() },
                    )

                // 카테고리 다중 선택 칩
                else -> Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    CategoryFilterRow(
                        selected = state.selectedCategories,
                        onToggle = viewModel::toggleCategory,
                        onClearAll = viewModel::clearCategories,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // "이 지역에서 다시 검색" — 지도를 옮겼을 때만 노출. 탭하면 현재 화면 기준 재조회.
        val showResearch = state.canResearch &&
            state.searchResults.isEmpty() &&
            !(searchFocused && state.searchQuery.isBlank())
        if (showResearch) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 132.dp)
                    .clickable {
                        val pos = cameraPositionState.position
                        viewModel.researchHere(pos.target.latitude, pos.target.longitude, pos.zoom)
                    },
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "이 지역에서 다시 검색",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                when {
                    granted -> trackingMode = LocationTrackingMode.Follow
                    locationPermissions.shouldShowRationale || !requestedLocationOnce -> {
                        requestedLocationOnce = true
                        locationPermissions.launchMultiplePermissionRequest()
                    }
                    else -> showLocationSettingsDialog = true // 영구 거부 → 설정 안내
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "내 위치")
        }

        when {
            state.isSeeding -> SeedingOverlay()
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }

    // 마커 미리보기 바텀시트
    previewPlace?.let { place ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { previewPlace = null },
            sheetState = sheetState,
        ) {
            PlacePreviewSheet(
                place = place,
                isFavorite = place.id in state.favoriteIds,
                onToggleFavorite = { viewModel.toggleFavorite(place) },
                onDetail = {
                    val id = place.id
                    previewPlace = null
                    onPlaceClick(id)
                },
            )
        }
    }

    // 겹쳐 있어 줌으로 못 펼치는 클러스터 → 장소 목록 바텀시트
    clusterList?.let { places ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { clusterList = null },
            sheetState = sheetState,
        ) {
            Text(
                text = "이 위치의 장소 ${places.size}곳",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                items(places, key = { it.id }) { p ->
                    PlaceCard(
                        place = p.copy(isFavorite = p.id in state.favoriteIds),
                        onClick = {
                            clusterList = null
                            onPlaceClick(p.id)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(p) },
                    )
                }
            }
        }
    }

    // 위치 권한 영구 거부 시 설정 안내
    if (showLocationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            title = { Text("위치 권한이 필요해요") },
            text = { Text("내 위치를 지도에 표시하려면 위치 권한이 필요합니다. 설정에서 권한을 허용해 주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationSettingsDialog = false
                    context.openAppSettings()
                }) { Text("설정 열기") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationSettingsDialog = false }) { Text("닫기") }
            },
        )
    }
}

// 네이버 지도 최대 줌(21) 직전. 이 이상에선 줌인 대신 목록으로 펼친다.
private const val MAX_CLUSTER_ZOOM = 19.0
// 멤버들이 이 거리(m) 이내면 사실상 같은 좌표로 보고 목록으로 펼친다.
private const val CO_LOCATED_M = 25.0

@Composable
private fun RecentSearchPanel(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Column(modifier = Modifier.heightIn(max = 360.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "최근 검색",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearAll) { Text("전체 삭제") }
            }
            recents.forEach { term ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(term) }
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(place: Place, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(place.category.softColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                place.category.icon,
                contentDescription = null,
                tint = place.category.color,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                place.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                place.roadAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeedingOverlay() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                "장소 데이터를 준비하고 있어요…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
