package com.kimdev.petmap.ui.common

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.naver.maps.map.LocationSource
import com.naver.maps.map.location.FusedLocationSource
import com.kimdev.petmap.core.util.openAppSettings
import com.kimdev.petmap.ui.components.LocationSettingsDialog

/**
 * 위치 권한 요청 상태머신 (지도·목록 공용).
 *
 * 두 화면이 각자 들고 있던 동일한 분기 — "허용됨 / 설명 가능하거나 아직 안 물었으면 시스템 요청 /
 * 영구 거부면 설정 안내 다이얼로그" — 와 그에 딸린 상태(한 번 물었는지, 다이얼로그 표시)를 한 곳에 모은다.
 * 다이얼로그는 [rememberLocationPermission] 이 직접 그리므로 화면은 [request] 만 호출하면 된다.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Stable
class LocationPermissionController internal constructor(
    private val permissions: MultiplePermissionsState,
    private val requestedOnce: () -> Boolean,
    private val markRequested: () -> Unit,
    private val showSettingsDialog: () -> Unit,
) {
    /**
     * 정밀(FINE)·대략(COARSE) 중 하나라도 허용되면 위치 기능을 쓸 수 있다.
     * Android 12+ 의 "대략적인 위치" 선택은 COARSE 만 허용하므로, 둘 다를 요구하면 그 사용자는
     * 파란 점도 못 보고 FAB 를 눌러도 요청만 반복된다. FusedLocation 은 COARSE 만으로도 동작한다.
     */
    val granted: Boolean get() = permissions.permissions.any { it.status.isGranted }

    /**
     * 사용자가 위치 기능을 명시적으로 눌렀을 때. 이미 허용됐으면 아무것도 하지 않는다
     * (호출 측이 granted 를 먼저 보고 기능을 실행한다).
     */
    fun request() {
        when {
            granted -> Unit
            permissions.shouldShowRationale || !requestedOnce() -> {
                markRequested()
                permissions.launchMultiplePermissionRequest()
            }
            else -> showSettingsDialog() // 영구 거부 → 설정 안내
        }
    }

    /**
     * 화면 첫 진입 시 1회 자동 요청. 거부 후 탭 전환·회전으로 재구성돼도 다시 묻지 않는다
     * (requestedOnce 가 rememberSaveable). 영구 거부 상태에서는 다이얼로그를 띄우지 않는다.
     */
    fun requestOnFirstEntry() {
        if (granted || requestedOnce()) return
        markRequested()
        permissions.launchMultiplePermissionRequest()
    }
}

/**
 * @param settingsMessage 영구 거부 시 설정 안내 다이얼로그 문구 (화면별 맥락)
 * @param onResult 시스템 권한 다이얼로그 결과 콜백 (granted 여부)
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberLocationPermission(
    settingsMessage: String,
    onResult: (granted: Boolean) -> Unit = {},
): LocationPermissionController {
    val context = LocalContext.current
    // rememberSaveable: 탭 전환·회전으로 화면이 재구성될 때마다 거부한 권한을 다시 묻지 않는다.
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    ) { results -> onResult(results.values.any { it }) }

    if (showSettingsDialog) {
        LocationSettingsDialog(
            message = settingsMessage,
            onOpenSettings = { context.openAppSettings() },
            onDismiss = { showSettingsDialog = false },
        )
    }

    return remember(permissions) {
        LocationPermissionController(
            permissions = permissions,
            requestedOnce = { requestedOnce },
            markRequested = { requestedOnce = true },
            showSettingsDialog = { showSettingsDialog = true },
        )
    }
}

/**
 * 네이버 지도 위치 소스(파란 점·Follow). 권한 판정을 [LocationPermissionController.granted] 와 같은
 * 기준(FINE 또는 COARSE 하나라도 허용)으로 맞춘 구현.
 *
 * 라이브러리의 rememberFusedLocationSource 는 allPermissionsGranted 를 요구해 COARSE 만 허용한
 * 사용자에게 Follow 진입마다 시스템 권한 요청을 다시 띄웠다. 권한 요청 자체도 컨트롤러로 위임해
 * 영구 거부 시 설정 안내 다이얼로그가 뜨게 한다.
 */
@Composable
fun rememberLocationSource(controller: LocationPermissionController): LocationSource {
    val context = LocalContext.current
    val source = remember(context, controller) {
        object : FusedLocationSource(context) {
            override fun hasPermissions(): Boolean = context.hasAnyLocationPermission()
            override fun onPermissionRequest() = controller.request()
        }
    }
    // 권한이 (방금) 허용되면 대기 중이던 활성화를 이어간다
    val granted = controller.granted
    LaunchedEffect(source, granted) {
        if (granted) source.onPermissionGranted()
    }
    return source
}

private fun Context.hasAnyLocationPermission(): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
