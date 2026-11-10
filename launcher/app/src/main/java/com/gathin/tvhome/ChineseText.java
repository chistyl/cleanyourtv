package com.gathin.tvhome;

import android.os.Build;

/** Converts Simplified Chinese app labels to Traditional Chinese on supported Android versions. */
final class ChineseText {
    private ChineseText() {}

    static String toTraditional(String value) {
        if (value == null || value.isEmpty() || Build.VERSION.SDK_INT < 24) return value;
        try {
            return android.icu.text.Transliterator.getInstance("Hans-Hant").transliterate(value);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}
