# V17.1 changes

- Add condition-specific weather symbols to each day in the weekly weather card, matching the supplied visual reference.
- Anchor the top-right buttons to the date row rather than the expanded left header block, so the weekly weather card no longer pushes Wi-Fi/Settings downward (restores the V16.8 header alignment).
- Correct GridLayout row/column specifications for folder tiles and the add tile. Four equal-weight columns now fill the available width with symmetric left/right margins.

Build note: APK build was attempted with the included Gradle wrapper, but Gradle 8.9 could not be downloaded because services.gradle.org is unreachable in this environment. No APK was produced or validated.
