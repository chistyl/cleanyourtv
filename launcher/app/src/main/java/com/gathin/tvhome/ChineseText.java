package com.gathin.tvhome;

import com.github.houbb.opencc4j.util.ZhTwConverterUtil;

/** Converts Simplified Chinese app labels to Taiwan-standard Traditional Chinese. */
final class ChineseText {
    private ChineseText() {}

    static String toTraditional(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            return ZhTwConverterUtil.toTraditional(value);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}
