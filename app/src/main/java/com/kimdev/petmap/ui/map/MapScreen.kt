package com.kimdev.petmap.ui.map

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.ui.components.CategoryFilterRow
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

@OptIn(ExperimentalNaverMapApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    onPlaceClick: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

    // 권한이 막 허용되면 현재 위치로 따라가기 시작
    LaunchedEffect(granted) {
        trackingMode = if (granted) LocationTrackingMode.Follow else LocationTrackingMode.NoFollow
    }

    // 카메라가 멈출 때마다 보이는 영역을 다시 조회
    LaunchedEffect(cameraPositionState.isMoving, state.isSeeding) {
        if (!cameraPositionState.isMoving && !state.isSeeding) {
            val pos = cameraPositionState.position
            viewModel.onCameraIdle(
                centerLat = pos.target.latitude,
                centerLng = pos.target.longitude,
                radiusKm = radiusForZoom(pos.zoom),
            )
        }
    }

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
            state.places.forEach { place ->
                Marker(
                    state = MarkerState(position = LatLng(place.lat, place.lng)),
                    captionText = place.name,
                    onClick = {
                        onPlaceClick(place.id)
                        true
                    },
                )
            }
        }

        CategoryFilterRow(
            selected = state.selectedCategory,
            onSelect = viewModel::selectCategory,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )

        // 내 위치 버튼: 권한 없으면 요청, 있으면 현재 위치로 따라가기
        FloatingActionButton(
            onClick = {
                if (granted) trackingMode = LocationTrackingMode.Follow
                else locationPermissions.launchMultiplePermissionRequest()
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

/** 줌 레벨에 따른 조회 반경(km) 근사 */
private fun radiusForZoom(zoom: Double): Double = when {
    zoom >= 15.0 -> 1.5
    zoom >= 13.0 -> 4.0
    zoom >= 11.0 -> 12.0
    zoom >= 9.0 -> 40.0
    else -> 120.0
}
