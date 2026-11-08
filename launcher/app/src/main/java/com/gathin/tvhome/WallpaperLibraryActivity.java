package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.AssetManager;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Remote-first browser for the persistent wallpaper catalogue stored beside the active theme. */
public final class WallpaperLibraryActivity extends Activity {
    private static final int COLUMNS = 3;
    private static final int BG = Color.rgb(16, 20, 25);
    private static final int PANEL = Color.rgb(36, 43, 52);
    private static final int ACCENT = Color.rgb(77, 174, 141);
    private final List<WallpaperItem> allItems = new ArrayList<>();
    private final List<WallpaperItem> visibleItems = new ArrayList<>();
    private WallpaperAdapter adapter;
    private Spinner categories;
    private TextView empty;
    private SharedPreferences choices;

    private static final class WallpaperItem {
        final String theme;
        final File file;
        WallpaperItem(String theme, File file) { this.theme = theme; this.file = file; }
        String choice() { return ThemeFiles.WALLPAPER_LIBRARY + "/" + theme + "/" + file.getName(); }
        String title() { return file.getName(); }
    }

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        installBundledWallpapers();
        loadItems();
        buildScreen();
    }

    /**
     * The curated catalogue is bundled in the APK and copied automatically on first entry.
     * It lives beside theme files so the existing wallpaper resolver can use it without
     * changing the active theme or requiring a network connection.
     */
    private void installBundledWallpapers() {
        File appDir = Config.dir(this);
        if (appDir == null) return;
        File library = new File(appDir, ThemeFiles.WALLPAPER_LIBRARY);
        try {
            copyAssetDirectory(getAssets(), "wallpaper-library", library);
        } catch (IOException ignored) {
            // Keep any previously installed catalogue usable if storage is temporarily unavailable.
        }
    }

    private void copyAssetDirectory(AssetManager assets, String assetPath, File destination)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            if (!destination.exists() && !destination.mkdirs()) throw new IOException("Cannot create " + destination);
            for (String child : children) {
                copyAssetDirectory(assets, assetPath + "/" + child, new File(destination, child));
            }
            return;
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void loadItems() {
        File dir = Config.dir(this);
        File library = dir == null ? null : new File(dir, ThemeFiles.WALLPAPER_LIBRARY);
        File[] themes = library == null ? null : library.listFiles(File::isDirectory);
        if (themes == null) return;
        java.util.Arrays.sort(themes, Comparator.comparing(File::getName));
        for (File theme : themes) {
            for (File image : WallpaperRotation.images(theme)) allItems.add(new WallpaperItem(theme.getName(), image));
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(48), dp(28), dp(48), dp(28));

        TextView title = new TextView(this);
        title.setText(R.string.wallpaper_library_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText(R.string.wallpaper_library_hint);
        hint.setTextColor(Color.rgb(172, 184, 193));
        hint.setTextSize(15);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = dp(6);
        root.addView(hint, hintLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button follow = actionButton(R.string.wallpaper_theme_default);
        follow.setOnClickListener(v -> saveChoice(null));
        actions.addView(follow, weighted());
        Button none = actionButton(R.string.wallpaper_none);
        none.setOnClickListener(v -> saveChoice(WallpaperRotation.CHOICE_NONE));
        LinearLayout.LayoutParams noneLp = weighted();
        noneLp.leftMargin = dp(12);
        actions.addView(none, noneLp);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(52));
        actionLp.topMargin = dp(18);
        root.addView(actions, actionLp);

        categories = new Spinner(this);
        categories.setPrompt(getString(R.string.wallpaper_library_category));
        List<String> categoryNames = new ArrayList<>();
        categoryNames.add(getString(R.string.wallpaper_library_all));
        for (WallpaperItem item : allItems) if (!categoryNames.contains(item.theme)) categoryNames.add(item.theme);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoryNames);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categories.setAdapter(categoryAdapter);
        LinearLayout.LayoutParams categoryLp = new LinearLayout.LayoutParams(dp(360), dp(48));
        categoryLp.topMargin = dp(14);
        root.addView(categories, categoryLp);

        empty = new TextView(this);
        empty.setText(R.string.wallpaper_library_empty);
        empty.setTextColor(Color.LTGRAY);
        empty.setTextSize(18);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new LinearLayout.LayoutParams(-1, dp(90)));

        GridView grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setHorizontalSpacing(dp(14));
        grid.setVerticalSpacing(dp(14));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(android.R.color.transparent);
        grid.setFocusable(true);
        adapter = new WallpaperAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> showPreview(visibleItems.get(position)));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        gridLp.topMargin = dp(8);
        root.addView(grid, gridLp);

        categories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filter(position == 0 ? null : String.valueOf(parent.getItemAtPosition(position)));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        setContentView(root);
        filter(null);
        if (visibleItems.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);
        }
    }

    private void filter(String theme) {
        visibleItems.clear();
        for (WallpaperItem item : allItems) if (theme == null || theme.equals(item.theme)) visibleItems.add(item);
        if (adapter != null) adapter.notifyDataSetChanged();
        if (empty != null) {
            empty.setVisibility(visibleItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showPreview(WallpaperItem item) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(8));
        content.setBackgroundColor(PANEL);
        ImageView preview = new ImageView(this);
        preview.setImageBitmap(decode(item.file, 1920, 1080));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(310)));
        TextView name = new TextView(this);
        name.setText(item.theme + "  ·  " + item.title());
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, dp(44));
        content.addView(name, nameLp);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_library_preview)
                .setView(content)
                .setPositiveButton(R.string.wallpaper_library_apply, (d, which) -> saveChoice(item.choice()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void saveChoice(String choice) {
        SharedPreferences.Editor edit = choices.edit();
        if (choice == null) edit.remove(Config.WALLPAPER_CHOICE_KEY);
        else edit.putString(Config.WALLPAPER_CHOICE_KEY, choice);
        edit.putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        setResult(RESULT_OK);
        Toast.makeText(this, choice == null ? R.string.wallpaper_theme_default : R.string.wallpaper_applied,
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private Button actionButton(int text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(background(PANEL, dp(12), Color.rgb(65, 78, 89), dp(1)));
        button.setFocusable(true);
        return button;
    }

    private class WallpaperAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleItems.size(); }
        @Override public Object getItem(int position) { return visibleItems.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            WallpaperItem item = visibleItems.get(position);
            LinearLayout card;
            ImageView image;
            TextView label;
            if (convertView instanceof LinearLayout) {
                card = (LinearLayout) convertView;
                image = (ImageView) card.getChildAt(0);
                label = (TextView) card.getChildAt(1);
            } else {
                card = new LinearLayout(WallpaperLibraryActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(7), dp(7), dp(7), dp(7));
                card.setBackground(background(PANEL, dp(13), Color.TRANSPARENT, 0));
                card.setFocusable(true);
                card.setClickable(true);
                image = new ImageView(WallpaperLibraryActivity.this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(image, new LinearLayout.LayoutParams(-1, dp(132)));
                label = new TextView(WallpaperLibraryActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(14);
                label.setMaxLines(2);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(dp(5), dp(4), dp(5), dp(2));
                card.addView(label, new LinearLayout.LayoutParams(-1, dp(42)));
                card.setOnFocusChangeListener((v, focused) ->
                        v.setBackground(background(focused ? Color.rgb(55, 91, 91) : PANEL,
                                dp(13), focused ? ACCENT : Color.TRANSPARENT, focused ? dp(2) : 0)));
            }
            image.setImageBitmap(decode(item.file, 480, 270));
            label.setText(item.theme + "  ·  " + item.title());
            card.setContentDescription(item.theme + " " + item.title());
            return card;
        }
    }

    private Bitmap decode(File file, int maxWidth, int maxHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > maxWidth * 2 || bounds.outHeight / sample > maxHeight * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private GradientDrawable background(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
