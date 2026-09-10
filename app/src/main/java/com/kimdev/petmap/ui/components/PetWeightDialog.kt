package com.kimdev.petmap.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kimdev.petmap.R
import com.kimdev.petmap.data.local.PetProfileStore
import java.util.Locale

/** 체중 표시용 포맷: 5.0 → "5", 5.5 → "5.5" */
fun formatPetWeight(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else String.format(Locale.US, "%.1f", kg)

/**
 * 내 반려견 체중 입력 다이얼로그. 설정 화면과 목록·지도의 "내 아이 가능" 칩(미등록 상태)이 함께 쓴다.
 * @param onClear 등록 해제(설정 화면에서만 노출). null 이면 버튼을 그리지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetWeightDialog(
    current: Double?,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(current?.let(::formatPetWeight) ?: "") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it in PetProfileStore.MIN_KG..PetProfileStore.MAX_KG }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pet_weight_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.pet_weight_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; showError = false },
                    placeholder = { Text(stringResource(R.string.pet_weight_hint)) },
                    suffix = { Text(stringResource(R.string.pet_weight_unit)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.pet_weight_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // 정확한 체중을 모를 때 쓰는 대표값. 소형 10kg·중형 25kg 기준의 가운데쯤이다.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.pet_weight_preset_small to "5",
                        R.string.pet_weight_preset_medium to "15",
                        R.string.pet_weight_preset_large to "30",
                    ).forEach { (labelRes, value) ->
                        SuggestionChip(
                            onClick = { text = value; showError = false },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (parsed != null) onSave(parsed) else showError = true }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (onClear != null && current != null) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
