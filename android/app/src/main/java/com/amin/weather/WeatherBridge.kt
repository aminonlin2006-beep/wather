package com.amin.weather

import android.content.Context
import com.amin.weather.core.ForecastEngine
import java.util.concurrent.Executors

/**
 * پل بین داشبورد وب و اندروید.
 * [refreshData] کل زنجیره محاسباتی (واکشی مدل‌ها → ensemble → همرفتی →
 * رهگیری → بازه‌ها) را در Kotlin اجرا می‌کند و رشته JS کامل
 * `window.FORECAST_DATA = {...};` برمی‌گرداند — بدون نیاز به سرور پایتون.
 */
class WeatherBridge(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * اجرای به‌روزرسانی در پس‌زمینه و تحویل آبجکت JSON (رشته) به [onDone].
     * این آبجکت در localStorage ذخیره می‌شود تا داشبورد بعد از reload آن را
     * بخواند — بدون نیاز به سرور.
     */
    fun refreshData(onDone: (String) -> Unit) {
        executor.execute {
            val json = try {
                ForecastEngine.buildDashboardData().toString()
            } catch (e: Exception) {
                "{\"error\": " + JSONQuote(e.message ?: "unknown") + "}"
            }
            onDone(json)
        }
    }

    /** نقل‌قول امن یک رشته برای داخل‌شدن در جاوااسکریپت. */
    private fun JSONQuote(s: String): String {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
