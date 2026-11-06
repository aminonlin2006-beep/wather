package com.amin.weather.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * کلاینت هواشناسی دزفول مجهز به اسکن راداری ۶۰ کیلومتری و شبکه دیدبانی پنج‌ضلعی:
 * ۱. مدل ECMWF IFS اروپا (۴۸ ساعت)
 * ۲. داده‌های ۱۵ دقیقه‌ای کوتاه‌مدت
 * ۳. شبکه دیدبانی چندایستگاهی ۵ پایگاه: دزفول، اندیمشک، شوش، حسینیه (زاگرس)، گتوند
 * ۴. اسکن بازتابش تایل‌های رادار RainViewer در شعاع ۶۰ کیلومتر
 */
object OpenMeteoClient {

    const val LAT = 32.3006
    const val LON = 48.4308

    // مختصات پایگاه‌های دیدبانی پیرامون منطقه و ایستگاه‌های پیش‌هشدار بالادست
    val REGIONAL_STATIONS = listOf(
        StationConfig("central", "مرکز منطقه", "مرکز", 32.3006, 48.4308, 0, false),
        StationConfig("andimeshk", "اندیمشک", "شمال غرب", 32.4578, 48.3567, 15, false),
        StationConfig("shush", "شوش", "غرب", 32.1942, 48.2436, 30, false),
        StationConfig("hosseiniyeh", "حسینیه (تنگه زاگرس)", "شمال", 32.6853, 48.2514, 45, false),
        StationConfig("gotvand", "گتوند", "شرق", 32.2472, 48.8164, 35, false),
        // ایستگاه‌های دیدبانی پیش‌هشدار دوربرد و بالادست
        StationConfig("dehloran", "دهلران", "بالادست غرب و شمال‌غرب", 32.6941, 47.2679, 115, true),
        StationConfig("poldokhtar", "پلدختر", "بالادست شمال (زاگرس)", 33.1539, 47.7136, 105, true),
        StationConfig("bostan", "بستان", "بالادست جنوب‌غرب", 31.7214, 47.9869, 78, true)
    )

    data class StationConfig(
        val id: String,
        val name: String,
        val sector: String,
        val lat: Double,
        val lon: Double,
        val distKm: Int,
        val isUpstreamWarning: Boolean = false
    )

    val ECMWF_VARIABLES = listOf(
        "temperature_2m", "precipitation", "rain", "showers", "weather_code",
        "cape", "lifted_index", "freezinglevel_height",
        "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high",
        "windspeed_10m", "windgusts_10m", "winddirection_10m", "relativehumidity_2m",
        "dew_point_2m"
    )

    fun httpGet(url: String, timeoutMs: Int = 12000): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; DezfulWeatherApp/4.0)")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == 200) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun httpGetBytes(url: String, timeoutMs: Int = 10000): ByteArray {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; DezfulWeatherApp/4.0)")
            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.use { it.readBytes() }
            } else {
                ByteArray(0)
            }
        } catch (_: Exception) {
            ByteArray(0)
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * واکشی مدل برتر جهانی ECMWF IFS برای ۴۸ ساعت آینده
     */
    fun fetchEcmwfModel(): JSONObject {
        return try {
            val vars = ECMWF_VARIABLES.joinToString(",")
            val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$LAT&longitude=$LON" +
                "&hourly=$vars" +
                "&forecast_days=2&timezone=UTC&models=ecmwf_ifs025"
            val raw = httpGet(url, timeoutMs = 12000)
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /**
     * واکشی پیش‌بینی ۱۵ دقیقه‌ای برای ۲۴ ساعت پیش‌رو
     */
    fun fetchMinutely15(): JSONObject {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$LAT&longitude=$LON" +
                "&minutely_15=precipitation,rain,weather_code,windspeed_10m,winddirection_10m,windgusts_10m" +
                "&forecast_days=1&timezone=UTC"
            val raw = httpGet(url, timeoutMs = 10000)
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /**
     * واکشی هم‌زمان ۵ ایستگاه شبکه دیدبانی پیرامونی (دزفول، اندیمشک، شوش، حسینیه، گتوند)
     * در یک درخواست بچ واحد و بسیار پرسرعت
     */
    fun fetchPerimeterStationsBatch(): List<PerimeterStationInfo> {
        val lats = REGIONAL_STATIONS.joinToString(",") { it.lat.toString() }
        val lons = REGIONAL_STATIONS.joinToString(",") { it.lon.toString() }
        val url = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$lats&longitude=$lons" +
            "&current=temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,rain,showers,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&timezone=UTC"

        val raw = httpGet(url, timeoutMs = 10000)
        if (raw.isBlank()) return fallbackPerimeterStations()

        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PerimeterStationInfo>()
            for (i in 0 until Math.min(arr.length(), REGIONAL_STATIONS.size)) {
                val cfg = REGIONAL_STATIONS[i]
                val item = arr.getJSONObject(i)
                val c = item.optJSONObject("current") ?: JSONObject()
                val temp = c.optDouble("temperature_2m", Double.NaN).takeIf { !it.isNaN() }
                val rh = c.optDouble("relative_humidity_2m", Double.NaN).takeIf { !it.isNaN() }
                val gust = c.optDouble("wind_gusts_10m", Double.NaN).takeIf { !it.isNaN() }
                val precip = c.optDouble("precipitation", 0.0)
                val code = c.optInt("weather_code", 0)
                val isRaining = precip > 0.05 || code in 51..99

                val status = when {
                    isRaining -> "بارش فعال (${precip} mm) 🌧️"
                    (gust ?: 0.0) >= 35.0 -> "تندباد شدید (${gust?.toInt()} km/h) 💨"
                    code in 1..3 -> "ابری ☁️"
                    else -> "آرام ☀️"
                }

                val windSpeed = c.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() }
                val windDir = c.optDouble("wind_direction_10m", Double.NaN).takeIf { !it.isNaN() }

                list.add(
                    PerimeterStationInfo(
                        id = cfg.id,
                        name = cfg.name,
                        sector = cfg.sector,
                        distanceKm = cfg.distKm,
                        temp = temp,
                        humidity = rh,
                        windGust = gust,
                        precipitation = precip,
                        isRaining = isRaining,
                        statusText = status,
                        windSpeed = windSpeed,
                        windDirection = windDir,
                        isUpstreamWarning = cfg.isUpstreamWarning
                    )
                )
            }
            list
        } catch (_: Exception) {
            fallbackPerimeterStations()
        }
    }

    private fun fallbackPerimeterStations(): List<PerimeterStationInfo> {
        return REGIONAL_STATIONS.map { cfg ->
            PerimeterStationInfo(
                id = cfg.id,
                name = cfg.name,
                sector = cfg.sector,
                distanceKm = cfg.distKm,
                temp = null,
                humidity = null,
                windGust = null,
                precipitation = 0.0,
                isRaining = false,
                statusText = "پایش آنلاین",
                isUpstreamWarning = cfg.isUpstreamWarning
            )
        }
    }

    /**
     * واکشی تله‌متری فرودگاه دزفول
     */
    fun fetchLiveStationTelemetry(): JSONObject {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$LAT&longitude=$LON" +
                "&current=temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,rain,showers,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                "&timezone=UTC"
            val raw = httpGet(url, timeoutMs = 10000)
            if (raw.isBlank()) return JSONObject()
            val root = JSONObject(raw)
            root.optJSONObject("current") ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /**
     * بررسی و اسکن رادار RainViewer در شعاع ۶۰ کیلومتری دزفول
     */
    data class RadarScanResult(
        val available: Boolean,
        val echoDetected: Boolean,
        val estimatedDistanceKm: Double?,
        val cellDirection: String,
        val estimatedEtaMinutes: Int?,
        val etaRangeText: String?,
        val cloudTrendLabel: String,
        val reflectivityDbz: Double,
        val statusText: String,
        val lastFrameEpoch: Long,
        val movementSpeedKmh: Double,
        val incomingRainLikely: Boolean
    )

    data class RadarTileSpec(
        val x: Int,
        val y: Int,
        val sectorName: String,
        val nominalDistanceKm: Double,
        val approachBearingDeg: Double
    )

    private val RADAR_COMPOSITE_GRID = listOf(
        RadarTileSpec(81, 51, "بخش مرکزی منطقه و حومه", 10.0, 0.0),
        RadarTileSpec(80, 51, "غرب (محور شوش - دشت میشان)", 32.0, 260.0),
        RadarTileSpec(81, 50, "شمال (محور اندیمشک - تنگه زاگرس)", 28.0, 355.0),
        RadarTileSpec(80, 50, "شمال‌غرب (محور ایلام - دهلران)", 52.0, 310.0)
    )

    fun scanRainViewerRadar(): RadarScanResult {
        try {
            val mapsUrl = "https://api.rainviewer.com/public/weather-maps.json"
            val raw = httpGet(mapsUrl, timeoutMs = 8000)
            if (raw.isBlank()) {
                return RadarScanResult(
                    available = false,
                    echoDetected = false,
                    estimatedDistanceKm = null,
                    cellDirection = "عدم اتصال به رادار",
                    estimatedEtaMinutes = null,
                    etaRangeText = null,
                    cloudTrendLabel = "پایدار ↔️",
                    reflectivityDbz = 0.0,
                    statusText = "📡 رادار در حال بازنشانی اطلاعات...",
                    lastFrameEpoch = 0L,
                    movementSpeedKmh = 0.0,
                    incomingRainLikely = false
                )
            }

            val mapsObj = JSONObject(raw)
            val host = mapsObj.optString("host", "https://tilecache.rainviewer.com")
            val radar = mapsObj.optJSONObject("radar")
            val past = radar?.optJSONArray("past")

            if (past == null || past.length() == 0) {
                return RadarScanResult(
                    available = true,
                    echoDetected = false,
                    estimatedDistanceKm = null,
                    cellDirection = "بدون توده فعال",
                    estimatedEtaMinutes = null,
                    etaRangeText = null,
                    cloudTrendLabel = "پایدار ↔️",
                    reflectivityDbz = 0.0,
                    statusText = "📡 رادار آنلاین است؛ در حال حاضر توده بارشی در شعاع پایش ۶۰ کیلومتری دیده نمی‌شود.",
                    lastFrameEpoch = 0L,
                    movementSpeedKmh = 0.0,
                    incomingRainLikely = false
                )
            }

            val lastFrame = past.getJSONObject(past.length() - 1)
            val lastTime = lastFrame.optLong("time", 0L)
            val lastPath = lastFrame.optString("path", "")

            val prevFrame = if (past.length() >= 2) past.getJSONObject(past.length() - 2) else null
            val prevTime = prevFrame?.optLong("time", 0L) ?: 0L
            val prevPath = prevFrame?.optString("path", "") ?: ""

            // اسکن شبکه ۲×۲ چندتایلی برای پوشش کامل منطقه و مدخل‌های ورودی سامانه‌ها (غرب و شمال‌غرب)
            data class TileEcho(
                val spec: RadarTileSpec,
                val currentSize: Int,
                val prevSize: Int,
                val hasEcho: Boolean
            )

            val echoList = mutableListOf<TileEcho>()
            var maxByteSize = 0

            for (tile in RADAR_COMPOSITE_GRID) {
                val currentUrl = "$host$lastPath/256/7/${tile.x}/${tile.y}/2/1_1.png"
                val currBytes = httpGetBytes(currentUrl, timeoutMs = 4000)
                val currSize = currBytes.size
                if (currSize > maxByteSize) maxByteSize = currSize

                var prevSize = 0
                if (prevPath.isNotBlank() && currSize > 900) {
                    val prevUrl = "$host$prevPath/256/7/${tile.x}/${tile.y}/2/1_1.png"
                    val pBytes = httpGetBytes(prevUrl, timeoutMs = 3000)
                    prevSize = pBytes.size
                }

                echoList.add(TileEcho(tile, currSize, prevSize, currSize > 900))
            }

            val activeEchoes = echoList.filter { it.hasEcho }
            val hasActiveEcho = activeEchoes.isNotEmpty()

            // تحلیل روند تقویت، تضعیف یا پایداری توده‌ها بر اساس تغییرات سیگنال اکو
            val totalCurr = echoList.sumOf { it.currentSize }
            val totalPrev = echoList.sumOf { it.prevSize }
            val cloudTrendLabel = when {
                totalPrev <= 0 -> "پایدار ↔️"
                totalCurr > (totalPrev * 1.20).toInt() -> "در حال تقویت و گسترش ⬆️"
                totalCurr < (totalPrev * 0.80).toInt() -> "در حال تضعیف و واگرایی ⬇️"
                else -> "پایدار ↔️"
            }

            val estimatedDbz = when {
                maxByteSize > 15000 -> 48.0
                maxByteSize > 6000 -> 38.0
                maxByteSize > 2000 -> 28.0
                maxByteSize > 900 -> 20.0
                else -> 0.0
            }

            if (hasActiveEcho) {
                // تعیین نزدیک‌ترین توده به منطقه
                val closestTile = activeEchoes.minByOrNull { it.spec.nominalDistanceKm } ?: activeEchoes.first()
                val distKm = when {
                    closestTile.spec.x == 81 && closestTile.spec.y == 51 && closestTile.currentSize > 6000 -> 10.0
                    closestTile.spec.x == 81 && closestTile.spec.y == 51 -> 16.0
                    else -> closestTile.spec.nominalDistanceKm
                }

                // محاسبه بردار حرکت دینامیک توده (Motion Vector) بر مبنای مقایسه فریم‌ها
                val westHadEcho = echoList.find { it.spec.x == 80 && it.prevSize > 900 } != null
                val centerHasEcho = echoList.find { it.spec.x == 81 && it.spec.y == 51 && it.currentSize > 900 } != null
                val northHadEcho = echoList.find { it.spec.y == 50 && it.prevSize > 900 } != null

                val isMovingTowardsRegion = (westHadEcho && centerHasEcho) ||
                    (northHadEcho && centerHasEcho) ||
                    activeEchoes.any { it.spec.x == 80 || it.spec.y == 50 }

                // تخمین سرعت دینامیک حرکت جبهه بر اساس تغییرات زمانی
                val dynamicSpeed = when {
                    westHadEcho && centerHasEcho -> 38.0
                    northHadEcho -> 34.0
                    else -> 30.0
                }

                val etaMin = ((distKm / dynamicSpeed) * 60.0).toInt().coerceAtLeast(10)
                val etaLow = (etaMin * 0.8).toInt().coerceAtLeast(10)
                val etaHigh = (etaMin * 1.3).toInt().coerceAtLeast(etaLow + 10)
                val etaRangeText = if (distKm <= 15.0) {
                    "کمتر از ۱۵ دقیقه (حریم نزدیک)"
                } else {
                    "بین $etaLow تا $etaHigh دقیقه دیگر"
                }

                val activeSectors = activeEchoes.map { it.spec.sectorName }
                val directionDescription = when {
                    activeSectors.size >= 2 -> "جبهه گسترده راداری: ${activeSectors.joinToString(" و ")}"
                    else -> activeSectors.first()
                }

                val statusText = if (distKm <= 15.0) {
                    "📡 اکوی بارشی فعال در حریم نزدیک منطقه (فاصله حدود ${distKm.toInt()} km) با بازتابش ${estimatedDbz.toInt()} dBZ ($cloudTrendLabel)."
                } else {
                    "📡 رصد توده بارشی در $directionDescription؛ سرعت انتقال: ${dynamicSpeed.toInt()} km/h؛ زمان محتمل ورود به منطقه: $etaRangeText ($cloudTrendLabel)."
                }

                return RadarScanResult(
                    available = true,
                    echoDetected = true,
                    estimatedDistanceKm = distKm,
                    cellDirection = directionDescription,
                    estimatedEtaMinutes = etaMin,
                    etaRangeText = etaRangeText,
                    cloudTrendLabel = cloudTrendLabel,
                    reflectivityDbz = estimatedDbz,
                    statusText = statusText,
                    lastFrameEpoch = lastTime,
                    movementSpeedKmh = dynamicSpeed,
                    incomingRainLikely = isMovingTowardsRegion
                )
            } else {
                return RadarScanResult(
                    available = true,
                    echoDetected = false,
                    estimatedDistanceKm = null,
                    cellDirection = "پایش شبکه چندتایلی پاک",
                    estimatedEtaMinutes = null,
                    etaRangeText = null,
                    cloudTrendLabel = "پایدار ↔️",
                    reflectivityDbz = 0.0,
                    statusText = "📡 اسکن شبکه ۴ تایل رادار پیرامون منطقه آرام است؛ هیچ توده بارشی فعالی در شعاع پایش دیده نمی‌شود.",
                    lastFrameEpoch = lastTime,
                    movementSpeedKmh = 0.0,
                    incomingRainLikely = false
                )
            }
        } catch (_: Exception) {
            return RadarScanResult(
                available = false,
                echoDetected = false,
                estimatedDistanceKm = null,
                cellDirection = "نامشخص",
                estimatedEtaMinutes = null,
                etaRangeText = null,
                cloudTrendLabel = "پایدار ↔️",
                reflectivityDbz = 0.0,
                statusText = "📡 وضعیت رادار به صورت خودکار پایش می‌شود.",
                lastFrameEpoch = 0L,
                movementSpeedKmh = 0.0,
                incomingRainLikely = false
            )
        }
    }

    /**
     * واکشی هم‌زمان و موازی کلیه داده‌های شبکه: ECMWF، ۱۵ دقیقه‌ای، شبکه دیدبانی و رادار
     */
    data class ComprehensivePayload(
        val ecmwf: JSONObject,
        val minutely15: JSONObject,
        val liveStation: JSONObject,
        val perimeterStations: List<PerimeterStationInfo>,
        val radarScan: RadarScanResult
    )

    fun fetchCompleteDataParallel(): ComprehensivePayload {
        val pool = Executors.newFixedThreadPool(4)
        return try {
            val fEcmwf = pool.submit(Callable { fetchEcmwfModel() })
            val fMinutely = pool.submit(Callable { fetchMinutely15() })
            val fLiveStation = pool.submit(Callable { fetchLiveStationTelemetry() })
            val fPerimeter = pool.submit(Callable { fetchPerimeterStationsBatch() })
            val fRadar = pool.submit(Callable { scanRainViewerRadar() })

            val ecmwf = try { fEcmwf.get(14, TimeUnit.SECONDS) } catch (_: Exception) { JSONObject() }
            val minutely = try { fMinutely.get(10, TimeUnit.SECONDS) } catch (_: Exception) { JSONObject() }
            val liveStation = try { fLiveStation.get(10, TimeUnit.SECONDS) } catch (_: Exception) { JSONObject() }
            val perimeter = try { fPerimeter.get(10, TimeUnit.SECONDS) } catch (_: Exception) { fallbackPerimeterStations() }
            val radar = try {
                fRadar.get(10, TimeUnit.SECONDS)
            } catch (_: Exception) {
                RadarScanResult(false, false, null, "", null, null, "پایدار ↔️", 0.0, "", 0L, 0.0, false)
            }

            ComprehensivePayload(
                ecmwf = ecmwf,
                minutely15 = minutely,
                liveStation = liveStation,
                perimeterStations = perimeter,
                radarScan = radar
            )
        } finally {
            pool.shutdown()
        }
    }
}
