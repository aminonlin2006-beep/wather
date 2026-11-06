package com.amin.weather.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WeatherRepository(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, "forecast_cache.json")

    suspend fun getCachedForecast(): ForecastDashboard? = withContext(Dispatchers.IO) {
        try {
            if (!cacheFile.exists()) return@withContext null
            val raw = cacheFile.readText()
            if (raw.isBlank()) return@withContext null
            parseDashboard(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshForecast(): Result<ForecastDashboard> = withContext(Dispatchers.IO) {
        try {
            val json = ForecastEngine.buildDashboardData()
            val dashboard = parseDashboard(json)
            val hourlyCount = dashboard.ranges[ForecastRange.HOURLY]?.size ?: 0
            if (hourlyCount > 0) {
                try {
                    cacheFile.writeText(json.toString())
                } catch (_: Exception) {
                    // Ignore cache write errors
                }
            }
            Result.success(dashboard)
        } catch (e: Exception) {
            // در صورت بروز خطای شبکه، اگر داده‌های قبلی کش موجود باشد آن را بازیابی کن
            val fallback = try {
                if (cacheFile.exists()) {
                    val raw = cacheFile.readText()
                    if (raw.isNotBlank()) parseDashboard(JSONObject(raw)) else null
                } else null
            } catch (_: Exception) {
                null
            }
            if (fallback != null && (fallback.ranges[ForecastRange.HOURLY]?.size ?: 0) > 0) {
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun parseDashboard(d: JSONObject): ForecastDashboard {
        val meta = d.optJSONObject("meta") ?: JSONObject()
        val location = meta.optString("location", "منطقه (شعاع پایش ۶۰ کیلومتر)")
        val lat = meta.optDouble("lat", OpenMeteoClient.LAT)
        val lon = meta.optDouble("lon", OpenMeteoClient.LON)
        val generatedIran = meta.optString("generated_iran", "")

        val verdictObj = d.optJSONObject("verdict") ?: JSONObject()
        val tierStr = verdictObj.optString("tier", "green")
        val alertTier = when (tierStr.lowercase()) {
            "red" -> AlertTier.RED
            "orange" -> AlertTier.ORANGE
            "yellow" -> AlertTier.YELLOW
            else -> AlertTier.GREEN
        }
        val verdict = VerdictInfo(
            tier = alertTier,
            text = verdictObj.optString("text", "آسمان آرام و پایدار در منطقه"),
            advisoryNote = verdictObj.optString("advisory_note", "")
        )

        // رادار ناواست ۶۰ کیلومتری
        val radarObj = d.optJSONObject("radar_nowcast") ?: JSONObject()
        val rTierStr = radarObj.optString("tier", tierStr)
        val rTier = when (rTierStr.lowercase()) {
            "red" -> AlertTier.RED
            "orange" -> AlertTier.ORANGE
            "yellow" -> AlertTier.YELLOW
            else -> AlertTier.GREEN
        }
        val radarNowcast = RadarNowcastInfo(
            available = radarObj.optBoolean("available", false),
            tier = rTier,
            echoDetected = radarObj.optBoolean("echo_detected", false),
            estimatedDistanceKm = radarObj.optNullableDouble("estimated_distance_km"),
            cellAzimuthDirection = radarObj.optString("cell_azimuth_direction", "شعاع ۶۰ کیلومتر پاک"),
            estimatedEtaMinutes = if (radarObj.has("estimated_eta_minutes") && !radarObj.isNull("estimated_eta_minutes")) {
                radarObj.optInt("estimated_eta_minutes")
            } else null,
            reflectivityDbz = radarObj.optDouble("reflectivity_dbz", 0.0),
            statusText = radarObj.optString("status_text", ""),
            lastFrameIran = radarObj.optString("last_frame_iran", ""),
            movementSpeedKmh = radarObj.optDouble("movement_speed_kmh", 0.0),
            incomingRainLikely = radarObj.optBoolean("incoming_rain_likely", false),
            scanRadiusKm = radarObj.optInt("scan_radius_km", 60)
        )

        // شبکه دیدبانی پنج‌ضلعی پیرامونی (شوش، اندیمشک، حسینیه، گتوند، دزفول)
        val perimObj = d.optJSONObject("perimeter_network") ?: JSONObject()
        val perimStationsArr = perimObj.optJSONArray("stations") ?: JSONArray()
        val perimStationsList = mutableListOf<PerimeterStationInfo>()
        for (i in 0 until perimStationsArr.length()) {
            val pj = perimStationsArr.optJSONObject(i) ?: continue
            perimStationsList.add(
                PerimeterStationInfo(
                    id = pj.optString("id", ""),
                    name = pj.optString("name", ""),
                    sector = pj.optString("sector", ""),
                    distanceKm = pj.optInt("distance_km", 0),
                    temp = pj.optNullableDouble("temp"),
                    humidity = pj.optNullableDouble("humidity"),
                    windGust = pj.optNullableDouble("wind_gust"),
                    precipitation = pj.optNullableDouble("precipitation"),
                    isRaining = pj.optBoolean("is_raining", false),
                    statusText = pj.optString("status_text", "آرام ☀️"),
                    windSpeed = pj.optNullableDouble("wind_speed") ?: pj.optNullableDouble("wind_speed_kmh"),
                    windDirection = pj.optNullableDouble("wind_direction") ?: pj.optNullableDouble("wind_direction_deg")
                )
            )
        }
        val perimeterNetwork = PerimeterNetworkSummary(
            stations = perimStationsList,
            activeRainStationsCount = perimObj.optInt("active_rain_stations_count", 0),
            activeGustStationsCount = perimObj.optInt("active_gust_stations_count", 0),
            threatDirection = perimObj.optString("threat_direction").takeIf { it.isNotBlank() },
            networkStatusSummary = perimObj.optString("network_status_summary", "")
        )

        // شبکه هندسی و مثلث‌بندی راداری دزفول (اندیمشک، شوش، گتوند)
        val triObj = d.optJSONObject("triangulation") ?: JSONObject()
        val triVerticesArr = triObj.optJSONArray("vertices") ?: JSONArray()
        val triVerticesList = mutableListOf<TriangulationVertex>()
        for (i in 0 until triVerticesArr.length()) {
            val vj = triVerticesArr.optJSONObject(i) ?: continue
            triVerticesList.add(
                TriangulationVertex(
                    id = vj.optString("id", ""),
                    name = vj.optString("name", ""),
                    sector = vj.optString("sector", ""),
                    lat = vj.optDouble("lat", 0.0),
                    lon = vj.optDouble("lon", 0.0),
                    xKm = vj.optDouble("x_km", 0.0),
                    yKm = vj.optDouble("y_km", 0.0),
                    distanceKm = vj.optDouble("distance_km", 0.0),
                    barycentricWeight = vj.optDouble("barycentric_weight", 0.33),
                    rainRateMmH = vj.optDouble("rain_rate_mm_h", 0.0),
                    rainProbability = vj.optDouble("rain_probability", 0.0),
                    temp = vj.optNullableDouble("temp"),
                    humidity = vj.optNullableDouble("humidity"),
                    windSpeedKmh = vj.optNullableDouble("wind_speed_kmh"),
                    windGustKmh = vj.optNullableDouble("wind_gust_kmh"),
                    windDirectionDeg = vj.optNullableDouble("wind_direction_deg"),
                    isRaining = vj.optBoolean("is_raining", false),
                    statusText = vj.optString("status_text", "آرام")
                )
            )
        }

        val triangulation = SpatialTriangulationResult(
            primaryStationName = triObj.optString("primary_station_name", "مرکز منطقه (مختصات مبنا: ۰,۰)"),
            vertices = triVerticesList,
            spatialPrecipProbability = triObj.optDouble("spatial_precip_probability", 0.0),
            spatialRainRateMmH = triObj.optDouble("spatial_rain_rate_mm_h", 0.0),
            gradientMagnitude = triObj.optDouble("gradient_magnitude", 0.0),
            gradientDirectionDeg = triObj.optDouble("gradient_direction_deg", 0.0),
            gradientDirectionLabel = triObj.optString("gradient_direction_label", "بدون جریان بارشی"),
            advectionFluxPerHour = triObj.optDouble("advection_flux_per_hour", 0.0),
            triangleAreaSqKm = triObj.optDouble("triangle_area_sq_km", 754.3),
            isInsideTriangle = triObj.optBoolean("is_inside_triangle", true),
            convergenceSummary = triObj.optString("convergence_summary", "پایش مداوم شبکه دیدبانی پیرامون منطقه"),
            threatVertexName = triObj.optString("threat_vertex_name").takeIf { it.isNotBlank() }
        )

        // تله‌متری ایستگاه مرکزی منطقه
        val stationObj = d.optJSONObject("station_telemetry") ?: JSONObject()
        val stationTelemetry = StationTelemetryInfo(
            stationName = stationObj.optString("station_name", "ایستگاه مرکزی هواشناسی منطقه"),
            currentTemp = stationObj.optNullableDouble("current_temp"),
            currentHumidity = stationObj.optNullableDouble("current_humidity"),
            currentDewPoint = stationObj.optNullableDouble("current_dew_point"),
            dewPointDepression = stationObj.optNullableDouble("dew_point_depression"),
            currentWeatherDesc = stationObj.optString("current_weather_desc").takeIf { it.isNotBlank() },
            currentWindSpeed = stationObj.optNullableDouble("current_wind_speed"),
            currentWindGust = stationObj.optNullableDouble("current_wind_gust"),
            currentCloudCover = stationObj.optNullableDouble("current_cloud_cover"),
            observedPrecipitation = stationObj.optNullableDouble("observed_precipitation"),
            gustFrontRisk = stationObj.optBoolean("gust_front_risk", false)
        )

        // خلاصه الگو ۲۴ ساعته
        val patObj = d.optJSONObject("pattern") ?: JSONObject()
        val pattern = PatternSummary(
            windowStartIran = patObj.optString("window_start_iran", ""),
            windowEndIran = patObj.optString("window_end_iran", ""),
            peakPrecipTimeIran = patObj.optString("peak_precip_time_iran", "—"),
            peakPrecipMm = patObj.optDouble("peak_precip_mm", 0.0),
            peakTsTimeIran = patObj.optString("peak_ts_time_iran", "—"),
            peakConvectiveScore = patObj.optDouble("peak_convective_score", 0.0),
            maxCape = patObj.optDouble("max_cape", 0.0),
            totalPrecip24hLikely = patObj.optDouble("total_precip_24h_likely", 0.0),
            totalPrecip24hMax = patObj.optDouble("total_precip_24h_max", 0.0),
            maxProb24h = patObj.optDouble("max_prob_24h", 0.0),
            maxGust24h = patObj.optDouble("max_gust_24h", 0.0),
            precipSummaryText = patObj.optString("precip_summary_text", ""),
            convectiveHoursCount = patObj.optInt("convective_hours_count", 0),
            virgaHoursCount = patObj.optInt("virga_hours_count", 0)
        )

        val rangesObj = d.optJSONObject("ranges") ?: JSONObject()
        val rangesMap = mutableMapOf<ForecastRange, List<ForecastRow>>()
        for (range in ForecastRange.entries) {
            val arr = rangesObj.optJSONArray(range.key) ?: JSONArray()
            val list = mutableListOf<ForecastRow>()
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                list.add(parseRow(row))
            }
            rangesMap[range] = list
        }

        return ForecastDashboard(
            location = location,
            lat = lat,
            lon = lon,
            generatedIran = generatedIran,
            verdict = verdict,
            radarNowcast = radarNowcast,
            perimeterNetwork = perimeterNetwork,
            stationTelemetry = stationTelemetry,
            pattern = pattern,
            ranges = rangesMap,
            triangulation = triangulation
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }

    private fun parseRow(row: JSONObject): ForecastRow {
        val timeIran = when {
            row.has("time_iran") && !row.isNull("time_iran") -> row.optString("time_iran")
            row.has("date_iran") && !row.isNull("date_iran") -> row.optString("date_iran")
            else -> ""
        }
        val source = row.optString("forecast_source", "🇪🇺 مدل اروپایی ECMWF")
        val precipMax = row.optNullableDouble("precip_max") ?: row.optNullableDouble("precipitation_max")
        val precipMean = row.optNullableDouble("precip_mean") ?: row.optNullableDouble("precipitation_mean")
        val tempMean = row.optNullableDouble("temp_mean") ?: row.optNullableDouble("temperature_2m_mean")
        val precipProb = row.optNullableDouble("precip_prob") ?: row.optNullableDouble("precipitation_probability")
        val convIndex = row.optNullableDouble("conv_index") ?: row.optNullableDouble("conv_potential")
        val thunderstormScore = row.optNullableDouble("thunderstorm_score") ?: convIndex
        val windSpeed = row.optNullableDouble("wind_speed") ?: row.optNullableDouble("windspeed_10m_mean")
        val windGust = row.optNullableDouble("wind_gust") ?: row.optNullableDouble("windgusts_10m_mean")
        val windDirLabel = if (row.has("wind_dir_label") && !row.isNull("wind_dir_label")) {
            row.optString("wind_dir_label")
        } else null
        val cloudCover = row.optNullableDouble("cloud_cover") ?: row.optNullableDouble("cloudcover_mean")
        val convectiveRisk = row.optBoolean("convective_risk", (convIndex ?: 0.0) >= 30.0 || (precipMax ?: 0.0) >= 0.8)
        val isVirgaRisk = row.optBoolean("is_virga_risk", false)
        val precipRange = row.optString("precip_range", if ((precipMax ?: 0.0) > 0.05) "۰ تا ${precipMax}" else "۰")
        val rainTypeLabel = row.optString("rain_type_label", "بدون بارش ☀️")
        val dewPoint = row.optNullableDouble("dew_point")
        val dewPointDepression = row.optNullableDouble("dew_point_depression")
        val capeValue = row.optNullableDouble("cape_value")

        return ForecastRow(
            timeIran = timeIran,
            forecastSource = source,
            tempMean = tempMean,
            precipMean = precipMean,
            precipMax = precipMax,
            precipProb = precipProb,
            precipRange = precipRange,
            convIndex = convIndex,
            thunderstormScore = thunderstormScore,
            windSpeed = windSpeed,
            windGust = windGust,
            windDirLabel = windDirLabel,
            cloudCover = cloudCover,
            rainTypeLabel = rainTypeLabel,
            convectiveRisk = convectiveRisk,
            isVirgaRisk = isVirgaRisk,
            dewPoint = dewPoint,
            dewPointDepression = dewPointDepression,
            capeValue = capeValue
        )
    }
}
