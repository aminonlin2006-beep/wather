package com.amin.weather.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * موتور یکپارچه و بهینه‌سازی‌شده پیش‌بینی هواشناسی دزفول:
 * ۱. تلفیق اسکن راداری ۶۰ کیلومتری با شبکه دیدبانی پنج‌ضلعی پیرامونی (شوش، اندیمشک، حسینیه، گتوند، دزفول)
 * ۲. سامانه هشدار ۴ سطحی هوشمند (سبز، زرد، نارنجی، قرمز) با تعیین جهت و فاصله تهدید
 * ۳. شاخص همرفتی شدید (SCI) بر مبنای مدل برتر ECMWF IFS برای افق ۳ تا ۲۴ ساعت
 * ۴. بازه‌های زمانی کاربردی (۱، ۳، ۶، ۱۲ و ۲۴ ساعت آینده)
 */
object ForecastEngine {

    private val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val iranFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT+03:30")
    }

    fun utcToIran(utcIso: String?): String {
        if (utcIso.isNullOrBlank()) return ""
        return try {
            val clean = utcIso.take(16).replace(' ', 'T')
            val date = utcFormat.parse(clean) ?: return utcIso.replace('T', ' ')
            iranFormat.format(date)
        } catch (_: Exception) {
            utcIso.replace('T', ' ')
        }
    }

    private fun arr(obj: JSONObject?, key: String): JSONArray? = obj?.optJSONArray(key)

    private fun optD(arr: JSONArray?, i: Int): Double? {
        if (arr == null || i < 0 || i >= arr.length()) return null
        val v = arr.opt(i)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun r2(v: Double?): Double? = v?.let { Math.round(it * 100.0) / 100.0 }

    private fun r1(v: Double?): Double? = v?.let { Math.round(it * 10.0) / 10.0 }

    /**
     * فرمول مگنوس برای محاسبه نقطه شبنم
     */
    fun calculateDewPoint(temp: Double, rh: Double): Double {
        val a = 17.27
        val b = 237.7
        val clampedRh = rh.coerceIn(1.0, 100.0)
        val alpha = ((a * temp) / (b + temp)) + Math.log(clampedRh / 100.0)
        val denom = a - alpha
        return if (Math.abs(denom) > 1e-6) (b * alpha) / denom else temp
    }

    private val WIND_DIRS = listOf(
        "شمالی", "شمال شمال شرقی", "شمال شرقی", "شرق شمال شرقی",
        "شرقی", "شرق جنوب شرقی", "جنوب شرقی", "جنوب جنوب شرقی",
        "جنوبی", "جنوب جنوب غربی", "جنوب غربی", "غرب جنوب غربی",
        "غربی", "غرب شمال غربی", "شمال غربی", "شمال شمال غربی"
    )

    private fun windDirLabel(deg: Double?): String {
        if (deg == null) return "—"
        val idx = (((deg + 11.25) / 22.5).toInt()) % 16
        return WIND_DIRS[if (idx < 0) idx + 16 else idx]
    }

    /**
     * محاسبه شاخص همرفتی شدید (SCI) دشت خوزستان
     */
    fun computeSevereConvectiveIndex(
        cape: Double?,
        li: Double?,
        dewPointDepression: Double,
        cloudMid: Double?,
        gustSpeed: Double?,
        rh: Double
    ): Double {
        var score = 0.0

        val c = cape ?: 0.0
        if (c > 100.0) {
            score += (c / 35.0).coerceAtMost(35.0)
        }

        val l = li ?: 0.0
        if (l < 0) {
            score += (-l * 4.0).coerceAtMost(20.0)
        }

        val cm = cloudMid ?: 0.0
        if (cm > 30.0) {
            score += ((cm - 30.0) / 4.0).coerceAtMost(18.0)
        }

        val g = gustSpeed ?: 0.0
        if (g > 25.0) {
            score += ((g - 25.0) * 0.7).coerceAtMost(17.0)
        }

        if (rh in 45.0..85.0) {
            score += 10.0
        }

        return Math.min(99.0, Math.round(score * 10.0) / 10.0)
    }

    fun getEventLabel(
        sci: Double,
        precip: Double,
        precipMax: Double,
        isVirga: Boolean,
        isRadarActive: Boolean
    ): String {
        return when {
            isRadarActive -> "رگبار راداری در راه 📡"
            isVirga && precip < 0.3 -> "تبخیر زیر ابر (ویرگا) 🌫️"
            sci >= 50.0 || (precipMax >= 1.5 && sci >= 35.0) -> "رگبار همرفتی و صاعقه ⚡"
            sci >= 25.0 || precipMax >= 0.5 -> "رگبار موضعی پراکنده 🌦️"
            precip >= 2.0 -> "بارش باران 🌧️"
            precip >= 0.3 -> "بارش ملایم 🌦️"
            precip >= 0.05 -> "نم‌نم باران 🌦️"
            sci >= 15.0 -> "پتانسیل همرفتی محلی ☁️"
            else -> "بدون بارش ☀️"
        }
    }

    /**
     * واکشی و ساخت پیش‌بینی یکپارچه با شبکه دیدبانی پنج‌ضلعی و مدل ECMWF
     */
    fun buildDashboardData(): JSONObject {
        val payload = OpenMeteoClient.fetchCompleteDataParallel()
        val ecmwf = payload.ecmwf
        val hourlyObj = ecmwf.optJSONObject("hourly")
            ?: throw java.io.IOException("خطا در ارتباط با سرور اروپایی ECMWF. لطفاً اتصال اینترنت را بررسی نمایید.")

        val times = hourlyObj.optJSONArray("time")
            ?: throw java.io.IOException("عدم دریافت جدول زمانی از سرور هواشناسی.")

        val nowUtcIso = utcFormat.format(Date())
        val nowHour = nowUtcIso.take(13)

        // تله‌متری زنده فرودگاه دزفول
        val st = payload.liveStation
        val liveTemp = st.optDouble("temperature_2m", Double.NaN).takeIf { !it.isNaN() }
        val liveRh = st.optDouble("relative_humidity_2m", Double.NaN).takeIf { !it.isNaN() }
        val liveDp = st.optDouble("dew_point_2m", Double.NaN).takeIf { !it.isNaN() }
        val liveWind = st.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() }
        val liveGust = st.optDouble("wind_gusts_10m", Double.NaN).takeIf { !it.isNaN() }
        val liveCloud = st.optDouble("cloud_cover", Double.NaN).takeIf { !it.isNaN() }
        val livePrecip = st.optDouble("precipitation", 0.0)
        val liveCode = st.optInt("weather_code", 0)

        val liveDpDepression = if (liveTemp != null && liveDp != null) {
            Math.max(0.0, liveTemp - liveDp)
        } else if (liveTemp != null && liveRh != null) {
            Math.max(0.0, liveTemp - calculateDewPoint(liveTemp, liveRh))
        } else null

        // تفکیک پایگاه‌های حلقه پیرامونی نزدیک از ایستگاه‌های پیش‌هشدار بالادست
        val perimeter = payload.perimeterStations
        val localPerimeter = perimeter.filter { !it.isUpstreamWarning }
        val upstreamStations = perimeter.filter { it.isUpstreamWarning }

        val rainingStations = localPerimeter.filter { it.isRaining }
        val gustyStations = localPerimeter.filter { (it.windGust ?: 0.0) >= 32.0 }
        val threatDirections = rainingStations.map { "${it.name} (${it.sector})" }

        val upstreamRaining = upstreamStations.filter { it.isRaining }
        val upstreamGusty = upstreamStations.filter { (it.windGust ?: 0.0) >= 35.0 }
        val distantWarningStationsCount = upstreamRaining.size + upstreamGusty.size

        val distantWarningSummary: String? = when {
            upstreamRaining.isNotEmpty() ->
                "🌧️ ثبت بارش فعال در پایگاه بالادست ${upstreamRaining.joinToString("، ") { it.name }}؛ احتمال حرکت سامانه به دشت طی ساعات آینده."
            upstreamGusty.isNotEmpty() ->
                "💨 وزش تندباد شدید در پایگاه بالادست ${upstreamGusty.joinToString("، ") { it.name }}؛ هشدار اولیه عبور جبهه باد و گردوخاک."
            else -> null
        }

        val networkSummaryText = when {
            rainingStations.size >= 2 ->
                "⚠️ هشدار جبهه بارشی: بارش فعال هم‌زمان در ${threatDirections.joinToString(" و ")} ثبت شده و در حال پیشروی است."
            rainingStations.isNotEmpty() ->
                "🌧️ بارش فعال در ${threatDirections.first()} در فاصله نزدیک گزارش شد."
            distantWarningSummary != null ->
                distantWarningSummary
            gustyStations.isNotEmpty() ->
                "💨 وزش تندباد لحظه‌ای در ${gustyStations.map { it.name }.joinToString(" و ")} (احتمال پیش‌قراول جبهه هوای ناپایدار)."
            else ->
                "تمامی پایگاه‌های دیدبانی پیرامونی شرایط آرامی ثبت کرده‌اند."
        }

        // اسکن رادار ۶۰ کیلومتر
        val radar = payload.radarScan
        val isRadarEcho = radar.echoDetected || rainingStations.isNotEmpty() || livePrecip > 0.05 || liveCode in 51..99

        // شناسایی احتمال پدیده ویرگا (تبخیر باران در لایه خشک زیرین جو و تبدیل به تندباد سطحی)
        val isVirgaLikely = isRadarEcho && (liveDpDepression ?: 0.0) >= 10.0 && (liveRh ?: 50.0) <= 45.0

        // محاسبات مثلث‌بندی راداری و پایگاه‌های همجوار
        val triangulationJson = computeSpatialTriangulation(perimeter, radar, st)
        val sppScore = triangulationJson.optDouble("spatial_precip_probability", 0.0)

        // تعیین هوشمند سطح هشدار چهارگانه (AlertTier)
        val alertTier: AlertTier
        val verdictText: String
        val advisoryNote: String

        when {
            // ۱. سطح قرمز: رگبار در منطقه یا کمتر از ۱۵ کیلومتر یا احتمال فضایی بسیار بالا
            livePrecip > 0.2 || (isRadarEcho && radar.estimatedDistanceKm != null && radar.estimatedDistanceKm <= 15.0) || (sppScore >= 85.0 && isRadarEcho) -> {
                alertTier = AlertTier.RED
                verdictText = "هشدار قرمز: رگبار شدید باران، تندباد لحظه‌ای و صاعقه در محدوده منطقه (احتمال بارش ${JalaliCalendar.formatNumber(sppScore)}٪)."
                advisoryNote = if (isVirgaLikely) {
                    "توده فعال بر فراز منطقه است؛ به دلیل خشکی لایه زیرین احتمال تبخیر باران در هوا (ویرگا) و وزش تندبادهای ناگهانی شدید و خاک وجود دارد."
                } else {
                    "توده فعال بر روی منطقه قرار دارد؛ از تردد غیرضروری در فضای باز و بستر رودخانه‌ها خودداری فرمایید."
                }
            }
            // ۲. سطح نارنجی: توده فعال راداری یا گزارش باران در ایستگاه‌های همجوار در فاصله ۳۰ تا ۵۰ کیلومتر یا SPP بالای ۶۰٪
            isRadarEcho || rainingStations.isNotEmpty() || sppScore >= 60.0 -> {
                val dist = radar.estimatedDistanceKm?.toInt() ?: if (rainingStations.isNotEmpty()) rainingStations.first().distanceKm else 35
                val eta = radar.estimatedEtaMinutes ?: ((dist / 32.0) * 60).toInt().coerceAtLeast(15)
                val etaRange = radar.etaRangeText ?: "حدود $eta دقیقه دیگر"
                val dir = if (rainingStations.isNotEmpty()) threatDirections.first() else radar.cellDirection
                alertTier = AlertTier.ORANGE
                verdictText = "هشدار نارنجی: توده بارشی فعال در فاصله تقریبی $dist کیلومتری ($dir) به سمت منطقه در حال حرکت است (احتمال بارش ${JalaliCalendar.formatNumber(sppScore)}٪)."
                advisoryNote = if (isVirgaLikely) {
                    "زمان محتمل ورود: $etaRange (${radar.cloudTrendLabel}). احتمال تبخیر قطرات در هوا و وزش تندباد شدید موضعی."
                } else {
                    "زمان محتمل ورود: $etaRange (${radar.cloudTrendLabel}). احتمال وقوع رگبار و تندباد ناگهانی."
                }
            }
            else -> {
                alertTier = AlertTier.GREEN
                verdictText = "آسمان آرام و پایدار در منطقه و نقاط همجوار؛ هیچ توده بارشی فعالی در شعاع ۶۰ کیلومتری دیده نمی‌شود."
                advisoryNote = if (distantWarningSummary != null) {
                    "ایستگاه‌های دوربرد بالادست تحرکاتی ثبت کرده‌اند که روند آن به طور مداوم پایش می‌شود."
                } else {
                    "شرایط با اسکن مستمر رادار و شبکه ایستگاه‌های پیرامون پایش می‌شود."
                }
            }
        }

        // پردازش ردیف‌های ۲۴ ساعت آینده
        var startIdx = 0
        for (i in 0 until times.length()) {
            if (times.optString(i).take(13) >= nowHour) {
                startIdx = i
                break
            }
        }

        val rows24 = JSONArray()
        val endIdx = Math.min(startIdx + 24, times.length())

        var peakPrecipMm = 0.0
        var peakPrecipTime = ""
        var peakTsTime = ""
        var peakSci = 0.0
        var maxCape = 0.0
        var maxGust = 0.0
        var sumPrecipLikely = 0.0
        var sumPrecipMax = 0.0
        var virgaCount = 0
        var convectiveCount = 0

        for (idx in startIdx until endIdx) {
            val tUtc = times.optString(idx)
            val tIran = utcToIran(tUtc)
            val temp = optD(arr(hourlyObj, "temperature_2m"), idx) ?: 24.0
            val rawP = optD(arr(hourlyObj, "precipitation"), idx) ?: 0.0
            val rain = optD(arr(hourlyObj, "rain"), idx) ?: 0.0
            val showers = optD(arr(hourlyObj, "showers"), idx) ?: 0.0
            val cape = optD(arr(hourlyObj, "cape"), idx)
            val li = optD(arr(hourlyObj, "lifted_index"), idx)
            val cloud = optD(arr(hourlyObj, "cloudcover"), idx) ?: 20.0
            val cloudMid = optD(arr(hourlyObj, "cloudcover_mid"), idx) ?: 0.0
            val windSpeed = optD(arr(hourlyObj, "windspeed_10m"), idx)
            val windGust = optD(arr(hourlyObj, "windgusts_10m"), idx)
            val windDir = optD(arr(hourlyObj, "winddirection_10m"), idx)
            val rh = optD(arr(hourlyObj, "relativehumidity_2m"), idx) ?: 50.0
            val dewPoint = optD(arr(hourlyObj, "dew_point_2m"), idx) ?: calculateDewPoint(temp, rh)
            val dpDepression = Math.max(0.0, temp - dewPoint)

            val sci = computeSevereConvectiveIndex(cape, li, dpDepression, cloudMid, windGust, rh)
            if (sci > peakSci) { peakSci = sci; peakTsTime = tIran }
            if ((cape ?: 0.0) > maxCape) maxCape = cape ?: 0.0
            if ((windGust ?: 0.0) > maxGust) maxGust = windGust ?: 0.0

            val isVirga = dpDepression >= 12.0 && rh <= 40.0 && (rawP > 0.0 || sci >= 25.0)
            if (isVirga) virgaCount++

            val isNowcastWindow = (idx - startIdx) < 3
            val isRadarInfluenced = isNowcastWindow && (isRadarEcho || sppScore >= 50.0)

            var pLikely = rawP
            var pMax = Math.max(rawP, showers)
            val sourceTag: String

            if (isRadarInfluenced) {
                sourceTag = if (sppScore >= 60.0) "📡 رادار + مثلث‌بندی شبکه" else "📡 رادار + ایستگاه همجوار"
                val boost = if (idx == startIdx) 1.8 else 1.2
                val spatialEstimate = (sppScore / 100.0) * 2.0 * boost
                pLikely = Math.max(pLikely, spatialEstimate)
                pMax = Math.max(pMax, pLikely * 1.8)
            } else {
                sourceTag = "🇪🇺 مدل اروپایی ECMWF"
                if (sci >= 35.0 && pLikely < 0.2) {
                    pMax = Math.max(pMax, (sci / 30.0))
                }
            }

            pLikely = Math.round(pLikely * 100.0) / 100.0
            pMax = Math.round(pMax * 100.0) / 100.0

            if (pLikely > peakPrecipMm) { peakPrecipMm = pLikely; peakPrecipTime = tIran }
            sumPrecipLikely += pLikely
            sumPrecipMax += pMax

            val isConvective = sci >= 28.0 || pMax >= 0.8
            if (isConvective) convectiveCount++

            val rainLabel = getEventLabel(sci, pLikely, pMax, isVirga, isRadarInfluenced)
            val precipRange = when {
                pMax <= 0.05 -> "۰"
                pLikely < 0.1 -> "۰ تا ${JalaliCalendar.formatNumber(pMax)}"
                else -> "${JalaliCalendar.formatNumber(pLikely)} تا ${JalaliCalendar.formatNumber(pMax)}"
            }

            val prob = when {
                isNowcastWindow && sppScore >= 60.0 -> Math.max(sppScore, 80.0)
                isRadarInfluenced -> Math.max(sppScore, 90.0)
                sci >= 45.0 -> 75.0
                pLikely >= 1.0 -> 70.0
                sci >= 25.0 -> 45.0
                pMax >= 0.3 -> 30.0
                else -> if (isNowcastWindow && sppScore < 15.0) Math.min(10.0, sppScore) else 10.0
            }

            val r = JSONObject()
            r.put("time_iran", tIran)
            r.put("forecast_source", sourceTag)
            r.put("temp_mean", r1(temp))
            r.put("precip_mean", r2(pLikely))
            r.put("precip_max", r2(pMax))
            r.put("precip_prob", r1(prob))
            r.put("precip_range", precipRange)
            r.put("conv_index", r1(sci))
            r.put("thunderstorm_score", r1(sci))
            r.put("wind_speed", r1(windSpeed))
            r.put("wind_gust", r1(windGust))
            r.put("wind_dir_label", windDirLabel(windDir))
            r.put("cloud_cover", r1(cloud))
            r.put("rain_type_label", rainLabel)
            r.put("convective_risk", isConvective)
            r.put("is_virga_risk", isVirga)
            r.put("dew_point", r1(dewPoint))
            r.put("dew_point_depression", r1(dpDepression))
            r.put("cape_value", r1(cape))

            rows24.put(r)
        }

        // ساخت بازه‌های زمانی تفکیک‌شده (H1, H3, H6, H12, H24, HOURLY)
        val rangesJson = JSONObject()
        rangesJson.put(ForecastRange.HOURLY.key, rows24)

        fun sliceBlock(count: Int): JSONObject {
            val validCount = Math.min(count, rows24.length())
            if (validCount <= 0) return JSONObject()
            var sumP = 0.0
            var maxP = 0.0
            var maxProb = 0.0
            var maxSc = 0.0
            var maxG = 0.0
            var maxT = -999.0

            for (i in 0 until validCount) {
                val row = rows24.getJSONObject(i)
                val p = row.optDouble("precip_mean", 0.0)
                val mx = row.optDouble("precip_max", 0.0)
                val pr = row.optDouble("precip_prob", 0.0)
                val sc = row.optDouble("conv_index", 0.0)
                val g = row.optDouble("wind_gust", 0.0)
                val t = row.optDouble("temp_mean", 22.0)
                sumP += p
                maxP = Math.max(maxP, mx)
                maxProb = Math.max(maxProb, pr)
                maxSc = Math.max(maxSc, sc)
                maxG = Math.max(maxG, g)
                maxT = Math.max(maxT, t)
            }

            val first = rows24.getJSONObject(0)
            val last = rows24.getJSONObject(validCount - 1)
            val blk = JSONObject()
            blk.put("time_iran", "${first.optString("time_iran").takeLast(5)} تا ${last.optString("time_iran").takeLast(5)}")
            blk.put("temp_mean", r1(maxT))
            blk.put("precip_mean", r2(sumP))
            blk.put("precip_max", r2(maxP))
            blk.put("precip_prob", r1(maxProb))
            blk.put("precip_range", if (maxP > 0.05) "۰ تا ${JalaliCalendar.formatNumber(maxP)}" else "۰")
            blk.put("conv_index", r1(maxSc))
            blk.put("thunderstorm_score", r1(maxSc))
            blk.put("wind_speed", first.optDouble("wind_speed", 0.0))
            blk.put("wind_gust", r1(maxG))
            blk.put("wind_dir_label", first.optString("wind_dir_label"))
            blk.put("cloud_cover", first.optDouble("cloud_cover", 20.0))
            blk.put("rain_type_label", if (maxSc >= 35.0 || maxP >= 1.0) "پتانسیل رگبار همرفتی ⚡" else if (sumP > 0.1) "بارش باران 🌧️" else "آرام ☀️")
            blk.put("forecast_source", if (count <= 3 && (isRadarEcho || sppScore >= 50.0)) "📡 رادار + مثلث‌بندی شبکه" else "🇪🇺 مدل اروپایی ECMWF")
            blk.put("convective_risk", maxSc >= 30.0)
            return blk
        }

        rangesJson.put(ForecastRange.H1.key, JSONArray().put(sliceBlock(1)))
        rangesJson.put(ForecastRange.H3.key, JSONArray().put(sliceBlock(3)))
        rangesJson.put(ForecastRange.H6.key, JSONArray().put(sliceBlock(6)))
        rangesJson.put(ForecastRange.H12.key, JSONArray().put(sliceBlock(12)))
        rangesJson.put(ForecastRange.H24.key, JSONArray().put(sliceBlock(24)))

        // ساخت شیء رادار ناواست ۶۰ کیلومتری
        val radarJson = JSONObject()
        radarJson.put("available", radar.available)
        radarJson.put("tier", alertTier.levelName)
        radarJson.put("echo_detected", isRadarEcho)
        radarJson.put("estimated_distance_km", r1(radar.estimatedDistanceKm))
        radarJson.put("cell_azimuth_direction", if (threatDirections.isNotEmpty()) threatDirections.first() else radar.cellDirection)
        radarJson.put("estimated_eta_minutes", radar.estimatedEtaMinutes)
        radarJson.put("eta_range_text", radar.etaRangeText)
        radarJson.put("cloud_trend_label", radar.cloudTrendLabel)
        radarJson.put("reflectivity_dbz", r1(radar.reflectivityDbz))
        radarJson.put("status_text", if (isVirgaLikely && isRadarEcho) "${radar.statusText} ⚠️ احتمال تبخیر قطرات باران پیش از رسیدن به زمین (ویرگا) وجود دارد." else radar.statusText)
        radarJson.put("last_frame_iran", if (radar.lastFrameEpoch > 0) utcToIran(utcFormat.format(Date(radar.lastFrameEpoch * 1000L))) else "")
        radarJson.put("movement_speed_kmh", r1(radar.movementSpeedKmh))
        radarJson.put("incoming_rain_likely", isRadarEcho)
        radarJson.put("is_virga_likely", isVirgaLikely)
        radarJson.put("scan_radius_km", 60)

        // ساخت شیء شبکه دیدبانی پیرامونی و پیش‌هشدار دوربرد
        val perimeterArr = JSONArray()
        for (ps in perimeter) {
            val pj = JSONObject()
            pj.put("id", ps.id)
            pj.put("name", ps.name)
            pj.put("sector", ps.sector)
            pj.put("distance_km", ps.distanceKm)
            pj.put("temp", r1(ps.temp))
            pj.put("humidity", r1(ps.humidity))
            pj.put("wind_gust", r1(ps.windGust))
            pj.put("precipitation", r2(ps.precipitation))
            pj.put("is_raining", ps.isRaining)
            pj.put("status_text", ps.statusText)
            pj.put("is_upstream_warning", ps.isUpstreamWarning)
            perimeterArr.put(pj)
        }

        val perimeterSummaryJson = JSONObject()
        perimeterSummaryJson.put("stations", perimeterArr)
        perimeterSummaryJson.put("active_rain_stations_count", rainingStations.size)
        perimeterSummaryJson.put("active_gust_stations_count", gustyStations.size)
        perimeterSummaryJson.put("distant_warning_stations_count", distantWarningStationsCount)
        perimeterSummaryJson.put("threat_direction", if (threatDirections.isNotEmpty()) threatDirections.joinToString("، ") else null)
        perimeterSummaryJson.put("distant_warning_summary", distantWarningSummary)
        perimeterSummaryJson.put("network_status_summary", networkSummaryText)

        // تله‌متری ایستگاه مرکزی منطقه
        val stationJson = JSONObject()
        stationJson.put("station_name", "ایستگاه مرکزی هواشناسی منطقه")
        stationJson.put("current_temp", r1(liveTemp))
        stationJson.put("current_humidity", r1(liveRh))
        stationJson.put("current_dew_point", r1(liveDp))
        stationJson.put("dew_point_depression", r1(liveDpDepression))
        stationJson.put("current_wind_speed", r1(liveWind))
        stationJson.put("current_wind_gust", r1(liveGust))
        stationJson.put("current_cloud_cover", r1(liveCloud))
        stationJson.put("observed_precipitation", r2(livePrecip))
        stationJson.put("gust_front_risk", (liveDpDepression ?: 0.0) >= 12.0 && (liveGust ?: 0.0) >= 30.0)
        stationJson.put("current_weather_desc", when (liveCode) {
            0 -> "آسمان صاف ☀️"
            1, 2 -> "کمی ابری 🌤️"
            3 -> "ابری ☁️"
            45, 48 -> "مه‌آلود 🌫️"
            51, 53, 55 -> "نم‌نم باران 🌦️"
            61, 63, 65 -> "بارش باران 🌧️"
            80, 81, 82 -> "رگبار تند باران 🌧️"
            95, 96, 99 -> "رعدوبرق و صاعقه ⚡"
            else -> "عادی"
        })

        val precipSummary = when {
            isRadarEcho -> "رادار زنده و شبکه دیدبانی همجوار بارش فعال یا ورود توده رگباری را تایید کرده‌اند."
            sumPrecipLikely >= 3.0 -> "بارش باران به حجم کلی حدود ${JalaliCalendar.formatNumber(sumPrecipLikely)} میلی‌متر پیش‌بینی می‌شود."
            peakSci >= 35.0 -> "پتانسیل رگبار موضعی همرفتی و صاعقه در ساعات اوج ناپایداری."
            virgaCount >= 2 -> "خشکی لایه‌های زیرین جو؛ احتمال تبخیر باران در هوا (ویرگا) و وزش تندباد محلی."
            else -> "۲۴ ساعت آینده در منطقه شرایط جوی عمدتاً آرام پیش‌بینی می‌شود."
        }

        val patternJson = JSONObject()
        patternJson.put("window_start_iran", if (rows24.length() > 0) rows24.getJSONObject(0).optString("time_iran") else "")
        patternJson.put("window_end_iran", if (rows24.length() > 0) rows24.getJSONObject(rows24.length() - 1).optString("time_iran") else "")
        patternJson.put("peak_precip_time_iran", if (peakPrecipMm > 0.0) peakPrecipTime else "—")
        patternJson.put("peak_precip_mm", r2(peakPrecipMm))
        patternJson.put("peak_ts_time_iran", if (peakSci >= 20.0) peakTsTime else "—")
        patternJson.put("peak_convective_score", r1(peakSci))
        patternJson.put("max_cape", r1(maxCape))
        patternJson.put("total_precip_24h_likely", r2(sumPrecipLikely))
        patternJson.put("total_precip_24h_max", r2(sumPrecipMax))
        patternJson.put("max_prob_24h", r1(rows24.length().let { 0 until it }.maxOfOrNull { rows24.getJSONObject(it).optDouble("precip_prob", 0.0) } ?: 0.0))
        patternJson.put("max_gust_24h", r1(maxGust))
        patternJson.put("precip_summary_text", precipSummary)
        patternJson.put("convective_hours_count", convectiveCount)
        patternJson.put("virga_hours_count", virgaCount)

        val verdictJson = JSONObject()
        verdictJson.put("tier", alertTier.levelName)
        verdictJson.put("text", verdictText)
        verdictJson.put("advisory_note", advisoryNote)

        val meta = JSONObject()
        meta.put("location", "منطقه (شعاع پایش ۶۰ کیلومتر)")
        meta.put("lat", OpenMeteoClient.LAT)
        meta.put("lon", OpenMeteoClient.LON)
        meta.put("generated_iran", utcToIran(nowUtcIso))

        val d = JSONObject()
        d.put("meta", meta)
        d.put("verdict", verdictJson)
        d.put("radar_nowcast", radarJson)
        d.put("perimeter_network", perimeterSummaryJson)
        d.put("triangulation", triangulationJson)
        d.put("station_telemetry", stationJson)
        d.put("pattern", patternJson)
        d.put("ranges", rangesJson)
        return d
    }

    /**
     * محاسبات مثلث‌بندی راداری و دیدبانی دزفول:
     * - تعریف شبکه هندسی مسطح (Cartesian Plane) به مرکزیت دزفول (۰،۰)
     * - سه رأس مثلث دیدبانی:
     *   ۱. اندیمشک (شمال غرب، ۱۸.۷ کیلومتر)
     *   ۲. شوش (جنوب غرب، ۲۱.۲ کیلومتر)
     *   ۳. گتوند (شرق، ۳۶.۸ کیلومتر)
     * - استخراج ضرایب باریسنتریک (Barycentric Weights: λ1, λ2, λ3)
     * - محاسبه گرادیان فضایی بارش (Spatial Gradient: ∇P)
     * - محاسبه شار انتقال افقی باد (Horizontal Advection Flux)
     * - تلفیق با بازتابش رادار و محاسبه احتمال بارش فضایی (Spatial Precipitation Probability - SPP)
     */
    fun computeSpatialTriangulation(
        perimeter: List<PerimeterStationInfo>,
        radar: OpenMeteoClient.RadarScanResult,
        liveStation: JSONObject
    ): JSONObject {
        // مختصات مرجع دزفول (۰،۰)
        val lat0 = OpenMeteoClient.LAT
        val lon0 = OpenMeteoClient.LON
        val kmPerDegLat = 111.132
        val kmPerDegLon = 111.132 * Math.cos(Math.toRadians(lat0))

        // سه پایگاه تشکیل‌دهنده مثلث هندسی
        val andimeshkInfo = perimeter.find { it.id == "andimeshk" }
        val shushInfo = perimeter.find { it.id == "shush" }
        val gotvandInfo = perimeter.find { it.id == "gotvand" }

        // مختصات جغرافیایی رئوس مثلث
        val v1Lat = 32.4578; val v1Lon = 48.3567 // اندیمشک (شمال غرب)
        val v2Lat = 32.1942; val v2Lon = 48.2436 // شوش (جنوب غرب)
        val v3Lat = 32.2472; val v3Lon = 48.8164 // گتوند (شرق)

        // مختصات کارتزین محلی نسبت به دزفول (km)
        val x1 = (v1Lon - lon0) * kmPerDegLon
        val y1 = (v1Lat - lat0) * kmPerDegLat
        val d1 = Math.sqrt(x1 * x1 + y1 * y1)

        val x2 = (v2Lon - lon0) * kmPerDegLon
        val y2 = (v2Lat - lat0) * kmPerDegLat
        val d2 = Math.sqrt(x2 * x2 + y2 * y2)

        val x3 = (v3Lon - lon0) * kmPerDegLon
        val y3 = (v3Lat - lat0) * kmPerDegLat
        val d3 = Math.sqrt(x3 * x3 + y3 * y3)

        // مساحت مثلث و دترمینان تبدیل مختصات (2 * Area)
        val deltaTotal = (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3)
        val triangleArea = 0.5 * Math.abs(deltaTotal)

        // ضرایب باریسنتریک برای نقطه دزفول (x0=0, y0=0)
        // λ1 = (x2*y3 - x3*y2) / deltaTotal
        // λ2 = (x3*y1 - x1*y3) / deltaTotal
        // λ3 = (x1*y2 - x2*y1) / deltaTotal
        val lambda1Raw = (x2 * y3 - x3 * y2) / deltaTotal
        val lambda2Raw = (x3 * y1 - x1 * y3) / deltaTotal
        val lambda3Raw = (x1 * y2 - x2 * y1) / deltaTotal

        // نرمال‌سازی ضرایب (مجموع دقیقاً ۱.۰)
        val sumLambda = lambda1Raw + lambda2Raw + lambda3Raw
        val lambda1 = if (Math.abs(sumLambda) > 1e-6) lambda1Raw / sumLambda else 0.35
        val lambda2 = if (Math.abs(sumLambda) > 1e-6) lambda2Raw / sumLambda else 0.39
        val lambda3 = if (Math.abs(sumLambda) > 1e-6) lambda3Raw / sumLambda else 0.26
        val isInside = lambda1 >= -0.01 && lambda2 >= -0.01 && lambda3 >= -0.01

        // نرخ بارش و احتمال بارندگی در هر یک از رئوس
        fun vertexProb(st: PerimeterStationInfo?): Double {
            if (st == null) return 5.0
            val p = st.precipitation ?: 0.0
            val rh = st.humidity ?: 50.0
            val g = st.windGust ?: 15.0
            return when {
                st.isRaining || p >= 0.5 -> 95.0
                p > 0.05 -> 75.0
                rh >= 80.0 && g >= 30.0 -> 50.0
                rh >= 70.0 -> 30.0
                else -> 5.0
            }
        }

        fun vertexRainRate(st: PerimeterStationInfo?): Double {
            if (st == null) return 0.0
            val p = st.precipitation ?: 0.0
            return if (st.isRaining && p < 0.3) 0.5 else p
        }

        val p1 = vertexProb(andimeshkInfo)
        val p2 = vertexProb(shushInfo)
        val p3 = vertexProb(gotvandInfo)

        val r1Rate = vertexRainRate(andimeshkInfo)
        val r2Rate = vertexRainRate(shushInfo)
        val r3Rate = vertexRainRate(gotvandInfo)

        // درون‌یابی باریسنتریک در مبدأ (دزفول)
        val pBarycentric = lambda1 * p1 + lambda2 * p2 + lambda3 * p3
        val rBarycentric = lambda1 * r1Rate + lambda2 * r2Rate + lambda3 * r3Rate

        // برازش رویه خطی گرادیان فضایی: P(x, y) = a*x + b*y + c
        val aGrad = if (Math.abs(deltaTotal) > 1e-6) {
            ((p1 - p3) * (y2 - y3) - (p2 - p3) * (y1 - y3)) / deltaTotal
        } else 0.0
        val bGrad = if (Math.abs(deltaTotal) > 1e-6) {
            ((x1 - x3) * (p2 - p3) - (x2 - x3) * (p1 - p3)) / deltaTotal
        } else 0.0
        val gradMagnitude = Math.sqrt(aGrad * aGrad + bGrad * bGrad)

        // جهت گرادیان (سمت بیشترین افزایش احتمال بارش)
        val gradCartesianAngle = Math.atan2(bGrad, aGrad)
        var gradBearing = (90.0 - Math.toDegrees(gradCartesianAngle)) % 360.0
        if (gradBearing < 0.0) gradBearing += 360.0
        val gradDirectionLabel = if (gradMagnitude < 0.08) "همگن (بدون گرادیان محسوس)" else windDirLabel(gradBearing)

        // بردار میانگین وزنی جریان باد در شبکه مثلثی جهت شار انتقال افقی (Advection Flux)
        fun stationWindFlow(st: PerimeterStationInfo?): Pair<Double, Double> {
            val speed = st?.windSpeed ?: 16.0
            val dir = st?.windDirection ?: 270.0 // جهت وزش پیش‌فرض از غرب/شمال‌غرب
            // زاویه حرکت جریان هوا = dir + 180 (رو به کجا می‌رود)
            val flowRad = Math.toRadians((dir + 180.0) % 360.0)
            val u = speed * Math.sin(flowRad)
            val v = speed * Math.cos(flowRad)
            return Pair(u, v)
        }

        val (u1, v1) = stationWindFlow(andimeshkInfo)
        val (u2, v2) = stationWindFlow(shushInfo)
        val (u3, v3) = stationWindFlow(gotvandInfo)

        val uAvg = lambda1 * u1 + lambda2 * u2 + lambda3 * u3
        val vAvg = lambda1 * v1 + lambda2 * v2 + lambda3 * v3

        // شار انتقال افقی: - (V_flow . grad P)
        // اگر توده بارشی در بالادست باد باشد، شار مثبت شده و احتمال بارش دزفول را افزایش می‌دهد
        val advectionFlux = -(uAvg * aGrad + vAvg * bGrad)
        val clampedFlux = advectionFlux.coerceIn(-25.0, 35.0)

        // ادغام بازتابش راداری
        val radarBonus = when {
            radar.echoDetected -> {
                val dbz = radar.reflectivityDbz.coerceAtLeast(18.0)
                val base = (dbz / 50.0) * 35.0
                if (radar.incomingRainLikely && (radar.estimatedDistanceKm ?: 99.0) <= 35.0) {
                    base + 15.0
                } else {
                    base
                }
            }
            else -> 0.0
        }

        // احتمال نهایی بارش فضایی (SPP)
        var spp = (pBarycentric + clampedFlux + radarBonus).coerceIn(0.0, 99.0)
        val activeRainsCount = listOfNotNull(andimeshkInfo, shushInfo, gotvandInfo).count { it.isRaining }

        if (activeRainsCount >= 2) {
            spp = Math.max(spp, 85.0)
        } else if (activeRainsCount == 1) {
            spp = Math.max(spp, 65.0)
        } else if (!radar.echoDetected && p1 <= 10.0 && p2 <= 10.0 && p3 <= 10.0) {
            spp = Math.min(spp, 10.0)
        }

        // شناسایی رأس دارای بیشترین بارندگی یا ناپایداری
        val vertexMap = mapOf(
            "اندیمشک (شمال غرب)" to p1,
            "شوش (جنوب غرب)" to p2,
            "گتوند (شرق)" to p3
        )
        val threatVertex = vertexMap.maxByOrNull { it.value }?.takeIf { it.value >= 40.0 }?.key

        val convergenceSummary = when {
            spp >= 75.0 ->
                "⚠️ هشدار ورود توده بارشی: تمرکز بارش در ${threatVertex ?: "بخش‌های غربی"} با سرعت ${r1(Math.sqrt(uAvg * uAvg + vAvg * vAvg))} کیلومتر بر ساعت به سمت منطقه."
            spp >= 40.0 ->
                "🌦️ پتانسیل شکل‌گیری باران: جهت حرکت ابرها به سمت ${threatVertex ?: gradDirectionLabel}؛ جریان باد در حال هدایت رطوبت به سمت منطقه است."
            else ->
                "✅ آرامش جوی در شبکه دیدبانی: تمامی ایستگاه‌های پیرامون منطقه آرام و بدون بارش رصد شدند."
        }

        // ایجاد اشیاء رئوس برای JSON
        fun makeVertexJson(
            id: String,
            name: String,
            sector: String,
            lat: Double,
            lon: Double,
            x: Double,
            y: Double,
            dist: Double,
            weight: Double,
            rate: Double,
            prob: Double,
            st: PerimeterStationInfo?
        ): JSONObject {
            val vj = JSONObject()
            vj.put("id", id)
            vj.put("name", name)
            vj.put("sector", sector)
            vj.put("lat", lat)
            vj.put("lon", lon)
            vj.put("x_km", r1(x))
            vj.put("y_km", r1(y))
            vj.put("distance_km", r1(dist))
            vj.put("barycentric_weight", r2(weight))
            vj.put("rain_rate_mm_h", r2(rate))
            vj.put("rain_probability", r1(prob))
            vj.put("temp", r1(st?.temp))
            vj.put("humidity", r1(st?.humidity))
            vj.put("wind_speed_kmh", r1(st?.windSpeed))
            vj.put("wind_gust_kmh", r1(st?.windGust))
            vj.put("wind_direction_deg", r1(st?.windDirection))
            vj.put("is_raining", st?.isRaining ?: false)
            vj.put("status_text", st?.statusText ?: "آرام")
            return vj
        }

        val verticesArr = JSONArray()
        verticesArr.put(makeVertexJson("andimeshk", "اندیمشک", "شمال غرب", v1Lat, v1Lon, x1, y1, d1, lambda1, r1Rate, p1, andimeshkInfo))
        verticesArr.put(makeVertexJson("shush", "شوش", "جنوب غرب", v2Lat, v2Lon, x2, y2, d2, lambda2, r2Rate, p2, shushInfo))
        verticesArr.put(makeVertexJson("gotvand", "گتوند", "شرق", v3Lat, v3Lon, x3, y3, d3, lambda3, r3Rate, p3, gotvandInfo))

        val triJson = JSONObject()
        triJson.put("primary_station_name", "مرکز منطقه (مختصات مبنا: ۰,۰)")
        triJson.put("vertices", verticesArr)
        triJson.put("spatial_precip_probability", r1(spp))
        triJson.put("spatial_rain_rate_mm_h", r2(rBarycentric))
        triJson.put("gradient_magnitude", r2(gradMagnitude))
        triJson.put("gradient_direction_deg", r1(gradBearing))
        triJson.put("gradient_direction_label", gradDirectionLabel)
        triJson.put("advection_flux_per_hour", r2(clampedFlux))
        triJson.put("triangle_area_sq_km", r1(triangleArea))
        triJson.put("is_inside_triangle", isInside)
        triJson.put("convergence_summary", convergenceSummary)
        triJson.put("threat_vertex_name", threatVertex)
        return triJson
    }
}
