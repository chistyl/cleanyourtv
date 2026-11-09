# Workflow: Launcher language policy

The launcher ships **Traditional Chinese (Taiwan) only**. Its user interface and date/time formatting are fixed to the zh-TW locale.

Do not add language-qualified values-* resource folders, an Android localeConfig, a language picker, or per-language layout overrides. Put all launcher strings in launcher/app/src/main/res/values/strings.xml and calendar strings in values/lunar.xml. Keep placeholders such as %1$s and %1$d unchanged.

The shared Chinese typography resources in layout-zh/ and drawable-zh/ are intentional and must remain; they are not alternate language packs.

When adding or changing visible UI text:
1. Write it in Taiwan Traditional Chinese.
2. Add it to the default resource file and keep XML valid.
3. Preserve formatting arguments and plural/resource names used by Java.
4. Run python3 -m unittest discover -s tests -v.
5. Build the release APK and verify the UI strings are included. CI has no APK file-size ceiling.

Do not reintroduce multilingual UI support unless the project owner explicitly changes this policy.
