import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image

from tools import package_wallpapers


class PackageWallpapersTest(unittest.TestCase):
    def make_theme(self, root: Path, name: str, count: int) -> Path:
        theme = root / name
        (theme / "wallpapers").mkdir(parents=True)
        (theme / "config.json").write_text("{}", encoding="utf-8")
        for index in range(count):
            Image.new("RGB", (2560, 1440), (index * 20, 80, 120)).save(
                theme / "wallpapers" / f"{index + 1:02d}.png"
            )
        return theme

    def test_limits_each_theme_to_three_and_keeps_single(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "themes"
            output = Path(temp) / "out"
            root.mkdir()
            self.make_theme(root, "many", 5)
            self.make_theme(root, "one", 1)
            with patch.object(package_wallpapers, "SOURCE", root):
                result = package_wallpapers.package(output)
            self.assertEqual(result, {"many": 3, "one": 1})
            self.assertEqual(len(list(output.rglob("*.jpg"))), 4)

    def test_skips_themes_without_wallpapers(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "themes"
            output = Path(temp) / "out"
            root.mkdir()
            theme = root / "empty"
            theme.mkdir()
            (theme / "config.json").write_text("{}", encoding="utf-8")
            with patch.object(package_wallpapers, "SOURCE", root):
                result = package_wallpapers.package(output)
            self.assertEqual(result, {})
            self.assertEqual(list(output.rglob("*")), [])

    def test_resizes_to_full_hd_and_outputs_jpeg(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "themes"
            output = Path(temp) / "out"
            root.mkdir()
            self.make_theme(root, "wide", 1)
            with patch.object(package_wallpapers, "SOURCE", root):
                package_wallpapers.package(output)
            with Image.open(output / "wide" / "01.jpg") as image:
                self.assertEqual(image.format, "JPEG")
                self.assertLessEqual(image.width, 1920)
                self.assertLessEqual(image.height, 1080)


if __name__ == "__main__":
    unittest.main()
