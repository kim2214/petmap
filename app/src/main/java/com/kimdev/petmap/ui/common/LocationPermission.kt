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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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
    val granted: Boolean get() = permissions.allPermissionsGranted

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
