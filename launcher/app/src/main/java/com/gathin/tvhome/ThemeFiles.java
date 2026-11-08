package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** A separate, persistent catalogue; theme.sh deliberately never clears this directory. */
    static final String WALLPAPER_LIBRARY = "wallpaper-library";
    private static final Pattern LIBRARY_THEME = Pattern.compile("[A-Za-z0-9_-]+");
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the app's external files directory, or null if the URI is not allow-listed. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        // Keep the official wallpaper catalogue beside (not inside) the active theme collection. Theme pushes
        // and --reset clear only wallpapers/, so a user's independent library survives every theme change.
        if (segments.size() == 3 && WALLPAPER_LIBRARY.equals(segments.get(0))
                && segments.get(1) != null && LIBRARY_THEME.matcher(segments.get(1)).matches()
                && collectionImage(segments.get(2))) {
            return WALLPAPER_LIBRARY + "/" + segments.get(1) + "/" + segments.get(2);
        }
        return null;
    }

    /** True for "wallpapers" alone: a delete of it clears the active theme's collection images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
