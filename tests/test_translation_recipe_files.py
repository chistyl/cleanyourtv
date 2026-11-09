"""There are no per-language resource overlays: all launcher strings live in values/."""
from pathlib import Path
import unittest
RES=Path(__file__).resolve().parents[1]/"launcher/app/src/main/res"
class SingleResourceSetTest(unittest.TestCase):
 def test_no_locale_specific_resource_directories_remain(self):
  self.assertEqual([],sorted(p.name for p in RES.glob("values-*")))
  self.assertFalse((RES/"layout-iw").exists())
 def test_chinese_layout_and_drawables_are_retained(self):
  self.assertTrue((RES/"layout-zh/row_app_library.xml").is_file())
  self.assertTrue((RES/"drawable-zh").is_dir())
 def test_all_language_strings_are_in_the_default_resource_set(self):
  s=(RES/"values/strings.xml").read_text(encoding="utf-8")
  for word in ("主畫面","輸入來源","桌面設定","重新啟動"):
   with self.subTest(word=word): self.assertIn(word,s)
if __name__=="__main__": unittest.main()
