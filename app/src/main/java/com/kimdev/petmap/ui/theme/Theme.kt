package com.kimdev.petmap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = TealContainer,
    background = Cream,
    onBackground = OnLight,
    surface = CardSurface,
    onSurface = OnLight,
    surfaceVariant = WarmVariant,
    onSurfaceVariant = OnWarmVariant,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF003914),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenContainer,
    secondary = SageDark,
    onSecondary = Color(0xFF24341F),
    secondaryContainer = Color(0xFF374B33),
    onSecondaryContainer = SageContainer,
    tertiary = TealDark,
    onTertiary = Color(0xFF00363A),
    tertiaryContainer = Color(0xFF1E4D4F),
    onTertiaryContainer = TealContainer,
    background = NightBg,
    onBackground = Color(0xFFE3E3DB),
    surface = NightSurface,
    onSurface = Color(0xFFE3E3DB),
    surfaceVariant = NightVariant,
    onSurfaceVariant = OnNightVariant,
)

/**
 * 브랜드 그린/크림 테마. 디바이스 다이내믹 컬러 대신 브랜드 색을 항상 적용한다.
 */
@Composable
fun PetMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
    ) {
        // 스타일을 지정하지 않은 Text 도 나눔스퀘어라운드를 쓰도록 전역 기본값 제공
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = NanumSquareRound),
            content = content,
        )
    }
}
