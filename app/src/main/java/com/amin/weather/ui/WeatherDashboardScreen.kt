package com.amin.weather.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import com.amin.weather.core.ConvectiveCellForecast
import com.amin.weather.core.SkyObservationReport
import java.io.ByteArrayOutputStream
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
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * جعبه آموزشی تاشو و بازشونده برای شفاف‌سازی مبانی پیش‌بینی و آموزش مفاهیم به زبان ساده
 */
@Composable
fun EducationalDisclosureBox(
    sourceName: String,
    reasoningText: String,
    accentColor: Color = SkyAccent,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateDeep.copy(alpha = 0.55f))
            .border(0.8.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
    ) {
        // نوار لمسی کلید باز/بسته کردن
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🎓 مبنای این پیش‌بینی و تحلیل علمی",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "بستن آموزش" else "مشاهده آموزش",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        // محتوای بازشونده انیمیشنی
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HorizontalDivider(
                    color = SlateBorder.copy(alpha = 0.35f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // منبع داده
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "📡 منبع داده: ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SkyAccent
                    )
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // چرا و چگونه
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "🔍 تحلیل و مبنای علمی: ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberStorm
                    )
                    Text(
                        text = reasoningText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
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
    onClearFeedbackMessage: () -> Unit,
    onSelectBottomTab: (Int) -> Unit = {},
    onSubmitSkyObservation: (String?, String, String, Double?, String) -> Unit = { _, _, _, _, _ -> },
    onClearSkyObservation: () -> Unit = {}
) {
    val context = LocalContext.current
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showSkyObservationSheet by remember { mutableStateOf(false) }

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
                                    text = "هواشناسی امین",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "پایش راداری و دیدبانی هوشمند منطقه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        // دکمه ثبت دیدبانی آسمان و دوربین
                        IconButton(
                            onClick = { showSkyObservationSheet = true },
                            modifier = Modifier.testTag("sky_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "دیده‌بان آسمان و کالیبراسیون",
                                tint = if (uiState.dashboard?.userObservation != null) AmberStorm else SkyAccent
                            )
                        }
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
                            onSelectRange = onSelectRange,
                            selectedBottomTab = uiState.selectedBottomTab,
                            onSelectBottomTab = onSelectBottomTab,
                            onClearSkyObservation = onClearSkyObservation
                        )
                    }
                }
            }

            // مودال باتم شیت دیده‌بان آسمان و کالیبراسیون داده‌ها
            if (showSkyObservationSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSkyObservationSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SlateCard
                ) {
                    SkyObservationBottomSheetContent(
                        existingReport = uiState.dashboard?.userObservation,
                        onSubmit = { photo, dir, phen, temp, notes ->
                            onSubmitSkyObservation(photo, dir, phen, temp, notes)
                            showSkyObservationSheet = false
                        },
                        onClear = {
                            onClearSkyObservation()
                            showSkyObservationSheet = false
                        },
                        onDismiss = { showSkyObservationSheet = false }
                    )
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
    onSelectRange: (ForecastRange) -> Unit,
    selectedBottomTab: Int = 0,
    onSelectBottomTab: (Int) -> Unit = {},
    onClearSkyObservation: () -> Unit = {}
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

        // ۲. بنر سطح هشدار چهارگانه (Hero Alert Tier) همراه با دمای حسی و نشانگر کالیبراسیون
        item {
            HeroAlertTierCard(
                verdict = dashboard.verdict,
                telemetry = dashboard.stationTelemetry,
                userObservation = dashboard.userObservation,
                onClearObservation = onClearSkyObservation
            )
        }

        // ۳. نوار افقی و مدرن تایم‌لاین ساعتی (کاملاً سبک و بدون فضای خالی)
        item {
            HourlyTimelineSection(
                rows = hourlyRows,
                selectedRange = selectedRange,
                onSelectRange = onSelectRange
            )
        }

        // ۴. نوار انتخاب تب‌های سه‌گانه
        item {
            BottomSegmentedTabs(
                selectedTab = selectedBottomTab,
                onSelectTab = onSelectBottomTab
            )
        }

        // ۵. محتوای تب فعال
        item {
            when (selectedBottomTab) {
                0 -> RadarTrackingCard(radar = dashboard.radarNowcast)
                1 -> FieldNetworkUnifiedCard(
                    triangulation = dashboard.triangulation,
                    telemetry = dashboard.stationTelemetry,
                    perimeter = dashboard.perimeterNetwork
                )
                else -> ConvectivePanel(
                    pattern = dashboard.pattern,
                    cellForecast = dashboard.cellForecast
                )
            }
        }

        // ۶. پانویس با برندینگ رسمی هواشناسی امین
        item {
            Text(
                text = "⚡ سامانه پایش هوشمند هواشناسی امین • پایش رادار چندتایلی و شبکه ایستگاهی",
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
    telemetry: StationTelemetryInfo,
    userObservation: SkyObservationReport? = null,
    onClearObservation: () -> Unit = {}
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = tier.name,
                    tint = tierColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // نشانگر کالیبراسیون با دیدبانی میدانی کاربر
            if (userObservation != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberStorm.copy(alpha = 0.2f))
                        .border(1.dp, AmberStorm.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
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
                            tint = AmberStorm,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📍 کالیبره شده با دیدبانی میدانی شما: ${userObservation.phenomenon} (${userObservation.direction})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberStorm,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onClearObservation,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "حذف دیدبانی",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
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
                            text = "دمای حسی: ${JalaliCalendar.formatNumber(feelsLike)}°C",
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

            Spacer(modifier = Modifier.height(10.dp))

            EducationalDisclosureBox(
                sourceName = "ترکیب همزمان رادار بارشی ۶۰ کیلومتری + ایستگاه مرکزی و شهرهای همجوار",
                reasoningText = "بررسی همزمان وجود توده‌های باران در رادار، رطوبت نسبی و سرعت باد؛ هرگاه ابری در شعاع ۳۰ کیلومتری نباشد وضعیت سبز و هرگاه توده فعال نزدیک شود سطح هشدار بالا می‌رود.",
                accentColor = tierColor
            )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "رادار",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "رهگیری راداری ابرها (شعاع ۶۰km)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (radar.echoDetected) CoralAlert.copy(alpha = 0.2f) else EmeraldOk.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (radar.echoDetected) "اکوی فعال" else "شبکه آرام",
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

            if (radar.isVirgaLikely) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberStorm.copy(alpha = 0.16f))
                        .border(1.dp, AmberStorm.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberStorm,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "احتمال پدیده ویرگا (تبخیر باران در آسمان): توده در ارتفاع شکل گرفته ولی به دلیل خشکی لایه‌های پایینی جو، ممکن است قطرات باران پیش از رسیدن به زمین تبخیر شده و به جای باران، وزش باد شدید و خاک احساس شود.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

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

                // زمان ورود (ETA بازه‌ای و شناور)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "بازه ورود", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = radar.etaRangeText ?: (radar.estimatedEtaMinutes?.let { "حدود $it دقیقه" } ?: "بدون تهدید"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (radar.echoDetected) AmberStorm else EmeraldOk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // روند رشد یا تضعیف ابرها
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "روند توده", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = radar.cloudTrendLabel,
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
                if (radar.isVirgaLikely) {
                    "⚠️ ابرها در ارتفاع فعالند اما ممکن است باران در هوا تبخیر شده و باد شدید محلی ایجاد شود. در فضای باز مراقب گردوخاک و تندباد باشید."
                } else {
                    "⚠️ ابرهای بارانی در حال نزدیک شدن هستند (${radar.etaRangeText ?: "طی ساعت آینده"})؛ وسایل حساس به آب را جمع کرده و پیش از شروع رگبار برنامه‌ریزی کنید."
                }
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

            Spacer(modifier = Modifier.height(10.dp))

            EducationalDisclosureBox(
                sourceName = "اسکن زنده شبکه راداری هواشناسی با ۴ تایل پیرامونی (شعاع ۶۰ کیلومتر)",
                reasoningText = "تطبیق و مقایسه فریم‌های متوالی رادار در فواصل ۱۰ دقیقه‌ای برای استخراج بردار سرعت دینامیک (${radar.movementSpeedKmh.toInt()} km/h)، محاسبه بازه زمانی ورود و پایش روند گسترش/تضعیف (${radar.cloudTrendLabel}).",
                accentColor = accentColor
            )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "شبکه دیدبانی",
                        tint = SkyAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "شبکه پایگاه‌های همجوار و جهت باران",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "پایش ایستگاه‌های پیرامون و بالادست",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
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
                    "پیرامونی و دوربرد" to 2
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
                    // تب ۳: پایگاه‌های حلقه پیرامونی و ایستگاه‌های پیش‌هشدار دوربرد
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        perimeter.distantWarningSummary?.let { warningText ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AmberStorm.copy(alpha = 0.15f))
                                    .border(1.dp, AmberStorm.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = warningText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AmberStorm
                                )
                            }
                        }
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

            Spacer(modifier = Modifier.height(10.dp))

            EducationalDisclosureBox(
                sourceName = "شبکه حسگرهای زمینی شهرهای همجوار (اندیمشک، شوش، گتوند، دهلران، پلدختر، بستان) و ایستگاه مرکزی",
                reasoningText = "اندازه‌گیری لحظه‌ای اختلاف فشار، رطوبت و سرعت باد میان پایگاه‌های اطراف و بالادست تا مسیر ورود جبهه‌های بارشی به سمت دشت قبل از متراکم شدن ابرها ردیابی شود.",
                accentColor = sppColor
            )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = st.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (st.isUpstreamWarning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SkyAccent.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "پیش‌هشدار بالادست",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SkyAccent
                            )
                        }
                    }
                }
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
            "⚡ احتمال رعدوبرق یا وزش تندباد ناگهانی؛ در فضای باز پناه بگیرید."
        (row.precipMean ?: 0.0) >= 0.5 || row.rainTypeLabel.contains("باران") ->
            "🌧️ بارش باران و لغزندگی؛ چتر همراه داشته و با احتیاط برانید."
        (row.windGust ?: 0.0) >= 30.0 ->
            "💨 وزش تندباد موضعی؛ مراقب اشیاء سبک در فضای باز باشید."
        else ->
            "☀️ هوای آرام و مساعد برای فعالیت در فضای باز."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ساعت ${JalaliCalendar.formatLong(row.timeIran)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "— ${row.rainTypeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.rainTypeLabel.contains("صاعقه") || row.rainTypeLabel.contains("رگبار")) AmberStorm else SkyAccent
                    )
                }

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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricMiniTile(
                    title = "بازه بارش",
                    value = row.precipRange ?: "۰",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "باد / تندباد",
                    value = "${JalaliCalendar.formatNumber(row.windSpeed)} | ${JalaliCalendar.formatNumber(row.windGust)}k",
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

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SlateDeep.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SkyAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = hourAdvice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = AmberStorm,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "خلاصه وضعیت بارندگی ۲۴ ساعت آینده",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

            Spacer(modifier = Modifier.height(10.dp))

            EducationalDisclosureBox(
                sourceName = "مدل جهانی هواشناسی اروپا (ECMWF) با تفکیک شبانه‌روزی لایه‌های جو",
                reasoningText = "بررسی نوسان دمایی میان لایه زیرین جو و هوای سرد بالای سر؛ اگر اختلاف دما زیاد باشد هوای گرم صعود کرده و ابرها جوششی و رگباری می‌شوند.",
                accentColor = AmberStorm
            )
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

/**
 * نوار انتخاب تب‌های سه‌گانه
 */
@Composable
private fun BottomSegmentedTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        "📡 رادار و توده‌ها",
        "🌐 ایستگاه‌های کمکی",
        "⚡ توده‌ای و رعدوبرق"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_segmented_tabs"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SlateSurface else Color.Transparent)
                        .border(
                            if (isSelected) 1.dp else 0.dp,
                            if (isSelected) SkyAccent else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectTab(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) SkyAccent else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * پنل یکپارچه پیش‌بینی همرفتی و تفکیک بارش توده‌ای
 */
@Composable
private fun ConvectivePanel(
    pattern: PatternSummary,
    cellForecast: ConvectiveCellForecast
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ConvectiveCellForecastCard(cellForecast = cellForecast)
        ConvectiveSummaryCard(pattern = pattern)
    }
}

/**
 * کارت پیش‌بینی تفکیک‌شده توده و رگبار نقطه‌ای (CPI)
 */
@Composable
private fun ConvectiveCellForecastCard(cellForecast: ConvectiveCellForecast) {
    val probColor = when {
        cellForecast.cellRiskProbability >= 70.0 -> CoralAlert
        cellForecast.cellRiskProbability >= 40.0 -> AmberStorm
        else -> EmeraldOk
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("convective_cell_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, probColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = AmberStorm,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "پیش‌بینی توده‌ای و رگبار نقطه‌ای (CPI)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(probColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ریسک: ${JalaliCalendar.formatNumber(cellForecast.cellRiskProbability)}٪",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = probColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = cellForecast.cellTypeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // مقایسه بارش عمومی دشت در برابر بیشینه اصابت مستقیم توده
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    title = "بارش عمومی منطقه",
                    value = "${JalaliCalendar.formatNumber(cellForecast.generalPrecipMm)} میلی‌متر",
                    valueColor = SkyAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "بیشینه در اصابت مستقیم",
                    value = "${JalaliCalendar.formatNumber(cellForecast.potentialCellPrecipMaxMm)} میلی‌متر",
                    valueColor = if (cellForecast.potentialCellPrecipMaxMm >= 5.0) CoralAlert else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // پنجره زمانی بحرانی و هشدارهای همراه
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    title = "پنجره بحرانی توده",
                    value = cellForecast.criticalWindowIran,
                    valueColor = AmberStorm,
                    modifier = Modifier.weight(1.2f)
                )
                MetricMiniTile(
                    title = "تندباد پیش‌قراول",
                    value = if (cellForecast.gustFrontRisk) "خطر بالا ⚠️" else "عادی ✅",
                    valueColor = if (cellForecast.gustFrontRisk) AmberStorm else EmeraldOk,
                    modifier = Modifier.weight(0.8f)
                )
            }

            if (cellForecast.virgaAlert) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberStorm.copy(alpha = 0.15f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Air, contentDescription = null, tint = AmberStorm, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "هشدار ویرگا: خشکی هوای زیر ابر ممکن است باعث تبخیر باران و تبدیل آن به باد شدید ناگهانی شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (cellForecast.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = cellForecast.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * باتم شیت دیده‌بان آسمان، ارسال عکس و کالیبراسیون داده‌ها
 */
@Composable
private fun SkyObservationBottomSheetContent(
    existingReport: SkyObservationReport?,
    onSubmit: (photo: String?, direction: String, phenomenon: String, temp: Double?, notes: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedDirection by remember { mutableStateOf(existingReport?.direction ?: "غرب") }
    var selectedPhenomenon by remember { mutableStateOf(existingReport?.phenomenon ?: "ابرهای تیره و جوششی") }
    var tempText by remember { mutableStateOf(existingReport?.userTemp?.let { it.toString() } ?: "") }
    var notesText by remember { mutableStateOf(existingReport?.notes ?: "") }
    var photoBase64 by remember { mutableStateOf<String?>(existingReport?.photoBase64) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(existingReport?.photoBase64?.let { base64ToBitmap(it) }) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            val scaled = scaleBitmap(bmp, 600)
            previewBitmap = scaled
            photoBase64 = bitmapToBase64(scaled)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = uriToBitmap(context, uri)
            if (bmp != null) {
                val scaled = scaleBitmap(bmp, 600)
                previewBitmap = scaled
                photoBase64 = bitmapToBase64(scaled)
            }
        }
    }

    val directions = listOf("غرب", "شمال غرب", "شمال", "جنوب غرب", "بالای سر")
    val phenomena = listOf("ابرهای تیره و جوششی", "رعدوبرق", "نم‌نم باران", "رگبار شدید", "تندباد و خاک", "آسمان صاف")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("sky_observation_bottom_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = SkyAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "دیده‌بان آسمان و کالیبراسیون محلی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ارسال گزارش میدانی برای تنظیم دقیق رادار و پیش‌بینی",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // پیش‌نمایش یا دکمه‌های بارگذاری تصویر
        if (previewBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateDeep),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "تصویر آسمان ارسال‌شده",
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = {
                        previewBitmap = null
                        photoBase64 = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف تصویر", tint = CoralAlert)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("take_sky_photo_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SlateSurface)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("عکاسی از آسمان", color = MaterialTheme.colorScheme.onSurface)
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_sky_photo_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("انتخاب از گالری", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "جهت مشاهده ابر یا توده:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(directions.size) { i ->
                val dir = directions[i]
                FilterChip(
                    selected = selectedDirection == dir,
                    onClick = { selectedDirection = dir },
                    label = { Text(dir) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SkyAccent.copy(alpha = 0.25f),
                        selectedLabelColor = SkyAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "پدیده حاضر در آسمان محل شما:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(phenomena.size) { i ->
                val ph = phenomena[i]
                FilterChip(
                    selected = selectedPhenomenon == ph,
                    onClick = { selectedPhenomenon = ph },
                    label = { Text(ph) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberStorm.copy(alpha = 0.25f),
                        selectedLabelColor = AmberStorm
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = tempText,
            onValueChange = { tempText = it },
            label = { Text("دمای دماسنج شما (°C) — اختیاری") },
            placeholder = { Text("مثلاً ۲۴.۵") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_temp_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SkyAccent,
                unfocusedBorderColor = SlateBorder
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("توضیحات و گزارش میدانی — اختیاری") },
            placeholder = { Text("مثلاً: باد شدید ناگهانی از سمت غرب شروع شد...") },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_notes_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SkyAccent,
                unfocusedBorderColor = SlateBorder
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val parsedTemp = tempText.toDoubleOrNull()
                    onSubmit(photoBase64, selectedDirection, selectedPhenomenon, parsedTemp, notesText)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("submit_sky_observation_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SkyAccent)
            ) {
                Text("ثبت دیدبانی و کالیبراسیون", fontWeight = FontWeight.Bold, color = SlateDeep)
            }

            if (existingReport != null) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("clear_sky_observation_button")
                ) {
                    Text("حذف دیدبانی", color = CoralAlert)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

private fun base64ToBitmap(base64Str: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (_: Exception) {
        null
    }
}

private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}

private fun scaleBitmap(bitmap: Bitmap, maxDim: Int = 600): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDim && height <= maxDim) return bitmap
    val ratio = width.toFloat() / height.toFloat()
    val targetWidth = if (width > height) maxDim else (maxDim * ratio).toInt().coerceAtLeast(1)
    val targetHeight = if (height > width) maxDim else (maxDim / ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}
