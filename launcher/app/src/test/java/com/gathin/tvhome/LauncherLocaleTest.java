package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LauncherLocaleTest {
    @Test
    public void launcherLocaleIsAlwaysTraditionalChineseTaiwan() {
        assertEquals("zh", LauncherLocale.fixedLocale().getLanguage());
        assertEquals("TW", LauncherLocale.fixedLocale().getCountry());
    }
}
