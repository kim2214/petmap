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
 * 본문(body*)은 모두 Normal — bodyLarge 는 상세 정보·설정 항목 등 주요 읽기 텍스트에
 * 쓰이므로 Light 를 쓰면 다크 배경의 한글 획이 얇아져 저시력 사용자에게 불리하다.
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
    bodyLarge = base.bodyLarge.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
    bodyMedium = base.bodyMedium.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
    bodySmall = base.bodySmall.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
    labelLarge = base.labelLarge.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
    labelSmall = base.labelSmall.copy(fontFamily = NanumSquareRound, fontWeight = FontWeight.Normal),
)
