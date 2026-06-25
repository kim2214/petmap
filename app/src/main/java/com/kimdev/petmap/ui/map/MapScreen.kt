package com.kimdev.petmap.ui.map

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.util.openAppSettings
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.ui.components.CategoryFilterRow
import com.kimdev.petmap.ui.components.PlaceCard
import com.kimdev.petmap.ui.components.PlacePreviewSheet
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

    LaunchedEffect(granted) {
        trackingMode = if (granted) LocationTrackingMode.Follow else LocationTrackingMode.NoFollow
    }

    // 카메라 위치/줌이 바뀔 때마다(드래그·줌·프로그램적 이동 포함) 영역 재조회 + 클러스터링.
    // isMoving 대신 position 을 직접 관찰해야 클러스터 탭으로 인한 프로그램적 줌도 반영된다.
    LaunchedEffect(state.isSeeding) {
        if (state.isSeeding) return@LaunchedEffect
        snapshotFlow { cameraPositionState.position }
            .debounce(180)
            .collect { pos ->
                viewModel.onCameraIdle(pos.target.latitude, pos.target.longitude, pos.zoom)
            }
    }

    // 클러스터 아이콘 캐시 (개수별)
    val iconCache = remember { mutableMapOf<Int, OverlayImage>() }
    fun clusterIcon(count: Int): OverlayImage =
        iconCache.getOrPut(count) { OverlayImage.fromBitmap(makeClusterBitmap(count)) }

    Box(modifier = Modifier.fillMaxSize()) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            locationSource = locationSource,
            properties = MapProperties(
                locationTrackingMode = if (granted) trackingMode else LocationTrackingMode.None,
            ),
            uiSettings = MapUiSettings(isLocationButtonEnabled = false),
        ) {
            state.clusters.forEach { cluster ->
                val single = cluster.single
                if (single != null) {
                    Marker(
                        state = MarkerState(position = LatLng(single.lat, single.lng)),
                        captionText = single.name,
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

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            CategoryFilterRow(
                selected = state.selectedCategory,
                onSelect = viewModel::selectCategory,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
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
