"""The single-language launcher has no right-to-left Hebrew resource overrides."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
RES=Path(__file__).resolve().parents[1]/"launcher/app/src/main/res"
class NoRtlLocaleResourcesTest(unittest.TestCase):
 def test_hebrew_resource_overrides_are_absent(self):
  self.assertFalse((RES/"layout-iw").exists()); self.assertFalse((RES/"values-iw").exists())
 def test_default_layout_direction_is_left_to_right(self):
  root=ET.parse(RES/"values/bools.xml").getroot()
  v={n.get("name"):(n.text or "").strip() for n in root}
  self.assertEqual("false",v["rtl_app_grid"]); self.assertEqual("false",v["rtl_menu_bar"])
if __name__=="__main__": unittest.main()
