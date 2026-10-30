package com.gathin.tvhome;

import java.util.Locale;

/**
 * تصنيف ذكي للتطبيقات المثبتة في اللانشر (Smart Folders):
 * يصنّف التطبيقات تلقائياً بحسب الحزمة والاسم (سينما، رياضة، ألعاب، أدوات).
 */
public final class SmartCategory {

    public static final String CAT_ALL = "all";
    public static final String CAT_STREAMING = "streaming";
    public static final String CAT_SPORTS = "sports";
    public static final String CAT_GAMES = "games";
    public static final String CAT_TOOLS = "tools";

    public static String categorize(String pkg, String label) {
        String p = (pkg == null ? "" : pkg).toLowerCase(Locale.ROOT);
        String l = (label == null ? "" : label).toLowerCase(Locale.ROOT);

        // 1. رياضة وبث مباشر
        if (p.contains("sports") || p.contains("bein") || p.contains("tod") || p.contains("ssc")
                || p.contains("espn") || p.contains("iptv") || p.contains("yalla") || p.contains("alkass")
                || l.contains("رياضة") || l.contains("كورة") || l.contains("sport") || l.contains("ssc")
                || l.contains("tod") || l.contains("bein") || l.contains("iptv")) {
            return CAT_SPORTS;
        }

        // 2. سينما وبث وأفلام
        if (p.contains("youtube") || p.contains("shahid") || p.contains("netflix") || p.contains("primevideo")
                || p.contains("amazon.avod") || p.contains("disney") || p.contains("osn") || p.contains("apple.atv")
                || p.contains("stremio") || p.contains("kodi") || p.contains("plex") || p.contains("vlc")
                || p.contains("mxplayer") || p.contains("starzplay") || p.contains("wavo") || p.contains("spacetoon")
                || l.contains("شاهد") || l.contains("يوتيوب") || l.contains("سينما") || l.contains("أفلام")
                || l.contains("netflix") || l.contains("youtube") || l.contains("shahid") || l.contains("movie")) {
            return CAT_STREAMING;
        }

        // 3. ألعاب وترفيه
        if (p.contains("game") || p.contains("steam") || p.contains("retroarch") || p.contains("emulator")
                || p.contains("playstation") || p.contains("xbox") || p.contains("geforce")
                || l.contains("لعبة") || l.contains("ألعاب") || l.contains("game")) {
            return CAT_GAMES;
        }

        // 4. أدوات ونظام
        if (p.contains("settings") || p.contains("file") || p.contains("browser") || p.contains("explorer")
                || p.contains("clean") || p.contains("download") || p.contains("speedtest") || p.contains("store")
                || p.contains("market") || p.contains("aptoide")
                || l.contains("إعدادات") || l.contains("ملفات") || l.contains("متصفح") || l.contains("تحميل")
                || l.contains("تنظيف") || l.contains("متجر")) {
            return CAT_TOOLS;
        }

        return CAT_ALL;
    }

    public static boolean matchesCategory(String category, String pkg, String label) {
        if (CAT_ALL.equals(category)) return true;
        return category.equals(categorize(pkg, label));
    }
}

