package com.kimdev.petmap.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.kimdev.petmap.R

/** 구성요소 이름·라이선스 (표시 문구는 다른 화면과 같이 리소스에서) */
private data class LicenseItem(@StringRes val name: Int, @StringRes val license: Int)

private val LICENSES = listOf(
    LicenseItem(R.string.license_name_compose, R.string.license_apache2),
    LicenseItem(R.string.license_name_hilt, R.string.license_apache2),
    LicenseItem(R.string.license_name_room, R.string.license_apache2),
    LicenseItem(R.string.license_name_accompanist, R.string.license_apache2),
    LicenseItem(R.string.license_name_naver_map_compose, R.string.license_apache2),
    LicenseItem(R.string.license_name_naver_maps_sdk, R.string.license_naver_cloud_terms),
    LicenseItem(R.string.license_name_admob, R.string.license_android_sdk),
    LicenseItem(R.string.license_name_ump_review, R.string.license_android_sdk),
    LicenseItem(R.string.license_name_firebase, R.string.license_apache2),
    LicenseItem(R.string.license_name_nanum_font, R.string.license_nanum_font),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            LICENSES.forEach { item ->
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(stringResource(item.name), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(item.license),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
