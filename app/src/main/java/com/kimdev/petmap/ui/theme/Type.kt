package com.kimdev.petmap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kimdev.petmap.R

/** 나눔스퀘어라운드 (둥근 친근한 톤). 보유 굵기: Light/Regular/Bold/ExtraBold */
val NanumSquareRound = FontFamily(
    Font(R.font.nanum_square_round_light, FontWeight.Light),
    Font(R.font.nanum_square_round_regular, FontWeight.Normal),
    Font(R.font.nanum_square_round_regular, FontWeight.Medium),     // 500 → Regular 대체
    Font(R.font.nanum_square_round_bold, FontWeight.SemiBold),      // 600 → Bold 대체
    Font(R.font.nanum_square_round_bold, FontWeight.Bold),
    Font(R.font.nanum_square_round_extrabold, FontWeight.ExtraBold),
)

private val base = Typography()

/**
 * 굵기 대비 강조: 제목/헤드라인 = ExtraBold, 기능 라벨 = Regular/Bold.
 * 본문은 Normal 을 쓴다 — Light 는 bodySmall(12sp) 같은 소형 텍스트에서 대비가 부족해
 * 저시력 사용자에게 읽기 어렵다. 큰 본문만 Light 로 톤을 살린다.
 */
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    displayMedium = base.displayMedium.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    displaySmall = base.displaySmall.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    titleSmall = base.titleSmall.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.ExtraBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Light),
    bodyMedium = base.bodyMedium.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
    bodySmall = base.bodySmall.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
    labelLarge = base.labelLarge.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
    labelSmall = base.labelSmall.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
)
