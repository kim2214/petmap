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
import androidx.compose.ui.unit.dp

private val LICENSES = listOf(
    "Jetpack Compose · AndroidX" to "Apache License 2.0",
    "Hilt (Dagger)" to "Apache License 2.0",
    "Retrofit · OkHttp" to "Apache License 2.0",
    "kotlinx.serialization" to "Apache License 2.0",
    "Room" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
    "Accompanist" to "Apache License 2.0",
    "naver-map-compose (fornewid)" to "Apache License 2.0",
    "NAVER Maps SDK" to "NAVER Cloud Platform 이용약관",
    "Google Mobile Ads (AdMob)" to "Android Software Development Kit License",
    "Firebase (Crashlytics · Analytics)" to "Apache License 2.0",
    "나눔스퀘어라운드 글꼴" to "네이버 나눔글꼴 라이선스 (무료 사용)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("오픈소스 라이선스") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
                "이 앱은 아래 오픈소스 및 제3자 구성요소를 사용합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            LICENSES.forEach { (name, license) ->
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
