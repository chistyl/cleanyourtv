package com.gathin.tvhome;

import com.github.houbb.opencc4j.util.ZhTwConverterUtil;

/**
 * Offline Simplified-to-Taiwan-Traditional conversion for launcher app labels.
 * Uses the pure-Java OpenCC implementation and does not change app contents or system locale.
 */
final class SimplifiedTraditional {
    private SimplifiedTraditional() {}

    static String convert(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            return ZhTwConverterUtil.toTraditional(text);
        } catch (RuntimeException ignored) {
            // A label must remain displayable even if conversion data cannot be loaded.
            return text;
        }
    }
}
