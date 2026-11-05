package com.amin.weather.core

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * کلاینت Open-Meteo: واکشی داده ساعتی چهار مدل قطعی + احتمال ensemble.
 * دقیقاً همان درخواست‌های پایتون (fetch_models.py / fetch_ensemble.py).
 */
object OpenMeteoClient {

    const val LAT = 32.3006
    const val LON = 48.4308

    // کلید داخلی → نام API مدل (سه مدل مستقل؛ GFS013 تکراری حذف شد)
    val API_MODELS = mapOf(
        "gfs" to "gfs_seamless",
        "ecmwf" to "ecmwf_ifs025",
        "icon" to "icon_seamless"
    )

    val VARIABLES = listOf(
        "temperature_2m", "precipitation", "cape", "lifted_index", "freezinglevel_height",
        "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high",
        "windspeed_10m", "windgusts_10m", "winddirection_10m", "relativehumidity_2m"
    )

    fun httpGet(url: String, timeoutMs: Int = 60000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode != 200) {
                return ""
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            return reader.use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** واکشی یک مدل قطعی → JSONObject با آرایه‌های ساعتی. */
    fun fetchModel(key: String): JSONObject {
        val apiModel = API_MODELS[key] ?: return JSONObject()
        val vars = VARIABLES.joinToString(",")
        val url = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$LAT&longitude=$LON" +
            "&hourly=$vars" +
            "&forecast_days=8&timezone=UTC&models=$apiModel"
        val raw = httpGet(url)
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    /** واکشی احتمال ensemble برای یک مدل → Map<ساعت, احتمال>. */
    fun fetchEnsembleProb(key: String): Map<String, Double> {
        val apiModel = when (key) {
            "gfs" -> "gfs_seamless"
            "ecmwf" -> "ecmwf_ifs025"
            else -> return emptyMap()
        }
        val url = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$LAT&longitude=$LON" +
            "&hourly=temperature_2m,precipitation_probability,precipitation" +
            "&forecast_days=8&timezone=UTC&models=$apiModel&ensemble=true"
        val raw = httpGet(url)
        if (raw.isBlank()) return emptyMap()
        val obj = JSONObject(raw)
        val hourly = obj.optJSONObject("hourly") ?: return emptyMap()
        val times = hourly.optJSONArray("time") ?: return emptyMap()
        val probs = hourly.optJSONArray("precipitation_probability") ?: return emptyMap()
        val out = mutableMapOf<String, Double>()
        for (i in 0 until times.length()) {
            val t = times.optString(i)
            val p = probs.optDouble(i, Double.NaN)
            if (t.isNotEmpty() && !p.isNaN()) out[t] = p
        }
        return out
    }

    /** واکشی همه مدل‌ها → Map<کلید, JSONObject>. */
    fun fetchAllModels(): Map<String, JSONObject> {
        return API_MODELS.keys.associateWith { fetchModel(it) }
    }
}
