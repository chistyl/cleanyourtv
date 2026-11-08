#!/usr/bin/env python3
"""Prepare a small, deterministic offline wallpaper catalogue for the Android APK."""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "themes"
DEFAULT_OUTPUT = ROOT / "launcher/app/src/main/assets/wallpaper-library"
MAX_PER_THEME = 3
MAX_SIZE = (1920, 1080)
JPEG_QUALITY = 88
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def source_images(theme_dir: Path) -> list[Path]:
    rotation = theme_dir / "wallpapers"
    candidates = sorted(
        (p for p in rotation.iterdir() if p.is_file() and p.suffix.lower() in EXTENSIONS),
        key=lambda p: p.name.casefold(),
    ) if rotation.is_dir() else []
    if not candidates:
        single = theme_dir / "wallpaper.jpg"
        if single.is_file():
            candidates = [single]
    return candidates[:MAX_PER_THEME]


def package(output: Path = DEFAULT_OUTPUT) -> dict[str, int]:
    if output.exists():
        import shutil
        shutil.rmtree(output)
    output.mkdir(parents=True, exist_ok=True)
    result: dict[str, int] = {}
    for theme_dir in sorted(SOURCE.iterdir(), key=lambda p: p.name.casefold()):
        if not theme_dir.is_dir() or not (theme_dir / "config.json").is_file():
            continue
        selected = source_images(theme_dir)
        if not selected:
            continue
        target_dir = output / theme_dir.name
        target_dir.mkdir(parents=True, exist_ok=True)
        for index, source in enumerate(selected, start=1):
            target = target_dir / f"{index:02d}.jpg"
            with Image.open(source) as original:
                image = ImageOps.exif_transpose(original).convert("RGB")
                image.thumbnail(MAX_SIZE, Image.Resampling.LANCZOS)
                image.save(target, "JPEG", quality=JPEG_QUALITY, optimize=True, progressive=True)
        result[theme_dir.name] = len(selected)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    result = package(args.output)
    total = sum(result.values())
    size = sum(p.stat().st_size for p in args.output.rglob("*.jpg"))
    print(f"Packaged {total} wallpapers across {len(result)} themes ({size} bytes).")
    for theme, count in result.items():
        print(f"{theme}: {count}")


if __name__ == "__main__":
    main()
