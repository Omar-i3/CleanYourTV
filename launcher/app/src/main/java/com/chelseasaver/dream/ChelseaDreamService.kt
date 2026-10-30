package com.chelseasaver.dream

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chelseasaver.dream.data.ApiConfig
import com.chelseasaver.dream.data.ScreensaverConfig
import com.chelseasaver.dream.data.TeamMatch
import com.chelseasaver.dream.data.FootballRepository
import com.chelseasaver.dream.data.PrayerDay
import com.chelseasaver.dream.data.PrayerEvent
import com.chelseasaver.dream.data.PrayerRepository
import com.chelseasaver.dream.data.RamadanCalculator
import com.chelseasaver.dream.data.WeatherRepository
import com.chelseasaver.dream.data.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * الشاشة الرسمية (Screen Saver / DreamService). كل عملها هنا هو:
 *  1) عرض WebView بملء الشاشة يحمّل dashboard.html من الأصول (assets).
 *  2) عند تحميل الصفحة: دفع إعدادات ApiConfig (المدينة/الفريق/المنطقة الزمنية) وقائمة صور الخلفية
 *     الموجودة في assets/backgrounds (الصفحة هي من تبدّلها).
 *  3) كل دقيقة: حساب عداد رمضان محليًا + أوقات الصلاة (من الكاش أولًا) + جلب الطقس والمباريات
 *     من الشبكة في خيط خلفي، ثم دفعها إلى صفحة الويب عبر evaluateJavascript.
 * الساعة وعدّاد الصلاة ودوران المباريات والخلفيات كلها تُحدَّث داخل JavaScript محليًا
 * (لا تحتاج اتصال شبكة).
 */
class ChelseaDreamService : DreamService() {

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundExecutor: ExecutorService? = null

    private val periodicRefresh = object : Runnable {
        override fun run() {
            refreshData()
            mainHandler.postDelayed(this, ApiConfig.REFRESH_INTERVAL_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // إعدادات شاشة التوقف: غير تفاعلية، ملء الشاشة، وبسطوع طبيعي (وليس وضع Doze خافت)
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        backgroundExecutor = Executors.newSingleThreadExecutor()

        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(Color.parseColor("#070B1A"))
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // إعدادات المدينة والفريق والتوقيت من ApiConfig تُدفع أولًا ومرة واحدة،
                    // فتأخذ الصفحة اسم المدينة واسم الفريق والمنطقة الزمنية قبل أي بيانات
                    pushConfig()
                    pushBackgrounds()
                    pushWeather(WeatherRepository.getCachedOrFallback())
                    refreshData()
                }
            }
            loadUrl("file:///android_asset/dashboard.html")
        }

        webView = wv
        setContentView(wv)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        mainHandler.postDelayed(periodicRefresh, ApiConfig.REFRESH_INTERVAL_MS)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        mainHandler.removeCallbacks(periodicRefresh)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(periodicRefresh)
        backgroundExecutor?.shutdownNow()
        backgroundExecutor = null
        webView?.destroy()
        webView = null
    }

    private fun refreshData() {
        // عداد رمضان حساب محلي فوري، لا يحتاج شبكة
        pushRamadanCountdown()

        val executor = backgroundExecutor ?: return
        executor.execute {
            // أوقات الصلاة أولًا: المرحلة 1 من الكاش المحلي فورًا (حتى لو الشبكة بطيئة أو مقطوعة)،
            // والمرحلة 2 تجلب من الشبكة فقط إن نقص الكاش أيامًا قادمة، وتدفع النتيجة إن تغيّرت.
            val cityLat = ScreensaverConfig.getCityLat(this@ChelseaDreamService)
            val cityLon = ScreensaverConfig.getCityLon(this@ChelseaDreamService)

            val cachedPrayers = try {
                PrayerRepository.getSchedule(
                    applicationContext, cityLat, cityLon, allowNetwork = false
                )
            } catch (e: Exception) {
                emptyList()
            }
            val todayCached = PrayerRepository.getTodayPrayers(applicationContext, cityLat, cityLon)
            if (cachedPrayers.isNotEmpty()) {
                mainHandler.post { pushPrayers(cachedPrayers, todayCached) }
            }

            val freshPrayers = try {
                PrayerRepository.getSchedule(
                    applicationContext, cityLat, cityLon, allowNetwork = true
                )
            } catch (e: Exception) {
                cachedPrayers
            }
            val todayFresh = PrayerRepository.getTodayPrayers(applicationContext, cityLat, cityLon) ?: todayCached
            if (freshPrayers != cachedPrayers || freshPrayers.isEmpty()) {
                mainHandler.post { pushPrayers(freshPrayers, todayFresh) }
            }

            val weather = try {
                WeatherRepository.fetchWeather(
                    cityLat,
                    cityLon,
                    ApiConfig.OPENWEATHER_API_KEY
                )
            } catch (e: Exception) {
                null
            }

            // مباريات الناديين (الأساسي + الإضافي إن وجد) من واجهة ESPN المباشرة
            val team1Id = ScreensaverConfig.getTeamId(this@ChelseaDreamService)
            val team1Name = ScreensaverConfig.getTeamName(this@ChelseaDreamService)
            val team2Id = ScreensaverConfig.getTeam2Id(this@ChelseaDreamService)
            val team2Name = ScreensaverConfig.getTeam2Name(this@ChelseaDreamService)

            val matches = try {
                com.chelseasaver.dream.data.FootballRepository.fetchMatchesForDualTeams(
                    team1Id, team1Name, team2Id, team2Name
                )
            } catch (e: Exception) {
                emptyList()
            }

            val news = try {
                com.chelseasaver.dream.data.FootballRepository.fetchClubNews(team1Name, team2Name)
            } catch (e: Exception) {
                emptyList()
            }

            mainHandler.post {
                weather?.let { pushWeather(it) }
                pushMatches(matches)
                pushNews(news)
            }
        }
    }

    private fun pushRamadanCountdown() {
        val countdown = RamadanCalculator.countdownToRamadan()
        val json = JSONObject().apply {
            put("days", countdown.days)
            put("hours", countdown.hours)
            put("minutes", countdown.minutes)
            put("target", countdown.targetMillis)
        }
        evaluateJs("updateRamadan($json)")
    }

    private fun pushPrayers(events: List<PrayerEvent>, today: PrayerDay? = null) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("key", e.key)
                    put("ts", e.epochMillis)
                }
            )
        }
        val json = JSONObject().apply {
            put("events", arr)
            today?.let { t ->
                put(
                    "today",
                    JSONObject().apply {
                        put("fajr", t.fajr)
                        put("dhuhr", t.dhuhr)
                        put("asr", t.asr)
                        put("maghrib", t.maghrib)
                        put("isha", t.isha)
                    }
                )
            }
        }
        evaluateJs("updatePrayers($json)")
    }

    // يدفع إعدادات ApiConfig التي تحتاجها الصفحة: اسم المدينة (فوق الطقس)، اسم الفريق (في المباراة)،
    // والمنطقة الزمنية لحساب الساعة والتواريخ. offsetMs إزاحة المنطقة الآن، احتياط فقط إن لم يدعم
    // الـ WebView أسماء المناطق الزمنية (نادر جدًا).
    private fun pushConfig() {
        val json = JSONObject().apply {
            put("city", ScreensaverConfig.getCityName(this@ChelseaDreamService))
            put("team", ScreensaverConfig.getTeamName(this@ChelseaDreamService))
            put("teamId", ScreensaverConfig.getTeamId(this@ChelseaDreamService))
            put("team2", ScreensaverConfig.getTeam2Name(this@ChelseaDreamService))
            put("team2Id", ScreensaverConfig.getTeam2Id(this@ChelseaDreamService))
            put("showNews", ScreensaverConfig.showNews(this@ChelseaDreamService))
            put("matchRotateSec", ScreensaverConfig.getMatchRotateSec(this@ChelseaDreamService))
            put("arabicNumerals", ScreensaverConfig.isArabicNumerals(this@ChelseaDreamService))
            put("fontFamily", ScreensaverConfig.getFontFamily(this@ChelseaDreamService))
            put("fontScale", ScreensaverConfig.getFontScale(this@ChelseaDreamService))
            put("bgDim", ScreensaverConfig.getBgDim(this@ChelseaDreamService))
            put("clockFormat", ScreensaverConfig.getClockFormat(this@ChelseaDreamService))
            put("clockSeconds", ScreensaverConfig.showClockSeconds(this@ChelseaDreamService))
            put("greeting", ScreensaverConfig.getGreetingText(this@ChelseaDreamService))
            put("newsSpeedSec", ScreensaverConfig.getNewsSpeedSec(this@ChelseaDreamService))
            put("tz", ApiConfig.TIMEZONE)
            put("offsetMs", TimeZone.getTimeZone(ApiConfig.TIMEZONE).getOffset(System.currentTimeMillis()))
            put("showWeather", ScreensaverConfig.showWeather(this@ChelseaDreamService))
            put("showHadith", ScreensaverConfig.showHadith(this@ChelseaDreamService))
            put("showPrayers", ScreensaverConfig.showPrayers(this@ChelseaDreamService))
            put("showRamadan", ScreensaverConfig.showRamadan(this@ChelseaDreamService))
            put("showMatches", ScreensaverConfig.showMatches(this@ChelseaDreamService))
            put("bgIntervalMs", ScreensaverConfig.getBgIntervalMs(this@ChelseaDreamService))
            put("bgSource", ScreensaverConfig.getBgSource(this@ChelseaDreamService))
            put("matchdayMode", ScreensaverConfig.isMatchdayMode(this@ChelseaDreamService))
            put("prayerAlert", ScreensaverConfig.isPrayerAlertEnabled(this@ChelseaDreamService))
            put(
                "iqamah",
                JSONObject().apply {
                    put("fajr", ScreensaverConfig.getIqamahFajr(this@ChelseaDreamService))
                    put("dhuhr", ScreensaverConfig.getIqamahDhuhr(this@ChelseaDreamService))
                    put("asr", ScreensaverConfig.getIqamahAsr(this@ChelseaDreamService))
                    put("maghrib", ScreensaverConfig.getIqamahMaghrib(this@ChelseaDreamService))
                    put("isha", ScreensaverConfig.getIqamahIsha(this@ChelseaDreamService))
                }
            )
        }
        evaluateJs("setConfig($json)")
    }

    // يقرأ صور المعالم وألبوم العائلة ويدفعها للصفحة
    private fun pushBackgrounds() {
        val bgSource = ScreensaverConfig.getBgSource(this@ChelseaDreamService)
        val resultList = mutableListOf<String>()

        if (bgSource == "landmarks" || bgSource == "both") {
            try {
                val assetNames = assets.list(ApiConfig.BACKGROUNDS_ASSET_DIR)
                    ?.filter { name ->
                        val lower = name.lowercase()
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                            lower.endsWith(".png") || lower.endsWith(".webp")
                    } ?: emptyList()
                resultList.addAll(assetNames)
            } catch (ignored: Exception) {}
        }

        if (bgSource == "family" || bgSource == "both") {
            try {
                val familyDirs = listOf(
                    java.io.File("/sdcard/Pictures/FamilyScreensaver"),
                    java.io.File("/sdcard/Screensaver/Photos"),
                    getExternalFilesDir("photos")
                )
                for (dir in familyDirs) {
                    if (dir != null && dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { file ->
                            val lower = file.name.lowercase()
                            if (file.isFile && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                                lower.endsWith(".png") || lower.endsWith(".webp"))) {
                                resultList.add(file.toURI().toString())
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        if (resultList.isEmpty()) {
            try {
                val assetNames = assets.list(ApiConfig.BACKGROUNDS_ASSET_DIR)
                    ?.filter { name ->
                        val lower = name.lowercase()
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                            lower.endsWith(".png") || lower.endsWith(".webp")
                    } ?: emptyList()
                resultList.addAll(assetNames)
            } catch (ignored: Exception) {}
        }

        val arr = JSONArray()
        resultList.forEach { arr.put(it) }
        evaluateJs("setBackgrounds($arr)")
    }

    private fun pushWeather(weather: WeatherSnapshot) {
        val dailyArray = JSONArray()
        weather.daily.forEach { day ->
            dailyArray.put(
                JSONObject().apply {
                    put("day", day.dayLabel)
                    put("min", day.minTemp)
                    put("max", day.maxTemp)
                    put("icon", day.iconCode)
                }
            )
        }
        val json = JSONObject().apply {
            put("temp", weather.currentTemp)
            put("description", weather.description)
            put("icon", weather.iconCode)
            put("daily", dailyArray)
        }
        evaluateJs("updateWeather($json)")
    }

    private fun pushMatches(matches: List<TeamMatch>) {
        val arr = JSONArray()
        matches.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("clubName", m.clubName)
                    put("opponent", m.opponent)
                    put("isHome", m.isHome)
                    put("competition", m.competition)
                    put("date", m.dateLabel)
                    put("time", m.timeLabel)
                    put("ts", m.kickoffMillis)
                    put("status", m.status)
                    put("statusText", m.statusText)
                    put("myScore", m.myScore)
                    put("oppScore", m.opponentScore)
                    put("venue", m.venue)
                    val goalsArr = JSONArray()
                    m.goals.forEach { g ->
                        goalsArr.put(
                            JSONObject().apply {
                                put("player", g.player)
                                put("minute", g.minute)
                                put("isMyTeam", g.isMyTeam)
                                put("isPenalty", g.isPenalty)
                                put("isOwnGoal", g.isOwnGoal)
                            }
                        )
                    }
                    put("goals", goalsArr)
                }
            )
        }
        evaluateJs("updateMatches($arr)")
    }

    private fun pushNews(news: List<com.chelseasaver.dream.data.ClubNewsItem>) {
        val arr = JSONArray()
        news.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("headline", item.headline)
                    put("source", item.source)
                    put("clubName", item.clubName)
                }
            )
        }
        evaluateJs("updateNews($arr)")
    }

    private fun evaluateJs(script: String) {
        webView?.evaluateJavascript(script, null)
    }
}
