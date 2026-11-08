"""The independent wallpaper sync tool must keep its files outside the active theme collection."""
from pathlib import Path
import shutil
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "wallpaper-library.sh"


class WallpaperLibraryToolTest(unittest.TestCase):
    def test_script_is_valid_bash(self):
        bash = shutil.which("bash")
        if not bash:
            self.skipTest("bash is not installed")
        result = subprocess.run([bash, "-n", str(SCRIPT)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_sync_targets_the_separate_allow_listed_library(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("wallpaper-library/$theme/$name", source)
        self.assertIn("content://$PKG.theme/wallpaper-library/$theme/$name", source)
        self.assertIn('find "$ROOT/themes" -mindepth 2 -maxdepth 2 -type f -name config.json', source)
        self.assertIn('"$ROOT/themes/$theme/wallpaper.jpg"', source)
        self.assertIn('local theme="$1" image="$2" name stage target', source)

    def test_sync_does_not_clear_or_replace_the_active_theme(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertNotIn("CLEAR_WALLPAPERS", source)
        self.assertNotIn("theme_revision", source)
        self.assertNotIn("content delete", source)
        self.assertNotIn("rm -rf", source)
        self.assertIn("active theme and its wallpaper rotation were not changed", source)

    def test_theme_and_image_names_are_constrained_before_remote_path_use(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn('case "$ARG" in', source)
        self.assertIn('[[ ! "$name" =~ ^[A-Za-z0-9_.-]+$ || "$name" == *..* ]]', source)
        self.assertIn("*.[jJ][pP][gG]", source)
        self.assertIn("*.[wW][eE][bB][pP]", source)


if __name__ == "__main__":
    unittest.main()
