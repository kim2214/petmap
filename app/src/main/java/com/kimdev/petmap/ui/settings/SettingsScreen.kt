package com.kimdev.petmap.ui.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AdUnits
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.BuildConfig
import com.kimdev.petmap.R
import com.kimdev.petmap.core.ads.AdsConsent
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.util.openUrl
import com.kimdev.petmap.core.util.sendEmail
import com.kimdev.petmap.data.local.FontScale
import com.kimdev.petmap.data.local.ThemeMode

/** 테마 모드 표시 라벨 (data enum → UI 문자열). */
@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

/** 글자 크기 표시 라벨. */
@get:StringRes
private val FontScale.labelRes: Int
    get() = when (this) {
        FontScale.NORMAL -> R.string.font_scale_normal
        FontScale.LARGE -> R.string.font_scale_large
        FontScale.EXTRA_LARGE -> R.string.font_scale_extra_large
    }

private const val PRIVACY_POLICY_URL =
    "https://kim2214.github.io/privacy-policy.html"
private const val CONTACT_EMAIL = Constants.CONTACT_EMAIL

@Composable
fun SettingsScreen(
    onOpenLicenses: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    var showFontScaleDialog by remember { mutableStateOf(false) }
    // EEA 등에서만 true. 이 경우 동의를 다시 변경할 진입점 제공이 Google 정책상 필수다.
    val adPrivacyRequired by AdsConsent.privacyOptionsRequired.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
                .semantics { heading() },
        )

        SectionTitle(stringResource(R.string.settings_section_screen))
        SettingsCard {
            SettingRow(
                Icons.Filled.DarkMode,
                stringResource(R.string.settings_theme),
                subtitle = stringResource(themeMode.labelRes),
            ) { showThemeDialog = true }
            SettingRow(
                Icons.Filled.FormatSize,
                stringResource(R.string.settings_font_scale),
                subtitle = stringResource(fontScale.labelRes),
            ) { showFontScaleDialog = true }
            SettingRow(
                Icons.Filled.Replay,
                stringResource(R.string.settings_replay_onboarding),
                subtitle = stringResource(R.string.settings_replay_onboarding_desc),
                onClick = onReplayOnboarding,
            )
        }

        SectionTitle(stringResource(R.string.settings_section_terms))
        SettingsCard {
            SettingRow(Icons.Filled.Policy, stringResource(R.string.settings_privacy)) { context.openUrl(PRIVACY_POLICY_URL) }
            if (adPrivacyRequired) {
                SettingRow(
                    Icons.Filled.AdUnits,
                    stringResource(R.string.settings_ad_privacy),
                    subtitle = stringResource(R.string.settings_ad_privacy_desc),
                ) {
                    // UMP 폼은 Activity 위에 표시된다
                    (context as? Activity)?.let { AdsConsent.showPrivacyOptions(it) }
                }
            }
            SettingRow(Icons.AutoMirrored.Filled.Article, stringResource(R.string.settings_licenses), onClick = onOpenLicenses)
        }

        SectionTitle(stringResource(R.string.settings_section_contact))
        val emailSubject = stringResource(R.string.settings_email_subject)
        SettingsCard {
            SettingRow(Icons.Filled.Email, stringResource(R.string.settings_email), subtitle = CONTACT_EMAIL) {
                context.sendEmail(CONTACT_EMAIL, subject = emailSubject)
            }
        }

        SectionTitle(stringResource(R.string.settings_section_data_source))
        SettingsCard {
            SettingRow(
                Icons.Filled.Storage,
                stringResource(R.string.settings_data_source),
                subtitle = stringResource(R.string.settings_data_source_desc),
            )
        }

        SectionTitle(stringResource(R.string.settings_section_app_info))
        SettingsCard {
            SettingRow(
                Icons.Filled.Info,
                stringResource(R.string.settings_version),
                subtitle = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showThemeDialog) {
        RadioDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            current = themeMode,
            labelOf = { stringResource(it.labelRes) },
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showFontScaleDialog) {
        RadioDialog(
            title = stringResource(R.string.settings_font_scale),
            options = FontScale.entries,
            current = fontScale,
            labelOf = { stringResource(it.labelRes) },
            onSelect = {
                viewModel.setFontScale(it)
                showFontScaleDialog = false
            },
            onDismiss = { showFontScaleDialog = false },
        )
    }
}

/** 라디오 선택 다이얼로그 (테마·글자 크기 공용). 선택 즉시 적용되고 닫힌다. */
@Composable
private fun <T> RadioDialog(
    title: String,
    options: List<T>,
    current: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // selectable + null onClick: 행과 라디오가 별개 타깃으로 두 번 읽히지 않게
                // 하나의 라디오버튼 시맨틱으로 병합한다.
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == current,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Text(labelOf(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        // 선택 즉시 적용·닫힘이라 확인 버튼이 없다. 닫기는 바깥 탭/뒤로가기로 충분.
        confirmButton = {},
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** 섹션별 설정 묶음 카드. 목록의 PlaceCard 와 같은 흰 카드 + 20dp 라운드 시각 언어. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
