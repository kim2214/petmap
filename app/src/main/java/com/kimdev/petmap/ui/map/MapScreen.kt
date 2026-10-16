package com.kimdev.petmap.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import com.kimdev.petmap.ui.theme.LocalIsDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Place
import com.kimdev.petmap.ui.components.RegionSheet
import com.kimdev.petmap.R
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.domain.model.GeoClusterCell
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.util.distanceMeters
import com.kimdev.petmap.core.util.openDirections
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.PetFilterChips
import com.kimdev.petmap.ui.components.PetWeightDialog
import androidx.compose.foundation.horizontalScroll
import com.kimdev.petmap.ui.components.PlaceCard
import com.kimdev.petmap.ui.components.RecentSearchList
import com.kimdev.petmap.ui.components.PlacePreviewSheet
import com.kimdev.petmap.ui.components.SearchTextField
import com.kimdev.petmap.ui.components.color
import com.kimdev.petmap.ui.components.icon
import com.kimdev.petmap.ui.components.softColor
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import com.kimdev.petmap.domain.model.PlaceCategory
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.CameraPositionState
import com.naver.maps.map.compose.DisposableMapEffect
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.LocationTrackingMode
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapType
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.compose.rememberUpdatedMarkerState
import com.naver.maps.map.NaverMap as NaverMapSdk
import com.naver.maps.map.overlay.OverlayImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.foundation.layout.ColumnScope
import com.kimdev.petmap.ui.components.EmptyState
import com.kimdev.petmap.ui.common.rememberLocationPermission
import com.kimdev.petmap.ui.common.rememberLocationSource

@OptIn(
    ExperimentalNaverMapApi::class,
    ExperimentalMaterial3Api::class,
    FlowPreview::class,
)
@Composable
fun MapScreen(
    onPlaceClick: (String) -> Unit,
    reselects: Flow<Unit> = emptyFlow(),
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()

    // 마커 탭 시 미리보기할 장소 / 더 못 쪼개는 클러스터를 탭했을 때 펼쳐 보여줄 장소 목록.
    // ViewModel 이 들고 있어 회전·글자 크기 변경으로 화면이 재생성돼도 시트가 유지된다.
    val previewPlace by viewModel.previewPlace.collectAsStateWithLifecycle()
    val clusterList by viewModel.clusterList.collectAsStateWithLifecycle()
    // 검색창 포커스 여부 (최근 검색어 표시용)
    var searchFocused by remember { mutableStateOf(false) }
    // 검색바 하단(윈도우 px). 지도 contentPadding 을 하드코딩 대신 실측으로 잡는다.
    var searchBarBottomPx by remember { mutableStateOf(0f) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(
            LatLng(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG),
            Constants.DEFAULT_ZOOM,
        )
    }

    // 위치 권한 상태머신(요청/설정 안내 공용) + 네이버 위치 소스(파란 점)
    val locationPermission = rememberLocationPermission(
        settingsMessage = stringResource(R.string.location_message_map),
    )
    // 라이브러리 기본 rememberFusedLocationSource 는 FINE+COARSE 둘 다 있어야 권한 있음으로 보고,
    // 아니면 Follow 를 켤 때마다 시스템 권한 요청을 다시 띄운다. 권한 상태머신과 같은 기준(하나라도 허용)을 쓴다.
    val locationSource = rememberLocationSource(locationPermission)
    // rememberSaveable: 탭 전환·회전 시 NoFollow 로 초기화되면 아래 효과가 Follow 를 재지정해
    // 보고 있던 지역에서 내 위치로 카메라가 튀어 버린다.
    var trackingMode by rememberSaveable { mutableStateOf(LocationTrackingMode.NoFollow) }
    // 지도 유형 (기본/위성/지형). 공원·여행지 탐색 시 실제 지형 확인용.
    var mapType by rememberSaveable { mutableStateOf(MapType.Basic) }
    var showMapTypeMenu by remember { mutableStateOf(false) }
    // '내 아이 가능' 칩을 체중 미등록 상태에서 눌렀을 때의 등록 다이얼로그
    var showPetWeightDialog by rememberSaveable { mutableStateOf(false) }
    // 지역 선택 시트(시/도 → 시/군/구). 선택하면 카메라가 그 범위로 이동한다
    var showRegionSheet by rememberSaveable { mutableStateOf(false) }
    // 권한이 "이미 있음"이 아니라 "처음 확인/방금 허용"일 때만 Follow 를 켜기 위한 플래그
    var followInitialized by rememberSaveable { mutableStateOf(false) }
    val granted = locationPermission.granted
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // 시스템 설정이 아니라 앱에 적용된 테마(설정에서 강제 가능)에 맞춰 지도 야간 모드를 켠다.
    val isDarkTheme = LocalIsDarkTheme.current

    // 카메라를 부드럽게 이동. 화면 스코프에서 실행해 호출부(효과/콜백)가 끝나도 애니메이션이 끊기지 않는다.
    fun moveCameraTo(lat: Double, lng: Double, zoom: Double, animation: CameraAnimation = CameraAnimation.Fly) {
        scope.launch {
            cameraPositionState.animate(CameraUpdate.scrollAndZoomTo(LatLng(lat, lng), zoom), animation)
        }
    }

    // 지도 첫 진입 시 위치 권한이 없으면 1회 자동 요청 (첫 접근에 현재 위치를 보여주기 위함)
    LaunchedEffect(Unit) { locationPermission.requestOnFirstEntry() }

    LaunchedEffect(granted) {
        if (granted) {
            // Follow 전환은 최초 1회만 (탭 전환·회전마다 보던 지역에서 내 위치로 튀지 않게).
            // "지도에서 보기"로 push 된 지도는 장소를 비추는 게 목적이라 Follow 를 켜지 않는다
            // (켜면 위치 소스가 카메라를 내 위치로 끌고 가 포커스 이동을 덮어쓴다).
            if (!followInitialized) {
                followInitialized = true
                if (!viewModel.hasFocusTarget) trackingMode = LocationTrackingMode.Follow
            }
            // 카메라 1회 이동은 ViewModel 이 관리한다 — 성공 전엔 재호출이 재시도가 되고
            // (위치 못 잡은 콜드스타트), 성공 후엔 no-op 이라 카메라가 다시 튀지 않는다.
            viewModel.moveToCurrentLocationOnce()
        } else {
            trackingMode = LocationTrackingMode.NoFollow
        }
    }

    // 일회성 이벤트는 한 곳에서 순서대로 처리한다. 상태+consume 방식은 consume 이 키를 바꿔
    // LaunchedEffect 를 취소해 스낵바가 사라지는 문제가 있었다.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MapEvent.MoveToLocation -> {
                    moveCameraTo(event.location.lat, event.location.lng, INITIAL_LOCATION_ZOOM)
                    viewModel.researchHere(event.location.lat, event.location.lng, INITIAL_LOCATION_ZOOM)
                }
                is MapEvent.FocusPlace -> {
                    // 조회는 ViewModel 이 이 장소 기준으로 이미 시작했다 — 여기선 카메라·미리보기만
                    moveCameraTo(event.place.lat, event.place.lng, MapViewModel.FOCUS_ZOOM)
                    viewModel.showPreview(event.place)
                }
                is MapEvent.ExpandCluster -> viewModel.showClusterList(event.places)
                is MapEvent.FitRegion -> scope.launch {
                    val b = event.bounds
                    val update = if (b.isPoint) {
                        CameraUpdate.scrollAndZoomTo(LatLng(b.centerLat, b.centerLng), MapViewModel.FOCUS_ZOOM)
                    } else {
                        CameraUpdate.fitBounds(
                            LatLngBounds(LatLng(b.minLat, b.minLng), LatLng(b.maxLat, b.maxLng)),
                            FIT_BOUNDS_PADDING_PX,
                        )
                    }
                    trackingMode = LocationTrackingMode.NoFollow
                    cameraPositionState.animate(update, CameraAnimation.Fly)
                    // 도착한 뷰포트로 곧바로 재조회 — 같은 줌 버킷 안의 이동은 자동 재조회되지 않기 때문
                    val pos = cameraPositionState.position
                    viewModel.researchHere(pos.target.latitude, pos.target.longitude, pos.zoom)
                }
                is MapEvent.RegionEmpty -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.map_region_empty_format, event.region.shortLabel))
                }
                is MapEvent.FitPlaces -> {
                    val bounds = LatLngBounds.Builder().apply {
                        event.places.forEach { include(LatLng(it.lat, it.lng)) }
                    }.build()
                    scope.launch {
                        // 결과가 한 곳이면 fitBounds 가 최대 줌으로 파고든다 → 포커스 줌으로 제한
                        val update = if (event.places.size == 1) {
                            CameraUpdate.scrollAndZoomTo(bounds.center, MapViewModel.FOCUS_ZOOM)
                        } else {
                            CameraUpdate.fitBounds(bounds, FIT_BOUNDS_PADDING_PX)
                        }
                        cameraPositionState.animate(update, CameraAnimation.Fly)
                    }
                }
                // 스낵바는 해제될 때까지 suspend 한다. 이 수집기에서 직접 기다리면 4~10초 동안 뒤의
                // 카메라 이동·클러스터 펼침 이벤트가 큐에 갇힌다 → 별도 코루틴으로 띄운다.
                MapEvent.NoResults -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.map_no_results))
                }
                MapEvent.LoadFailed -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.map_load_failed),
                        actionLabel = resources.getString(R.string.action_retry),
                        // actionLabel 이 있으면 기본이 Indefinite → 시간 제한을 둔다
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val pos = cameraPositionState.position
                        viewModel.researchHere(pos.target.latitude, pos.target.longitude, pos.zoom)
                    }
                }
            }
        }
    }

    // 하단 탭 "지도" 재선택 → 내 위치로 재센터링(내 위치 FAB 와 동일한 Follow 전환)
    LaunchedEffect(granted) {
        reselects.collect {
            if (granted) trackingMode = LocationTrackingMode.Follow
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


    // 카테고리별 단일 장소 마커 (컬러 핀 + 흰 아이콘). 최초 1회만 생성.
    val categoryMarkers = rememberCategoryMarkers()
    // 클러스터 아이콘 (라벨·배율별 프로세스 전역 캐시 — 탭 전환으로 지도가 dispose 돼도 유지)
    val clusterIcon = rememberClusterIconProvider()

    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            locationSource = locationSource,
            properties = MapProperties(
                locationTrackingMode = if (granted) trackingMode else LocationTrackingMode.None,
                // 야간 모드는 기본 지도에만 의미가 있다(위성/지형은 SDK 가 지원 안 함)
                isNightModeEnabled = isDarkTheme && mapType == MapType.Basic,
                mapType = mapType,
            ),
            uiSettings = MapUiSettings(isLocationButtonEnabled = false),
            // 지도가 상태바까지 풀블리드라 SDK 컨트롤(나침반 등)이 검색바 뒤에 깔린다.
            // 검색바 하단을 실측해 내린다 — 높이를 하드코딩하면 글자 크기 배율(시스템·앱 설정)에
            // 따라 검색바가 커질 때 다시 가려진다. 지도는 풀블리드라 윈도우 좌표 = 지도 좌표.
            contentPadding = PaddingValues(
                top = with(LocalDensity.current) { searchBarBottomPx.toDp() },
            ),
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
            // 별도 컴포저블로 분리: MapUiState 의 다른 필드(검색어 타이핑, favoriteIds 등)가
            // 바뀔 때마다 모든 마커가 재조합되는 것을 막는다. clusters 인스턴스가 같으면 스킵된다.
            MapMarkers(
                clusters = state.clusters,
                categoryMarkers = categoryMarkers,
                clusterIcon = clusterIcon,
                cameraPositionState = cameraPositionState,
                onSingleClick = { viewModel.showPreview(it) },
                onExpandCluster = { viewModel.showClusterList(it) },
                onExpandAggregated = viewModel::expandAggregatedCell,
                onZoomTo = { lat, lng, zoom -> moveCameraTo(lat, lng, zoom, CameraAnimation.Easing) },
            )
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
                // 키보드 "검색" 키: 자동완성 대신 검색 결과 전체를 마커로 뿌린다 (+ 최근 검색어 기록)
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (state.searchQuery.isNotBlank()) {
                        viewModel.recordRecentSearch(state.searchQuery)
                        viewModel.submitSearch(state.searchQuery)
                    }
                    focusManager.clearFocus()
                }),
                onFocusChanged = { searchFocused = it },
                modifier = Modifier.onGloballyPositioned {
                    searchBarBottomPx = it.boundsInWindow().bottom
                },
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
                                viewModel.showPreview(p)
                                viewModel.researchHere(p.lat, p.lng, 16.0)
                                if (q.isNotBlank()) viewModel.recordRecentSearch(q)
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                            }
                        }
                    }
                }

                // 검색했지만 결과 없음. 확정된 검색어(마커 표시 중)는 결과가 지도에 있으니 제외 —
                // 그 경우 자동완성 목록이 비어 있어도 아래 칩 + pill 을 보여준다.
                state.searchQuery.isNotBlank() && state.searchMarkers?.query != state.searchQuery.trim() -> Surface(
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

                // 최근 검색어 (검색창 포커스 + 입력 비어있음). 지도 위에 카드로 띄운다.
                searchFocused && state.searchQuery.isBlank() && state.recentSearches.isNotEmpty() ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    ) {
                        RecentSearchList(
                            recents = state.recentSearches,
                            onPick = { viewModel.onSearchQueryChange(it) },
                            onRemove = { viewModel.removeRecentSearch(it) },
                            onClearAll = { viewModel.clearRecentSearches() },
                            modifier = Modifier.heightIn(max = 360.dp),
                            horizontalPadding = 16.dp,
                            itemVerticalPadding = 10.dp,
                        )
                    }

                // 카테고리 다중 선택 칩 + 반려동물 동반 조건 칩 (목록과 동일한 필터)
                else -> Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        CategoryFilterRow(
                            selected = state.selectedCategories,
                            onToggle = viewModel::toggleCategory,
                            onClearAll = viewModel::clearCategories,
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 지역으로 이동(여행 계획용). 필터가 아니라 카메라 이동이라 선택 상태를 유지하지 않는다
                            FilterChip(
                                selected = false,
                                onClick = {
                                    showRegionSheet = true
                                },
                                label = { Text(stringResource(R.string.filter_region)) },
                                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                            )
                            FilterChip(
                                selected = state.openNowOnly,
                                onClick = viewModel::toggleOpenNow,
                                label = { Text(stringResource(R.string.label_open_now)) },
                                leadingIcon = if (state.openNowOnly) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                            PetFilterChips(
                                selected = state.petFilters,
                                onToggle = viewModel::togglePetFilter,
                                myPetKg = state.myPetKg,
                                onNeedWeight = { showPetWeightDialog = true },
                            )
                        }
                    }
                }
            }

            // '영업 중' 필터는 개별 마커에만 걸린다 — 저줌 집계(개수 클러스터)에선 확대하라고 알린다
            if (state.openNowOnly && state.isAggregated && state.searchResults.isEmpty() &&
                !(searchFocused && state.searchQuery.isBlank())
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.map_open_now_zoom_hint),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // 검색 결과 마커 모드 안내 pill: 결과 수 + 해제(✕). 자동완성 목록이 떠 있으면 숨긴다.
            val markers = state.searchMarkers
            if (markers != null && state.searchResults.isEmpty() && !(searchFocused && state.searchQuery.isBlank())) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp),
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Text(
                            stringResource(
                                if (markers.truncated) R.string.map_search_markers_more_format
                                else R.string.map_search_markers_format,
                                markers.query, markers.count,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        IconButton(
                            onClick = {
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_search))
                        }
                    }
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
                        // 지도 갱신의 유일한 수동 수단 — 스크린리더에 버튼으로 읽히게 한다
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.map_research_here),
                        ) {
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                SmallFloatingActionButton(
                    onClick = { showMapTypeMenu = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.map_type))
                }
                DropdownMenu(
                    expanded = showMapTypeMenu,
                    onDismissRequest = { showMapTypeMenu = false },
                ) {
                    listOf(
                        MapType.Basic to R.string.map_type_basic,
                        MapType.Hybrid to R.string.map_type_satellite, // 위성 + 도로/라벨
                        MapType.Terrain to R.string.map_type_terrain,
                    ).forEach { (type, labelRes) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes)) },
                            trailingIcon = if (mapType == type) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                mapType = type
                                showMapTypeMenu = false
                            },
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = {
                    if (granted) trackingMode = LocationTrackingMode.Follow
                    else locationPermission.request() // 시스템 요청 또는(영구 거부) 설정 안내
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_my_location))
            }
        }

        when {
            state.isSeeding -> SeedingOverlay()
            state.seedFailed -> SeedFailedOverlay(onRetry = viewModel::retrySeeding)
            state.isLoading -> {
                val loadingDesc = stringResource(R.string.map_loading)
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .semantics {
                            contentDescription = loadingDesc
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
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
        // 지도 조회 경로는 distanceMeters 를 채우지 않으므로 표시 직전에 계산한다(위치 없으면 생략)
        val placeWithDistance = userLocation?.let { loc ->
            place.copy(distanceMeters = distanceMeters(loc.lat, loc.lng, place.lat, place.lng))
        } ?: place
        ModalBottomSheet(
            onDismissRequest = { viewModel.showPreview(null) },
            sheetState = sheetState,
        ) {
            PlacePreviewSheet(
                place = placeWithDistance,
                isFavorite = place.id in state.favoriteIds,
                onToggleFavorite = { viewModel.toggleFavorite(place) },
                onDirections = { context.openDirections(place) },
                onDetail = {
                    val id = place.id
                    viewModel.showPreview(null)
                    onPlaceClick(id)
                },
            )
        }
    }

    // 겹쳐 있어 줌으로 못 펼치는 클러스터 → 장소 목록 바텀시트
    clusterList?.let { places ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.showClusterList(null) },
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
                            viewModel.showClusterList(null)
                            onPlaceClick(p.id)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(p) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showPetWeightDialog) {
        PetWeightDialog(
            current = state.myPetKg,
            onSave = { kg ->
                viewModel.setMyPetWeight(kg)
                showPetWeightDialog = false
            },
            onDismiss = { showPetWeightDialog = false },
        )
    }

    if (showRegionSheet) {
        RegionSheet(
            current = null,
            counts = state.regionCounts,
            isError = state.regionCountsError,
            onLoad = viewModel::loadRegionCounts,
            onSelect = { region ->
                showRegionSheet = false
                if (region != null) viewModel.selectRegion(region)
            },
            onDismiss = { showRegionSheet = false },
        )
    }
}

// 네이버 지도 최대 줌(21) 직전. 이 이상에선 줌인 대신 목록으로 펼친다.
private const val MAX_CLUSTER_ZOOM = 19.0
// 멤버들이 이 거리(m) 이내면 사실상 같은 좌표로 보고 목록으로 펼친다.
private const val CO_LOCATED_M = 25.0
// 첫 진입 시 현재 위치로 이동할 때의 줌(동네 단위로 주변 장소가 보이는 수준).
private const val INITIAL_LOCATION_ZOOM = 15.0
// 검색 결과 범위 맞춤 시 가장자리 마커가 검색바·FAB 에 가리지 않도록 두는 여백(px)
private const val FIT_BOUNDS_PADDING_PX = 160

/**
 * 지도 위 마커/클러스터 렌더링. [MapScreen] 본문에서 분리해, 지도와 무관한 상태 변경
 * (검색어 타이핑 등)이 마커 전체 재조합으로 번지지 않게 한다.
 * key(cluster.id): 재클러스터링으로 리스트 순서·개수가 바뀔 때 위치 기반 슬롯 재사용으로
 * 마커가 "제자리에서 다른 장소로 변신"하며 깜빡이는 것을 막는다.
 */
@OptIn(ExperimentalNaverMapApi::class)
@Composable
private fun MapMarkers(
    clusters: List<MapCluster>,
    categoryMarkers: Map<PlaceCategory, OverlayImage>,
    clusterIcon: (Int) -> OverlayImage,
    cameraPositionState: CameraPositionState,
    onSingleClick: (Place) -> Unit,
    onExpandCluster: (List<Place>) -> Unit,
    onExpandAggregated: (GeoClusterCell) -> Unit,
    onZoomTo: (lat: Double, lng: Double, zoom: Double) -> Unit,
) {
    clusters.forEach { cluster ->
        key(cluster.id) {
            val single = cluster.single
            if (single != null) {
                Marker(
                    state = rememberUpdatedMarkerState(LatLng(single.lat, single.lng)),
                    captionText = single.name,
                    // 밀집 지역에서 장소명 캡션이 서로 겹쳐 읽을 수 없는 것을 방지
                    isHideCollidedCaptions = true,
                    icon = categoryMarkers.getValue(single.category),
                    anchor = MarkerAnchor,
                    onClick = {
                        onSingleClick(single)
                        true
                    },
                )
            } else {
                Marker(
                    state = rememberUpdatedMarkerState(LatLng(cluster.lat, cluster.lng)),
                    icon = clusterIcon(cluster.count),
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        val z = cameraPositionState.position.zoom
                        // 더 줌인해도 안 쪼개지면(같은 좌표/최대 줌) 목록으로 펼친다.
                        val canExpand = cluster.members.size >= 2 &&
                            (z >= MAX_CLUSTER_ZOOM || cluster.spanMeters() < CO_LOCATED_M)
                        val cell = cluster.cell
                        when {
                            canExpand -> onExpandCluster(cluster.members)
                            // 저줌 집계 클러스터: 개수가 적으면 줌인 연타 대신 셀 범위를
                            // 재조회해 바로 목록으로 펼친다 (결과는 ExpandCluster 이벤트로 도착)
                            cell != null && cluster.count <= MapViewModel.AGGREGATE_EXPAND_MAX ->
                                onExpandAggregated(cell)
                            else ->
                                onZoomTo(cluster.lat, cluster.lng, (z + 2.0).coerceAtMost(MAX_CLUSTER_ZOOM))
                        }
                        true
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(place: Place, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            // 스크린리더가 행 전체를 하나의 버튼(이름+주소)으로 읽게 한다
            .clickable(role = Role.Button) { onClick() }
            .semantics(mergeDescendants = true) {}
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
    BlockingOverlay {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.map_seeding),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * 에셋 DB 준비 실패(저장 공간 부족·파일 손상) 전면 안내. 조용히 빈 지도를 보이면 "데이터가 없다"로
 * 오해되므로 원인 후보와 재시도 동선을 준다.
 */
@Composable
private fun SeedFailedOverlay(onRetry: () -> Unit) {
    BlockingOverlay {
        EmptyState(
            icon = Icons.Filled.CloudOff,
            title = stringResource(R.string.seed_failed_title),
            description = stringResource(R.string.seed_failed_desc),
            action = { Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } },
        )
    }
}

@Composable
private fun BlockingOverlay(content: @Composable ColumnScope.() -> Unit) {
    // Surface 만으로는 히트 테스트를 막지 못해 오버레이 뒤의 검색창·지도가 눌린다 → 입력을 삼킨다.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
