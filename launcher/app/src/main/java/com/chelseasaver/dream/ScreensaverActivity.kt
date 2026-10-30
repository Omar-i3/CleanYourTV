package com.chelseasaver.dream

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chelseasaver.dream.data.ApiConfig
import com.chelseasaver.dream.data.ScreensaverConfig
import com.chelseasaver.dream.data.FootballRepository
import com.chelseasaver.dream.data.PrayerDay
import com.chelseasaver.dream.data.PrayerEvent
import com.chelseasaver.dream.data.PrayerRepository
import com.chelseasaver.dream.data.RamadanCalculator
import com.chelseasaver.dream.data.TeamMatch
import com.chelseasaver.dream.data.WeatherRepository
import com.chelseasaver.dream.data.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.PowerManager
import android.webkit.JavascriptInterface

/**
 * نشاط الواجهة الرئيسي (MainActivity):
 * يعرض شاشة التوقف مباشرة بملء الشاشة عند فتح التطبيق من قائمة تطبيقات التلفزيون.
 * يعرض الساعة، مواقيت الصلاة والإقامة لمدينة جدة، عداد رمضان، مباريات الهلال الحية، والطقس.
 */
class ScreensaverActivity : Activity() {

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundExecutor: ExecutorService? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val periodicRefresh = object : Runnable {
        override fun run() {
            refreshData()
            mainHandler.postDelayed(this, ApiConfig.REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // إبقاء الشاشة مضاءة وملء الشاشة بالكامل بدون أشرطة
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "KaiScreenSaver:AlwaysOnLock"
            )
            wakeLock?.acquire()
        } catch (e: Exception) {
            // تجاهل إن لم يدعم النظام الصلاحية
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()

        val wv = WebView(this).apply {
            keepScreenOn = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(Color.parseColor("#070B1A"))
            addJavascriptInterface(TvBridge(this@ScreensaverActivity), "TvBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {}
        mainHandler.postDelayed(periodicRefresh, ApiConfig.REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(periodicRefresh)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(periodicRefresh)
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {}
        backgroundExecutor?.shutdownNow()
        backgroundExecutor = null
        webView?.destroy()
        webView = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE)) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    private fun refreshData() {
        pushRamadanCountdown()

        val executor = backgroundExecutor ?: return
        executor.execute {
            val cityLat = ScreensaverConfig.getCityLat(this@ScreensaverActivity)
            val cityLon = ScreensaverConfig.getCityLon(this@ScreensaverActivity)

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
            val team1Id = ScreensaverConfig.getTeamId(this@ScreensaverActivity)
            val team1Name = ScreensaverConfig.getTeamName(this@ScreensaverActivity)
            val team2Id = ScreensaverConfig.getTeam2Id(this@ScreensaverActivity)
            val team2Name = ScreensaverConfig.getTeam2Name(this@ScreensaverActivity)

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

    private fun pushConfig() {
        val json = JSONObject().apply {
            put("city", ScreensaverConfig.getCityName(this@ScreensaverActivity))
            put("team", ScreensaverConfig.getTeamName(this@ScreensaverActivity))
            put("teamId", ScreensaverConfig.getTeamId(this@ScreensaverActivity))
            put("team2", ScreensaverConfig.getTeam2Name(this@ScreensaverActivity))
            put("team2Id", ScreensaverConfig.getTeam2Id(this@ScreensaverActivity))
            put("showNews", ScreensaverConfig.showNews(this@ScreensaverActivity))
            put("matchRotateSec", ScreensaverConfig.getMatchRotateSec(this@ScreensaverActivity))
            put("arabicNumerals", ScreensaverConfig.isArabicNumerals(this@ScreensaverActivity))
            put("fontFamily", ScreensaverConfig.getFontFamily(this@ScreensaverActivity))
            put("fontScale", ScreensaverConfig.getFontScale(this@ScreensaverActivity))
            put("bgDim", ScreensaverConfig.getBgDim(this@ScreensaverActivity))
            put("clockFormat", ScreensaverConfig.getClockFormat(this@ScreensaverActivity))
            put("clockSeconds", ScreensaverConfig.showClockSeconds(this@ScreensaverActivity))
            put("greeting", ScreensaverConfig.getGreetingText(this@ScreensaverActivity))
            put("newsSpeedSec", ScreensaverConfig.getNewsSpeedSec(this@ScreensaverActivity))
            put("tz", ApiConfig.TIMEZONE)
            put("offsetMs", TimeZone.getTimeZone(ApiConfig.TIMEZONE).getOffset(System.currentTimeMillis()))
            put("showWeather", ScreensaverConfig.showWeather(this@ScreensaverActivity))
            put("showHadith", ScreensaverConfig.showHadith(this@ScreensaverActivity))
            put("showPrayers", ScreensaverConfig.showPrayers(this@ScreensaverActivity))
            put("showRamadan", ScreensaverConfig.showRamadan(this@ScreensaverActivity))
            put("showMatches", ScreensaverConfig.showMatches(this@ScreensaverActivity))
            put("bgIntervalMs", ScreensaverConfig.getBgIntervalMs(this@ScreensaverActivity))
            put("bgSource", ScreensaverConfig.getBgSource(this@ScreensaverActivity))
            put("matchdayMode", ScreensaverConfig.isMatchdayMode(this@ScreensaverActivity))
            put("prayerAlert", ScreensaverConfig.isPrayerAlertEnabled(this@ScreensaverActivity))
            put(
                "iqamah",
                JSONObject().apply {
                    put("fajr", ScreensaverConfig.getIqamahFajr(this@ScreensaverActivity))
                    put("dhuhr", ScreensaverConfig.getIqamahDhuhr(this@ScreensaverActivity))
                    put("asr", ScreensaverConfig.getIqamahAsr(this@ScreensaverActivity))
                    put("maghrib", ScreensaverConfig.getIqamahMaghrib(this@ScreensaverActivity))
                    put("isha", ScreensaverConfig.getIqamahIsha(this@ScreensaverActivity))
                }
            )
        }
        evaluateJs("setConfig($json)")
    }

    private fun pushBackgrounds() {
        val bgSource = ScreensaverConfig.getBgSource(this@ScreensaverActivity)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // يبقى في شاشة اللانشر الرئيسية ولا يخرج عند ضغط زر الرجوع في الريموت
    }

    class TvBridge(private val activity: Activity) {
        @JavascriptInterface
        fun launchApp(packageName: String): Boolean {
            return try {
                val pm = activity.packageManager
                val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
                    ?: pm.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun openSettings(): Boolean {
            return try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun getInstalledApps(): String {
            return try {
                val pm = activity.packageManager
                val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                }
                val standardIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val resolveInfos = mutableListOf<ResolveInfo>()
                resolveInfos.addAll(pm.queryIntentActivities(leanbackIntent, 0))
                resolveInfos.addAll(pm.queryIntentActivities(standardIntent, 0))

                val seen = HashSet<String>()
                val array = JSONArray()

                for (ri in resolveInfos) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == activity.packageName || seen.contains(pkg)) continue
                    seen.add(pkg)

                    val label = ri.loadLabel(pm).toString()
                    val obj = JSONObject().apply {
                        put("packageName", pkg)
                        put("name", label)
                    }
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun isTvLauncher(): Boolean = true
    }
}
