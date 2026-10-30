package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;

/**
 * مساعد إدارة وتطبيق الخطوط العربية المخصصة ديناميكياً:
 * خط ثمانية الفاخر، خط دبي التلفزيوني، أو خط النظام.
 */
public final class FontHelper {

    private FontHelper() {}

    public static String getSelectedFont(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("selected_font", "thmanyah");
    }

    public static Typeface getRegular(Context context) {
        String font = getSelectedFont(context);
        if ("dubai".equals(font)) {
            try {
                return ResourcesCompat.getFont(context, R.font.dubai_regular);
            } catch (Exception ignored) {}
        } else if ("system".equals(font)) {
            return Typeface.DEFAULT;
        }
        try {
            return ResourcesCompat.getFont(context, R.font.thmanyah_regular);
        } catch (Exception ignored) {
            return Typeface.DEFAULT;
        }
    }

    public static Typeface getBold(Context context) {
        String font = getSelectedFont(context);
        if ("dubai".equals(font)) {
            try {
                return ResourcesCompat.getFont(context, R.font.dubai_bold);
            } catch (Exception ignored) {}
        } else if ("system".equals(font)) {
            return Typeface.DEFAULT_BOLD;
        }
        try {
            return ResourcesCompat.getFont(context, R.font.thmanyah_bold);
        } catch (Exception ignored) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    public static Typeface getMedium(Context context) {
        String font = getSelectedFont(context);
        if ("dubai".equals(font)) {
            try {
                return ResourcesCompat.getFont(context, R.font.dubai_medium);
            } catch (Exception ignored) {}
        } else if ("system".equals(font)) {
            return Typeface.DEFAULT;
        }
        try {
            return ResourcesCompat.getFont(context, R.font.thmanyah_medium);
        } catch (Exception ignored) {
            return Typeface.DEFAULT;
        }
    }

    public static void applyFont(View root, Context context) {
        if (root == null || context == null) return;
        Typeface reg = getRegular(context);
        Typeface bld = getBold(context);
        Typeface med = getMedium(context);
        applyFontRecursive(root, reg, bld, med);
    }

    private static void applyFontRecursive(View v, Typeface reg, Typeface bld, Typeface med) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            Typeface cur = tv.getTypeface();
            if (cur != null && cur.isBold()) {
                tv.setTypeface(bld);
            } else {
                tv.setTypeface(reg);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyFontRecursive(g.getChildAt(i), reg, bld, med);
            }
        }
    }
}

