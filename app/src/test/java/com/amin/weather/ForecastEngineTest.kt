package com.amin.weather

import com.amin.weather.core.ForecastEngine
import com.amin.weather.core.ForecastRange
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineTest {

    @Test
    fun testBuildDashboardDataAndParsing() {
        println("Starting testBuildDashboardDataAndParsing...")
        val json = ForecastEngine.buildDashboardData()
        println("JSON generated successfully, size: ${json.toString().length}")

        println("Parsed JSON keys: ${json.keys().asSequence().toList()}")
        assertTrue(json.has("verdict"))
        assertTrue(json.has("radar_nowcast"))
        assertTrue(json.has("perimeter_network"))
        assertTrue(json.has("triangulation"))
        assertTrue(json.has("station_telemetry"))
        assertTrue(json.has("pattern"))
        assertTrue(json.has("ranges"))

        val tri = json.getJSONObject("triangulation")
        println("Triangulation primary: ${tri.optString("primary_station_name")}, SPP: ${tri.optDouble("spatial_precip_probability")}%, area: ${tri.optDouble("triangle_area_sq_km")} km²")
        assertTrue(tri.optBoolean("is_inside_triangle"))
        assertTrue(tri.optDouble("triangle_area_sq_km") in 700.0..800.0)
        assertTrue(tri.optDouble("spatial_precip_probability") in 0.0..100.0)

        val vertices = tri.getJSONArray("vertices")
        println("Triangulation vertices count: ${vertices.length()}")
        assertTrue(vertices.length() == 3)
        var sumWeights = 0.0
        for (i in 0 until vertices.length()) {
            val v = vertices.getJSONObject(i)
            val w = v.getDouble("barycentric_weight")
            println("  Vertex ${v.optString("name")}: weight = $w, dist = ${v.optDouble("distance_km")} km, rainRate = ${v.optDouble("rain_rate_mm_h")}")
            assertTrue(w > 0.1)
            sumWeights += w
        }
        println("Sum of barycentric weights: $sumWeights")
        assertTrue(Math.abs(sumWeights - 1.0) < 0.05)

        val radar = json.getJSONObject("radar_nowcast")
        println("Radar tier: ${radar.optString("tier")}, status: ${radar.optString("status_text")}, trend: ${radar.optString("cloud_trend_label")}")
        assertTrue(radar.has("cloud_trend_label"))
        assertTrue(radar.has("is_virga_likely"))

        val perim = json.getJSONObject("perimeter_network")
        val stations = perim.getJSONArray("stations")
        println("Perimeter stations count: ${stations.length()}, distant warnings: ${perim.optInt("distant_warning_stations_count")}")
        assertTrue(stations.length() >= 8)
        assertTrue(perim.has("distant_warning_stations_count"))

        val station = json.getJSONObject("station_telemetry")
        println("Central Station: ${station.optString("station_name")}, temp: ${station.opt("current_temp")}")

        val ranges = json.getJSONObject("ranges")
        for (range in ForecastRange.entries) {
            val key = range.key
            assertTrue("Ranges should contain $key", ranges.has(key))
            val arr = ranges.getJSONArray(key)
            println("Range $key has ${arr.length()} items.")
            assertTrue("Range $key should not be empty", arr.length() > 0)
            val first = arr.getJSONObject(0)
            println("  First item time_iran: ${first.optString("time_iran")}, source: ${first.optString("forecast_source")}")
        }

        // بررسی پیش‌بینی تفکیک‌شده توده‌ای (Convective Cell Forecast)
        assertTrue("Dashboard must contain cell_forecast", json.has("cell_forecast"))
        val cell = json.getJSONObject("cell_forecast")
        println("Cell Forecast: prob=${cell.optDouble("cell_risk_probability")}%, window=${cell.optString("critical_window_iran")}, type=${cell.optString("cell_type_label")}")
        assertTrue(cell.has("cell_risk_probability"))
        assertTrue(cell.has("potential_cell_precip_max_mm"))
        assertTrue(cell.has("critical_window_iran"))
        assertTrue(cell.has("cell_type_label"))

        // تست کالیبراسیون با دیدبانی شهروندی کاربر
        val userReport = com.amin.weather.core.SkyObservationReport(
            id = "test-1",
            photoBase64 = null,
            direction = "غرب",
            phenomenon = "رعدوبرق",
            userTemp = 23.5,
            notes = "تگرگ و صاعقه در غرب",
            timestampIran = "۱۴:۰۰",
            isCalibrated = true
        )
        val calibratedJson = ForecastEngine.buildDashboardData(userReport)
        assertTrue(calibratedJson.has("user_observation"))
        val uObs = calibratedJson.getJSONObject("user_observation")
        assertTrue(uObs.optBoolean("is_calibrated"))
        val calibCell = calibratedJson.getJSONObject("cell_forecast")
        println("Calibrated Cell Forecast: prob=${calibCell.optDouble("cell_risk_probability")}%")
        assertTrue(calibCell.optDouble("cell_risk_probability") >= 80.0)
    }
}
