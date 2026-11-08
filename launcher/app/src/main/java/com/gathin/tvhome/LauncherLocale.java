package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.regex.Pattern;

/** Resolves the launcher's saved language or follows the TV language on first launch. */
final class LauncherLocale {
    static final String LANGUAGE_KEY = "launcher_language_tag";
    static final String AUTO = "auto";
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");
    // Keep this list aligned with the official v3.4 locale-config and translated values-* resources.
    static final String[] SUPPORTED = {
            "en", "nb", "de", "ca", "es", "es-419", "fr", "ru", "zh-CN", "zh-TW",
            "th", "sr-Latn", "pl", "ar", "iw", "tr", "pt", "pt-PT", "fi", "vi",
            "bn", "cs", "it", "in", "nl", "uz", "uk", "ko", "da", "sv", "ast"
    };

    private LauncherLocale() {}

    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        return Locale.forLanguageTag(tag.trim());
    }

    static String saved(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(LANGUAGE_KEY, AUTO);
    }

    static boolean isSupported(String tag) {
        if (tag == null) return false;
        for (String supported : SUPPORTED) {
            if (supported.equalsIgnoreCase(tag)) return true;
        }
        return false;
    }

    private static String systemTag(Context base) {
        Configuration configuration = base.getResources().getConfiguration();
        Locale system = configuration.getLocales().isEmpty()
                ? Locale.getDefault() : configuration.getLocales().get(0);
        String language = system.getLanguage();
        if ("zh".equals(language)) {
            if ("TW".equalsIgnoreCase(system.getCountry()) || "HK".equalsIgnoreCase(system.getCountry())
                    || "MO".equalsIgnoreCase(system.getCountry())
                    || "Hant".equalsIgnoreCase(system.getScript())) return "zh-TW";
            return "zh-CN";
        }
        if ("he".equals(language)) language = "iw";
        if ("id".equals(language)) language = "in";
        if ("es".equals(language) && ("419".equals(system.getVariant())
                || "419".equals(system.getCountry()) || "MX".equalsIgnoreCase(system.getCountry())
                || "AR".equalsIgnoreCase(system.getCountry())
                || "CO".equalsIgnoreCase(system.getCountry()))) return "es-419";
        if ("pt".equals(language) && "PT".equalsIgnoreCase(system.getCountry())) return "pt-PT";
        if ("sr".equals(language) && ("Latn".equalsIgnoreCase(system.getScript())
                || "YU".equalsIgnoreCase(system.getCountry()))) return "sr-Latn";
        return language;
    }

    static Locale resolved(Context base) {
        String choice = saved(base);
        if (choice != null && !AUTO.equals(choice)) {
            Locale manual = parse(choice);
            if (manual != null && isSupported(choice)) return manual;
        }
        String system = systemTag(base);
        if (isSupported(system)) return parse(system);
        return Locale.forLanguageTag("zh-TW");
    }

    /** Every Activity uses the same resolved locale; AUTO follows the TV and unsupported TV languages use zh-TW. */
    static Context wrap(Context base) {
        Locale locale = resolved(base);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        return base.createConfigurationContext(configuration);
    }
}
