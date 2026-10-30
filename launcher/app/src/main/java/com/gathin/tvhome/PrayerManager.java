package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * حساب ومزامنة مواقيت الصلاة والإقامة الرسمية لمدينة جدة (تقويم أم القرى).
 */
public final class PrayerManager {

    public static final String CITY_NAME = "جدة";
    public static final double CITY_LAT = 21.5433;
    public static final double CITY_LON = 39.1728;
    public static final String TIMEZONE_ID = "Asia/Riyadh";

    // فترات الإقامة المعتمدة رسمياً في السعودية (بالدقائق)
    public static final int IQAMAH_FAJR = 25;
    public static final int IQAMAH_DHUHR = 20;
    public static final int IQAMAH_ASR = 20;
    public static final int IQAMAH_MAGHRIB = 10;
    public static final int IQAMAH_ISHA = 20;

    public static int getIqamahFajr(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt("iqamah_fajr", IQAMAH_FAJR);
    }

    public static int getIqamahDhuhr(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt("iqamah_dhuhr", IQAMAH_DHUHR);
    }

    public static int getIqamahAsr(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt("iqamah_asr", IQAMAH_ASR);
    }

    public static int getIqamahMaghrib(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt("iqamah_maghrib", IQAMAH_MAGHRIB);
    }

    public static int getIqamahIsha(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt("iqamah_isha", IQAMAH_ISHA);
    }

    public static void setIqamahDelay(Context context, String prayerKey, int minutes) {
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().putInt("iqamah_" + prayerKey, minutes).apply();
    }

    public static class City {
        public final String name;
        public final double lat;
        public final double lon;

        public City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final City[] CITIES = {
        new City("جدة", 21.5433, 39.1728),
        new City("الرياض", 24.7136, 46.6753),
        new City("مكة المكرمة", 21.4225, 39.8262),
        new City("المدينة المنورة", 24.4672, 39.6111),
        new City("الدمام", 26.4207, 50.0888),
        new City("بريدة", 26.3592, 43.9818),
        new City("تبوك", 28.3835, 36.5662),
        new City("أبها", 18.2164, 42.5053),
        new City("حائل", 27.5219, 41.6907),
        new City("جازان", 16.8892, 42.5511),
        new City("نجران", 17.4924, 44.1277)
    };

    public static String getCityName(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return sp.getString("selected_city_name", "جدة");
    }

    public static double getCityLat(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(sp.getLong("selected_city_lat", Double.doubleToRawLongBits(21.5433)));
    }

    public static double getCityLon(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(sp.getLong("selected_city_lon", Double.doubleToRawLongBits(39.1728)));
    }

    public static void setSelectedCity(Context context, City city) {
        SharedPreferences sp = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        sp.edit()
            .putString("selected_city_name", city.name)
            .putLong("selected_city_lat", Double.doubleToRawLongBits(city.lat))
            .putLong("selected_city_lon", Double.doubleToRawLongBits(city.lon))
            .apply();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static class PrayerInfo {
        public String fajrAzan = "٥:٠٦ ص";
        public String fajrIqamah = "٥:٣١ ص";
        public String dhuhrAzan = "١٢:٠٧ م";
        public String dhuhrIqamah = "١٢:٢٧ م";
        public String asrAzan = "٣:٢٦ م";
        public String asrIqamah = "٣:٤٦ م";
        public String maghribAzan = "٥:٥١ م";
        public String maghribIqamah = "٦:٠١ م";
        public String ishaAzan = "٧:٢١ م";
        public String ishaIqamah = "٧:٤١ م";

        public String activeKey = "fajr";
        public String nextStatus = "";
    }

    private static final String PREFS_NAME = "prayer_cache_v3";
    private static final TimeZone SA_TZ = TimeZone.getTimeZone(TIMEZONE_ID);
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");

    // جدول أساسي مسبق لمواقيت الصلاة لجدة لضمان الدقة الفورية
    private static final int[][] BASE_PRAYERS = {
        {5, 6},   // الفجر
        {12, 7},  // الظهر
        {15, 26}, // العصر
        {17, 51}, // المغرب
        {19, 21}  // العشاء
    };

    public static PrayerInfo getTodayPrayers(Context context) {
        PrayerInfo info = new PrayerInfo();
        Calendar now = Calendar.getInstance(SA_TZ);
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;
        int day = now.get(Calendar.DAY_OF_MONTH);

        String cityName = getCityName(context);
        String dateKey = cityName + "_" + String.format(Locale.US, "%04d-%02d-%02d", year, month, day);

        int[] fajrM = null, dhuhrM = null, asrM = null, maghribM = null, ishaM = null;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cachedJson = prefs.getString(dateKey, null);

        if (cachedJson != null) {
            try {
                JSONObject obj = new JSONObject(cachedJson);
                fajrM = parseTime(obj.optString("fajr"));
                dhuhrM = parseTime(obj.optString("dhuhr"));
                asrM = parseTime(obj.optString("asr"));
                maghribM = parseTime(obj.optString("maghrib"));
                ishaM = parseTime(obj.optString("isha"));
            } catch (Exception ignored) {}
        }

        if (fajrM == null || dhuhrM == null || asrM == null || maghribM == null || ishaM == null) {
            fajrM = BASE_PRAYERS[0];
            dhuhrM = BASE_PRAYERS[1];
            asrM = BASE_PRAYERS[2];
            maghribM = BASE_PRAYERS[3];
            ishaM = BASE_PRAYERS[4];

            syncWithAlAdhanAsync(context, year, month);
        }

        info.fajrAzan = format12H(fajrM[0], fajrM[1]);
        int[] fIq = addMinutes(fajrM[0], fajrM[1], getIqamahFajr(context));
        info.fajrIqamah = format12HNoPeriod(fIq[0], fIq[1]);

        info.dhuhrAzan = format12H(dhuhrM[0], dhuhrM[1]);
        int[] dIq = addMinutes(dhuhrM[0], dhuhrM[1], getIqamahDhuhr(context));
        info.dhuhrIqamah = format12HNoPeriod(dIq[0], dIq[1]);

        info.asrAzan = format12H(asrM[0], asrM[1]);
        int[] aIq = addMinutes(asrM[0], asrM[1], getIqamahAsr(context));
        info.asrIqamah = format12HNoPeriod(aIq[0], aIq[1]);

        info.maghribAzan = format12H(maghribM[0], maghribM[1]);
        int[] mIq = addMinutes(maghribM[0], maghribM[1], getIqamahMaghrib(context));
        info.maghribIqamah = format12HNoPeriod(mIq[0], mIq[1]);

        info.ishaAzan = format12H(ishaM[0], ishaM[1]);
        int[] iIq = addMinutes(ishaM[0], ishaM[1], getIqamahIsha(context));
        info.ishaIqamah = format12HNoPeriod(iIq[0], iIq[1]);

        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int fMin = fajrM[0] * 60 + fajrM[1];
        int dMin = dhuhrM[0] * 60 + dhuhrM[1];
        int aMin = asrM[0] * 60 + asrM[1];
        int mMin = maghribM[0] * 60 + maghribM[1];
        int iMin = ishaM[0] * 60 + ishaM[1];

        if (currentMinutes < fMin) {
            info.activeKey = "fajr";
            info.nextStatus = "الفجر بعد " + formatRemaining(fMin - currentMinutes);
        } else if (currentMinutes < fMin + IQAMAH_FAJR) {
            info.activeKey = "fajr";
            int rem = (fMin + IQAMAH_FAJR) - currentMinutes;
            info.nextStatus = "إقامة الفجر بعد " + toArabicDigits(String.valueOf(rem)) + " د 🕋";
        } else if (currentMinutes < dMin) {
            info.activeKey = "dhuhr";
            info.nextStatus = "الظهر بعد " + formatRemaining(dMin - currentMinutes);
        } else if (currentMinutes < dMin + IQAMAH_DHUHR) {
            info.activeKey = "dhuhr";
            int rem = (dMin + IQAMAH_DHUHR) - currentMinutes;
            info.nextStatus = "إقامة الظهر بعد " + toArabicDigits(String.valueOf(rem)) + " د 🕋";
        } else if (currentMinutes < aMin) {
            info.activeKey = "asr";
            info.nextStatus = "العصر بعد " + formatRemaining(aMin - currentMinutes);
        } else if (currentMinutes < aMin + IQAMAH_ASR) {
            info.activeKey = "asr";
            int rem = (aMin + IQAMAH_ASR) - currentMinutes;
            info.nextStatus = "إقامة العصر بعد " + toArabicDigits(String.valueOf(rem)) + " د 🕋";
        } else if (currentMinutes < mMin) {
            info.activeKey = "maghrib";
            info.nextStatus = "المغرب بعد " + formatRemaining(mMin - currentMinutes);
        } else if (currentMinutes < mMin + IQAMAH_MAGHRIB) {
            info.activeKey = "maghrib";
            int rem = (mMin + IQAMAH_MAGHRIB) - currentMinutes;
            info.nextStatus = "إقامة المغرب بعد " + toArabicDigits(String.valueOf(rem)) + " د 🕋";
        } else if (currentMinutes < iMin) {
            info.activeKey = "isha";
            info.nextStatus = "العشاء بعد " + formatRemaining(iMin - currentMinutes);
        } else if (currentMinutes < iMin + IQAMAH_ISHA) {
            info.activeKey = "isha";
            int rem = (iMin + IQAMAH_ISHA) - currentMinutes;
            info.nextStatus = "إقامة العشاء بعد " + toArabicDigits(String.valueOf(rem)) + " د 🕋";
        } else {
            info.activeKey = "fajr";
            int rem = (24 * 60 - currentMinutes) + fMin;
            info.nextStatus = "فجر الغد بعد " + formatRemaining(rem);
        }

        return info;
    }

    public static String toArabicDigits(String input) {
        if (input == null) return "";
        char[] arabicDigits = {'٠','١','٢','٣','٤','٥','٦','٧','٨','٩'};
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append(arabicDigits[c - '0']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String formatRemaining(int totalMinutes) {
        if (totalMinutes < 0) totalMinutes = 0;
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        if (h > 0 && m > 0) return toArabicDigits(String.valueOf(h)) + " س و " + toArabicDigits(String.valueOf(m)) + " د";
        if (h > 0) return toArabicDigits(String.valueOf(h)) + " ساعة";
        return toArabicDigits(String.valueOf(m)) + " دقيقة";
    }

    private static int[] addMinutes(int h, int m, int add) {
        int total = h * 60 + m + add;
        return new int[]{(total / 60) % 24, total % 60};
    }

    private static String format12H(int h, int m) {
        String period = h >= 12 ? "م" : "ص";
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return toArabicDigits(String.format(Locale.US, "%d:%02d", h12, m)) + " " + period;
    }

    private static String format12HNoPeriod(int h, int m) {
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return toArabicDigits(String.format(Locale.US, "%d:%02d", h12, m));
    }

    private static int[] parseTime(String s) {
        if (s == null) return null;
        Matcher matcher = TIME_PATTERN.matcher(s);
        if (matcher.find()) {
            try {
                int h = Integer.parseInt(matcher.group(1));
                int m = Integer.parseInt(matcher.group(2));
                return new int[]{h, m};
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static void syncWithAlAdhanAsync(Context context, int year, int month) {
        new Thread(() -> {
            try {
                String cityName = getCityName(context);
                double lat = getCityLat(context);
                double lon = getCityLon(context);
                String urlStr = "https://api.aladhan.com/v1/calendar/" + year + "/" + month
                        + "?latitude=" + lat + "&longitude=" + lon
                        + "&method=4&timezonestring=" + TIMEZONE_ID;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray data = root.optJSONArray("data");
                    if (data != null) {
                        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject dayObj = data.getJSONObject(i);
                            JSONObject timings = dayObj.getJSONObject("timings");
                            String gDate = dayObj.getJSONObject("date").getJSONObject("gregorian").getString("date"); // DD-MM-YYYY
                            String[] dp = gDate.split("-");
                            String key = cityName + "_" + dp[2] + "-" + dp[1] + "-" + dp[0];

                            JSONObject save = new JSONObject();
                            save.put("fajr", timings.getString("Fajr"));
                            save.put("dhuhr", timings.getString("Dhuhr"));
                            save.put("asr", timings.getString("Asr"));
                            save.put("maghrib", timings.getString("Maghrib"));
                            save.put("isha", timings.getString("Isha"));
                            editor.putString(key, save.toString());
                        }
                        editor.apply();
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }
}
