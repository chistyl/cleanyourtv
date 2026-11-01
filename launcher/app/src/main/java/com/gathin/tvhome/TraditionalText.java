package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.Transliterator;

/** Display-only Simplified Chinese to Traditional Chinese conversion. */
public final class TraditionalText {
    private static final String PREFS = "display_preferences";
    private static final String KEY_ENABLED = "simplified_to_traditional";
    private static final Transliterator CONVERTER =
            Transliterator.getInstance("Simplified-Traditional");

    private TraditionalText() {}

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String display(Context context, CharSequence value) {
        if (value == null) return "";
        String text = value.toString();
        return isEnabled(context) ? CONVERTER.transliterate(text) : text;
    }
}
