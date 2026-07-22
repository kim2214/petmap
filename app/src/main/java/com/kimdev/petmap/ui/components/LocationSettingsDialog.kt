package com.kimdev.petmap.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** 위치 권한이 영구 거부됐을 때 앱 설정으로 안내하는 다이얼로그 (지도/목록 공용) */
@Composable
fun LocationSettingsDialog(
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("위치 권한이 필요해요") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenSettings()
            }) { Text("설정 열기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}
