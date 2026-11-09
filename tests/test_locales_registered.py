"""The launcher ships one fixed UI language: Traditional Chinese (Taiwan)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"launcher/app/src/main/res"
MANIFEST=ROOT/"launcher/app/src/main/AndroidManifest.xml"
LAYOUT=RES/"layout/activity_launcher_settings.xml"
SETTINGS=ROOT/"launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java"
LOCALE=ROOT/"launcher/app/src/main/java/com/gathin/tvhome/LauncherLocale.java"
class SingleLocaleTest(unittest.TestCase):
 def test_no_language_qualified_value_resources_ship(self):
  self.assertEqual([],sorted(p.name for p in RES.glob("values-*")))
 def test_no_android_app_language_picker_is_registered(self):
  self.assertNotIn("localeConfig",MANIFEST.read_text(encoding="utf-8"))
  self.assertFalse((RES/"xml/locales_config.xml").exists())
  self.assertNotIn("languageChoice",LAYOUT.read_text(encoding="utf-8"))
 def test_no_launcher_language_picker_or_language_list_remains(self):
  s=SETTINGS.read_text(encoding="utf-8")
  self.assertNotIn("showLanguagePicker",s); self.assertNotIn("languageChoice",s)
  l=LOCALE.read_text(encoding="utf-8")
  self.assertIn('Locale.forLanguageTag("zh-TW")',l); self.assertNotIn("SUPPORTED",l)
 def test_default_resources_are_traditional_chinese(self):
  root=ET.parse(RES/"values/strings.xml").getroot()
  v={n.get("name"):"".join(n.itertext()) for n in root}
  self.assertEqual("主畫面",v["app_name"]); self.assertEqual("輸入來源",v["inputs"])
  self.assertEqual("桌面設定",v["launcher_settings"]); self.assertIn("桌布",v["wallpaper_label"])
  self.assertNotIn("language_label",v)
 def test_chinese_lunar_arrays_are_complete(self):
  root=ET.parse(RES/"values/lunar.xml").getroot(); v={n.get("name"):n for n in root}
  self.assertEqual(10,len(v["lunar_stems"])); self.assertEqual(12,len(v["lunar_branches"]))
if __name__=="__main__": unittest.main()
