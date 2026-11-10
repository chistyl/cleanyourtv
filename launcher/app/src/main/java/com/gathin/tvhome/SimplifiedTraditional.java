package com.gathin.tvhome;

import android.icu.text.Transliterator;

/**
 * Offline conversion for launcher app labels. Uses the ICU transliterator shipped with Android;
 * app contents and system locale are never changed.
 */
final class SimplifiedTraditional {
    private static final Transliterator CONVERTER =
            Transliterator.getInstance("Simplified-Traditional");

    private SimplifiedTraditional() {}

    static String convert(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            return CONVERTER.transliterate(text);
        } catch (RuntimeException ignored) {
            // A label must remain displayable even if a device's ICU data is incomplete.
            return text;
        }
    }
}
