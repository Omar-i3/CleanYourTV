package com.chelseasaver.dream.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class DailyForecast(
    val dayLabel: String,
    val minTemp: Int,
    val maxTemp: Int,
    val iconCode: String
)

data class WeatherSnapshot(
    val currentTemp: Int,
    val description: String,
    val iconCode: String,
    val daily: List<DailyForecast>
)

/**
 * جلب طقس مدينة جدة بدقة عالية ومجاناً 100% بدون الحاجة لأي مفتاح API (عبر Open-Meteo الموثوق):
 * يعطي درجات الحرارة الحالية والتوقعات اليومية بدقة بتوقيت مكة المكرمة.
 */
object WeatherRepository {

    private var cachedSnapshot: WeatherSnapshot? = null

    fun getCachedOrFallback(): WeatherSnapshot {
        return cachedSnapshot ?: WeatherSnapshot(
            currentTemp = 31,
            description = "صافٍ ومعتدل",
            iconCode = "01d",
            daily = listOf(
                DailyForecast("اليوم", 25, 33, "01d"),
                DailyForecast("غداً", 24, 32, "01d"),
                DailyForecast("بعد غد", 25, 34, "02d"),
                DailyForecast("الخميس", 26, 33, "01d"),
                DailyForecast("الجمعة", 25, 32, "02d")
            )
        )
    }

    fun fetchWeather(
        lat: Double = ApiConfig.CITY_LAT,
        lon: Double = ApiConfig.CITY_LON,
        apiKey: String = ""
    ): WeatherSnapshot {
        val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=Asia%2FRiyadh"

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "KaiScreenSaver/2.0")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return getCachedOrFallback()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)

            val current = root.optJSONObject("current_weather") ?: return getCachedOrFallback()
            val currentTemp = current.optDouble("temperature", 30.0).toInt()
            val currentCode = current.optInt("weathercode", 0)
            val (currentIcon, currentDesc) = mapWeatherCode(currentCode)

            val dailyObj = root.optJSONObject("daily")
            val dailyList = mutableListOf<DailyForecast>()

            if (dailyObj != null) {
                val times = dailyObj.optJSONArray("time")
                val maxs = dailyObj.optJSONArray("temperature_2m_max")
                val mins = dailyObj.optJSONArray("temperature_2m_min")
                val codes = dailyObj.optJSONArray("weathercode")

                val dateParse = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone(ApiConfig.TIMEZONE)
                }
                val dayFormat = SimpleDateFormat("EEEE", Locale("ar")).apply {
                    timeZone = TimeZone.getTimeZone(ApiConfig.TIMEZONE)
                }

                if (times != null && maxs != null && mins != null) {
                    val count = minOf(times.length(), 5)
                    for (i in 0 until count) {
                        val dStr = times.optString(i)
                        val maxT = maxs.optDouble(i, 32.0).toInt()
                        val minT = mins.optDouble(i, 24.0).toInt()
                        val code = codes?.optInt(i, 0) ?: 0
                        val (icon, _) = mapWeatherCode(code)

                        val dName = try {
                            val parsed = dateParse.parse(dStr)
                            if (parsed != null) dayFormat.format(parsed) else dStr
                        } catch (e: Exception) {
                            dStr
                        }

                        dailyList.add(
                            DailyForecast(
                                dayLabel = dName,
                                minTemp = minT,
                                maxTemp = maxT,
                                iconCode = icon
                            )
                        )
                    }
                }
            }

            val snapshot = WeatherSnapshot(
                currentTemp = currentTemp,
                description = currentDesc,
                iconCode = currentIcon,
                daily = dailyList
            )
            cachedSnapshot = snapshot
            snapshot
        } catch (e: Exception) {
            getCachedOrFallback()
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapWeatherCode(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("01d", "صافٍ")
            1, 2 -> Pair("02d", "غائم جزئياً")
            3 -> Pair("03d", "غائم")
            45, 48 -> Pair("50d", "ضباب خفيف")
            51, 53, 55 -> Pair("09d", "رذاذ مطر")
            61, 63, 65 -> Pair("10d", "أمطار")
            71, 73, 75 -> Pair("13d", "أجواء باردة")
            80, 81, 82 -> Pair("10d", "زخات مطر")
            95, 96, 99 -> Pair("11d", "عواصف رعدية")
            else -> Pair("01d", "معتدل")
        }
    }
}
