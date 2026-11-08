#!/usr/bin/env bash
# Copy the repository's official theme wallpaper collections into the launcher's persistent library.
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

push_theme() {
  local theme="$1"
  local source="$ROOT/themes/$theme/wallpapers"
  [ -d "$source" ] || { echo "no wallpapers/ collection for theme '$theme'; skipped"; return 0; }
  local count=0
  while IFS= read -r -d '' image; do
    local name
    name="$(basename "$image")"
    case "$name" in
      *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP]) ;;
      *) continue ;;
    esac
    if [[ ! "$name" =~ ^[A-Za-z0-9_.-]+$ || "$name" == *..* ]]; then
      echo "refusing unsafe wallpaper filename '$name'. Nothing further was sent." >&2
      return 2
    fi
    local stage="$TMP-$theme-$name"
    local target="$DIR/wallpaper-library/$theme/$name"
    # Stage the image on the TV, then let the provider create the app-owned library file.
    "$PY" -I tv.py --push "$image" "$stage"
    "$PY" -I tv.py "rm -f '$target'; { o=\$(content write --uri $URI/wallpaper-library/$theme/$name < '$stage' 2>&1) && case \"\$o\" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac; } || { mkdir -p '$DIR/wallpaper-library/$theme' 2>/dev/null; cp '$stage' '$target'; }; rm -f '$stage'"
    count=$((count + 1))
  done < <(find "$source" -maxdepth 1 -type f -print0 | sort -z)
  echo "$theme: $count wallpaper(s) copied to wallpaper-library/$theme/"
}

if [ "$ARG" = "--all" ]; then
  while IFS= read -r source; do
    theme="$(basename "$(dirname "$source")")"
    push_theme "$theme"
  done < <(find "$ROOT/themes" -mindepth 2 -maxdepth 2 -type d -name wallpapers | sort)
else
  [ -d "$ROOT/themes/$ARG" ] || {
    echo "unknown theme '$ARG'. Nothing was sent." >&2
    exit 2
  }
  push_theme "$ARG"
fi

echo "Wallpaper library sync complete. The active theme and its wallpaper rotation were not changed."
