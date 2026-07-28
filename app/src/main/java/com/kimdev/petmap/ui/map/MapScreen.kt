package com.kimdev.petmap.ui.map

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.R
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.util.openAppSettings
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.core.util.openNaverDirections
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.LocationSettingsDialog
import com.kimdev.petmap.ui.components.PlaceCard
import com.kimdev.petmap.ui.components.PlacePreviewSheet
import com.kimdev.petmap.ui.components.SearchTextField
import com.kimdev.petmap.ui.components.color
import com.kimdev.petmap.ui.components.icon
import com.kimdev.petmap.ui.components.softColor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.DisposableMapEffect
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.LocationTrackingMode
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.compose.rememberFusedLocationSource
import com.naver.maps.map.NaverMap as NaverMapSdk
import com.naver.maps.map.overlay.OverlayImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

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
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // 시스템 설정이 아니라 앱에 적용된 테마(설정에서 강제 가능)에 맞춰 지도 야간 모드를 켠다.
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 카메라를 부드럽게 이동. 화면 스코프에서 실행해 호출부(효과/콜백)가 끝나도 애니메이션이 끊기지 않는다.
    fun moveCameraTo(lat: Double, lng: Double, zoom: Double, animation: CameraAnimation = CameraAnimation.Fly) {
        scope.launch {
            cameraPositionState.animate(CameraUpdate.scrollAndZoomTo(LatLng(lat, lng), zoom), animation)
        }
    }

    // 지도 첫 진입 시 위치 권한이 없으면 1회 자동 요청 (첫 접근에 현재 위치를 보여주기 위함)
    LaunchedEffect(Unit) {
        if (!granted && !requestedLocationOnce) {
            requestedLocationOnce = true
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(granted) {
        trackingMode = if (granted) LocationTrackingMode.Follow else LocationTrackingMode.NoFollow
        // 권한이 있으면(첫 진입 또는 방금 허용) 현재 위치로 카메라를 1회 이동한다.
        if (granted) viewModel.moveToCurrentLocationOnce()
    }

    // 초기 현재 위치 이동 요청 → 카메라 이동 + 그 지점 기준 주변 재조회
    LaunchedEffect(state.pendingLocationMove) {
        state.pendingLocationMove?.let { loc ->
            moveCameraTo(loc.lat, loc.lng, INITIAL_LOCATION_ZOOM)
            viewModel.researchHere(loc.lat, loc.lng, INITIAL_LOCATION_ZOOM)
            viewModel.consumeLocationMove()
        }
    }

    // 검색 오버레이(자동완성·최근 검색)가 열려 있으면 뒤로가기로 앱 종료 대신 오버레이를 닫는다
    BackHandler(enabled = searchFocused || state.searchQuery.isNotBlank()) {
        viewModel.clearSearch()
        focusManager.clearFocus()
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
            moveCameraTo(target.lat, target.lng, 16.0)
            previewPlace = target
            viewModel.researchHere(target.lat, target.lng, 16.0)
            viewModel.consumeFocus()
        }
    }

    // 조회 결과 0건 안내 (다시 검색 후 마커만 사라지면 무반응으로 보이므로)
    // consume 을 먼저 호출하면 key 가 false 로 바뀌며 이 코루틴이 취소되어 스낵바가 뜨자마자 사라진다.
    LaunchedEffect(state.showNoResults) {
        if (state.showNoResults) {
            snackbarHostState.showSnackbar(resources.getString(R.string.map_no_results))
            viewModel.consumeNoResults()
        }
    }

    // 카테고리별 단일 장소 마커 (컬러 핀 + 흰 아이콘). 최초 1회만 생성.
    val categoryMarkers = rememberCategoryMarkers()
    // 클러스터 아이콘 캐시 (개수별). 숫자는 브랜드 폰트(나눔 ExtraBold)로 렌더링.
    // 개수 값이 제각각이라 무제한 증가를 막기 위해 LRU(접근순)로 상한을 둔다.
    val clusterTypeface = remember { ResourcesCompat.getFont(context, R.font.nanum_square_round_extrabold) }
    val screenDensity = LocalDensity.current.density
    val iconCache = remember(screenDensity) {
        object : LinkedHashMap<Int, OverlayImage>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, OverlayImage>): Boolean =
                size > CLUSTER_ICON_CACHE_MAX
        }
    }
    fun clusterIcon(count: Int): OverlayImage = iconCache.getOrPut(count) {
        OverlayImage.fromBitmap(makeClusterBitmap(count, clusterTypeface, screenDensity))
    }

    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            locationSource = locationSource,
            properties = MapProperties(
                locationTrackingMode = if (granted) trackingMode else LocationTrackingMode.None,
                isNightModeEnabled = isDarkTheme,
            ),
            uiSettings = MapUiSettings(isLocationButtonEnabled = false),
        ) {
            // 사용자가 지도를 팬하면 SDK 가 Follow → NoFollow 로 스스로 전환하지만 Compose 상태는
            // Follow 로 남는다. 그 상태에서 "내 위치" FAB 가 Follow 를 재지정해도 값이 같아 SDK 에
            // 반영되지 않으므로, 지도의 실제 모드를 상태로 되읽어 동기화한다.
            DisposableMapEffect(Unit) { map ->
                val listener = NaverMapSdk.OnOptionChangeListener {
                    trackingMode = LocationTrackingMode.entries
                        .first { it.value == map.locationTrackingMode }
                }
                map.addOnOptionChangeListener(listener)
                onDispose { map.removeOnOptionChangeListener(listener) }
            }
            state.clusters.forEach { cluster ->
                val single = cluster.single
                if (single != null) {
                    Marker(
                        state = MarkerState(position = LatLng(single.lat, single.lng)),
                        captionText = single.name,
                        icon = categoryMarkers.getValue(single.category),
                        anchor = MarkerAnchor,
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
                            // 더 줌인해도 안 쪼개지면(같은 좌표/최대 줌) 목록으로 펼친다.
                            // 저줌 집계 클러스터(members 비어 있음)는 펼칠 수 없으므로 항상 줌인한다.
                            val canExpand = cluster.members.size >= 2 &&
                                (z >= MAX_CLUSTER_ZOOM || cluster.spanMeters() < CO_LOCATED_M)
                            if (canExpand) {
                                clusterList = cluster.members
                            } else {
                                moveCameraTo(
                                    cluster.lat,
                                    cluster.lng,
                                    (z + 2.0).coerceAtMost(MAX_CLUSTER_ZOOM),
                                    CameraAnimation.Easing,
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
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            SearchTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                onClear = {
                    viewModel.clearSearch()
                    focusManager.clearFocus()
                },
                onFocusChanged = { searchFocused = it },
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
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        state.searchResults.forEach { p ->
                            SearchResultRow(p) {
                                val q = state.searchQuery
                                moveCameraTo(p.lat, p.lng, 16.0)
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
                            stringResource(R.string.map_search_no_result_format, state.searchQuery),
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

            // "이 지역에서 다시 검색" — 지도를 옮겼을 때만 노출. 탭하면 현재 화면 기준 재조회.
            // 검색바/칩과 같은 Column 에 두어 글꼴 크기가 커져도 겹치지 않는다.
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
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
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
                            stringResource(R.string.map_research_here),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
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
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_my_location))
        }

        when {
            state.isSeeding -> SeedingOverlay()
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 내 위치 FAB(BottomEnd, 56dp + 16dp 여백)을 가리지 않도록 그 위에 띄운다
                .padding(bottom = 88.dp),
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
                onDirections = { context.openNaverDirections(place) },
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
                text = stringResource(R.string.map_cluster_places_format, places.size),
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    // 위치 권한 영구 거부 시 설정 안내
    if (showLocationSettingsDialog) {
        LocationSettingsDialog(
            message = stringResource(R.string.location_message_map),
            onOpenSettings = { context.openAppSettings() },
            onDismiss = { showLocationSettingsDialog = false },
        )
    }
}

// 클러스터 아이콘 비트맵 캐시 상한(개수 값별). 화면에 동시에 보이는 클러스터 수를 크게 상회한다.
private const val CLUSTER_ICON_CACHE_MAX = 64
// 네이버 지도 최대 줌(21) 직전. 이 이상에선 줌인 대신 목록으로 펼친다.
private const val MAX_CLUSTER_ZOOM = 19.0
// 멤버들이 이 거리(m) 이내면 사실상 같은 좌표로 보고 목록으로 펼친다.
private const val CO_LOCATED_M = 25.0
// 첫 진입 시 현재 위치로 이동할 때의 줌(동네 단위로 주변 장소가 보이는 수준).
private const val INITIAL_LOCATION_ZOOM = 15.0

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
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp),
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
                            contentDescription = stringResource(R.string.action_delete),
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
                stringResource(R.string.map_seeding),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
