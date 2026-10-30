package com.chelseasaver.dream.data

import com.gathin.tvhome.BuildConfig

/**
 * كل الثوابت والإعدادات القابلة للتعديل في مكان واحد.
 *
 * ⭐ لتغيير الفريق أو المدينة عدّل قسم «إعداداتك» أدناه فقط — لا تحتاج لمس أي ملف آخر:
 *    الطقس وأوقات الصلاة والتوقيت وأسماء الفريق والمدينة على الشاشة كلها تتبع هذه القيم.
 *    (التفاصيل في README القسم 6)
 */
object ApiConfig {

    // تُقرأ من local.properties عبر BuildConfig — راجع README لشرح الإعداد
    val OPENWEATHER_API_KEY: String = BuildConfig.OPENWEATHER_API_KEY
    val FOOTBALL_DATA_API_KEY: String = BuildConfig.FOOTBALL_DATA_API_KEY

    // ═══════════════════════ إعداداتك ═══════════════════════

    // ── المدينة: الطقس + أوقات الصلاة + المنطقة الزمنية ──

    // الاسم الذي يظهر على الشاشة فوق الطقس («طقس جدة»)
    const val CITY_NAME: String = "جدة"

    // إحداثيات مدينة جدة الرسمية لأوقات الصلاة والطقس
    const val CITY_LAT: Double = 21.5433
    const val CITY_LON: Double = 39.1728

    // المنطقة الزمنية الرسمية للمملكة العربية السعودية
    const val TIMEZONE: String = "Asia/Riyadh"

    // طريقة حساب الصلاة في AlAdhan: 4 = جامعة أم القرى بمكة المكرمة (المعتمدة رسميًا في السعودية)
    const val PRAYER_METHOD: Int = 4

    // ── فترات الإقامة بالدقائق بعد الأذان (المعتمدة رسميًا في مساجد السعودية) ──
    const val IQAMAH_FAJR_MIN: Int = 25     // الفجر: 25 دقيقة بعد الأذان
    const val IQAMAH_DHUHR_MIN: Int = 20    // الظهر: 20 دقيقة بعد الأذان
    const val IQAMAH_ASR_MIN: Int = 20      // العصر: 20 دقيقة بعد الأذان
    const val IQAMAH_MAGHRIB_MIN: Int = 10  // المغرب: 10 دقائق بعد الأذان
    const val IQAMAH_ISHA_MIN: Int = 20     // العشاء: 20 دقيقة بعد الأذان

    // ── الفريق: مباريات نادي الهلال السعودي ──

    // رقم نادي الهلال في واجهة ESPN الرياضية الموثوقة: 929
    const val TEAM_ID: Int = 929

    // اسم الفريق كما تريده على الشاشة («الهلال»)
    const val TEAM_NAME: String = "الهلال"

    // ═══════════════════════ إعدادات متقدمة ═══════════════════════

    // مجلد صور الخلفية داخل assets — ضع صورك (jpg / png / webp) في app/src/main/assets/backgrounds/
    const val BACKGROUNDS_ASSET_DIR: String = "backgrounds"

    // كل كم ميلي ثانية تُحدَّث بيانات الطقس/المباريات/عداد رمضان (دقيقة واحدة كما طُلب)
    const val REFRESH_INTERVAL_MS: Long = 60_000L
}
