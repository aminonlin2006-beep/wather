package com.amin.weather.core

/**
 * سطوح هشدار چهارگانه رادار و وضعیت جوی منطقه:
 * ۱. سبز (آرام و پایدار)
 * ۲. زرد (احتمال رگبار موضعی در ساعت‌های آینده)
 * ۳. نارنجی (توده بارشی در راه - فاصله ۳۰ تا ۶۰ کیلومتری)
 * ۴. قرمز (هشدار فوری: رگبار شدید و تندباد در کمتر از ۱۵ کیلومتر)
 */
enum class AlertTier(
    val levelName: String,
    val title: String,
    val hexColor: String
) {
    GREEN("green", "آسمان آرام و پایدار", "#10B981"),
    YELLOW("yellow", "احتمال رگبار موضعی و ناپایداری جوی", "#F59E0B"),
    ORANGE("orange", "توده بارشی در راه (فاصله ۳۰ تا ۶۰ کیلومتر)", "#F97316"),
    RED("red", "هشدار فوری: رگبار شدید و تندباد لحظه‌ای", "#EF4444")
}

/**
 * بازه‌های زمانی کاربردی برای پیش‌بینی کوتاه‌مدت و ۲۴ ساعته
 */
enum class ForecastRange(val key: String, val label: String) {
    HOURLY("hourly", "ساعت به ساعت"),
    H1("h1", "۱ ساعت آینده"),
    H3("h3", "۳ ساعت آینده"),
    H6("h6", "۶ ساعت آینده"),
    H12("h12", "۱۲ ساعت آینده"),
    H24("h24", "۲۴ ساعت آینده")
}

enum class ForecastColumn(val key: String, val label: String, val defaultVisible: Boolean = true) {
    SOURCE("source", "منبع تحلیل", true),
    TEMP("temp", "دما (°C)", true),
    PRECIP("precip", "بارش محتمل (mm)", true),
    PRECIP_RANGE("precip_range", "بازه بارش", true),
    CONV_INDEX("conv_index", "شدت پتانسیل باران", true),
    RAIN_TYPE("rain_type", "نوع پدیده", true),
    THUNDERSTORM("thunderstorm", "احتمال رعدوبرق", true),
    WIND_GUST("wind_gust", "تندباد لحظه‌ای (km/h)", true),
    DEW_POINT("dew_point", "نقطه شبنم (°C)", false),
    VIRGA("virga", "بارش تبخیرشونده در آسمان", false),
    CLOUD("cloud", "پوشش ابر (%)", false)
}

data class VerdictInfo(
    val tier: AlertTier,
    val text: String,
    val advisoryNote: String = ""
)

/**
 * اطلاعات تفصیلی اسکن و رهگیری رادار در شعاع ۶۰ کیلومتری منطقه
 */
data class RadarNowcastInfo(
    val available: Boolean = false,
    val tier: AlertTier = AlertTier.GREEN,
    val echoDetected: Boolean = false,
    val estimatedDistanceKm: Double? = null,
    val cellAzimuthDirection: String = "بدون توده فعال",
    val estimatedEtaMinutes: Int? = null,
    val etaRangeText: String? = null,
    val cloudTrendLabel: String = "پایدار ↔️",
    val reflectivityDbz: Double = 0.0,
    val statusText: String = "",
    val lastFrameIran: String = "",
    val movementSpeedKmh: Double = 0.0,
    val incomingRainLikely: Boolean = false,
    val isVirgaLikely: Boolean = false,
    val scanRadiusKm: Int = 60
)

/**
 * اطلاعات تکی ایستگاه‌های حلقه دیدبانی پیرامون منطقه
 */
data class PerimeterStationInfo(
    val id: String,
    val name: String,
    val sector: String, // "مرکز", "شمال غرب", "غرب", "شمال", "شرق", "بالادست دوربرد"
    val distanceKm: Int,
    val temp: Double?,
    val humidity: Double?,
    val windGust: Double?,
    val precipitation: Double?,
    val isRaining: Boolean,
    val statusText: String,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val isUpstreamWarning: Boolean = false
)

/**
 * مشخصات هر رأس در شبکه هندسی ایستگاه‌های پایش منطقه
 */
data class TriangulationVertex(
    val id: String,
    val name: String,
    val sector: String,
    val lat: Double,
    val lon: Double,
    val xKm: Double,
    val yKm: Double,
    val distanceKm: Double,
    val barycentricWeight: Double, // سهم وزنی موقعیت
    val rainRateMmH: Double,
    val rainProbability: Double,
    val temp: Double?,
    val humidity: Double?,
    val windSpeedKmh: Double?,
    val windGustKmh: Double?,
    val windDirectionDeg: Double?,
    val isRaining: Boolean,
    val statusText: String
)

/**
 * خروجی جامع شبکه چند ایستگاهی و جهت حرکت بارش به سمت مرکز منطقه
 */
data class SpatialTriangulationResult(
    val primaryStationName: String = "مرکز منطقه (مختصات مبنا: ۰,۰)",
    val vertices: List<TriangulationVertex> = emptyList(),
    val spatialPrecipProbability: Double = 0.0,
    val spatialRainRateMmH: Double = 0.0,
    val gradientMagnitude: Double = 0.0,
    val gradientDirectionDeg: Double = 0.0,
    val gradientDirectionLabel: String = "بدون جریان بارشی",
    val advectionFluxPerHour: Double = 0.0,
    val triangleAreaSqKm: Double = 754.3,
    val isInsideTriangle: Boolean = true,
    val convergenceSummary: String = "پایش مداوم شبکه دیدبانی پیرامون منطقه",
    val threatVertexName: String? = null
)

/**
 * جمع‌بندی حلقه دیدبانی ایستگاه‌های پیرامون منطقه جهت اعتبارسنجی رادار
 */
data class PerimeterNetworkSummary(
    val stations: List<PerimeterStationInfo>,
    val activeRainStationsCount: Int = 0,
    val activeGustStationsCount: Int = 0,
    val distantWarningStationsCount: Int = 0,
    val threatDirection: String? = null,
    val distantWarningSummary: String? = null,
    val networkStatusSummary: String = "ایستگاه‌های دیدبانی پیرامون وضعیت جوی پایداری را گزارش کرده‌اند."
)

/**
 * تله‌متری زنده ایستگاه سینوپتیک مرکزی منطقه
 */
data class StationTelemetryInfo(
    val stationName: String = "ایستگاه مرکزی هواشناسی منطقه",
    val currentTemp: Double? = null,
    val currentHumidity: Double? = null,
    val currentDewPoint: Double? = null,
    val dewPointDepression: Double? = null,
    val currentWeatherDesc: String? = null,
    val currentWindSpeed: Double? = null,
    val currentWindGust: Double? = null,
    val currentCloudCover: Double? = null,
    val observedPrecipitation: Double? = null,
    val gustFrontRisk: Boolean = false
)

/**
 * خلاصه وضعیت بارندگی و ناپایداری ۲۴ ساعت آینده
 */
data class PatternSummary(
    val windowStartIran: String,
    val windowEndIran: String,
    val peakPrecipTimeIran: String,
    val peakPrecipMm: Double,
    val peakTsTimeIran: String,
    val peakConvectiveScore: Double,
    val maxCape: Double,
    val totalPrecip24hLikely: Double = 0.0,
    val totalPrecip24hMax: Double = 0.0,
    val maxProb24h: Double = 0.0,
    val maxGust24h: Double = 0.0,
    val precipSummaryText: String = "",
    val convectiveHoursCount: Int = 0,
    val virgaHoursCount: Int = 0
)

/**
 * ردیف پیش‌بینی برای هر گام زمانی در افق ۲۴ ساعته
 */
data class ForecastRow(
    val timeIran: String,
    val forecastSource: String, // "📡 رادار زنده", "🇪🇺 مدل برتر ECMWF", "📡 رادار + مدل پیش‌بینی"
    val tempMean: Double?,
    val precipMean: Double?,
    val precipMax: Double?,
    val precipProb: Double?,
    val precipRange: String?,
    val convIndex: Double?, // شاخص شکل‌گیری بارش ۰ تا ۱۰۰
    val thunderstormScore: Double?,
    val windSpeed: Double?,
    val windGust: Double?,
    val windDirLabel: String?,
    val cloudCover: Double?,
    val rainTypeLabel: String,
    val convectiveRisk: Boolean,
    val isVirgaRisk: Boolean = false,
    val dewPoint: Double? = null,
    val dewPointDepression: Double? = null,
    val capeValue: Double? = null
)

/**
 * داشبورد جامع پیش‌بینی هواشناسی منطقه
 */
data class ForecastDashboard(
    val location: String = "منطقه (شعاع پایش ۶۰ کیلومتر)",
    val lat: Double = 32.3006,
    val lon: Double = 48.4308,
    val generatedIran: String,
    val verdict: VerdictInfo,
    val radarNowcast: RadarNowcastInfo,
    val perimeterNetwork: PerimeterNetworkSummary,
    val stationTelemetry: StationTelemetryInfo,
    val pattern: PatternSummary,
    val ranges: Map<ForecastRange, List<ForecastRow>>,
    val triangulation: SpatialTriangulationResult = SpatialTriangulationResult()
)

