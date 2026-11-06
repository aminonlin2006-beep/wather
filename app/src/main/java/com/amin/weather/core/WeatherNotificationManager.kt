package com.amin.weather.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.amin.weather.MainActivity
import com.amin.weather.R

data class AlertSettings(
    val windowHours: Int = 3, // 1, 3, or 6
    val notifyThunderstorm: Boolean = true,
    val notifyHeavyRain: Boolean = true,
    val notifyRainClouds: Boolean = true,
    val isEnabled: Boolean = true
)

data class AlertEvaluationResult(
    val shouldNotify: Boolean,
    val title: String,
    val summary: String,
    val details: List<String>,
    val alertType: String // "thunderstorm", "heavy_rain", "rain_clouds", "none"
)

class WeatherNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "regional_weather_alerts"
        const val CHANNEL_NAME = "هشدارهای هواشناسی منطقه"
        const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "regional_weather_alert_prefs"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    fun loadSettings(): AlertSettings {
        return AlertSettings(
            windowHours = prefs.getInt("window_hours", 3),
            notifyThunderstorm = prefs.getBoolean("notify_thunderstorm", true),
            notifyHeavyRain = prefs.getBoolean("notify_heavy_rain", true),
            notifyRainClouds = prefs.getBoolean("notify_rain_clouds", true),
            isEnabled = prefs.getBoolean("is_enabled", true)
        )
    }

    fun saveSettings(settings: AlertSettings) {
        prefs.edit()
            .putInt("window_hours", settings.windowHours)
            .putBoolean("notify_thunderstorm", settings.notifyThunderstorm)
            .putBoolean("notify_heavy_rain", settings.notifyHeavyRain)
            .putBoolean("notify_rain_clouds", settings.notifyRainClouds)
            .putBoolean("is_enabled", settings.isEnabled)
            .apply()

        if (settings.isEnabled) {
            schedulePeriodicSync()
        } else {
            cancelPeriodicSync()
        }
    }

    fun schedulePeriodicSync() {
        val settings = loadSettings()
        if (!settings.isEnabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = Intent(context, WeatherAlertReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 2001, intent, flags)
        val triggerAt = System.currentTimeMillis() + 30 * 60 * 1000L // بررسی خودکار هر ۳۰ دقیقه
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelPeriodicSync() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = Intent(context, WeatherAlertReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 2001, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "هشدارهای رعدوبرق خطرناک، بارش سنگین و ابرهای باران‌زا در منطقه"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun evaluateDashboard(
        dashboard: ForecastDashboard,
        settings: AlertSettings
    ): AlertEvaluationResult {
        if (!settings.isEnabled) {
            return AlertEvaluationResult(false, "", "", emptyList(), "none")
        }

        val rows = dashboard.ranges[ForecastRange.HOURLY].orEmpty()
        val baseEval = evaluateForecast(rows, settings)

        val detectedDetails = baseEval.details.toMutableList()
        var hasThunderstorm = baseEval.alertType == "thunderstorm"
        var hasHeavyRain = baseEval.alertType == "heavy_rain"
        var hasRainClouds = baseEval.alertType == "rain_clouds"

        // بررسی اختصاصی خروجی پایش فضایی منطقه
        val spp = dashboard.triangulation.spatialPrecipProbability
        if (spp >= 65.0) {
            hasHeavyRain = true
            detectedDetails.add(0, "📐 پایش شبکه دیدبانی: احتمال بارندگی ${JalaliCalendar.formatNumber(spp)}٪ در منطقه")
        }

        // بررسی اختصاصی رادار چندتایلی ۶۰ کیلومتری
        val radar = dashboard.radarNowcast
        if (radar.echoDetected && (radar.estimatedDistanceKm ?: 99.0) <= 35.0) {
            hasThunderstorm = true
            val etaStr = radar.estimatedEtaMinutes?.let { "حدود $it دقیقه دیگر" } ?: "به زودی"
            detectedDetails.add(0, "📡 رادار زنده: توده فعال بارشی در فاصله ${radar.estimatedDistanceKm?.toInt()} کیلومتری ($etaStr)")
        }

        // بررسی ریسک جبهه تندباد
        val tel = dashboard.stationTelemetry
        if (tel.gustFrontRisk) {
            hasThunderstorm = true
            detectedDetails.add("💨 احتمال وزش باد شدید در منطقه (سرعت لحظه‌ای ${tel.currentWindGust?.toInt()} کیلومتر بر ساعت)")
        }

        if (!hasThunderstorm && !hasHeavyRain && !hasRainClouds) {
            return AlertEvaluationResult(
                shouldNotify = false,
                title = "وضعیت جوی آرام",
                summary = "در حال حاضر آسمان آرام است و هیچ توده بارشی خطرناکی در منطقه ثبت نشده است.",
                details = emptyList(),
                alertType = "none"
            )
        }

        val alertType = when {
            hasThunderstorm -> "thunderstorm"
            hasHeavyRain -> "heavy_rain"
            else -> "rain_clouds"
        }

        val title = when (alertType) {
            "thunderstorm" -> "⚠️ هشدار فوری رعدوبرق و تندباد در منطقه"
            "heavy_rain" -> "🌧️ هشدار رگبار باران شدید در راه منطقه"
            else -> "☁️ هشدار تشکیل ابرهای باران‌زا پیرامون منطقه"
        }

        val summary = detectedDetails.firstOrNull() ?: "هشدار شرایط ناپایدار جوی"

        return AlertEvaluationResult(
            shouldNotify = true,
            title = title,
            summary = summary,
            details = detectedDetails,
            alertType = alertType
        )
    }

    fun evaluateForecast(
        rows: List<ForecastRow>,
        settings: AlertSettings
    ): AlertEvaluationResult {
        if (!settings.isEnabled || rows.isEmpty()) {
            return AlertEvaluationResult(false, "", "", emptyList(), "none")
        }

        val slice = rows.take(settings.windowHours)
        if (slice.isEmpty()) {
            return AlertEvaluationResult(false, "", "", emptyList(), "none")
        }

        val detectedDetails = mutableListOf<String>()
        var hasThunderstorm = false
        var hasHeavyRain = false
        var hasRainClouds = false

        // ۱. بررسی رعد و برق خطرناک و ناپایداری همرفتی شدید
        if (settings.notifyThunderstorm) {
            val maxTs = slice.mapNotNull { it.thunderstormScore }.maxOrNull() ?: 0.0
            val maxConv = slice.mapNotNull { it.convIndex }.maxOrNull() ?: 0.0
            val maxCape = slice.mapNotNull { it.capeValue }.maxOrNull() ?: 0.0

            if (maxTs >= 35.0 || maxConv >= 30.0 || maxCape >= 400.0) {
                hasThunderstorm = true
                detectedDetails.add(
                    "⚡ رعد و برق و پتانسیل رگبار شدید همرفتی (شاخص ناپایداری: ${JalaliCalendar.formatNumber(maxConv)})"
                )
            }
        }

        // ۲. بررسی بارش تا ۲ میلی‌متر یا بیشتر
        if (settings.notifyHeavyRain) {
            val maxLikely = slice.mapNotNull { it.precipMean }.maxOrNull() ?: 0.0
            val maxPeak = slice.mapNotNull { it.precipMax }.maxOrNull() ?: 0.0
            val sumPrecip = slice.mapNotNull { it.precipMean }.sum()

            if (maxLikely >= 1.8 || maxPeak >= 2.0 || sumPrecip >= 2.0) {
                hasHeavyRain = true
                val amountStr = JalaliCalendar.formatNumber(Math.max(maxLikely, maxPeak))
                detectedDetails.add(
                    "🌧️ بارش سنگین یا رگبار بالای ۲ میل (بیشینه پیش‌بینی: $amountStr mm)"
                )
            }
        }

        // ۳. بررسی ابرهای باران‌زا و رادار فعال
        if (settings.notifyRainClouds) {
            val maxCloud = slice.mapNotNull { it.cloudCover }.maxOrNull() ?: 0.0
            val maxProb = slice.mapNotNull { it.precipProb }.maxOrNull() ?: 0.0
            val hasRadarCell = slice.any { it.forecastSource.contains("رادار") }
            val hasConvCloud = slice.any {
                it.convectiveRisk || it.rainTypeLabel.contains("رگبار")
            }

            if ((maxCloud >= 70.0 && maxProb >= 25.0) || hasConvCloud || hasRadarCell) {
                hasRainClouds = true
                val sourceText = if (hasRadarCell) "رصد توده بارشی توسط رادار زنده" else "تشکیل ابرهای متراکم باران‌زا"
                detectedDetails.add(
                    "☁️ $sourceText (پوشش ابر: ${JalaliCalendar.formatNumber(maxCloud)}٪)"
                )
            }
        }

        if (!hasThunderstorm && !hasHeavyRain && !hasRainClouds) {
            return AlertEvaluationResult(
                shouldNotify = false,
                title = "وضعیت آرام",
                summary = "در ${settings.windowHours} ساعت آینده شرایط حادی پیش‌بینی نمی‌شود.",
                details = emptyList(),
                alertType = "none"
            )
        }

        val windowLabel = when (settings.windowHours) {
            1 -> "۱ ساعت آینده"
            3 -> "۳ ساعت آینده"
            6 -> "۶ ساعت آینده"
            else -> "${settings.windowHours} ساعت آینده"
        }

        val alertType = when {
            hasThunderstorm -> "thunderstorm"
            hasHeavyRain -> "heavy_rain"
            else -> "rain_clouds"
        }

        val title = when (alertType) {
            "thunderstorm" -> "⚠️ هشدار رعد و برق خطرناک ($windowLabel)"
            "heavy_rain" -> "🌧️ هشدار رگبار و بارش بالای ۲ میل ($windowLabel)"
            else -> "☁️ هشدار تشکیل ابرهای باران‌زا ($windowLabel)"
        }

        val summary = detectedDetails.firstOrNull() ?: "هشدار شرایط ناپایدار جوی در منطقه"

        return AlertEvaluationResult(
            shouldNotify = true,
            title = title,
            summary = summary,
            details = detectedDetails,
            alertType = alertType
        )
    }

    fun sendNotificationWithCooldown(result: AlertEvaluationResult, force: Boolean = false): Boolean {
        if (!result.shouldNotify) return false

        val lastTime = prefs.getLong("last_alert_time", 0L)
        val lastType = prefs.getString("last_alert_type", "")
        val now = System.currentTimeMillis()
        val cooldownMs = 90 * 60 * 1000L // حداقل ۹۰ دقیقه بین اعلان‌های مشابه

        val isHigherSeverity = (result.alertType == "thunderstorm" && lastType != "thunderstorm")
        if (!force && (now - lastTime < cooldownMs) && !isHigherSeverity) {
            // جلوگیری از اسپم هشدار تکراری
            return false
        }

        val sent = sendNotification(result)
        if (sent) {
            prefs.edit()
                .putLong("last_alert_time", now)
                .putString("last_alert_type", result.alertType)
                .apply()
        }
        return sent
    }

    fun sendNotification(result: AlertEvaluationResult): Boolean {
        if (!result.shouldNotify) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val bigText = buildString {
            append(result.summary)
            if (result.details.size > 1) {
                append("\n\nجزئیات هشدار:\n")
                result.details.forEach { append("• $it\n") }
            }
            append("\nموقعیت: منطقه (شعاع ۶۰ کیلومتر)")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(result.title)
            .setContentText(result.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
