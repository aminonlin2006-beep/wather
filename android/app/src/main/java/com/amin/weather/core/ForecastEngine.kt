package com.amin.weather.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * موتور پیش‌بینی — انتقال کامل منطق پایتون به Kotlin:
 *   ensemble (وزن‌دهی هوشمند + میانه + کالیبره بایاس)
 *   تحلیل همرفتی (سطح ناپایداری، صاعقه، شدید، نوع ابر، برش باد)
 *   رهگیری فاز و استخراج الگو
 *   ساخت بازه‌های زمانی (ساعتی/۶ساعته/۱۲ساعته/روزانه)
 */
object ForecastEngine {

    val MODEL_KEYS = listOf("gfs", "ecmwf", "icon")
    val PRECIP_BIAS = mapOf("icon" to 0.6)

    val VARS = listOf(
        "temperature_2m", "precipitation", "cape", "lifted_index", "freezinglevel_height",
        "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high",
        "windspeed_10m", "windgusts_10m", "winddirection_10m", "relativehumidity_2m"
    )

    // ---------------------------------------------------------------- helpers

    private fun arr(obj: JSONObject, key: String): JSONArray? =
        obj.optJSONArray(key)

    private fun optD(arr: JSONArray?, i: Int): Double? {
        if (arr == null || i < 0 || i >= arr.length()) return null
        val v = arr.opt(i)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun r2(v: Double?): Double? = v?.let { Math.round(it * 100) / 100.0 }

    fun median(vals: List<Double?>): Double? {
        val s = vals.filterNotNull().sorted()
        if (s.isEmpty()) return null
        val n = s.size
        val mid = n / 2
        return if (n % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }

    private fun mean(vals: List<Double?>): Double? {
        val s = vals.filterNotNull()
        if (s.isEmpty()) return null
        return s.sum() / s.size
    }

    private fun std(vals: List<Double?>): Double {
        val s = vals.filterNotNull()
        if (s.size < 2) return 0.0
        val m = s.sum() / s.size
        return Math.sqrt(s.sumOf { (it - m) * (it - m) } / s.size)
    }

    private fun wmean(vals: List<Double?>, weights: List<Double>): Double? {
        val pairs = vals.zip(weights).filter { it.first != null }
        if (pairs.isEmpty()) return null
        val s = pairs.sumOf { it.first!! * it.second }
        val ws = pairs.sumOf { it.second }
        return if (ws != 0.0) s / ws else null
    }

    // ------------------------------------------------------------ weights

    fun computeWeights(models: Map<String, JSONObject>): Map<String, Double> {
        // وزن‌دهی بر اساس مهارت واقعی (از اعتبارسنجی ایستگاه‌ها).
        // اگر تاریخچه کافی نباشد، وزن مساوی.
        val cal = loadCalibrated()
        val tb = cal.optJSONObject("temperature_bias") ?: JSONObject()
        val pb = cal.optJSONObject("precipitation_bias") ?: JSONObject()
        var hasSkill = false
        for (name in MODEL_KEYS) {
            if (!tb.isNull(name) || !pb.isNull(name)) { hasSkill = true; break }
        }
        if (!hasSkill) return MODEL_KEYS.associateWith { 1.0 / MODEL_KEYS.size }

        val weights = mutableMapOf<String, Double>()
        for (name in MODEL_KEYS) {
            var err = 0.0
            var cnt = 0
            if (!tb.isNull(name)) { err += tb.optDouble(name); cnt++ }
            if (!pb.isNull(name)) { err += pb.optDouble(name) * 2.0; cnt++ }
            if (cnt == 0) err = 1.0 else err /= cnt
            weights[name] = 1.0 / (1.0 + err)
        }
        val total = weights.values.sum()
        if (total <= 0) return MODEL_KEYS.associateWith { 1.0 / MODEL_KEYS.size }
        return weights.mapValues { it.value / total }
    }

    // -------------------------------------------------------- calibration

    private fun calibratedFile(): File {
        return File(OpenMeteoClient::class.java.classLoader?.getResource("")?.path ?: ".", "calibrated_bias.json")
    }

    fun loadCalibrated(): JSONObject {
        val f = calibratedFile()
        return try {
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun tempBias(): Double {
        val cal = loadCalibrated()
        val tb = cal.optJSONObject("temperature_bias") ?: return 0.0
        val vals = mutableListOf<Double>()
        val it = tb.keys()
        while (it.hasNext()) {
            val v = tb.optDouble(it.next(), Double.NaN)
            if (!v.isNaN()) vals.add(v)
        }
        return if (vals.size >= 3) Math.round(vals.sum() / vals.size * 100) / 100.0 else 0.0
    }

    fun precipBias(): Map<String, Double> {
        val cal = loadCalibrated()
        val pb = cal.optJSONObject("precipitation_bias") ?: JSONObject()
        val merged = PRECIP_BIAS.toMutableMap()
        val it = pb.keys()
        while (it.hasNext()) {
            val name = it.next()
            val ratio = pb.optDouble(name, Double.NaN)
            if (!ratio.isNaN() && ratio > 0) {
                val c = Math.min(2.0, Math.max(0.2, 1.0 / ratio))
                merged[name] = Math.round(c * 1000) / 1000.0
            }
        }
        return merged
    }

    // ---------------------------------------------------------- ensemble

    fun buildEnsemble(models: Map<String, JSONObject>): JSONArray {
        val weights = computeWeights(models)
        val order = MODEL_KEYS
        val wlist = order.map { weights[it] ?: 0.0 }
        val tempBias = tempBias()
        val precipBias = precipBias()

        val ref = models[order[0]] ?: return JSONArray()
        val refHourly = ref.optJSONObject("hourly") ?: return JSONArray()
        val times = arr(refHourly, "time") ?: return JSONArray()

        val rows = JSONArray()
        for (i in 0 until times.length()) {
            val row = JSONObject()
            row.put("time", times.optString(i))
            val wj = JSONObject()
            weights.forEach { (k, v) -> wj.put(k, Math.round(v * 1000) / 1000.0) }
            row.put("model_weights", wj)

            for (varName in VARS) {
                val vals = mutableListOf<Double?>()
                for (name in order) {
                    val m = models[name] ?: continue
                    val hourly = m.optJSONObject("hourly") ?: continue
                    vals.add(optD(arr(hourly, varName), i))
                }
                if (varName == "precipitation") {
                    val calVals = mutableListOf<Double>()
                    for ((name, v) in order.zip(vals)) {
                        if (v != null) calVals.add(v * (precipBias[name] ?: 1.0))
                    }
                    val med = median(calVals)
                    val mx = if (calVals.isNotEmpty()) calVals.max() else null
                    if (mx != null) row.put("precipitation_max", Math.round(mx * 1000) / 1000.0)
                    if (calVals.isNotEmpty() && mx != null && med != null && mx > med * 2) {
                        row.put("precipitation_mean", Math.round(Math.max(med, 0.5 * mx) * 1000) / 1000.0)
                    } else {
                        row.put("precipitation_mean", if (med != null) Math.round(med * 1000) / 1000.0 else JSONObject.NULL)
                    }
                } else {
                    val w = wmean(vals, wlist)
                    var value = w
                    if (varName == "temperature_2m" && tempBias != 0.0 && value != null) {
                        value = Math.round((value - tempBias) * 100) / 100.0
                    }
                    row.put("${varName}_mean", value ?: JSONObject.NULL)
                }
                row.put("${varName}_std", Math.round(std(vals) * 1000) / 1000.0)
            }

            // توافق
            var nz = 0
            var pos = 0
            for (name in order) {
                val m = models[name] ?: continue
                val hourly = m.optJSONObject("hourly") ?: continue
                val p = optD(arr(hourly, "precipitation"), i)
                val c = optD(arr(hourly, "cape"), i)
                if (p != null && p > 0) nz++
                if (c != null && c > 0) pos++
            }
            row.put("precip_nonzero_models", nz)
            row.put("cape_positive_models", pos)
            row.put("n_models", order.size)
            rows.put(row)
        }
        return rows
    }

    // ------------------------------------------------------- convection

    private fun capeLevel(cape: Double?): String {
        if (cape == null || cape <= 0) return "پایین"
        return if (cape < 500) "پایین" else if (cape < 1500) "متوسط" else "بالا"
    }

    private fun thunderstormScore(cape: Double?, precip: Double?, cloudDepth: Double?, gust: Double?): Double {
        var s = 0.0
        if (cape != null && cape > 0) s += Math.min(40.0, cape / 50.0)
        s += Math.min(30.0, (precip ?: 0.0) * 6.0)
        s += (cloudDepth ?: 0.0) / 5.0
        s += Math.min(10.0, (gust ?: 0.0) / 13.0)
        return Math.round(Math.min(100.0, s) * 10) / 10.0
    }

    private fun severeIndex(cape: Double?, shear: Double?, rh: Double?): Double {
        var s = 0.0
        if (cape != null && cape > 0) s += Math.min(40.0, cape / 50.0)
        if (shear != null) s += Math.min(30.0, shear / 5.0)
        if (rh != null) s += Math.max(0.0, Math.min(30.0, (rh - 40.0) * 0.6))
        return Math.round(Math.min(100.0, s) * 10) / 10.0
    }

    private fun cloudType(cloudDepth: Double?, cape: Double?, precip: Double?): String {
        if (cape != null && cape > 300 && precip != null && precip >= 0.1) return "همرفتی"
        if (cloudDepth != null && cloudDepth >= 80) {
            return if (cape != null && cape > 100) "همرفتی/لایه‌ای" else "لایه‌ای"
        }
        return "پراکنده"
    }

    private fun windShear(prev: Pair<Double?, Double?>, cur: Pair<Double?, Double?>): Double {
        val (ps, pd) = prev
        val (cs, cd) = cur
        if (ps == null || pd == null || cs == null || cd == null) return 0.0
        val pr = Math.toRadians(pd); val cr = Math.toRadians(cd)
        val pu = -ps * Math.sin(pr); val pv = -ps * Math.cos(pr)
        val cu = -cs * Math.sin(cr); val cv = -cs * Math.cos(cr)
        return Math.hypot(cu - pu, cv - pv)
    }

    fun analyze(rows: JSONArray): JSONArray {
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val cape = r.optDouble("cape_mean", Double.NaN).takeIf { !it.isNaN() }
            val precip = r.optDouble("precipitation_mean", Double.NaN).takeIf { !it.isNaN() }
            val low = r.optDouble("cloudcover_low_mean", Double.NaN).takeIf { !it.isNaN() }
            val mid = r.optDouble("cloudcover_mid_mean", Double.NaN).takeIf { !it.isNaN() }
            val high = r.optDouble("cloudcover_high_mean", Double.NaN).takeIf { !it.isNaN() }
            val gust = r.optDouble("windgusts_10m_mean", Double.NaN).takeIf { !it.isNaN() }
            val speed = r.optDouble("windspeed_10m_mean", Double.NaN).takeIf { !it.isNaN() }
            val direction = r.optDouble("winddirection_10m_mean", Double.NaN).takeIf { !it.isNaN() }

            val clouds = listOfNotNull(low, mid, high)
            val cloudDepth = if (clouds.isNotEmpty()) clouds.sum() / clouds.size else null

            val prevWind = if (i > 0) {
                val pr = rows.getJSONObject(i - 1)
                Pair(
                    pr.optDouble("windspeed_10m_mean", Double.NaN).takeIf { !it.isNaN() },
                    pr.optDouble("winddirection_10m_mean", Double.NaN).takeIf { !it.isNaN() }
                )
            } else Pair(null, null)
            val shear = windShear(prevWind, Pair(speed, direction))

            r.put("cape_level", capeLevel(cape))
            r.put("thunderstorm_score", thunderstormScore(cape, precip, cloudDepth, gust))
            r.put("severe_index", severeIndex(cape, shear, r.optDouble("relativehumidity_2m_mean", Double.NaN).takeIf { !it.isNaN() }))
            r.put("lifted_index", r.optDouble("lifted_index_mean", Double.NaN).takeIf { !it.isNaN() })
            r.put("freezing_level", r.optDouble("freezinglevel_height_mean", Double.NaN).takeIf { !it.isNaN() })
            r.put("cloud_depth", if (cloudDepth != null) Math.round(cloudDepth * 10) / 10.0 else JSONObject.NULL)
            r.put("cloud_type", cloudType(cloudDepth, cape, precip))
            r.put("wind_shear", Math.round(shear * 10) / 10.0)

            val nz = r.optInt("precip_nonzero_models", 0)
            val pos = r.optInt("cape_positive_models", 0)
            val n = r.optInt("n_models", 4)
            val conf = Math.min(100.0, 50.0 + (nz.toDouble() / n) * 30.0 + (pos.toDouble() / n) * 20.0)
            r.put("confidence", Math.round(conf * 10) / 10.0)
        }

        // محرک همرفتی
        for (i in rows.length() - 1 downTo 1) {
            val r = rows.getJSONObject(i)
            val prev = rows.getJSONObject(i - 1)
            val cape = r.optDouble("cape_mean", 0.0)
            val prevCape = prev.optDouble("cape_mean", 0.0)
            val precip = r.optDouble("precipitation_mean", 0.0)
            val prevPrecip = prev.optDouble("precipitation_mean", 0.0)
            var trigger = false
            val reasons = mutableListOf<String>()
            if (cape - prevCape >= 200) { trigger = true; reasons.add("جهش CAPE") }
            if (precip > 0 && prevPrecip == 0.0 && cape > 0) { trigger = true; reasons.add("شروع بارش") }
            r.put("convective_trigger", trigger)
            r.put("trigger_reason", reasons.joinToString("؛ "))
        }
        return rows
    }

    // ---------------------------------------------------------- track

    fun buildTrack(rows: JSONArray, nowIso: String): JSONObject {
        val nowHour = nowIso.take(13)
        var startIdx = 0
        for (i in 0 until rows.length()) {
            if (rows.getJSONObject(i).optString("time").take(13) >= nowHour) { startIdx = i; break }
        }
        val end = Math.min(startIdx + 12, rows.length())
        val window = JSONArray()
        for (i in startIdx until end) window.put(rows.getJSONObject(i))

        for (i in 0 until window.length()) {
            val r = window.getJSONObject(i)
            val score = r.optDouble("thunderstorm_score", 0.0)
            val precip = r.optDouble("precipitation_mean", 0.0)
            val trigger = r.optBoolean("convective_trigger", false)
            val phase = when {
                score >= 40.0 || precip >= 1.0 -> "اوج (فعال)"
                trigger -> "شروع (توسعه)"
                score >= 15.0 -> "در حال توسعه"
                else -> "آرام"
            }
            r.put("phase", phase)
            val nz = r.optInt("precip_nonzero_models", 0)
            val pos = r.optInt("cape_positive_models", 0)
            val n = r.optInt("n_models", 4)
            val conf = Math.min(100.0, 50.0 + (nz.toDouble() / n) * 30.0 + (pos.toDouble() / n) * 20.0)
            r.put("confidence", Math.round(conf * 10) / 10.0)
        }

        val triggers = JSONArray()
        val activeTimes = mutableListOf<String>()
        var peakPrecipTime = ""
        var peakPrecipMm = 0.0
        var peakTsTime = ""
        var peakTsScore = 0.0
        var maxCape = 0.0

        for (i in 0 until window.length()) {
            val r = window.getJSONObject(i)
            val t = r.optString("time")
            if (r.optBoolean("convective_trigger", false)) triggers.put(t)
            if (r.optString("phase").startsWith("اوج")) activeTimes.add(t)
            val precip = r.optDouble("precipitation_mean", 0.0)
            val score = r.optDouble("thunderstorm_score", 0.0)
            val cape = r.optDouble("cape_mean", 0.0)
            if (precip > peakPrecipMm) { peakPrecipMm = precip; peakPrecipTime = t }
            if (score > peakTsScore) { peakTsScore = score; peakTsTime = t }
            if (cape > maxCape) maxCape = cape
        }

        val duration = if (activeTimes.isNotEmpty()) {
            val first = activeTimes.first().take(13); val last = activeTimes.last().take(13)
            try {
                val h1 = first.substring(11, 13).toInt(); val h2 = last.substring(11, 13).toInt()
                val d1 = first.substring(8, 10).toInt(); val d2 = last.substring(8, 10).toInt()
                (d2 - d1) * 24 + (h2 - h1) + 1
            } catch (e: Exception) { 1 }
        } else 0

        val pattern = JSONObject()
        pattern.put("trigger_times", triggers)
        pattern.put("peak_precip_time", peakPrecipTime)
        pattern.put("peak_precip_mm", Math.round(peakPrecipMm * 100) / 100.0)
        pattern.put("peak_thunderstorm_time", peakTsTime)
        pattern.put("peak_thunderstorm_score", Math.round(peakTsScore * 10) / 10.0)
        pattern.put("active_duration_h", duration)
        pattern.put("max_cape", Math.round(maxCape * 10) / 10.0)
        if (window.length() > 0) {
            pattern.put("window_start", window.getJSONObject(0).optString("time"))
            pattern.put("window_end", window.getJSONObject(window.length() - 1).optString("time"))
        }

        val result = JSONObject()
        result.put("window", window)
        result.put("pattern", pattern)
        result.put("generated_at", nowIso)
        return result
    }

    // --------------------------------------------------------- ranges

    private fun utcToIran(utcIso: String): String {
        // "YYYY-MM-DDTHH:MM" → +3:30
        return try {
            val date = utcIso.take(10)
            val time = utcIso.substring(11, 16)
            val h = time.substring(0, 2).toInt()
            val m = time.substring(3, 5).toInt()
            var total = h * 60 + m + 210
            var day = date.substring(8, 10).toInt()
            val y = date.substring(0, 4).toInt()
            val mo = date.substring(5, 7).toInt()
            val daysInMonth = when (mo) {
                1,3,5,7,8,10,12 -> 31
                4,6,9,11 -> 30
                else -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
            }
            if (total >= 1440) { total -= 1440; day += 1 }
            if (day > daysInMonth) { day = 1 }
            "%s-%s-%02d %02d:%02d".format(date.substring(0,4), date.substring(5,7), day, total / 60, total % 60)
        } catch (e: Exception) { utcIso.replace('T', ' ') }
    }

    private val WIND_DIRS = listOf(
        "شمالی", "شمال شمال شرقی", "شمال شرقی", "شرق شمال شرقی",
        "شرقی", "شرق جنوب شرقی", "جنوب شرقی", "جنوب جنوب شرقی",
        "جنوبی", "جنوب جنوب غربی", "جنوب غربی", "غرب جنوب غربی",
        "غربی", "غرب شمال غربی", "شمال غربی", "شمال شمال غربی"
    )

    private fun windDirLabel(deg: Double?): String {
        if (deg == null) return "—"
        val idx = ((deg + 11.25) / 22.5).toInt() % 16
        return WIND_DIRS[idx]
    }

    private fun agreement(r: JSONObject): String {
        val pstd = r.optDouble("precipitation_std", Double.NaN).takeIf { !it.isNaN() }
        val pmean = r.optDouble("precipitation_mean", 0.0)
        if (pstd != null && pstd >= 0.5 && pstd > pmean * 1.5) return "weak"
        return "good"
    }

    private fun qpf(r: JSONObject, prob: Double): Double? {
        val med = r.optDouble("precipitation_mean", 0.0)
        val mx = r.optDouble("precipitation_max", 0.0)
        return r2(Math.max(med, mx * prob / 100.0))
    }

    private fun precipRisk(r: JSONObject, prob: Double): String {
        val conv = r.optDouble("precipitation_max", 0.0) >= 0.5 &&
            r.optDouble("precipitation_mean", 0.0) < 0.2
        return when {
            conv || prob >= 50 -> "بالا"
            prob >= 25 || r.optDouble("precipitation_mean", 0.0) >= 0.2 -> "متوسط"
            else -> "پایین"
        }
    }

    private fun convPotential(h: JSONObject, nowcastCat: String): Double {
        var s = 0.0
        s += Math.min(40.0, h.optDouble("prob", 0.0) * 0.8)
        s += Math.min(30.0, h.optDouble("precip_max", 0.0) * 12.0)
        val rh = h.optDouble("rh", Double.NaN).takeIf { !it.isNaN() }
        if (rh != null) s += Math.max(0.0, Math.min(20.0, (rh - 40.0) * 0.4))
        if (nowcastCat == "moderate" || nowcastCat == "heavy") s += 10.0
        return Math.round(Math.min(100.0, s) * 10) / 10.0
    }

    private fun hourRow(r: JSONObject, prob: Double, nowcastCat: String): JSONObject {
        val h = JSONObject()
        h.put("time_utc", r.optString("time"))
        h.put("time_iran", utcToIran(r.optString("time")))
        h.put("temp", r2(r.optDouble("temperature_2m_mean", Double.NaN).takeIf { !it.isNaN() }))
        h.put("precip", r2(r.optDouble("precipitation_mean", Double.NaN).takeIf { !it.isNaN() }))
        h.put("prob", r2(prob))
        h.put("cape", r2(r.optDouble("cape_mean", Double.NaN).takeIf { !it.isNaN() }))
        h.put("cape_level", r.optString("cape_level", "—"))
        h.put("rh", r2(r.optDouble("relativehumidity_2m_mean", Double.NaN).takeIf { !it.isNaN() }))
        h.put("cloud_depth", r2(r.optDouble("cloud_depth", Double.NaN).takeIf { !it.isNaN() }))
        h.put("cloud_type", r.optString("cloud_type", "—"))
        h.put("wind_speed", r2(r.optDouble("windspeed_10m_mean", Double.NaN).takeIf { !it.isNaN() }))
        h.put("gust", r2(r.optDouble("windgusts_10m_mean", Double.NaN).takeIf { !it.isNaN() }))
        h.put("wdir", windDirLabel(r.optDouble("winddirection_10m_mean", Double.NaN).takeIf { !it.isNaN() }))
        h.put("score", r2(r.optDouble("thunderstorm_score", 0.0)))
        h.put("severe", r2(r.optDouble("severe_index", 0.0)))
        h.put("phase", r.optString("phase", "—"))
        h.put("confidence", r2(r.optDouble("confidence", 0.0)))
        h.put("agreement", agreement(r))
        h.put("precip_max", r2(r.optDouble("precipitation_max", Double.NaN).takeIf { !it.isNaN() }))
        val conv = r.optDouble("precipitation_max", 0.0) >= 0.5 &&
            r.optDouble("precipitation_mean", 0.0) < 0.2
        h.put("convective_risk", conv)
        h.put("precip_risk", precipRisk(r, prob))
        h.put("qpf", qpf(r, prob))
        h.put("conv_potential", convPotential(h, nowcastCat))
        return h
    }

    private fun blockAgg(rows: List<JSONObject>, prob: Map<String, Double>): JSONObject {
        val temps = rows.mapNotNull { it.optDouble("temperature_2m_mean", Double.NaN).takeIf { v -> !v.isNaN() } }
        val precips = rows.map { it.optDouble("precipitation_mean", 0.0) }
        val capes = rows.mapNotNull { it.optDouble("cape_mean", Double.NaN).takeIf { v -> !v.isNaN() } }
        val clouds = rows.mapNotNull { it.optDouble("cloud_depth", Double.NaN).takeIf { v -> !v.isNaN() } }
        val winds = rows.mapNotNull { it.optDouble("windspeed_10m_mean", Double.NaN).takeIf { v -> !v.isNaN() } }
        val gusts = rows.mapNotNull { it.optDouble("windgusts_10m_mean", Double.NaN).takeIf { v -> !v.isNaN() } }
        val scores = rows.map { it.optDouble("thunderstorm_score", 0.0) }
        val severes = rows.map { it.optDouble("severe_index", 0.0) }
        val probs = rows.map { prob[it.optString("time")] ?: 0.0 }
        val phases = rows.mapNotNull { it.optString("phase").takeIf { p -> p.isNotEmpty() && p != "—" } }
        val phaseSev = mapOf("آرام" to 0, "در حال توسعه" to 1, "در حال زوال" to 2, "فعال" to 3)
        val domPhase = phases.maxByOrNull { phaseSev[it] ?: 0 } ?: "—"
        val maxs = rows.mapNotNull { it.optDouble("precipitation_max", Double.NaN).takeIf { v -> !v.isNaN() } }
        val blockMax = if (maxs.isNotEmpty()) maxs.max() else 0.0
        val levels = rows.mapNotNull { it.optString("cape_level").takeIf { l -> l.isNotEmpty() } }
        val capeSev = mapOf("پایین" to 0, "متوسط" to 1, "بالا" to 2)
        val domLevel = levels.maxByOrNull { capeSev[it] ?: 0 } ?: "—"

        val b = JSONObject()
        b.put("start_iran", utcToIran(rows.first().optString("time")))
        b.put("end_iran", utcToIran(rows.last().optString("time")))
        b.put("temp_min", r2(if (temps.isNotEmpty()) temps.min() else null))
        b.put("temp_max", r2(if (temps.isNotEmpty()) temps.max() else null))
        b.put("precip_sum", r2(precips.sum()))
        b.put("precip_max", r2(blockMax))
        b.put("convective_risk", blockMax >= 0.5 && precips.sum() < 0.2 * rows.size)
        b.put("prob_max", r2(if (probs.isNotEmpty()) probs.max() else 0.0))
        b.put("cape_max", r2(if (capes.isNotEmpty()) capes.max() else 0.0))
        b.put("cape_level", domLevel)
        b.put("score_max", r2(if (scores.isNotEmpty()) scores.max() else 0.0))
        b.put("severe_max", r2(if (severes.isNotEmpty()) severes.max() else 0.0))
        b.put("cloud_mean", r2(if (clouds.isNotEmpty()) clouds.sum() / clouds.size else null))
        b.put("wind_max", r2(if (winds.isNotEmpty()) winds.max() else null))
        b.put("gust_max", r2(if (gusts.isNotEmpty()) gusts.max() else null))
        b.put("phase", domPhase)
        return b
    }

    private fun chunk(rows: List<JSONObject>, n: Int): List<List<JSONObject>> {
        val out = mutableListOf<List<JSONObject>>()
        var i = 0
        while (i < rows.size) {
            val end = Math.min(i + n, rows.size)
            out.add(rows.subList(i, end))
            i = end
        }
        return out
    }

    private fun groupByIranDay(rows: List<JSONObject>, prob: Map<String, Double>): List<JSONObject> {
        val groups = linkedMapOf<String, MutableList<JSONObject>>()
        for (r in rows) {
            val date = utcToIran(r.optString("time")).take(10)
            groups.getOrPut(date) { mutableListOf() }.add(r)
        }
        return groups.values.map { blockAgg(it, prob) }
    }

    fun buildRanges(analysis: JSONArray, prob: Map<String, Double>, nowIso: String): JSONObject {
        val nowHour = nowIso.take(13)
        val rows = mutableListOf<JSONObject>()
        for (i in 0 until analysis.length()) {
            val r = analysis.getJSONObject(i)
            if (r.optString("time").take(13) >= nowHour) rows.add(r)
        }
        val hourly = JSONArray()
        for (i in 0 until Math.min(168, rows.size)) {
            val r = rows[i]
            hourly.put(hourRow(r, prob[r.optString("time")] ?: 0.0, "none"))
        }
        val h6 = JSONArray()
        for (ch in chunk(rows.take(48), 6)) h6.put(blockAgg(ch, prob))
        val h12 = JSONArray()
        for (ch in chunk(rows.take(48), 12)) h12.put(blockAgg(ch, prob))
        val day = JSONArray()
        for (b in groupByIranDay(rows, prob).take(7)) day.put(b)

        val ranges = JSONObject()
        ranges.put("hourly", hourly)
        ranges.put("h6", h6)
        ranges.put("h12", h12)
        ranges.put("day", day)
        return ranges
    }

    // ------------------------------------------------------ dashboard

    fun buildDashboardJson(): String {
        return "window.FORECAST_DATA = " + buildDashboardData().toString() + ";"
    }

    fun buildDashboardData(): JSONObject {
        val models = OpenMeteoClient.fetchAllModels()
        val ensemble = buildEnsemble(models)
        val analysis = analyze(ensemble)
        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
            .format(java.util.Date())

        // احتمال ensemble
        val prob = mutableMapOf<String, Double>()
        val series = listOf("gfs", "ecmwf").map { OpenMeteoClient.fetchEnsembleProb(it) }
        val allTimes = series.flatMap { it.keys }.toSet()
        for (t in allTimes) {
            val vals = series.mapNotNull { it[t] }
            if (vals.isNotEmpty()) prob[t] = Math.round(vals.sum() / vals.size * 10) / 10.0
        }

        val track = buildTrack(analysis, nowIso)
        val pat = track.getJSONObject("pattern")
        val ranges = buildRanges(analysis, prob, nowIso)

        // verdict
        val hours = track.getJSONArray("window")
        var maxScore = 0.0
        var maxPrecip = 0.0
        for (i in 0 until hours.length()) {
            val h = hours.getJSONObject(i)
            maxScore = Math.max(maxScore, h.optDouble("thunderstorm_score", 0.0))
            maxPrecip = Math.max(maxPrecip, h.optDouble("precipitation_mean", 0.0))
        }
        val verdict: Pair<String, String> = when {
            maxScore >= 40 || maxPrecip >= 1 ->
                "alert" to "احتمال قابل توجه رعدوبرق/بارش همرفتی — هوشیاری لازم است."
            maxScore >= 15 || maxPrecip > 0 ->
                "watch" to "فعالیت همرفتی ضعیف تا متوسط؛ بارش سبک/پراکنده دور از انتظار نیست."
            else -> "ok" to "خطر همرفتی پایین؛ آسمان عمدتاً ابری بدون بارش محسوس."
        }

        val meta = JSONObject()
        meta.put("location", "بخش مرکزی دزفول (جنوب شهر)")
        meta.put("lat", OpenMeteoClient.LAT)
        meta.put("lon", OpenMeteoClient.LON)
        meta.put("iran_offset", "+3:30")
        meta.put("generated_iran", utcToIran(track.optString("generated_at")))

        val patternOut = JSONObject()
        patternOut.put("window_start_iran", utcToIran(pat.optString("window_start")))
        patternOut.put("window_end_iran", utcToIran(pat.optString("window_end")))
        patternOut.put("peak_precip_time_iran", utcToIran(pat.optString("peak_precip_time")))
        patternOut.put("peak_precip_mm", r2(pat.optDouble("peak_precip_mm", 0.0)))
        patternOut.put("peak_ts_time_iran", utcToIran(pat.optString("peak_thunderstorm_time")))
        patternOut.put("peak_ts_score", r2(pat.optDouble("peak_thunderstorm_score", 0.0)))
        patternOut.put("active_duration_h", pat.optInt("active_duration_h", 0))
        patternOut.put("max_cape", r2(pat.optDouble("max_cape", 0.0)))
        val trigIran = JSONArray()
        val trig = pat.optJSONArray("trigger_times") ?: JSONArray()
        for (i in 0 until trig.length()) trigIran.put(utcToIran(trig.optString(i)))
        patternOut.put("trigger_times_iran", trigIran)

        val weightsOut = JSONObject()
        if (analysis.length() > 0) {
            val w = analysis.getJSONObject(0).optJSONObject("model_weights") ?: JSONObject()
            val it = w.keys()
            while (it.hasNext()) {
                val k = it.next()
                weightsOut.put(k, Math.round(w.optDouble(k, 0.0) * 1000) / 1000.0)
            }
        }

        val d = JSONObject()
        d.put("meta", meta)
        d.put("pattern", patternOut)
        d.put("verdict", JSONObject().put("level", verdict.first).put("text", verdict.second))
        d.put("model_weights", weightsOut)
        d.put("plumes", JSONArray())
        d.put("hours", hours)
        d.put("ranges", ranges)
        d.put("station", JSONObject())
        d.put("observed", JSONArray())
        d.put("nowcast", JSONObject())
        d.put("verification", JSONObject())

        return d
    }
}
