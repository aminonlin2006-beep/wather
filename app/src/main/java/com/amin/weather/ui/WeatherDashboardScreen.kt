package com.amin.weather.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.amin.weather.core.AlertSettings
import com.amin.weather.core.AlertTier
import com.amin.weather.core.ForecastColumn
import com.amin.weather.core.ForecastDashboard
import com.amin.weather.core.ForecastRange
import com.amin.weather.core.ForecastRow
import com.amin.weather.core.JalaliCalendar
import com.amin.weather.core.PatternSummary
import com.amin.weather.core.PerimeterNetworkSummary
import com.amin.weather.core.PerimeterStationInfo
import com.amin.weather.core.RadarNowcastInfo
import com.amin.weather.core.SpatialTriangulationResult
import com.amin.weather.core.StationTelemetryInfo
import com.amin.weather.core.TriangulationVertex
import com.amin.weather.core.VerdictInfo
import com.amin.weather.ui.theme.AmberStorm
import com.amin.weather.ui.theme.CoralAlert
import com.amin.weather.ui.theme.EmeraldOk
import com.amin.weather.ui.theme.SkyAccent
import com.amin.weather.ui.theme.SlateBorder
import com.amin.weather.ui.theme.SlateCard
import com.amin.weather.ui.theme.SlateDeep
import com.amin.weather.ui.theme.SlateSurface
import com.amin.weather.ui.theme.TextSecondary

/**
 * محاسبه دمای حسی با توجه به دما، رطوبت نسبی و سرعت باد
 */
fun computeFeelsLike(tempC: Double?, humidity: Double?, windSpeedKmh: Double?): Double? {
    if (tempC == null) return null
    val t = tempC
    val rh = (humidity ?: 40.0).coerceIn(5.0, 100.0)
    val wsMs = ((windSpeedKmh ?: 8.0) / 3.6).coerceAtLeast(0.0)
    val e = (rh / 100.0) * 6.105 * Math.exp((17.27 * t) / (237.7 + t))
    val at = t + 0.33 * e - 0.70 * wsMs - 4.00
    return Math.round(at * 10.0) / 10.0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDashboardScreen(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    onSelectRange: (ForecastRange) -> Unit,
    onToggleColumn: (ForecastColumn) -> Unit,
    onUpdateAlertSettings: (AlertSettings) -> Unit,
    onTriggerAlertNotification: (Boolean) -> Unit,
    onClearFeedbackMessage: () -> Unit
) {
    val context = LocalContext.current
    var showNotificationSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onTriggerAlertNotification(isGranted)
    }

    val handleTriggerAlert: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                onTriggerAlertNotification(true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onTriggerAlertNotification(true)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = SlateDeep,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "آیکون رادار",
                                tint = SkyAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "هواشناسی هوشمند منطقه",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "پایش رادار چندتایلی + شبکه ایستگاه‌های همجوار",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        // دکمه اشتراک‌گذاری گزارش هوا
                        if (uiState.dashboard != null) {
                            IconButton(
                                onClick = {
                                    val d = uiState.dashboard
                                    val curTemp = d.stationTelemetry.currentTemp
                                    val feels = computeFeelsLike(curTemp, d.stationTelemetry.currentHumidity, d.stationTelemetry.currentWindSpeed)
                                    val shareText = buildString {
                                        append("🌤️ وضعیت هوای منطقه\n")
                                        append("📍 موقعیت: ${d.location}\n")
                                        append("⏱️ زمان به‌روزرسانی: ${JalaliCalendar.formatLong(d.generatedIran)}\n\n")
                                        if (curTemp != null) {
                                            append("🌡️ دمای فعلی: ${JalaliCalendar.formatNumber(curTemp)}°C")
                                            if (feels != null) append(" (دمای حسی: ${JalaliCalendar.formatNumber(feels)}°C)")
                                            append("\n")
                                        }
                                        append("⚠️ وضعیت جوی: ${d.verdict.text}\n")
                                        if (d.verdict.advisoryNote.isNotBlank()) {
                                            append("💡 توصیه: ${d.verdict.advisoryNote}\n")
                                        }
                                        if (d.radarNowcast.statusText.isNotBlank()) {
                                            append("📡 رادار: ${d.radarNowcast.statusText}\n")
                                        }
                                        append("\n📲 سامانه جامع هواشناسی هوشمند منطقه")
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val chooser = Intent.createChooser(sendIntent, "اشتراک‌گذاری وضعیت هوای منطقه")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)
                                },
                                modifier = Modifier.testTag("share_weather_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "اشتراک‌گذاری گزارش وضعیت هوا",
                                    tint = SkyAccent
                                )
                            }
                        }

                        // دکمه دسترسی سریع به تنظیمات اعلان
                        IconButton(
                            onClick = { showNotificationSheet = true },
                            modifier = Modifier.testTag("open_alerts_sheet_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.alertSettings.isEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "تنظیمات اعلان‌ها",
                                tint = if (uiState.alertSettings.isEnabled) AmberStorm else TextSecondary
                            )
                        }

                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                                color = SkyAccent
                            )
                        } else {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "به‌روزرسانی پیش‌بینی",
                                    tint = SkyAccent
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SlateSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isLoading && uiState.dashboard == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = SkyAccent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "در حال اسکن شبکه چندتایلی رادار و داده‌های میدانی...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    uiState.dashboard == null && uiState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "خطا",
                                tint = CoralAlert,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Text("تلاش مجدد")
                            }
                        }
                    }

                    uiState.dashboard != null -> {
                        DashboardContent(
                            dashboard = uiState.dashboard,
                            selectedRange = uiState.selectedRange,
                            isFromCache = uiState.isFromCache,
                            errorMessage = uiState.errorMessage,
                            onSelectRange = onSelectRange
                        )
                    }
                }
            }

            // مودال باتم شیت تنظیمات اعلان‌های هوشمند
            if (showNotificationSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showNotificationSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SlateCard
                ) {
                    WeatherAlertBottomSheetContent(
                        settings = uiState.alertSettings,
                        feedbackMessage = uiState.alertFeedbackMessage,
                        onUpdateSettings = onUpdateAlertSettings,
                        onTriggerAlert = handleTriggerAlert,
                        onClearFeedback = onClearFeedbackMessage,
                        onDismiss = { showNotificationSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    dashboard: ForecastDashboard,
    selectedRange: ForecastRange,
    isFromCache: Boolean,
    errorMessage: String?,
    onSelectRange: (ForecastRange) -> Unit
) {
    val hourlyRows = dashboard.ranges[ForecastRange.HOURLY].orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // نوار پایداری آفلاین
        if (isFromCache) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberStorm.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AmberStorm.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "آفلاین",
                            tint = AmberStorm,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📦 حالت آفلاین: نمایش آخرین داده‌های ذخیره‌شده (${JalaliCalendar.formatLong(dashboard.generatedIran)}). برای استعلام زنده دکمه 🔄 را بزنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // ۱. متادیتا و زمان آخرین واکشی داده‌ها
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "موقعیت: ${dashboard.location}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "به‌روزرسانی: ${JalaliCalendar.formatLong(dashboard.generatedIran)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SkyAccent
                )
            }
        }

        if (errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CoralAlert.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "⚠️ $errorMessage",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoralAlert,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // ۲. بنر سطح هشدار چهارگانه (Hero Alert Tier) همراه با دمای حسی و توصیه‌های کاربردی
        item {
            HeroAlertTierCard(
                verdict = dashboard.verdict,
                telemetry = dashboard.stationTelemetry
            )
        }

        // ۳. کارت رهگیری راداری چندتایلی ۶۰ کیلومتری
        item {
            RadarTrackingCard(radar = dashboard.radarNowcast)
        }

        // ۴. کارت تجمیع‌شده دیدبانی میدانی، شبکه ایستگاهی و جهت حرکت باران
        item {
            FieldNetworkUnifiedCard(
                triangulation = dashboard.triangulation,
                telemetry = dashboard.stationTelemetry,
                perimeter = dashboard.perimeterNetwork
            )
        }

        // ۵. نوار افقی و مدرن تایم‌لاین ساعتی
        item {
            HourlyTimelineSection(
                rows = hourlyRows,
                selectedRange = selectedRange,
                onSelectRange = onSelectRange
            )
        }

        // ۶. خلاصه وضعیت بارندگی و ناپایداری ۲۴ ساعت آینده
        item {
            ConvectiveSummaryCard(pattern = dashboard.pattern)
        }

        // ۷. پانویس
        item {
            Text(
                text = "⚡ سامانه پایش یکپارچه راداری و دیدبانی منطقه؛ داده‌های رادار چندتایلی و ایستگاه‌های همجوار برخط استعلام می‌شوند.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

/**
 * بنر سطح هشدار ۴گانه همراه با دمای حسی و راهنمای عملی برای کاربر
 */
@Composable
private fun HeroAlertTierCard(
    verdict: VerdictInfo,
    telemetry: StationTelemetryInfo
) {
    val tier = verdict.tier
    val tierColor = when (tier) {
        AlertTier.RED -> CoralAlert
        AlertTier.ORANGE -> Color(0xFFF97316)
        AlertTier.YELLOW -> AmberStorm
        AlertTier.GREEN -> EmeraldOk
    }
    val icon = when (tier) {
        AlertTier.RED -> Icons.Default.Warning
        AlertTier.ORANGE -> Icons.Default.Bolt
        AlertTier.YELLOW -> Icons.Default.Visibility
        AlertTier.GREEN -> Icons.Default.CheckCircle
    }

    val curTemp = telemetry.currentTemp
    val feelsLike = computeFeelsLike(curTemp, telemetry.currentHumidity, telemetry.currentWindSpeed)

    val impactExplanation = when (tier) {
        AlertTier.GREEN -> "✔️ برای کارهای روزمره، کشاورزی، شستشوی لباس و رانندگی آسمان کاملاً مساعد و آرام است و خطری وجود ندارد."
        AlertTier.YELLOW -> "🌂 در صورت خروج از منزل همراه داشتن چتر توصیه می‌شود؛ به دلیل رشد ابرهای محلی احتمال رگبار موقت یا وزش باد ناگهانی وجود دارد."
        AlertTier.ORANGE -> "🚗 توده بارشی در مسیر منطقه است؛ سرعت رانندگی را کم کرده، وسایل سبک را از پشت‌بام یا بالکن جمع‌آوری کرده و از نزدیکی به مسیل‌ها پرهیز کنید."
        AlertTier.RED -> "🚨 خطر تندباد شدید و رگبار سیل‌آسا: از پارک خودرو زیر درختان و پایه‌های برق خودداری فرمایید و در مکان‌های امن و سرپوشیده بمانید."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tierColor.copy(alpha = 0.14f)),
        border = BorderStroke(1.5.dp, tierColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ردیف عنوان وضعیت هشدار
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tier.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = tier.name,
                    tint = tierColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // نمایش دمای فعلی و دمایی که حس می‌شود
            if (curTemp != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = SkyAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "دمای فعلی: ${JalaliCalendar.formatNumber(curTemp)}°C",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (feelsLike != null) {
                        Text(
                            text = "دمایی که حس می‌کنید: ${JalaliCalendar.formatNumber(feelsLike)}°C",
                            style = MaterialTheme.typography.labelMedium,
                            color = SkyAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = verdict.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (verdict.advisoryNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "📢 ${verdict.advisoryNote}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // کادر توضیح تاثیر در زندگی کاربر
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface.copy(alpha = 0.85f))
                    .padding(10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = AmberStorm,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "💡 چه تاثیری برای شما دارد؟",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmberStorm
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = impactExplanation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * کارت رهگیری توده‌های راداری بر مبنای شبکه چندتایلی و ردیابی بردار حرکت
 */
@Composable
private fun RadarTrackingCard(radar: RadarNowcastInfo) {
    val isAlert = radar.echoDetected || radar.incomingRainLikely
    val accentColor = if (isAlert) Color(0xFFF97316) else SkyAccent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("radar_tracking_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "رادار",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📡 رهگیری راداری ابرهای باران‌زا (شعاع ۶۰km)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (radar.echoDetected) CoralAlert.copy(alpha = 0.2f) else EmeraldOk.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (radar.echoDetected) "اکوی فعال" else "شبکه ۴ تایل آرام",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (radar.echoDetected) CoralAlert else EmeraldOk
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = radar.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // فاصله
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "فاصله از مرکز", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = radar.estimatedDistanceKm?.let { "${JalaliCalendar.formatNumber(it)} km" } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (radar.echoDetected) CoralAlert else MaterialTheme.colorScheme.onSurface
                    )
                }

                // زمان ورود (ETA) و سرعت حرکت دینامیک
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "تخمین ورود", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = radar.estimatedEtaMinutes?.let { "حدود $it دقیقه" } ?: "بدون تهدید",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (radar.echoDetected) AmberStorm else EmeraldOk
                    )
                }

                // جهت استقرار و بردار پیشروی
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "جهت استقرار توده", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = radar.cellAzimuthDirection.take(18),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SkyAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // کادر توضیح تاثیر رادار در زندگی کاربر
            val radarAdvice = if (radar.echoDetected) {
                "⚠️ ابرهای بارانی در حال نزدیک شدن هستند (حدود ${radar.estimatedEtaMinutes ?: 25} دقیقه دیگر)؛ وسایل حساس به آب را جمع کرده و پیش از شروع رگبار برنامه‌ریزی کنید."
            } else {
                "✔️ تا شعاع ۶۰ کیلومتری هیچ ابر باران‌زای فعالی دیده نمی‌شود و تا حداقل یک ساعت آینده خطری از بابت باران ناگهانی برنامه‌های شما را تهدید نمی‌کند."
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface.copy(alpha = 0.85f))
                    .padding(10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = SkyAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "💡 چه تاثیری برای شما دارد؟",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SkyAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = radarAdvice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * کارت تجمیع‌شده دیدبانی میدانی، شبکه ایستگاهی و جهت حرکت باران
 * (کاهش شلوغی با ترکیب ۳ کارت مجزا به یک کامپوننت با تب‌های تعاملی)
 */
@Composable
private fun FieldNetworkUnifiedCard(
    triangulation: SpatialTriangulationResult,
    telemetry: StationTelemetryInfo,
    perimeter: PerimeterNetworkSummary
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: مثلث ۳ شهر، 1: ایستگاه مرکزی منطقه، 2: ۵ پایگاه پیرامونی

    val spp = triangulation.spatialPrecipProbability
    val sppColor = when {
        spp >= 70.0 -> CoralAlert
        spp >= 40.0 -> AmberStorm
        else -> EmeraldOk
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_network_unified_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, sppColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // سربرگ
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "مثلث‌بندی",
                        tint = SkyAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "🌐 شبکه ایستگاه‌های همجوار و جهت باران",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "پایش همزمان اندیمشک، شوش، گتوند و ایستگاه مرکزی",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sppColor.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "احتمال بارش: ${JalaliCalendar.formatNumber(spp)}٪",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sppColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = triangulation.convergenceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ۴ پارامتر کلیدی به زبان ساده
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    title = "مساحت پایش",
                    value = "${JalaliCalendar.formatNumber(triangulation.triangleAreaSqKm)} km²",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "شدت موج باران",
                    value = "${JalaliCalendar.formatNumber(triangulation.gradientMagnitude)} ٪/k",
                    valueColor = if (triangulation.gradientMagnitude >= 0.15) AmberStorm else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "جهت رانش ابر",
                    value = triangulation.gradientDirectionLabel.take(12),
                    valueColor = SkyAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "سرعت باد ناقل",
                    value = "${JalaliCalendar.formatNumber(triangulation.advectionFluxPerHour)} ٪/h",
                    valueColor = if (triangulation.advectionFluxPerHour > 5.0) AmberStorm else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // نوار انتخاب تب‌ها جهت مدیریت فضای بصری
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "شبکه مثلثی (۳ شهر)" to 0,
                    "ایستگاه مرکزی" to 1,
                    "پایگاه‌های پیرامونی" to 2
                ).forEach { (label, index) ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SkyAccent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) SkyAccent else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // محتوای تب انتخاب‌شده
            when (selectedTab) {
                0 -> {
                    // تب ۱: رئوس سه‌گانه مثلث هندسی
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        triangulation.vertices.forEach { vertex ->
                            TriangulationVertexRow(vertex)
                        }
                    }
                }
                1 -> {
                    // تب ۲: تله‌متری زنده ایستگاه مرکزی منطقه
                    StationTelemetryContent(telemetry)
                }
                2 -> {
                    // تب ۳: پایگاه‌های حلقه ۵ ضلعی پیرامونی
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        perimeter.stations.forEach { st ->
                            PerimeterStationRow(st)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // کادر توضیح تاثیر شبکه ایستگاهی در زندگی کاربر
            val networkAdvice = when {
                spp >= 60.0 -> "⚠️ ایستگاه‌های همجوار ورود توده بارانی فعال را تایید کرده‌اند؛ به زودی باران به محدوده شما خواهد رسید."
                spp >= 30.0 -> "🌦️ جریان باد در حال انتقال هوای مرطوب از شهرهای اطراف به سمت منطقه است؛ احتمال شکل‌گیری ابر و رگبارهای محلی وجود دارد."
                else -> "✔️ ایستگاه‌های همجوار پیرامون (شوش، اندیمشک، گتوند) وضعیت آرامی دارند و جبهه بارشی مزاحمی در مسیر نیست."
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface.copy(alpha = 0.85f))
                    .padding(10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = SkyAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "💡 چه تاثیری برای شما دارد؟",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SkyAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = networkAdvice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricMiniTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StationTelemetryContent(telemetry: StationTelemetryInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏛️ ${telemetry.stationName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = telemetry.currentWeatherDesc ?: "عادی",
                style = MaterialTheme.typography.labelSmall,
                color = SkyAccent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricMiniTile(
                title = "دما / رطوبت",
                value = "${JalaliCalendar.formatNumber(telemetry.currentTemp)}° | ${JalaliCalendar.formatNumber(telemetry.currentHumidity)}٪",
                modifier = Modifier.weight(1f)
            )
            MetricMiniTile(
                title = "نقطه شبنم / کسری",
                value = "${JalaliCalendar.formatNumber(telemetry.currentDewPoint)}° | ${JalaliCalendar.formatNumber(telemetry.dewPointDepression)}°",
                valueColor = if ((telemetry.dewPointDepression ?: 0.0) >= 12.0) AmberStorm else EmeraldOk,
                modifier = Modifier.weight(1f)
            )
            MetricMiniTile(
                title = "تندباد لحظه‌ای",
                value = "${JalaliCalendar.formatNumber(telemetry.currentWindGust)} km/h",
                valueColor = if ((telemetry.currentWindGust ?: 0.0) >= 30.0) AmberStorm else TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        if (telemetry.gustFrontRisk) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmberStorm.copy(alpha = 0.15f))
                    .padding(8.dp)
            ) {
                Text(
                    text = "⚠️ ریسک جبهه تندباد ناگهانی (خشکی لایه زیرین جو و وزش باد موضعی شدید)",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberStorm
                )
            }
        }
    }
}

@Composable
private fun TriangulationVertexRow(v: TriangulationVertex) {
    val bg = if (v.isRaining) CoralAlert.copy(alpha = 0.12f)
    else if (v.rainProbability >= 40.0) AmberStorm.copy(alpha = 0.12f)
    else SlateSurface.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (v.isRaining) CoralAlert else if (v.rainProbability >= 40.0) AmberStorm else EmeraldOk)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "${v.name} (${v.sector})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "فاصله: ${JalaliCalendar.formatNumber(v.distanceKm)} km | وزن باریسنتریک: ${JalaliCalendar.formatNumber(v.barycentricWeight * 100.0)}٪",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (v.isRaining) "بارش فعال (${JalaliCalendar.formatNumber(v.rainRateMmH)} mm/h) 🌧️"
                else "احتمال: ${JalaliCalendar.formatNumber(v.rainProbability)}٪",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (v.isRaining) CoralAlert else if (v.rainProbability >= 40.0) AmberStorm else MaterialTheme.colorScheme.onSurface
            )
            val info = buildList {
                v.temp?.let { add("${JalaliCalendar.formatNumber(it)}°") }
                v.windSpeedKmh?.let { add("باد ${it.toInt()}k") }
            }.joinToString(" | ")
            if (info.isNotBlank()) {
                Text(
                    text = info,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PerimeterStationRow(st: PerimeterStationInfo) {
    val bg = if (st.isRaining) CoralAlert.copy(alpha = 0.12f)
    else if ((st.windGust ?: 0.0) >= 35.0) AmberStorm.copy(alpha = 0.12f)
    else SlateSurface.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (st.isRaining) CoralAlert else if ((st.windGust ?: 0.0) >= 35.0) AmberStorm else EmeraldOk)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = st.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${st.sector} • ${st.distanceKm} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = st.statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (st.isRaining) CoralAlert else if ((st.windGust ?: 0.0) >= 35.0) AmberStorm else MaterialTheme.colorScheme.onSurface
            )
            val info = buildList {
                st.temp?.let { add("${JalaliCalendar.formatNumber(it)}°") }
                st.windGust?.takeIf { it >= 15.0 }?.let { add("باد ${it.toInt()}k") }
            }.joinToString(" | ")
            if (info.isNotBlank()) {
                Text(
                    text = info,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * بخش مدرن و تعاملی تایم‌لاین ساعتی (افقی)
 * جایگزین جدول شلوغ دسکتاپی با اسکرول افقی روی موبایل
 */
@Composable
private fun HourlyTimelineSection(
    rows: List<ForecastRow>,
    selectedRange: ForecastRange,
    onSelectRange: (ForecastRange) -> Unit
) {
    var selectedHourIndex by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hourly_timeline_section"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⏱️ پیش‌بینی ساعت به ساعت (۲۴ ساعت آینده)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "لمس هر ساعت برای جزئیات",
                    style = MaterialTheme.typography.labelSmall,
                    color = SkyAccent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // نوار افقی کارت‌های ساعت به ساعت
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                itemsIndexed(rows.take(24)) { index, row ->
                    val isSelected = index == selectedHourIndex
                    HourlyTimelineCard(
                        row = row,
                        isSelected = isSelected,
                        onClick = { selectedHourIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // کارت جزئیات تفصیلی ساعت انتخاب‌شده
            if (rows.isNotEmpty() && selectedHourIndex in rows.indices) {
                val activeRow = rows[selectedHourIndex]
                SelectedHourDetailCard(row = activeRow)
            }
        }
    }
}

/**
 * آیتم کوچک و زیبای هر ساعت در نوار افقی تایم‌لاین
 */
@Composable
private fun HourlyTimelineCard(
    row: ForecastRow,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val prob = row.precipProb ?: 0.0
    val probColor = when {
        prob >= 60.0 -> CoralAlert
        prob >= 35.0 -> AmberStorm
        else -> EmeraldOk
    }

    val borderColor = if (isSelected) SkyAccent else SlateBorder.copy(alpha = 0.4f)
    val cardBg = if (isSelected) SlateSurface else SlateDeep.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // زمان کوتاه (مثلا ۱۴:۳۰)
        Text(
            text = JalaliCalendar.formatShort(row.timeIran),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) SkyAccent else TextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // آیکون شرایط آب و هوا
        Icon(
            imageVector = when {
                row.rainTypeLabel.contains("صاعقه") || (row.convIndex ?: 0.0) >= 35.0 -> Icons.Default.Bolt
                row.rainTypeLabel.contains("رگبار") || (row.precipMax ?: 0.0) >= 1.0 -> Icons.Default.WaterDrop
                (row.precipMean ?: 0.0) >= 0.2 -> Icons.Default.WaterDrop
                (row.cloudCover ?: 0.0) >= 60.0 -> Icons.Default.Cloud
                else -> Icons.Default.WbSunny
            },
            contentDescription = null,
            tint = when {
                row.rainTypeLabel.contains("صاعقه") -> AmberStorm
                (row.precipMean ?: 0.0) >= 0.2 -> SkyAccent
                (row.cloudCover ?: 0.0) >= 60.0 -> Color(0xFF94A3B8)
                else -> Color(0xFFFBBF24)
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // دما
        Text(
            text = "${JalaliCalendar.formatNumber(row.tempMean)}°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // کپسول احتمال بارش
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(probColor.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${JalaliCalendar.formatNumber(prob)}٪",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = probColor
            )
        }

        if ((row.precipMean ?: 0.0) > 0.05) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${JalaliCalendar.formatNumber(row.precipMean)}mm",
                style = MaterialTheme.typography.labelSmall,
                color = SkyAccent
            )
        }
    }
}

/**
 * کارت مشخصات تکمیلی ساعت انتخاب‌شده از نوار تایم‌لاین همراه با راهنمای کاربردی
 */
@Composable
private fun SelectedHourDetailCard(row: ForecastRow) {
    val feelsLike = computeFeelsLike(row.tempMean, null, row.windSpeed)

    val hourAdvice = when {
        row.rainTypeLabel.contains("صاعقه") || (row.convIndex ?: 0.0) >= 30.0 ->
            "⚡ در این ساعت احتمال رعدوبرق یا وزش تندباد ناگهانی بالاست؛ فعالیت در فضای باز و زیر درختان را متوقف کنید."
        (row.precipMean ?: 0.0) >= 0.5 || row.rainTypeLabel.contains("باران") ->
            "🌧️ بارش باران و لغزندگی معابر؛ در صورت تردد چتر همراه داشته باشید و فاصله طولی خودرو را حفظ کنید."
        (row.windGust ?: 0.0) >= 30.0 ->
            "💨 وزش باد و تندباد موضعی؛ مراقب اشیاء سبک در فضای باز و بالکن باشید."
        else ->
            "☀️ هوای آرام و مساعد؛ شرایط برای رفت‌وآمد، کارهای بیرون و ورزش مناسب است."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ساعت ${JalaliCalendar.formatLong(row.timeIran)} — ${row.rainTypeLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (row.forecastSource.contains("رادار")) CoralAlert.copy(alpha = 0.2f)
                            else SkyAccent.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = row.forecastSource,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.forecastSource.contains("رادار")) CoralAlert else SkyAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    title = "بازه بارش",
                    value = row.precipRange ?: "۰",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "باد / تندباد",
                    value = "${JalaliCalendar.formatNumber(row.windSpeed)}k | ${JalaliCalendar.formatNumber(row.windGust)}k",
                    valueColor = if ((row.windGust ?: 0.0) >= 30.0) AmberStorm else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "جهت باد",
                    value = row.windDirLabel ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "دمای حسی",
                    value = feelsLike?.let { "${JalaliCalendar.formatNumber(it)}°" } ?: "—",
                    valueColor = SkyAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // باکس راهنمای اختصاصی برای این ساعت
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateDeep.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SkyAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = hourAdvice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * خلاصه وضعیت بارندگی و ناپایداری ۲۴ ساعت آینده به زبان ساده
 */
@Composable
private fun ConvectiveSummaryCard(pattern: PatternSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 خلاصه وضعیت بارندگی و ناپایداری ۲۴ ساعت آینده",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    title = "پتانسیل رگبار",
                    value = "${JalaliCalendar.formatNumber(pattern.peakConvectiveScore)} / ۱۰۰",
                    valueColor = if (pattern.peakConvectiveScore >= 35.0) AmberStorm else EmeraldOk,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "بارش محتمل",
                    value = "${JalaliCalendar.formatNumber(pattern.totalPrecip24hLikely)} mm",
                    valueColor = SkyAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "بیشترین بارش احتمالی",
                    value = "${JalaliCalendar.formatNumber(pattern.totalPrecip24hMax)} mm",
                    valueColor = if (pattern.totalPrecip24hMax >= 1.0) AmberStorm else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            if (pattern.precipSummaryText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface.copy(alpha = 0.85f))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = AmberStorm,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "💡 وضعیت کلی در یک نگاه:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberStorm
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pattern.precipSummaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * محتوای BottomSheet مدرن برای تنظیمات اعلان‌های هوشمند
 * (انتقال کامل از وسط صفحه اصلی برای خلوت‌سازی رابط کاربری)
 */
@Composable
private fun WeatherAlertBottomSheetContent(
    settings: AlertSettings,
    feedbackMessage: String?,
    onUpdateSettings: (AlertSettings) -> Unit,
    onTriggerAlert: () -> Unit,
    onClearFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("weather_alert_bottom_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = AmberStorm,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تنظیمات هشدارهای هوشمند نوتیفیکیشن",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Switch(
                checked = settings.isEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(isEnabled = it)) },
                modifier = Modifier.testTag("alert_master_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AmberStorm,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SlateSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "بازه زمانی مدنظر برای پایش خودکار:",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 3, 6).forEach { h ->
                val isSelected = settings.windowHours == h
                FilterChip(
                    selected = isSelected,
                    onClick = { onUpdateSettings(settings.copy(windowHours = h)) },
                    label = { Text("$h ساعت آینده") },
                    modifier = Modifier.weight(1f).testTag("alert_window_${h}h"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberStorm.copy(alpha = 0.22f),
                        selectedLabelColor = AmberStorm
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertOptionRow(
                icon = Icons.Default.Bolt,
                iconTint = AmberStorm,
                title = "رعد و برق و ناپایداری شدید",
                description = "هشدار صاعقه، رگبارهای همرفتی و توفان تندری",
                checked = settings.notifyThunderstorm,
                testTag = "alert_thunderstorm_switch",
                onCheckedChange = { onUpdateSettings(settings.copy(notifyThunderstorm = it)) }
            )
            AlertOptionRow(
                icon = Icons.Default.WaterDrop,
                iconTint = EmeraldOk,
                title = "بارش باران تا ۲ میلی‌متر یا بیشتر",
                description = "هشدار باران قابل توجه و رگبارهای متراکم",
                checked = settings.notifyHeavyRain,
                testTag = "alert_heavy_rain_switch",
                onCheckedChange = { onUpdateSettings(settings.copy(notifyHeavyRain = it)) }
            )
            AlertOptionRow(
                icon = Icons.Default.Cloud,
                iconTint = SkyAccent,
                title = "ابرهای باران‌زا و توده راداری",
                description = "پوشش ابرهای فعال و رصد توده بارشی در رادار",
                checked = settings.notifyRainClouds,
                testTag = "alert_rain_clouds_switch",
                onCheckedChange = { onUpdateSettings(settings.copy(notifyRainClouds = it)) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurface.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Text(
                text = "🛡️ سامانه ضداسپم فعال است: برای جلوگیری از مزاحمت، اعلان‌های هم‌سطح تا حداقل ۹۰ دقیقه تکرار نمی‌شوند.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onTriggerAlert,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trigger_alert_button"),
            colors = ButtonDefaults.buttonColors(containerColor = AmberStorm),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "آزمایش و تست فوری اعلان (${settings.windowHours} ساعت)",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        if (feedbackMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldOk,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = feedbackMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onClearFeedback,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun AlertOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iconTint,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SlateSurface
            )
        )
    }
}
