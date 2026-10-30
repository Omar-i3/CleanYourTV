package com.chelseasaver.dream.data

import android.content.Context
import android.content.SharedPreferences

/**
 * إدارة إعدادات وتخصيصات شاشة التوقف:
 * المدينة المختارة، النادي الأول والثاني، فترات الإقامة المخصصة،
 * شريط أخبار النادي، سرعة تبديل النتائج والخلفيات ومصدر الصور.
 */
object ScreensaverConfig {

    const val PREFS_NAME = "kai_screensaver_prefs"

    fun getCityName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("city_name", ApiConfig.CITY_NAME) ?: ApiConfig.CITY_NAME

    fun getCityLat(context: Context): Double =
        java.lang.Double.longBitsToDouble(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong("city_lat", java.lang.Double.doubleToRawLongBits(ApiConfig.CITY_LAT))
        )

    fun getCityLon(context: Context): Double =
        java.lang.Double.longBitsToDouble(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong("city_lon", java.lang.Double.doubleToRawLongBits(ApiConfig.CITY_LON))
        )

    fun getBgIntervalMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("bg_interval_ms", 90_000L)

    // النادي الأول (الأساسي)
    fun getTeamId(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("team_id", ApiConfig.TEAM_ID)

    fun getTeamName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("team_name", ApiConfig.TEAM_NAME) ?: ApiConfig.TEAM_NAME

    // النادي الثاني (إضافي لمتابعة نتائج ناديين معاً، 0 = معطّل)
    fun getTeam2Id(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("team2_id", 0)

    fun getTeam2Name(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("team2_name", "") ?: ""

    // شريط أخبار النادي
    fun showNews(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("show_news", true)

    // سرعة تدوير المباريات بين الناديين (بالثواني)
    fun getMatchRotateSec(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("match_rotate_sec", 10)

    // الأرقام العربية (٠-٩) أو اللاتينية (0-9)
    fun isArabicNumerals(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("arabic_numerals", true)

    // نوع الخط المفضل ("thmanyah", "dubai", "system")
    fun getFontFamily(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("font_family", "thmanyah") ?: "thmanyah"

    // حجم الخط ونصوص الشاشة ("normal", "medium", "large")
    fun getFontScale(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("font_scale", "normal") ?: "normal"

    // تعتيم وتظليل خلفية شاشة التوقف للتباين ("light", "medium", "heavy")
    fun getBgDim(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("bg_dim", "medium") ?: "medium"

    // نظام عرض الساعة ("12h", "24h")
    fun getClockFormat(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("clock_format", "12h") ?: "12h"

    // عرض الثواني الرقمية
    fun showClockSeconds(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("clock_seconds", false)

    // نص الترحيب العائلي المخصص
    fun getGreetingText(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("greeting_text", "") ?: ""

    // سرعة تبديل أخبار النادي (بالثواني)
    fun getNewsSpeedSec(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("news_speed_sec", 10)

    fun getBgSource(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("bg_source", "landmarks") ?: "landmarks"

    fun isMatchdayMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("matchday_mode", true)

    fun isPrayerAlertEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("prayer_alert", true)

    // فترات الإقامة بالدقائق لكل صلاة
    fun getIqamahFajr(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("iqamah_fajr", ApiConfig.IQAMAH_FAJR_MIN)

    fun getIqamahDhuhr(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("iqamah_dhuhr", ApiConfig.IQAMAH_DHUHR_MIN)

    fun getIqamahAsr(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("iqamah_asr", ApiConfig.IQAMAH_ASR_MIN)

    fun getIqamahMaghrib(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("iqamah_maghrib", ApiConfig.IQAMAH_MAGHRIB_MIN)

    fun getIqamahIsha(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("iqamah_isha", ApiConfig.IQAMAH_ISHA_MIN)

    fun showWeather(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("show_weather", true)

    fun showHadith(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("show_hadith", true)

    fun showPrayers(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("show_prayers", true)

    fun showRamadan(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("show_ramadan", true)

    fun showMatches(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("show_matches", true)

    fun saveConfig(
        context: Context,
        cityName: String? = null,
        cityLat: Double? = null,
        cityLon: Double? = null,
        bgIntervalMs: Long? = null,
        showWeather: Boolean? = null,
        showHadith: Boolean? = null,
        showPrayers: Boolean? = null,
        showRamadan: Boolean? = null,
        showMatches: Boolean? = null,
        teamId: Int? = null,
        teamName: String? = null,
        team2Id: Int? = null,
        team2Name: String? = null,
        showNews: Boolean? = null,
        matchRotateSec: Int? = null,
        arabicNumerals: Boolean? = null,
        bgSource: String? = null,
        matchdayMode: Boolean? = null,
        prayerAlert: Boolean? = null,
        iqamahFajr: Int? = null,
        iqamahDhuhr: Int? = null,
        iqamahAsr: Int? = null,
        iqamahMaghrib: Int? = null,
        iqamahIsha: Int? = null,
        fontFamily: String? = null,
        fontScale: String? = null,
        bgDim: String? = null,
        clockFormat: String? = null,
        clockSeconds: Boolean? = null,
        greetingText: String? = null,
        newsSpeedSec: Int? = null
    ) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        cityName?.let { editor.putString("city_name", it) }
        cityLat?.let { editor.putLong("city_lat", java.lang.Double.doubleToRawLongBits(it)) }
        cityLon?.let { editor.putLong("city_lon", java.lang.Double.doubleToRawLongBits(it)) }
        bgIntervalMs?.let { editor.putLong("bg_interval_ms", it) }
        showWeather?.let { editor.putBoolean("show_weather", it) }
        showHadith?.let { editor.putBoolean("show_hadith", it) }
        showPrayers?.let { editor.putBoolean("show_prayers", it) }
        showRamadan?.let { editor.putBoolean("show_ramadan", it) }
        showMatches?.let { editor.putBoolean("show_matches", it) }

        teamId?.let { editor.putInt("team_id", it) }
        teamName?.let { editor.putString("team_name", it) }
        team2Id?.let { editor.putInt("team2_id", it) }
        team2Name?.let { editor.putString("team2_name", it) }
        showNews?.let { editor.putBoolean("show_news", it) }
        matchRotateSec?.let { editor.putInt("match_rotate_sec", it) }
        arabicNumerals?.let { editor.putBoolean("arabic_numerals", it) }

        fontFamily?.let { editor.putString("font_family", it) }
        fontScale?.let { editor.putString("font_scale", it) }
        bgDim?.let { editor.putString("bg_dim", it) }
        clockFormat?.let { editor.putString("clock_format", it) }
        clockSeconds?.let { editor.putBoolean("clock_seconds", it) }
        greetingText?.let { editor.putString("greeting_text", it) }
        newsSpeedSec?.let { editor.putInt("news_speed_sec", it) }

        bgSource?.let { editor.putString("bg_source", it) }
        matchdayMode?.let { editor.putBoolean("matchday_mode", it) }
        prayerAlert?.let { editor.putBoolean("prayer_alert", it) }

        iqamahFajr?.let { editor.putInt("iqamah_fajr", it) }
        iqamahDhuhr?.let { editor.putInt("iqamah_dhuhr", it) }
        iqamahAsr?.let { editor.putInt("iqamah_asr", it) }
        iqamahMaghrib?.let { editor.putInt("iqamah_maghrib", it) }
        iqamahIsha?.let { editor.putInt("iqamah_isha", it) }

        editor.apply()
    }
}
