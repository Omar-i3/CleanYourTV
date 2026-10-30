package com.chelseasaver.dream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chelseasaver.dream.data.ScreensaverConfig

/**
 * يستقبل تحديثات التخصيص من لانشر CleanYourTV أو أي تطبيق إعدادات
 * ويحفظها فوراً لتطبيقها على شاشة التوقف.
 */
class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if ("com.chelseasaver.dream.ACTION_UPDATE_CONFIG" != intent.action) return

        val cityName = intent.getStringExtra("city_name")
        val cityLat = if (intent.hasExtra("city_lat")) intent.getDoubleExtra("city_lat", 0.0) else null
        val cityLon = if (intent.hasExtra("city_lon")) intent.getDoubleExtra("city_lon", 0.0) else null
        val bgIntervalMs = if (intent.hasExtra("bg_interval_ms")) intent.getLongExtra("bg_interval_ms", 90_000L) else null
        val showWeather = if (intent.hasExtra("show_weather")) intent.getBooleanExtra("show_weather", true) else null
        val showHadith = if (intent.hasExtra("show_hadith")) intent.getBooleanExtra("show_hadith", true) else null
        val showPrayers = if (intent.hasExtra("show_prayers")) intent.getBooleanExtra("show_prayers", true) else null
        val showRamadan = if (intent.hasExtra("show_ramadan")) intent.getBooleanExtra("show_ramadan", true) else null
        val showMatches = if (intent.hasExtra("show_matches")) intent.getBooleanExtra("show_matches", true) else null

        val teamId = if (intent.hasExtra("team_id")) intent.getIntExtra("team_id", 929) else null
        val teamName = intent.getStringExtra("team_name")

        val team2Id = if (intent.hasExtra("team2_id")) intent.getIntExtra("team2_id", 0) else null
        val team2Name = intent.getStringExtra("team2_name")
        val showNews = if (intent.hasExtra("show_news")) intent.getBooleanExtra("show_news", true) else null
        val matchRotateSec = if (intent.hasExtra("match_rotate_sec")) intent.getIntExtra("match_rotate_sec", 10) else null
        val arabicNumerals = if (intent.hasExtra("arabic_numerals")) intent.getBooleanExtra("arabic_numerals", true) else null

        val bgSource = intent.getStringExtra("bg_source")
        val matchdayMode = if (intent.hasExtra("matchday_mode")) intent.getBooleanExtra("matchday_mode", true) else null
        val prayerAlert = if (intent.hasExtra("prayer_alert")) intent.getBooleanExtra("prayer_alert", true) else null

        val iqamahFajr = if (intent.hasExtra("iqamah_fajr")) intent.getIntExtra("iqamah_fajr", 25) else null
        val iqamahDhuhr = if (intent.hasExtra("iqamah_dhuhr")) intent.getIntExtra("iqamah_dhuhr", 20) else null
        val iqamahAsr = if (intent.hasExtra("iqamah_asr")) intent.getIntExtra("iqamah_asr", 20) else null
        val iqamahMaghrib = if (intent.hasExtra("iqamah_maghrib")) intent.getIntExtra("iqamah_maghrib", 10) else null
        val iqamahIsha = if (intent.hasExtra("iqamah_isha")) intent.getIntExtra("iqamah_isha", 20) else null

        val fontFamily = intent.getStringExtra("font_family")
        val fontScale = intent.getStringExtra("font_scale")
        val bgDim = intent.getStringExtra("bg_dim")
        val clockFormat = intent.getStringExtra("clock_format")
        val clockSeconds = if (intent.hasExtra("clock_seconds")) intent.getBooleanExtra("clock_seconds", false) else null
        val greetingText = intent.getStringExtra("greeting_text")
        val newsSpeedSec = if (intent.hasExtra("news_speed_sec")) intent.getIntExtra("news_speed_sec", 10) else null

        ScreensaverConfig.saveConfig(
            context = context,
            cityName = cityName,
            cityLat = cityLat,
            cityLon = cityLon,
            bgIntervalMs = bgIntervalMs,
            showWeather = showWeather,
            showHadith = showHadith,
            showPrayers = showPrayers,
            showRamadan = showRamadan,
            showMatches = showMatches,
            teamId = teamId,
            teamName = teamName,
            team2Id = team2Id,
            team2Name = team2Name,
            showNews = showNews,
            matchRotateSec = matchRotateSec,
            arabicNumerals = arabicNumerals,
            bgSource = bgSource,
            matchdayMode = matchdayMode,
            prayerAlert = prayerAlert,
            iqamahFajr = iqamahFajr,
            iqamahDhuhr = iqamahDhuhr,
            iqamahAsr = iqamahAsr,
            iqamahMaghrib = iqamahMaghrib,
            iqamahIsha = iqamahIsha,
            fontFamily = fontFamily,
            fontScale = fontScale,
            bgDim = bgDim,
            clockFormat = clockFormat,
            clockSeconds = clockSeconds,
            greetingText = greetingText,
            newsSpeedSec = newsSpeedSec
        )
    }
}
