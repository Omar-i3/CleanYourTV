package com.gathin.tvhome;

import java.util.Calendar;

/**
 * مدير العبارات الترحيبية الإسلامية الأنيقة والموجزة (مطابقة لفيديو zm7md1).
 */
public final class IslamicGreetingManager {

    private static final String[] MORNING_GREETINGS = {
        "صباح الخير والبركة ☀️",
        "السلام عليكم ورحمة الله",
        "أصبحنا وأصبح الملك لله",
        "يوم مبارك بذكر الله 🌿",
        "أذكار الصباح وحفظ الرحمن"
    };

    private static final String[] AFTERNOON_GREETINGS = {
        "مساء الخير والسرور ⛅",
        "السلام عليكم ورحمة الله",
        "صلِّ على النبي ﷺ",
        "سبحان الله وبحمده 🌸",
        "أهلاً وسهلاً بك"
    };

    private static final String[] EVENING_GREETINGS = {
        "مساء الخير والرضا 🌙",
        "السلام عليكم ورحمة الله",
        "أمسينا وأمسى الملك لله",
        "طابت ليلتكم بذكر الله ✨",
        "أذكار المساء وحفظ الرحمن"
    };

    private static final String[] NIGHT_GREETINGS = {
        "ليلة هادئة بذكر الله 🕋",
        "السلام عليكم ورحمة الله",
        "لا تنسَ صلاة الوتر 🌙",
        "سبحان الله وبحمده ✨",
        "طابت ليلتك بحفظ الله"
    };

    public static String getGreeting() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int cycle = (minute / 5);

        String[] pool;
        if (hour >= 4 && hour < 12) {
            pool = MORNING_GREETINGS;
        } else if (hour >= 12 && hour < 17) {
            pool = AFTERNOON_GREETINGS;
        } else if (hour >= 17 && hour < 22) {
            pool = EVENING_GREETINGS;
        } else {
            pool = NIGHT_GREETINGS;
        }

        return pool[cycle % pool.length];
    }
}
