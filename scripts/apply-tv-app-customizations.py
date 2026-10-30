from pathlib import Path
print('Applying CleanYourTV per-app customization changes...')
base=Path('launcher/app/src/main/java/com/gathin/tvhome')
p=base/'AppAdapter.java'; s=p.read_text()
s=s.replace('import android.content.Context;','import android.content.Context;\nimport android.content.SharedPreferences;')
s=s.replace('import android.graphics.Bitmap;','import android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;')
s=s.replace('import android.graphics.Outline;','import android.graphics.Outline;\nimport android.graphics.Paint;')
s=s.replace('import android.graphics.Rect;','import android.graphics.Rect;\nimport android.graphics.Shader;')
s=s.replace('import android.graphics.drawable.Drawable;','import android.graphics.drawable.Drawable;\nimport android.graphics.drawable.BitmapDrawable;')
s=s.replace('import java.util.Set;','import java.util.Set;\nimport java.io.InputStream;')
old='''            String label = card != null ? card.label : Cards.cleanLabel(
                    appLabels.containsKey(pkg) ? appLabels.get(pkg) : String.valueOf(ri.loadLabel(pm)), pkg);'''
new='''            SharedPreferences custom = ctx.getSharedPreferences("tv_app_customizations", Context.MODE_PRIVATE);
            String customLabel = custom.getString("label_" + pkg, null);
            String label = card != null ? card.label : Cards.cleanLabel(
                    customLabel != null ? customLabel : (appLabels.containsKey(pkg) ? appLabels.get(pkg) : String.valueOf(ri.loadLabel(pm))), pkg);
            if (custom.getBoolean("hide_" + pkg, false)) label = "";'''
assert old in s
s=s.replace(old,new)
old='''            Drawable banner = null;
            // uniform_tiles draws the icon, so an app's banner is never loaded. A module card keeps its banner.
            if (!(uniformTiles && card == null)) {
                banner = bundledBanner(appBanners.get(pkg));
                if (banner == null) banner = iconTiles.contains(pkg) ? iconBanner(ri.loadIcon(pm)) : loadBanner(pm, ri);
            }
            // The icon is only drawn (and sampled for the glow) when there is no banner, so a tile with a
            // banner skips loading and decoding it.
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;'''
new='''            Drawable banner = null;
            Drawable icon = null;
            if (card == null) {
                String customIcon = custom.getString("icon_" + pkg, null);
                if (customIcon != null) {
                    try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(customIcon))) {
                        if (in != null) icon = new BitmapDrawable(ctx.getResources(), BitmapFactory.decodeStream(in));
                    } catch (Exception ignored) { }
                }
                if (icon == null) icon = ri.loadIcon(pm);
                banner = customIcon != null ? iconBanner(icon) : bundledBanner(appBanners.get(pkg));
                if (banner == null) banner = iconTiles.contains(pkg) ? iconBanner(icon) : loadBanner(pm, ri);
                // Apps without a real TV banner use their original mark centred on a naturally extended colour field.
                if (banner == null) banner = iconBanner(icon);
                icon = null;
            } else {
                banner = bundledBanner(appBanners.get(pkg));
                if (banner == null) banner = loadBanner(pm, ri);
                icon = banner == null ? ri.loadIcon(pm) : null;
            }'''
assert old in s
s=s.replace(old,new)
old='''    private Drawable iconBanner(Drawable icon) {
        int w = 320, h = 180, side = 104;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        canvas.drawColor(0xFF15202B);
        icon.setBounds((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2);
        icon.draw(canvas);
        return new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bmp);
    }'''
new='''    private Drawable iconBanner(Drawable icon) {
        int w = 640, h = 360, side = 148;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int base = dominantColor(icon);
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[1] = Math.min(0.58f, Math.max(0.22f, hsv[1] * 0.78f));
        hsv[2] = Math.min(0.48f, Math.max(0.22f, hsv[2] * 0.72f));
        int top = Color.HSVToColor(hsv);
        hsv[2] *= 0.72f;
        int bottom = Color.HSVToColor(hsv);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new android.graphics.LinearGradient(0, 0, w, h, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bg);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new android.graphics.RadialGradient(w * .5f, h * .5f, h * .72f,
                new int[]{0x44FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, glow);
        Drawable mark = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        mark.setBounds((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2);
        mark.draw(canvas);
        return new BitmapDrawable(ctx.getResources(), bmp);
    }'''
assert old in s
s=s.replace(old,new)
s=s.replace('h.name.setVisibility(dockKind && restNameAlpha == 0f ? View.INVISIBLE : View.VISIBLE);',
            'h.name.setVisibility(e.name.isEmpty() ? View.GONE : (dockKind && restNameAlpha == 0f ? View.INVISIBLE : View.VISIBLE));')
p.write_text(s)

p=base/'MainActivity.java'; s=p.read_text()
s=s.replace('import android.app.Activity;','import android.app.Activity;\nimport android.app.AlertDialog;')
s=s.replace('import android.text.TextUtils;','import android.text.TextUtils;\nimport android.text.InputType;')
s=s.replace('import android.widget.FrameLayout;','import android.widget.FrameLayout;\nimport android.widget.EditText;')
s=s.replace('    private static final int LAUNCHER_SETTINGS_REQUEST = 42;','    private static final int LAUNCHER_SETTINGS_REQUEST = 42;\n    private static final int APP_ICON_PICK_REQUEST = 143;\n    private String pendingIconPackage;')
old='''        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LAUNCHER_SETTINGS_REQUEST'''
new='''        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == APP_ICON_PICK_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            if (pendingIconPackage != null) {
                getSharedPreferences("tv_app_customizations", MODE_PRIVATE).edit()
                        .putString("icon_" + pendingIconPackage, uri.toString()).apply();
                pendingIconPackage = null;
                reloadApps(false);
            }
        }
        if (requestCode == LAUNCHER_SETTINGS_REQUEST'''
assert old in s
s=s.replace(old,new)
anchor='''        // Some firmware has no App info page at all; a row that only toasts "can't open" helps nobody.
        Intent info = SystemSettings.appInfo(this, pkg);'''
insert='''        SharedPreferences appPrefs = getSharedPreferences("tv_app_customizations", MODE_PRIVATE);
        String savedLabel = appPrefs.getString("label_" + pkg, adapter.labelAt(position));
        addSettingsMenuRow(rows, popup, "修改 APP 名稱", () -> {
            EditText edit = new EditText(this);
            edit.setSingleLine(true);
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            edit.setText(savedLabel);
            edit.setSelection(edit.getText().length());
            new AlertDialog.Builder(this).setTitle("修改 APP 名稱").setView(edit)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("儲存", (dialog, which) -> {
                        String value = edit.getText().toString().trim();
                        if (!value.isEmpty()) appPrefs.edit().putString("label_" + pkg, value).apply();
                        reloadApps(false);
                    }).show();
        });
        boolean nameHidden = appPrefs.getBoolean("hide_" + pkg, false);
        addSettingsMenuRow(rows, popup, nameHidden ? "顯示 APP 名稱" : "隱藏 APP 名稱", () -> {
            appPrefs.edit().putBoolean("hide_" + pkg, !nameHidden).apply();
            reloadApps(false);
        });
        addSettingsMenuRow(rows, popup, "更換圖標", () -> {
            pendingIconPackage = pkg;
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(pick, APP_ICON_PICK_REQUEST); }
            catch (ActivityNotFoundException ex) {
                pendingIconPackage = null;
                Toast.makeText(this, "找不到可用的圖片選擇器", Toast.LENGTH_LONG).show();
            }
        });
        if (appPrefs.contains("label_" + pkg) || appPrefs.contains("hide_" + pkg) || appPrefs.contains("icon_" + pkg)) {
            addSettingsMenuRow(rows, popup, "還原 APP 原始設定", () -> {
                appPrefs.edit().remove("label_" + pkg).remove("hide_" + pkg).remove("icon_" + pkg).apply();
                reloadApps(false);
            });
        }
        // Some firmware has no App info page at all; a row that only toasts "can't open" helps nobody.
        Intent info = SystemSettings.appInfo(this, pkg);'''
assert anchor in s
s=s.replace(anchor,insert)
p.write_text(s)
print("TV app customization patch applied")
