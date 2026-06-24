package com.example.petmap.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.petmap.core.common.Constants
import com.example.petmap.ui.components.CategoryFilterRow
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState

@OptIn(ExperimentalNaverMapApi::class)
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

    Box(modifier = Modifier.fillMaxSize()) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(),
        ) {
            state.visiblePlaces.forEach { place ->
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

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
