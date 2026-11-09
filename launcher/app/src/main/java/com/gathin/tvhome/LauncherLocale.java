package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Keeps every launcher screen and date formatter in Traditional Chinese (Taiwan). */
final class LauncherLocale {
    private static final Locale TRADITIONAL_CHINESE = Locale.forLanguageTag("zh-TW");
    private LauncherLocale() {}
    static Locale fixedLocale() { return TRADITIONAL_CHINESE; }
    static Context wrap(Context base) {
        Locale.setDefault(TRADITIONAL_CHINESE);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(TRADITIONAL_CHINESE);
        return base.createConfigurationContext(configuration);
    }
}
