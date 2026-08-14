package com.kimdev.petmap.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import com.kimdev.petmap.ui.theme.LocalIsDarkTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.compose.rememberUpdatedMarkerState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.core.util.copyToClipboard
import com.kimdev.petmap.core.util.dialPhone
import com.kimdev.petmap.core.util.sendEmail
import com.kimdev.petmap.core.util.openNaverDirections
import com.kimdev.petmap.core.util.openUrl
import com.kimdev.petmap.core.util.sharePlace
import com.kimdev.petmap.domain.model.Place
import com.kimdev.petmap.domain.model.PlaceCategory
import com.kimdev.petmap.R
import com.kimdev.petmap.domain.util.OpeningHours
import com.kimdev.petmap.ui.common.rememberIsOpenNow
import com.kimdev.petmap.ui.components.FavoriteIconButton
import com.kimdev.petmap.ui.components.color
import com.kimdev.petmap.ui.components.icon
import com.kimdev.petmap.ui.components.labelRes
import com.kimdev.petmap.ui.components.softColor
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val place = state.place

    // 히어로 헤더와 같은 색에서 시작해 스크롤하면 표면색으로 전환되는 고정 앱바.
    // 장소 이름은 헤더가 앱바 아래로 밀려 들어간 만큼(overlappedFraction)만 나타난다.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val appBarColor = place?.category?.headerGradient()?.first ?: MaterialTheme.colorScheme.background

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        place?.name ?: stringResource(R.string.detail_title_fallback),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (place == null) 1f else scrollBehavior.state.overlappedFraction
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (place != null) {
                        FavoriteIconButton(
                            isFavorite = place.isFavorite,
                            onClick = viewModel::toggleFavorite,
                            uncheckedTint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarColor,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            place == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.detail_not_found)) }
            else -> DetailContent(
                place = place,
                onShowOnMap = {
                    viewModel.showOnMap()
                    onShowOnMap()
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    place: Place,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Header(place)

        Column(modifier = Modifier.padding(16.dp)) {
            QuickActions(place, onShowOnMap)

            SectionTitle(stringResource(R.string.section_location))
            LocationMiniMap(place, onClick = onShowOnMap)

            SectionTitle(stringResource(R.string.section_info))
            val accent = place.category.color
            val addressLabel = stringResource(R.string.clipboard_label_address)
            // 주소는 탭으로 복사 (숙소 예약·메시지 공유 등에 붙여넣기)
            InfoRow(
                Icons.Filled.Place,
                place.roadAddress,
                tint = accent,
                onClick = { context.copyToClipboard(addressLabel, place.roadAddress) },
                onClickLabel = stringResource(R.string.row_action_copy_address),
            )
            place.operatingTime?.let { ot ->
                val schedule = remember(ot, place.closedDays) {
                    OpeningHours.weeklySchedule(ot, place.closedDays)
                }
                // 요일별로 파싱되면 표로, 아니면(자유 서식) 원문 그대로
                if (schedule == null) {
                    InfoRow(Icons.Filled.Schedule, ot, tint = accent)
                } else {
                    OperatingHoursRows(schedule, tint = accent)
                }
            }
            place.closedDays?.let { InfoRow(Icons.Filled.CalendarMonth, stringResource(R.string.closed_days_format, it), tint = accent) }
            // 상단 전화 버튼과 별개로, 번호 행 자체도 탭하면 다이얼로 연결 (홈페이지 행과 동작 일관)
            place.phone?.let { phone ->
                InfoRow(
                    Icons.Filled.Phone,
                    phone,
                    tint = accent,
                    onClick = { context.dialPhone(phone) },
                    onClickLabel = stringResource(R.string.row_action_call),
                )
            }
            place.homepage?.let { hp ->
                InfoRow(
                    Icons.Filled.Language,
                    hp,
                    valueColor = MaterialTheme.colorScheme.primary,
                    underline = true,
                    tint = accent,
                    onClick = { context.openUrl(hp) },
                    onClickLabel = stringResource(R.string.row_action_open_homepage),
                )
            }

            SectionTitle(stringResource(R.string.section_pet_info))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                place.petInfo.allowedPetSize?.let { Pill(stringResource(R.string.pet_size_format, it), highlight = true) }
                Pill(
                    stringResource(if (place.petInfo.indoorAllowed) R.string.pet_indoor_allowed else R.string.pet_indoor_not_allowed),
                    highlight = place.petInfo.indoorAllowed,
                )
                Pill(
                    stringResource(if (place.petInfo.outdoorAllowed) R.string.pet_outdoor_allowed else R.string.pet_outdoor_not_allowed),
                    highlight = place.petInfo.outdoorAllowed,
                )
                place.petInfo.restriction?.let { Pill(it, highlight = false) }
            }

            // 공공데이터 특성상 오류가 불가피 → 사용자 제보로 보완하는 진입점.
            // 장소 정보를 본문에 프리필해 사용자가 이름/주소를 옮겨 적지 않아도 된다.
            val reportSubject = stringResource(R.string.report_email_subject_format, place.name)
            val reportBody = stringResource(
                R.string.report_email_body_format,
                place.name,
                place.id,
                place.roadAddress,
            )
            TextButton(
                onClick = { context.sendEmail(Constants.CONTACT_EMAIL, reportSubject, reportBody) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
            ) {
                Icon(
                    Icons.Filled.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.report_wrong_info),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * 히어로 헤더 그라데이션 (상단색, 하단색). 카테고리 색을 배경 위에 합성한 불투명 색이라
 * 같은 상단색을 쓰는 고정 앱바와 이어져 한 면처럼 보인다.
 */
@Composable
private fun PlaceCategory.headerGradient(): Pair<Color, Color> {
    val background = MaterialTheme.colorScheme.background
    val dark = LocalIsDarkTheme.current
    val top = color.copy(alpha = if (dark) 0.26f else 0.20f).compositeOver(background)
    val bottom = color.copy(alpha = if (dark) 0.10f else 0.06f).compositeOver(background)
    return top to bottom
}

@Composable
private fun Header(place: Place) {
    val open = rememberIsOpenNow(place.operatingTime, place.closedDays)
    val (top, bottom) = place.category.headerGradient()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                place.category.icon,
                contentDescription = null,
                tint = place.category.color,
                modifier = Modifier.size(40.dp),
            )
        }
        // 앱바 제목은 1줄 말줄임이라, 전체 이름은 여기서 보여준다
        Text(
            place.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 14.dp)
                .semantics { heading() },
        )
        Text(
            place.roadAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = place.category.color,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    stringResource(place.category.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (open != null) {
                Surface(
                    color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (open) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        stringResource(if (open) R.string.label_open_now else R.string.label_closed),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** 길찾기/전화/공유/지도 컴팩트 액션 행. 세로 버튼 4개를 대체한다. */
@Composable
private fun QuickActions(place: Place, onShowOnMap: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        QuickAction(Icons.Filled.Directions, stringResource(R.string.action_directions), primary = true) {
            context.openNaverDirections(place)
        }
        place.phone?.let { phone ->
            QuickAction(Icons.Filled.Phone, stringResource(R.string.action_phone)) { context.dialPhone(phone) }
        }
        QuickAction(Icons.Filled.Share, stringResource(R.string.action_share)) { context.sharePlace(place) }
        QuickAction(Icons.Filled.Map, stringResource(R.string.nav_map)) { onShowOnMap() }
    }
}

@Composable
private fun RowScope.QuickAction(
    icon: ImageVector,
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    // 원+라벨 전체를 하나의 터치 타깃으로 묶는다 (라벨만 탭해도 동작)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
private fun LocationMiniMap(place: Place, onClick: () -> Unit) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(LatLng(place.lat, place.lng), 15.0)
    }
    // 시스템 설정이 아니라 앱에 적용된 테마(설정에서 강제 가능)에 맞춰 야간 모드를 켠다.
    val isDarkTheme = LocalIsDarkTheme.current
    val mapContentDesc = stringResource(R.string.detail_map_content_desc_format, place.name)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = mapContentDesc },
    ) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isNightModeEnabled = isDarkTheme),
            // 미리보기용: 제스처/버튼 비활성 (네이버 로고는 약관상 노출 유지)
            uiSettings = MapUiSettings(
                isScrollGesturesEnabled = false,
                isZoomGesturesEnabled = false,
                isTiltGesturesEnabled = false,
                isRotateGesturesEnabled = false,
                isStopGesturesEnabled = false,
                isZoomControlEnabled = false,
                isScaleBarEnabled = false,
                isLocationButtonEnabled = false,
            ),
        ) {
            Marker(
                // rememberUpdatedMarkerState: 즐겨찾기 토글로 place 인스턴스가 바뀔 때마다
                // MarkerState 를 새로 만들면 마커 오버레이가 재생성되며 깜빡인다.
                state = rememberUpdatedMarkerState(LatLng(place.lat, place.lng)),
                iconTintColor = place.category.color,
            )
        }
        // 제스처가 모두 꺼진 미리보기 지도 → 탭하면 본 지도에서 열리게 전체를 클릭 영역으로 덮는다
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClickLabel = stringResource(R.string.detail_show_on_map)) { onClick() },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    underline: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    // onClickLabel: TalkBack 이 "두 번 탭하여 주소 복사"처럼 동작을 읽어준다
                    Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel) { onClick() }
                } else {
                    Modifier
                }
            )
            // 탭 가능한 행(주소 복사·전화·홈페이지)의 접근성 최소 터치 타깃 확보
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            textDecoration = if (underline) TextDecoration.Underline else null,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * 요일별 영업시간. 접힌 상태에선 오늘 요일만, 탭하면 월~일 7행으로 펼친다.
 * 오늘 행은 굵게 강조, 정기휴무는 회색으로 표시한다.
 */
@Composable
private fun OperatingHoursRows(schedule: List<OpeningHours.DayHours>, tint: Color) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDateTime.now().dayOfWeek.value - 1 } // 0=월 .. 6=일
    val dayLabels = stringArrayResource(R.array.weekday_labels)
    val toggleLabel = stringResource(
        if (expanded) R.string.hours_collapse else R.string.hours_expand
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = toggleLabel) { expanded = !expanded }
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val visible = if (expanded) schedule else listOf(schedule[today])
            visible.forEach { day -> DayHoursLine(day, dayLabels[day.day], emphasized = day.day == today) }
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DayHoursLine(day: OpeningHours.DayHours, label: String, emphasized: Boolean) {
    val hoursText = when {
        day.isClosed -> stringResource(R.string.hours_closed_day)
        day.hours == null -> stringResource(R.string.hours_unknown)
        day.breaks.isEmpty() -> day.hours
        else -> day.hours + " · " + stringResource(R.string.hours_break_format, day.breaks.joinToString(", "))
    }
    val dimmed = day.isClosed || day.hours == null
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.Bold else null,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            hoursText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.Bold else null,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun Pill(text: String, highlight: Boolean) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
