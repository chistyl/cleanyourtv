package com.gathin.tvhome;

import com.github.houbb.opencc4j.util.ZhTwConverterUtil;

/**
 * Converts Simplified Chinese app labels to Taiwan-standard Traditional Chinese.
 * Conversion is best-effort: a dictionary/runtime issue must never take down the HOME launcher.
 */
final class ChineseText {
    private ChineseText() {}

    static String toTraditional(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            return ZhTwConverterUtil.toTraditional(value);
        } catch (RuntimeException | LinkageError ignored) {
            // OpenCC is an optional presentation enhancement. Keep the original app label if
            // its dictionary, reflective loader, or runtime dependency cannot initialise.
            return value;
        }
    }
}
