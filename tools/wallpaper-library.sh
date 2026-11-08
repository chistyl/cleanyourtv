#!/usr/bin/env bash
# Copy the repository's official wallpaper images into the launcher's persistent library.
# Unlike tools/theme.sh, this never clears wallpapers/ or wallpaper-library/.
#
# Usage:
#   TV_HOST=192.168.1.50 tools/wallpaper-library.sh --all
#   TV_HOST=192.168.1.50 tools/wallpaper-library.sh aerial
#
# Images are written through ThemeProvider so files are owned/readable by the launcher on Android 11+.
set -euo pipefail

ARG="${1:-}"
if [ -z "$ARG" ] || [ "$#" -ne 1 ]; then
  echo "usage: TV_HOST=<TV IP> $0 <theme-name|--all>" >&2
  exit 2
fi
case "$ARG" in
  --all) ;;
  *[!A-Za-z0-9_-]*|'')
    echo "invalid theme name: $ARG (use letters, digits, '_' or '-'). Nothing was sent." >&2
    exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$(dirname "$0")"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || {
  echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt." >&2
  exit 2
}
if [ -z "${TV_HOST:-}" ]; then
  echo "set TV_HOST to the TV IP address. Nothing was sent." >&2
  exit 2
fi

PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-wall-library

push_image() {
  local theme="$1" image="$2" name stage target
  name="$(basename "$image")"
  case "$name" in
    *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP]) ;;
    *) return 1 ;;
  esac
  if [[ ! "$name" =~ ^[A-Za-z0-9_.-]+$ || "$name" == *..* ]]; then
    echo "refusing unsafe wallpaper filename '$name'. Nothing further was sent." >&2
    return 2
  fi
  stage="$TMP-$theme-$name"
  target="$DIR/wallpaper-library/$theme/$name"
  # Stage the image on the TV, then let the provider create the app-owned library file.
  "$PY" -I tv.py --push "$image" "$stage"
  "$PY" -I tv.py "rm -f '$target'; { o=\$(content write --uri $URI/wallpaper-library/$theme/$name < '$stage' 2>&1) && case \"\$o\" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac; } || { mkdir -p '$DIR/wallpaper-library/$theme' 2>/dev/null; cp '$stage' '$target'; }; rm -f '$stage'"
}

push_theme() {
  local theme="$1" source="$ROOT/themes/$theme/wallpapers" count=0 image
  if [ -d "$source" ]; then
    while IFS= read -r -d '' image; do
      if push_image "$theme" "$image"; then
        count=$((count + 1))
      else
        rc=$?
        [ "$rc" -eq 1 ] || return "$rc"
      fi
    done < <(find "$source" -maxdepth 1 -type f -print0 | sort -z)
  fi
  # Themes may ship one static wallpaper.jpg instead of a wallpapers/ rotation folder.
  if [ -f "$ROOT/themes/$theme/wallpaper.jpg" ]; then
    if push_image "$theme" "$ROOT/themes/$theme/wallpaper.jpg"; then
      count=$((count + 1))
    else
      rc=$?
      [ "$rc" -eq 1 ] || return "$rc"
    fi
  fi
  if [ "$count" -eq 0 ]; then
    echo "no wallpaper images for theme '$theme'; skipped"
  else
    echo "$theme: $count wallpaper(s) copied to wallpaper-library/$theme/"
  fi
}

if [ "$ARG" = "--all" ]; then
  while IFS= read -r -d '' config; do
    theme="$(basename "$(dirname "$config")")"
    push_theme "$theme"
  done < <(find "$ROOT/themes" -mindepth 2 -maxdepth 2 -type f -name config.json -print0 | sort -z)
else
  [ -f "$ROOT/themes/$ARG/config.json" ] || {
    echo "unknown theme '$ARG'. Nothing was sent." >&2
    exit 2
  }
  push_theme "$ARG"
fi

echo "Wallpaper library sync complete. The active theme and its wallpaper rotation were not changed."
