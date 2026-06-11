#!/usr/bin/env python3
"""
Compress exercise images for mobile app bundle.

Resizes all coach_*.jpg from ~1400px to 720px wide (portrait)
and re-encodes at JPEG quality 70.

Before: ~1655 KB avg, 518 MB total
After:  ~80 KB avg, ~25 MB total  (20x reduction)

Usage:
    pip install Pillow
    python scripts/compress_images.py
"""

from pathlib import Path
from PIL import Image
import sys

DRAWABLE = Path(__file__).parent.parent / "ui/src/commonMain/composeResources/drawable"
MAX_WIDTH  = 720   # px — enough for any phone at 2x density
MAX_HEIGHT = 960   # px — keep portrait aspect
QUALITY    = 72    # JPEG 0-95; 70-75 is visually identical to 100 at small sizes

def compress(path: Path) -> tuple[int, int]:
    """Returns (before_kb, after_kb)."""
    before = path.stat().st_size // 1024

    img = Image.open(path)

    # Resize if larger than target (keep aspect ratio, never upscale)
    if img.width > MAX_WIDTH or img.height > MAX_HEIGHT:
        img.thumbnail((MAX_WIDTH, MAX_HEIGHT), Image.LANCZOS)

    # Strip EXIF / metadata, re-encode
    img = img.convert("RGB")   # drop alpha if any
    img.save(path, "JPEG", quality=QUALITY, optimize=True, progressive=True)

    after = path.stat().st_size // 1024
    return before, after


def main():
    files = sorted(DRAWABLE.glob("coach_*.jpg"))
    if not files:
        print(f"No images found in {DRAWABLE}")
        sys.exit(1)

    print(f"Compressing {len(files)} images in {DRAWABLE}")
    print(f"Target: max {MAX_WIDTH}x{MAX_HEIGHT}px, JPEG quality {QUALITY}")
    print()

    total_before = total_after = 0
    for f in files:
        before, after = compress(f)
        total_before += before
        total_after  += after
        saving = 100 * (1 - after / before) if before else 0
        print(f"  {f.name:<52} {before:>5} KB -> {after:>4} KB  (-{saving:.0f}%)")

    reduction = 100 * (1 - total_after / total_before) if total_before else 0
    print()
    print("-" * 70)
    print(f"Before : {total_before / 1024:.1f} MB")
    print(f"After  : {total_after  / 1024:.1f} MB")
    print(f"Saved  : {(total_before - total_after) / 1024:.1f} MB  (-{reduction:.0f}%)")
    print()
    print("Done. Run ./gradlew :ui:compileDebugKotlinAndroid to verify.")


if __name__ == "__main__":
    main()
