package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * مركز تخصيص وإعدادات التلفاز بنظام القائمتين (TV 2-Pane Master-Detail):
 * قائمة الأقسام الرئيسية باليمين، وخيارات وتفاصيل التخصيص الكاملة باليسار.
 */
public final class LauncherSettingsActivity extends Activity {

    private SharedPreferences prefs;

    // عناصر القائمة الجانبية (Tabs) والألواح المقابلة (Panes)
    private TextView tabPrayers, tabThemes, tabSports, tabApps, tabInputs, tabMobile, tabSystem;
    private View panePrayers, paneThemes, paneSports, paneApps, paneInputs, paneMobile, paneSystem;

    // عناصر المدن
    private final Map<String, TextView> cityViews = new HashMap<>();
    // عناصر الخلفيات
    private final Map<String, TextView> wpViews = new HashMap<>();
    // عناصر الأندية
    private final Map<Integer, TextView> teamViews = new HashMap<>();
    private final Map<Integer, TextView> team2Views = new HashMap<>();
    private final Map<Integer, TextView> matchSpeedViews = new HashMap<>();
    private final Map<String, TextView> fontViews = new HashMap<>();
    private final Map<String, TextView> fontScaleViews = new HashMap<>();
    private final Map<String, TextView> dimViews = new HashMap<>();
    private final Map<String, TextView> greetViews = new HashMap<>();
    private final Map<Integer, TextView> newsSpeedViews = new HashMap<>();
    // عناصر الأعمدة
    private final TextView[] colViews = new TextView[4];
    // عناصر سرعة شاشة التوقف
    private final Map<Integer, TextView> ssSpeedViews = new HashMap<>();

    // نصوص فترات الإقامة
    private TextView lblIqamahFajr, lblIqamahDhuhr, lblIqamahAsr, lblIqamahMaghrib, lblIqamahIsha;
    private int iqFajr, iqDhuhr, iqAsr, iqMaghrib, iqIsha;

    // عناصر التبديل
    private TextView togglePrayerWidget, togglePrayerAlert;
    private TextView toggleHadithWidget;
    private TextView animationsChoice, toggleSmartFolders;
    private TextView toggleCatStreaming, toggleCatSports, toggleCatGames, toggleCatTools;
    private final Map<String, TextView> defFolderViews = new HashMap<>();
    private boolean catStreamingEnabled = true;
    private boolean catSportsEnabled = true;
    private boolean catGamesEnabled = true;
    private boolean catToolsEnabled = true;
    private String defaultFolder = SmartCategory.CAT_ALL;
    private TextView toggleMatchdayMode, toggleSsMatches;
    private TextView toggleNewsTicker, toggleArabicNumerals;
    private TextView toggleClock24h, toggleClockSeconds;
    private TextView toggleSsRamadan, toggleSsWeather;
    private TextView bgSourceLandmarks, bgSourceFamily, bgSourceBoth;
    private TextView btnHdmi1Label, btnHdmi2Label, btnHdmi3Label;
    private TextView tvCompanionUrl;

    // القيم الحالية
    private String currentCity;
    private String currentWp;
    private int currentCols;
    private boolean currentAnimations;
    private boolean showPrayerWidget;
    private boolean showPrayerAlert;
    private boolean showHadithWidget;
    private boolean smartFolders;

    private int currentTeamId;
    private String currentTeamName;
    private int currentTeam2Id;
    private String currentTeam2Name;
    private boolean showNews;
    private int matchRotateSec;
    private boolean arabicNumerals;
    private String selectedFont;
    private String fontScale;
    private String bgDim;
    private boolean clock24h;
    private boolean clockSeconds;
    private String greetingText;
    private int newsSpeedSec;
    private boolean matchdayMode;
    private String bgSource; // "landmarks", "family", "both"
    private int ssSpeedSec;
    private boolean ssShowMatches;
    private boolean ssShowRamadan;
    private boolean ssShowWeather;

    private int hdmi1Preset = 0;
    private int hdmi2Preset = 0;
    private int hdmi3Preset = 0;
    private static final String[] HDMI1_PRESETS = {"🎮 PlayStation 5", "🎮 Xbox Series X", "💻 كمبيوتر / PC", "منفذ HDMI 1"};
    private static final String[] HDMI2_PRESETS = {"🍏 Apple TV", "📺 كروم كاست", "رسيفر ذكي", "منفذ HDMI 2"};
    private static final String[] HDMI3_PRESETS = {"📡 الرسيفر الفضائي", "⚽ beIN Sports", "رسيفر TOD", "منفذ HDMI 3"};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        FontHelper.applyFont(getWindow().getDecorView(), this);

        prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        loadPreferences();
        bindViews();
        setupNavigation();
        updateUI();

        // تشغيل سيرفر الجوال المحلي وتحديث عنوان الرابط
        CompanionServer.getInstance(this).start();
        String localIp = CompanionServer.getLocalIpAddress(this);
        if (tvCompanionUrl != null) {
            tvCompanionUrl.setText("http://" + localIp + ":" + CompanionServer.PORT);
        }

        findViewById(R.id.btnCloseSettings).requestFocus();
    }

    private void loadPreferences() {
        currentCity = PrayerManager.getCityName(this);
        currentWp = prefs.getString("wallpaper_choice", "auto");
        Config cfg = Config.load(this);
        currentCols = cfg.columns;
        currentAnimations = cfg.animations;
        showPrayerWidget = prefs.getBoolean("show_prayer_widget", true);
        showPrayerAlert = prefs.getBoolean("prayer_alert", true);
        showHadithWidget = prefs.getBoolean("show_hadith_widget", true);
        smartFolders = prefs.getBoolean("smart_folders", true);
        catStreamingEnabled = prefs.getBoolean("cat_streaming_enabled", true);
        catSportsEnabled = prefs.getBoolean("cat_sports_enabled", true);
        catGamesEnabled = prefs.getBoolean("cat_games_enabled", true);
        catToolsEnabled = prefs.getBoolean("cat_tools_enabled", true);
        defaultFolder = prefs.getString("default_folder", SmartCategory.CAT_ALL);

        currentTeamId = prefs.getInt("selected_team_id", 929);
        currentTeamName = prefs.getString("selected_team_name", "الهلال");
        currentTeam2Id = prefs.getInt("selected_team2_id", 0);
        currentTeam2Name = prefs.getString("selected_team2_name", "");
        showNews = prefs.getBoolean("ss_show_news", true);
        matchRotateSec = prefs.getInt("ss_match_rotate_sec", 10);
        arabicNumerals = prefs.getBoolean("ss_arabic_numerals", true);
        selectedFont = prefs.getString("selected_font", "thmanyah");
        fontScale = prefs.getString("font_scale", "normal");
        bgDim = prefs.getString("bg_dim", "medium");
        clock24h = prefs.getBoolean("clock_24h", false);
        clockSeconds = prefs.getBoolean("clock_seconds", false);
        greetingText = prefs.getString("greeting_text", "منزل العائلة المبارك 🌿");
        newsSpeedSec = prefs.getInt("news_speed_sec", 10);
        matchdayMode = prefs.getBoolean("matchday_mode", true);
        bgSource = prefs.getString("bg_source", "landmarks");

        ssSpeedSec = prefs.getInt("ss_bg_speed_sec", 90);
        ssShowMatches = prefs.getBoolean("ss_show_matches", true);
        ssShowRamadan = prefs.getBoolean("ss_show_ramadan", true);
        ssShowWeather = prefs.getBoolean("ss_show_weather", true);

        iqFajr = PrayerManager.getIqamahFajr(this);
        iqDhuhr = PrayerManager.getIqamahDhuhr(this);
        iqAsr = PrayerManager.getIqamahAsr(this);
        iqMaghrib = PrayerManager.getIqamahMaghrib(this);
        iqIsha = PrayerManager.getIqamahIsha(this);

        hdmi1Preset = prefs.getInt("hdmi1_preset", 0);
        hdmi2Preset = prefs.getInt("hdmi2_preset", 0);
        hdmi3Preset = prefs.getInt("hdmi3_preset", 0);
    }

    private void bindViews() {
        findViewById(R.id.btnCloseSettings).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });

        // ربط أزرار القائمة الجانبية
        tabPrayers = findViewById(R.id.tabPrayers);
        tabThemes = findViewById(R.id.tabThemes);
        tabSports = findViewById(R.id.tabSports);
        tabApps = findViewById(R.id.tabApps);
        tabInputs = findViewById(R.id.tabInputs);
        tabMobile = findViewById(R.id.tabMobile);
        tabSystem = findViewById(R.id.tabSystem);

        // ربط ألواح التفاصيل
        panePrayers = findViewById(R.id.panePrayers);
        paneThemes = findViewById(R.id.paneThemes);
        paneSports = findViewById(R.id.paneSports);
        paneApps = findViewById(R.id.paneApps);
        paneInputs = findViewById(R.id.paneInputs);
        paneMobile = findViewById(R.id.paneMobile);
        paneSystem = findViewById(R.id.paneSystem);

        // 1. قسم الصلاة
        bindCity(R.id.cityJeddah, "جدة");
        bindCity(R.id.cityRiyadh, "الرياض");
        bindCity(R.id.cityMakkah, "مكة المكرمة");
        bindCity(R.id.cityMadinah, "المدينة المنورة");
        bindCity(R.id.cityDammam, "الدمام");
        bindCity(R.id.cityBuraidah, "بريدة");
        bindCity(R.id.cityTabuk, "تبوك");
        bindCity(R.id.cityAbha, "أبها");
        bindCity(R.id.cityHail, "حائل");
        bindCity(R.id.cityJazan, "جازان");
        bindCity(R.id.cityNajran, "نجران");

        lblIqamahFajr = findViewById(R.id.lblIqamahFajr);
        lblIqamahDhuhr = findViewById(R.id.lblIqamahDhuhr);
        lblIqamahAsr = findViewById(R.id.lblIqamahAsr);
        lblIqamahMaghrib = findViewById(R.id.lblIqamahMaghrib);
        lblIqamahIsha = findViewById(R.id.lblIqamahIsha);

        setupIqamahButtons(R.id.btnFajrMinus, R.id.btnFajrPlus, "fajr");
        setupIqamahButtons(R.id.btnDhuhrMinus, R.id.btnDhuhrPlus, "dhuhr");
        setupIqamahButtons(R.id.btnAsrMinus, R.id.btnAsrPlus, "asr");
        setupIqamahButtons(R.id.btnMaghribMinus, R.id.btnMaghribPlus, "maghrib");
        setupIqamahButtons(R.id.btnIshaMinus, R.id.btnIshaPlus, "isha");

        togglePrayerWidget = findViewById(R.id.togglePrayerWidget);
        togglePrayerWidget.setOnClickListener(v -> {
            showPrayerWidget = !showPrayerWidget;
            prefs.edit().putBoolean("show_prayer_widget", showPrayerWidget).apply();
            setResult(RESULT_OK);
            broadcastConfigChange();
            updateUI();
        });

        togglePrayerAlert = findViewById(R.id.togglePrayerAlert);
        togglePrayerAlert.setOnClickListener(v -> {
            showPrayerAlert = !showPrayerAlert;
            prefs.edit().putBoolean("prayer_alert", showPrayerAlert).apply();
            broadcastConfigChange();
            updateUI();
        });

        // 2. قسم المظهر
        bindWallpaper(R.id.wpAuto, "auto");
        bindWallpaper(R.id.wpBlueSky, "blue_sky");
        bindWallpaper(R.id.wpMaraya, "maraya");
        bindWallpaper(R.id.wpElephant, "elephant");
        bindWallpaper(R.id.wpEdge, "edge");
        bindWallpaper(R.id.wpHegra, "hegra");
        bindWallpaper(R.id.wpOasis, "oasis");
        bindWallpaper(R.id.wpSunset, "sunset");
        bindWallpaper(R.id.wpStars, "stars");

        bgSourceLandmarks = findViewById(R.id.bgSourceLandmarks);
        bgSourceFamily = findViewById(R.id.bgSourceFamily);
        bgSourceBoth = findViewById(R.id.bgSourceBoth);

        bgSourceLandmarks.setOnClickListener(v -> setBgSource("landmarks"));
        bgSourceFamily.setOnClickListener(v -> setBgSource("family"));
        bgSourceBoth.setOnClickListener(v -> setBgSource("both"));

        bindSsSpeed(R.id.bgSpeed30, 30);
        bindSsSpeed(R.id.bgSpeed60, 60);
        bindSsSpeed(R.id.bgSpeed90, 90);
        bindSsSpeed(R.id.bgSpeed120, 120);

        // نوع الخط المفضل
        bindFont(R.id.fontThmanyah, "thmanyah");
        bindFont(R.id.fontDubai, "dubai");
        bindFont(R.id.fontSystem, "system");

        // حجم ومقياس الخط
        bindFontScale(R.id.fontScaleNormal, "normal");
        bindFontScale(R.id.fontScaleMedium, "medium");
        bindFontScale(R.id.fontScaleLarge, "large");

        // تعتيم وتظليل خلفية شاشة التوقف
        bindDim(R.id.dimLight, "light");
        bindDim(R.id.dimMedium, "medium");
        bindDim(R.id.dimHeavy, "heavy");

        // 3. قسم الرياضة
        bindTeam(R.id.teamHilal, 929, "الهلال");
        bindTeam(R.id.teamNassr, 931, "النصر");
        bindTeam(R.id.teamIttihad, 928, "الاتحاد");
        bindTeam(R.id.teamAhli, 927, "الأهلي");
        bindTeam(R.id.teamReal, 86, "ريال مدريد");
        bindTeam(R.id.teamBarca, 83, "برشلونة");
        bindTeam(R.id.teamCity, 382, "مانشستر سيتي");
        bindTeam(R.id.teamLiverpool, 364, "ليفربول");
        bindTeam(R.id.teamArsenal, 359, "آرسنال");
        bindTeam(R.id.teamChelsea, 363, "تشيلسي");
        bindTeam(R.id.teamShabab, 932, "الشباب");

        toggleMatchdayMode = findViewById(R.id.toggleMatchdayMode);
        toggleMatchdayMode.setOnClickListener(v -> {
            matchdayMode = !matchdayMode;
            prefs.edit().putBoolean("matchday_mode", matchdayMode).apply();
            broadcastConfigChange();
            updateUI();
        });

        toggleSsMatches = findViewById(R.id.toggleSsMatches);
        toggleSsMatches.setOnClickListener(v -> {
            ssShowMatches = !ssShowMatches;
            prefs.edit().putBoolean("ss_show_matches", ssShowMatches).apply();
            broadcastConfigChange();
            updateUI();
        });

        // النادي الثاني
        bindTeam2(R.id.team2None, 0, "");
        bindTeam2(R.id.team2Hilal, 929, "الهلال");
        bindTeam2(R.id.team2Nassr, 931, "النصر");
        bindTeam2(R.id.team2Ittihad, 928, "الاتحاد");
        bindTeam2(R.id.team2Ahli, 927, "الأهلي");
        bindTeam2(R.id.team2Real, 86, "ريال مدريد");
        bindTeam2(R.id.team2Barca, 83, "برشلونة");
        bindTeam2(R.id.team2City, 382, "مانشستر سيتي");
        bindTeam2(R.id.team2Liverpool, 364, "ليفربول");
        bindTeam2(R.id.team2Arsenal, 359, "آرسنال");
        bindTeam2(R.id.team2Chelsea, 363, "تشيلسي");
        bindTeam2(R.id.team2Shabab, 932, "الشباب");

        // سرعة تدوير مباريات الناديين
        bindMatchSpeed(R.id.matchSpeed5, 5);
        bindMatchSpeed(R.id.matchSpeed10, 10);
        bindMatchSpeed(R.id.matchSpeed15, 15);
        bindMatchSpeed(R.id.matchSpeed20, 20);

        // شريط الأخبار
        toggleNewsTicker = findViewById(R.id.toggleNewsTicker);
        toggleNewsTicker.setOnClickListener(v -> {
            showNews = !showNews;
            prefs.edit().putBoolean("ss_show_news", showNews).apply();
            broadcastConfigChange();
            updateUI();
        });

        // 4. قسم التطبيقات
        int[] colIds = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < 4; i++) {
            final int count = i + 4;
            colViews[i] = findViewById(colIds[i]);
            colViews[i].setOnClickListener(v -> {
                currentCols = count;
                prefs.edit().putInt("columns", count).apply();
                setResult(RESULT_OK);
                updateUI();
            });
        }

        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            currentAnimations = !currentAnimations;
            prefs.edit().putBoolean("animations", currentAnimations)
                    .putBoolean("advanced_effects", currentAnimations).apply();
            setResult(RESULT_OK);
            updateUI();
        });

        toggleSmartFolders = findViewById(R.id.toggleSmartFolders);
        toggleSmartFolders.setOnClickListener(v -> {
            smartFolders = !smartFolders;
            prefs.edit().putBoolean("smart_folders", smartFolders).apply();
            setResult(RESULT_OK);
            updateUI();
        });

        toggleCatStreaming = findViewById(R.id.toggleCatStreaming);
        toggleCatSports = findViewById(R.id.toggleCatSports);
        toggleCatGames = findViewById(R.id.toggleCatGames);
        toggleCatTools = findViewById(R.id.toggleCatTools);

        if (toggleCatStreaming != null) {
            toggleCatStreaming.setOnClickListener(v -> {
                catStreamingEnabled = !catStreamingEnabled;
                prefs.edit().putBoolean("cat_streaming_enabled", catStreamingEnabled).apply();
                setResult(RESULT_OK);
                updateUI();
            });
        }
        if (toggleCatSports != null) {
            toggleCatSports.setOnClickListener(v -> {
                catSportsEnabled = !catSportsEnabled;
                prefs.edit().putBoolean("cat_sports_enabled", catSportsEnabled).apply();
                setResult(RESULT_OK);
                updateUI();
            });
        }
        if (toggleCatGames != null) {
            toggleCatGames.setOnClickListener(v -> {
                catGamesEnabled = !catGamesEnabled;
                prefs.edit().putBoolean("cat_games_enabled", catGamesEnabled).apply();
                setResult(RESULT_OK);
                updateUI();
            });
        }
        if (toggleCatTools != null) {
            toggleCatTools.setOnClickListener(v -> {
                catToolsEnabled = !catToolsEnabled;
                prefs.edit().putBoolean("cat_tools_enabled", catToolsEnabled).apply();
                setResult(RESULT_OK);
                updateUI();
            });
        }

        defFolderViews.put(SmartCategory.CAT_ALL, findViewById(R.id.defCatAll));
        defFolderViews.put(SmartCategory.CAT_STREAMING, findViewById(R.id.defCatStreaming));
        defFolderViews.put(SmartCategory.CAT_SPORTS, findViewById(R.id.defCatSports));
        defFolderViews.put(SmartCategory.CAT_GAMES, findViewById(R.id.defCatGames));
        defFolderViews.put(SmartCategory.CAT_TOOLS, findViewById(R.id.defCatTools));

        for (Map.Entry<String, TextView> entry : defFolderViews.entrySet()) {
            final String fKey = entry.getKey();
            TextView tv = entry.getValue();
            if (tv != null) {
                tv.setOnClickListener(v -> {
                    defaultFolder = fKey;
                    prefs.edit().putString("default_folder", defaultFolder).apply();
                    setResult(RESULT_OK);
                    updateUI();
                });
            }
        }

        findViewById(R.id.btnReorderApps).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
            Toast.makeText(this, "اضغط مطولاً على أي تطبيق لتغيير مكانه بالريموت", Toast.LENGTH_LONG).show();
        });

        // 5. قسم المداخل الذكية (HDMI)
        btnHdmi1Label = findViewById(R.id.btnHdmi1Label);
        btnHdmi2Label = findViewById(R.id.btnHdmi2Label);
        btnHdmi3Label = findViewById(R.id.btnHdmi3Label);

        btnHdmi1Label.setOnClickListener(v -> {
            hdmi1Preset = (hdmi1Preset + 1) % HDMI1_PRESETS.length;
            prefs.edit().putInt("hdmi1_preset", hdmi1Preset)
                    .putString("custom_input_name_hdmi1", HDMI1_PRESETS[hdmi1Preset]).apply();
            updateUI();
        });

        btnHdmi2Label.setOnClickListener(v -> {
            hdmi2Preset = (hdmi2Preset + 1) % HDMI2_PRESETS.length;
            prefs.edit().putInt("hdmi2_preset", hdmi2Preset)
                    .putString("custom_input_name_hdmi2", HDMI2_PRESETS[hdmi2Preset]).apply();
            updateUI();
        });

        btnHdmi3Label.setOnClickListener(v -> {
            hdmi3Preset = (hdmi3Preset + 1) % HDMI3_PRESETS.length;
            prefs.edit().putInt("hdmi3_preset", hdmi3Preset)
                    .putString("custom_input_name_hdmi3", HDMI3_PRESETS[hdmi3Preset]).apply();
            updateUI();
        });

        // 6. قسم الجوال
        tvCompanionUrl = findViewById(R.id.tvCompanionUrl);

        // 7. قسم النظام
        findViewById(R.id.btnTestScreensaver).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, com.chelseasaver.dream.ScreensaverActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "تعذر تشغيل شاشة التوقف", Toast.LENGTH_SHORT).show();
            }
        });

        View btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> {
                Toast.makeText(this, "جاري التحقق من التحديثات عبر GitHub...", Toast.LENGTH_SHORT).show();
                AppUpdateManager.checkForUpdate(this, new AppUpdateManager.UpdateCheckCallback() {
                    @Override
                    public void onUpdateAvailable(String newVersion, String changelog, String downloadUrl) {
                        AppUpdateManager.showUpdateDialog(LauncherSettingsActivity.this, newVersion, changelog, downloadUrl);
                    }

                    @Override
                    public void onUpToDate() {
                        Toast.makeText(LauncherSettingsActivity.this, "✓ أنت تستخدم أحدث إصدار من الواجهة (" + BuildConfig.VERSION_NAME + ")", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(LauncherSettingsActivity.this, "تعذر الاتصال بـ GitHub: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }

        toggleHadithWidget = findViewById(R.id.toggleHadithWidget);
        toggleHadithWidget.setOnClickListener(v -> {
            showHadithWidget = !showHadithWidget;
            prefs.edit().putBoolean("show_hadith_widget", showHadithWidget).apply();
            setResult(RESULT_OK);
            broadcastConfigChange();
            updateUI();
        });

        toggleSsRamadan = findViewById(R.id.toggleSsRamadan);
        toggleSsRamadan.setOnClickListener(v -> {
            ssShowRamadan = !ssShowRamadan;
            prefs.edit().putBoolean("ss_show_ramadan", ssShowRamadan).apply();
            broadcastConfigChange();
            updateUI();
        });

        toggleSsWeather = findViewById(R.id.toggleSsWeather);
        toggleSsWeather.setOnClickListener(v -> {
            ssShowWeather = !ssShowWeather;
            prefs.edit().putBoolean("ss_show_weather", ssShowWeather).apply();
            broadcastConfigChange();
            updateUI();
        });

        toggleArabicNumerals = findViewById(R.id.toggleArabicNumerals);
        toggleArabicNumerals.setOnClickListener(v -> {
            arabicNumerals = !arabicNumerals;
            prefs.edit().putBoolean("ss_arabic_numerals", arabicNumerals).apply();
            broadcastConfigChange();
            updateUI();
        });

        // خيارات الساعة والثواني
        toggleClock24h = findViewById(R.id.toggleClock24h);
        toggleClock24h.setOnClickListener(v -> {
            clock24h = !clock24h;
            prefs.edit().putBoolean("clock_24h", clock24h).apply();
            broadcastConfigChange();
            updateUI();
        });

        toggleClockSeconds = findViewById(R.id.toggleClockSeconds);
        toggleClockSeconds.setOnClickListener(v -> {
            clockSeconds = !clockSeconds;
            prefs.edit().putBoolean("clock_seconds", clockSeconds).apply();
            broadcastConfigChange();
            updateUI();
        });

        // نص الترحيب العائلي
        bindGreeting(R.id.greetFamily, "منزل العائلة المبارك 🌿");
        bindGreeting(R.id.greetWelcome, "مرحباً بكم 🏠");
        bindGreeting(R.id.greetHappy, "بيت السعادة 🤍");
        bindGreeting(R.id.greetNone, "");

        // سرعة تبديل أخبار النادي
        bindNewsSpeed(R.id.newsSpeed6, 6);
        bindNewsSpeed(R.id.newsSpeed10, 10);
        bindNewsSpeed(R.id.newsSpeed15, 15);

        findViewById(R.id.btnTvSettings).setOnClickListener(v -> safeStartActivity(new Intent(Settings.ACTION_SETTINGS)));
        findViewById(R.id.btnWifiSettings).setOnClickListener(v -> safeStartActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        findViewById(R.id.btnHomeSettings).setOnClickListener(v -> safeStartActivity(new Intent(Settings.ACTION_HOME_SETTINGS)));
    }

    private void setupNavigation() {
        setupTab(tabPrayers, panePrayers);
        setupTab(tabThemes, paneThemes);
        setupTab(tabSports, paneSports);
        setupTab(tabApps, paneApps);
        setupTab(tabInputs, paneInputs);
        setupTab(tabMobile, paneMobile);
        setupTab(tabSystem, paneSystem);

        // تحديد التبويب الأول مبدئياً
        activateTab(tabPrayers, panePrayers);
    }

    private void setupTab(TextView tab, View pane) {
        tab.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) activateTab(tab, pane);
        });
        tab.setOnClickListener(v -> activateTab(tab, pane));
    }

    private void activateTab(TextView activeTab, View activePane) {
        TextView[] allTabs = {tabPrayers, tabThemes, tabSports, tabApps, tabInputs, tabMobile, tabSystem};
        View[] allPanes = {panePrayers, paneThemes, paneSports, paneApps, paneInputs, paneMobile, paneSystem};

        for (int i = 0; i < allTabs.length; i++) {
            boolean isCur = (allTabs[i] == activeTab);
            allTabs[i].setSelected(isCur);
            allPanes[i].setVisibility(isCur ? View.VISIBLE : View.GONE);
        }
    }

    private void setupIqamahButtons(int btnMinusId, int btnPlusId, String prayerKey) {
        findViewById(btnMinusId).setOnClickListener(v -> adjustIqamah(prayerKey, -1));
        findViewById(btnPlusId).setOnClickListener(v -> adjustIqamah(prayerKey, +1));
    }

    private void adjustIqamah(String prayerKey, int delta) {
        switch (prayerKey) {
            case "fajr":
                iqFajr = Math.max(5, Math.min(60, iqFajr + delta));
                PrayerManager.setIqamahDelay(this, "fajr", iqFajr);
                break;
            case "dhuhr":
                iqDhuhr = Math.max(5, Math.min(60, iqDhuhr + delta));
                PrayerManager.setIqamahDelay(this, "dhuhr", iqDhuhr);
                break;
            case "asr":
                iqAsr = Math.max(5, Math.min(60, iqAsr + delta));
                PrayerManager.setIqamahDelay(this, "asr", iqAsr);
                break;
            case "maghrib":
                iqMaghrib = Math.max(5, Math.min(60, iqMaghrib + delta));
                PrayerManager.setIqamahDelay(this, "maghrib", iqMaghrib);
                break;
            case "isha":
                iqIsha = Math.max(5, Math.min(60, iqIsha + delta));
                PrayerManager.setIqamahDelay(this, "isha", iqIsha);
                break;
        }
        setResult(RESULT_OK);
        broadcastConfigChange();
        updateUI();
    }

    private void bindCity(int viewId, String cityName) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        cityViews.put(cityName, tv);
        tv.setOnClickListener(v -> {
            for (PrayerManager.City c : PrayerManager.CITIES) {
                if (c.name.equals(cityName)) {
                    PrayerManager.setSelectedCity(this, c);
                    currentCity = cityName;
                    broadcastConfigChange();
                    setResult(RESULT_OK);
                    updateUI();
                    Toast.makeText(this, "تم تغيير المدينة إلى: " + cityName, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        });
    }

    private void bindWallpaper(int viewId, String key) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        wpViews.put(key, tv);
        tv.setOnClickListener(v -> {
            currentWp = key;
            prefs.edit().putString("wallpaper_choice", key).apply();
            setResult(RESULT_OK);
            updateUI();
            Toast.makeText(this, "تم تغيير خلفية الشاشة", Toast.LENGTH_SHORT).show();
        });
    }

    private void bindFont(int viewId, String key) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        fontViews.put(key, tv);
        tv.setOnClickListener(v -> {
            selectedFont = key;
            prefs.edit().putString("selected_font", key).apply();
            FontHelper.applyFont(getWindow().getDecorView(), this);
            setResult(RESULT_OK);
            broadcastConfigChange();
            updateUI();
            String name = key.equals("thmanyah") ? "خط ثمانية الفاخر" : (key.equals("dubai") ? "خط دبي التلفزيوني" : "خط النظام القياسي");
            Toast.makeText(this, "تم اختيار: " + name, Toast.LENGTH_SHORT).show();
        });
    }

    private void bindFontScale(int viewId, String scale) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        fontScaleViews.put(scale, tv);
        tv.setOnClickListener(v -> {
            fontScale = scale;
            prefs.edit().putString("font_scale", scale).apply();
            broadcastConfigChange();
            updateUI();
        });
    }

    private void bindDim(int viewId, String dim) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        dimViews.put(dim, tv);
        tv.setOnClickListener(v -> {
            bgDim = dim;
            prefs.edit().putString("bg_dim", dim).apply();
            broadcastConfigChange();
            updateUI();
        });
    }

    private void bindGreeting(int viewId, String text) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        greetViews.put(text, tv);
        tv.setOnClickListener(v -> {
            greetingText = text;
            prefs.edit().putString("greeting_text", text).apply();
            broadcastConfigChange();
            updateUI();
            if (text.isEmpty()) {
                Toast.makeText(this, "تم إخفاء نص الترحيب", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "الترحيب: " + text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindNewsSpeed(int viewId, int seconds) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        newsSpeedViews.put(seconds, tv);
        tv.setOnClickListener(v -> {
            newsSpeedSec = seconds;
            prefs.edit().putInt("news_speed_sec", seconds).apply();
            broadcastConfigChange();
            updateUI();
        });
    }

    private void bindTeam2(int viewId, int teamId, String teamName) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        team2Views.put(teamId, tv);
        tv.setOnClickListener(v -> {
            currentTeam2Id = teamId;
            currentTeam2Name = teamName;
            prefs.edit().putInt("selected_team2_id", teamId)
                    .putString("selected_team2_name", teamName).apply();
            broadcastConfigChange();
            updateUI();
            if (teamId == 0) {
                Toast.makeText(this, "تم إلغاء النادي الثاني (نادٍ واحد فقط)", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "تم اختيار النادي الثاني: " + teamName, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindMatchSpeed(int viewId, int seconds) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        matchSpeedViews.put(seconds, tv);
        tv.setOnClickListener(v -> {
            matchRotateSec = seconds;
            prefs.edit().putInt("ss_match_rotate_sec", seconds).apply();
            broadcastConfigChange();
            updateUI();
        });
    }

    private void bindTeam(int viewId, int teamId, String teamName) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        teamViews.put(teamId, tv);
        tv.setOnClickListener(v -> {
            currentTeamId = teamId;
            currentTeamName = teamName;
            prefs.edit().putInt("selected_team_id", teamId)
                    .putString("selected_team_name", teamName).apply();
            broadcastConfigChange();
            updateUI();
            Toast.makeText(this, "تم اختيار النادي: " + teamName, Toast.LENGTH_SHORT).show();
        });
    }

    private void setBgSource(String source) {
        bgSource = source;
        prefs.edit().putString("bg_source", source).apply();
        broadcastConfigChange();
        updateUI();
        String label = source.equals("family") ? "ألبوم العائلة الخاص" :
                (source.equals("both") ? "المزج بين المعالم والعائلة" : "معالم السعودية 4K");
        Toast.makeText(this, "مصدر الصور: " + label, Toast.LENGTH_SHORT).show();
    }

    private void bindSsSpeed(int viewId, int seconds) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        ssSpeedViews.put(seconds, tv);
        tv.setOnClickListener(v -> {
            ssSpeedSec = seconds;
            prefs.edit().putInt("ss_bg_speed_sec", seconds).apply();
            broadcastConfigChange();
            updateUI();
        });
    }

    private void updateUI() {
        // تحديث المدن
        for (Map.Entry<String, TextView> entry : cityViews.entrySet()) {
            boolean sel = entry.getKey().equals(currentCity);
            entry.getValue().setSelected(sel);
            entry.getValue().setText(sel ? "✓ " + entry.getKey() : entry.getKey());
        }

        // تحديث أوقات الإقامة
        lblIqamahFajr.setText(iqFajr + " دقيقة");
        lblIqamahDhuhr.setText(iqDhuhr + " دقيقة");
        lblIqamahAsr.setText(iqAsr + " دقيقة");
        lblIqamahMaghrib.setText(iqMaghrib + " دقيقة");
        lblIqamahIsha.setText(iqIsha + " دقيقة");

        // ودجات الصلاة
        togglePrayerWidget.setSelected(showPrayerWidget);
        togglePrayerWidget.setText(showPrayerWidget ? "✓ مواقيت الصلاة في الواجهة: مفعّلة" : "✕ مواقيت الصلاة: معطّلة");

        togglePrayerAlert.setSelected(showPrayerAlert);
        togglePrayerAlert.setText(showPrayerAlert ? "✓ تنبيهات الأذان والإقامة التفاعلية: مفعّلة" : "✕ تنبيهات الأذان: معطّلة");

        // تحديث الخلفيات
        for (Map.Entry<String, TextView> entry : wpViews.entrySet()) {
            boolean sel = entry.getKey().equals(currentWp);
            entry.getValue().setSelected(sel);
            String raw = getWallpaperLabel(entry.getKey());
            entry.getValue().setText(sel ? "✓ " + raw : raw);
        }

        // مصدر خلفيات شاشة التوقف
        bgSourceLandmarks.setSelected("landmarks".equals(bgSource));
        bgSourceFamily.setSelected("family".equals(bgSource));
        bgSourceBoth.setSelected("both".equals(bgSource));

        // سرعة شاشة التوقف
        for (Map.Entry<Integer, TextView> entry : ssSpeedViews.entrySet()) {
            boolean sel = (entry.getKey() == ssSpeedSec);
            entry.getValue().setSelected(sel);
            String label = entry.getKey() == 120 ? "دقيقتين" : entry.getKey() + " ثانية";
            entry.getValue().setText(sel ? "✓ " + label : label);
        }

        // الأندية
        for (Map.Entry<Integer, TextView> entry : teamViews.entrySet()) {
            boolean sel = (entry.getKey() == currentTeamId);
            entry.getValue().setSelected(sel);
        }

        toggleMatchdayMode.setSelected(matchdayMode);
        toggleMatchdayMode.setText(matchdayMode ? "✓ وضع يوم المباراة الحي: مفعّل" : "✕ وضع يوم المباراة: معطّل");

        String matchLabel = currentTeam2Id > 0 ? (currentTeamName + " و" + currentTeam2Name) : currentTeamName;
        toggleSsMatches.setSelected(ssShowMatches);
        toggleSsMatches.setText(ssShowMatches ? "✓ عرض مباريات " + matchLabel + ": مفعّلة" : "✕ مباريات الفريق: معطّلة");

        // النادي الثاني
        for (Map.Entry<Integer, TextView> entry : team2Views.entrySet()) {
            boolean sel = (entry.getKey() == currentTeam2Id);
            entry.getValue().setSelected(sel);
        }

        // سرعة تدوير المباريات
        for (Map.Entry<Integer, TextView> entry : matchSpeedViews.entrySet()) {
            boolean sel = (entry.getKey() == matchRotateSec);
            entry.getValue().setSelected(sel);
            entry.getValue().setText((sel ? "✓ " : "") + entry.getKey() + " ثوانٍ");
        }

        // شريط الأخبار
        toggleNewsTicker.setSelected(showNews);
        toggleNewsTicker.setText(showNews ? "✓ شريط أخبار وتصريحات النادي: مفعّل" : "✕ شريط الأخبار: معطّل");

        // الأرقام العربية
        toggleArabicNumerals.setSelected(arabicNumerals);
        toggleArabicNumerals.setText(arabicNumerals ? "✓ أرقام الوقت والتواريخ: عربية أصيلة (٠-٩)" : "✕ أرقام الوقت والتواريخ: إنجليزية (0-9)");

        // نوع الخط المفضل
        for (Map.Entry<String, TextView> entry : fontViews.entrySet()) {
            boolean sel = entry.getKey().equals(selectedFont);
            entry.getValue().setSelected(sel);
        }

        // حجم الخط
        for (Map.Entry<String, TextView> entry : fontScaleViews.entrySet()) {
            boolean sel = entry.getKey().equals(fontScale);
            entry.getValue().setSelected(sel);
        }

        // تعتيم الخلفية
        for (Map.Entry<String, TextView> entry : dimViews.entrySet()) {
            boolean sel = entry.getKey().equals(bgDim);
            entry.getValue().setSelected(sel);
        }

        // خيارات الساعة
        toggleClock24h.setSelected(clock24h);
        toggleClock24h.setText(clock24h ? "✓ نظام الساعة: 24 ساعة" : "✓ نظام الساعة: 12 ساعة (ص/م)");

        toggleClockSeconds.setSelected(clockSeconds);
        toggleClockSeconds.setText(clockSeconds ? "✓ عرض الثواني الرقمية: مفعّل" : "✕ عرض الثواني الرقمية: معطّل");

        // نص الترحيب
        for (Map.Entry<String, TextView> entry : greetViews.entrySet()) {
            boolean sel = entry.getKey().equals(greetingText);
            entry.getValue().setSelected(sel);
        }

        // سرعة أخبار النادي
        for (Map.Entry<Integer, TextView> entry : newsSpeedViews.entrySet()) {
            boolean sel = (entry.getKey() == newsSpeedSec);
            entry.getValue().setSelected(sel);
            entry.getValue().setText((sel ? "✓ " : "") + entry.getKey() + " ثوانٍ");
        }

        // الأعمدة
        for (int i = 0; i < 4; i++) {
            boolean sel = (currentCols == i + 4);
            colViews[i].setSelected(sel);
            colViews[i].setText((sel ? "✓ " : "") + (i + 4) + " تطبيقات");
        }

        animationsChoice.setSelected(currentAnimations);
        animationsChoice.setText(currentAnimations ? "✓ تأثيرات الحركة: مفعلة" : "✕ تأثيرات الحركة: معطلة");

        toggleSmartFolders.setSelected(smartFolders);
        View layoutFolderControls = findViewById(R.id.layoutFolderControls);
        if (layoutFolderControls != null) {
            layoutFolderControls.setVisibility(smartFolders ? View.VISIBLE : View.GONE);
        }
        if (toggleCatStreaming != null) {
            toggleCatStreaming.setSelected(catStreamingEnabled);
            toggleCatStreaming.setText(catStreamingEnabled ? "🎬 مجلد سينما وبث: مفعّل ومتاح في الشريط" : "❌ مجلد سينما وبث: مخفي");
        }
        if (toggleCatSports != null) {
            toggleCatSports.setSelected(catSportsEnabled);
            toggleCatSports.setText(catSportsEnabled ? "⚽ مجلد رياضة ومباريات: مفعّل ومتاح في الشريط" : "❌ مجلد رياضة ومباريات: مخفي");
        }
        if (toggleCatGames != null) {
            toggleCatGames.setSelected(catGamesEnabled);
            toggleCatGames.setText(catGamesEnabled ? "🎮 مجلد ألعاب وترفيه: مفعّل ومتاح في الشريط" : "❌ مجلد ألعاب وترفيه: مخفي");
        }
        if (toggleCatTools != null) {
            toggleCatTools.setSelected(catToolsEnabled);
            toggleCatTools.setText(catToolsEnabled ? "🛠️ مجلد أدوات ونظام: مفعّل ومتاح في الشريط" : "❌ مجلد أدوات ونظام: مخفي");
        }
        for (Map.Entry<String, TextView> entry : defFolderViews.entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().setSelected(entry.getKey().equals(defaultFolder));
            }
        }
        toggleSmartFolders.setText(smartFolders ? "✓ المجلدات والتصنيفات الذكية: مفعّلة" : "✕ المجلدات الذكية: معطّلة");

        // مسميات HDMI
        btnHdmi1Label.setText(HDMI1_PRESETS[hdmi1Preset]);
        btnHdmi2Label.setText(HDMI2_PRESETS[hdmi2Preset]);
        btnHdmi3Label.setText(HDMI3_PRESETS[hdmi3Preset]);

        // النظام والودجات
        toggleHadithWidget.setSelected(showHadithWidget);
        toggleHadithWidget.setText(showHadithWidget ? "✓ حديث اليوم الشريف في الواجهة: مفعّل" : "✕ حديث اليوم: معطّل");

        toggleSsRamadan.setSelected(ssShowRamadan);
        toggleSsRamadan.setText(ssShowRamadan ? "✓ عداد رمضان المبارك: مفعّل" : "✕ عداد رمضان: معطّل");

        toggleSsWeather.setSelected(ssShowWeather);
        toggleSsWeather.setText(ssShowWeather ? "✓ الطقس المباشر: مفعّل" : "✕ الطقس المباشر: معطّل");
    }

    private String getWallpaperLabel(String key) {
        switch (key) {
            case "auto": return "🔄 تبديل دوري تلقائي";
            case "blue_sky": return "سماء زرقاء";
            case "maraya": return "قاعة مرايا";
            case "elephant": return "صخرة الفيل";
            case "edge": return "حافة العالم";
            case "hegra": return "قصر الفريد";
            case "oasis": return "الواحة الخضراء";
            case "sunset": return "الغروب الناري";
            case "stars": return "ليل ونجوم";
            default: return "خلفية " + key;
        }
    }

    private void broadcastConfigChange() {
        try {
            Intent intent = new Intent("com.chelseasaver.dream.ACTION_UPDATE_CONFIG");
            intent.putExtra("city_name", currentCity);
            intent.putExtra("city_lat", PrayerManager.getCityLat(this));
            intent.putExtra("city_lon", PrayerManager.getCityLon(this));
            intent.putExtra("bg_interval_ms", (long) ssSpeedSec * 1000L);
            intent.putExtra("show_matches", ssShowMatches);
            intent.putExtra("show_ramadan", ssShowRamadan);
            intent.putExtra("show_weather", ssShowWeather);
            intent.putExtra("show_hadith", showHadithWidget);
            intent.putExtra("show_prayers", showPrayerWidget);

            intent.putExtra("team_id", currentTeamId);
            intent.putExtra("team_name", currentTeamName);
            intent.putExtra("team2_id", currentTeam2Id);
            intent.putExtra("team2_name", currentTeam2Name);
            intent.putExtra("show_news", showNews);
            intent.putExtra("match_rotate_sec", matchRotateSec);
            intent.putExtra("arabic_numerals", arabicNumerals);
            intent.putExtra("font_family", selectedFont);
            intent.putExtra("font_scale", fontScale);
            intent.putExtra("bg_dim", bgDim);
            intent.putExtra("clock_format", clock24h ? "24h" : "12h");
            intent.putExtra("clock_seconds", clockSeconds);
            intent.putExtra("greeting_text", greetingText);
            intent.putExtra("news_speed_sec", newsSpeedSec);
            intent.putExtra("bg_source", bgSource);
            intent.putExtra("matchday_mode", matchdayMode);
            intent.putExtra("prayer_alert", showPrayerAlert);

            intent.putExtra("iqamah_fajr", iqFajr);
            intent.putExtra("iqamah_dhuhr", iqDhuhr);
            intent.putExtra("iqamah_asr", iqAsr);
            intent.putExtra("iqamah_maghrib", iqMaghrib);
            intent.putExtra("iqamah_isha", iqIsha);

            intent.setPackage(getPackageName());
            sendBroadcast(intent);
            try {
                sendBroadcast(new Intent(intent).setPackage("com.chelseasaver.dream"));
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void safeStartActivity(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "غير مدعوم على هذا الجهاز", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(RESULT_OK);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
