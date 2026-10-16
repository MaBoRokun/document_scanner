#!/usr/bin/env python3
"""Builds the README demo GIF from screenshots taken with `adb exec-out screencap -p`.

    pip install pillow
    python tools/make_demo_gif.py frames/ docs/media/demo.gif [width] [ms_per_frame]

Frames are sorted by file name, scaled to `width` (default 300 px) and looped.
"""

import sys
from pathlib import Path

from PIL import Image


def main() -> None:
    frames_dir, out = Path(sys.argv[1]), Path(sys.argv[2])
    width = int(sys.argv[3]) if len(sys.argv) > 3 else 300
    delay = int(sys.argv[4]) if len(sys.argv) > 4 else 1400
    images = []
    for path in sorted(frames_dir.glob("*.png")):
        img = Image.open(path).convert("RGB")
        images.append(img.resize((width, round(img.height * width / img.width)), Image.LANCZOS))
    if not images:
        sys.exit(f"no PNG frames in {frames_dir}")
    out.parent.mkdir(parents=True, exist_ok=True)
    quantized = [im.quantize(colors=128, method=Image.Quantize.MEDIANCUT) for im in images]
    quantized[0].save(out, save_all=True, append_images=quantized[1:], duration=delay, loop=0, optimize=True)
    print(f"wrote {out} ({len(images)} frames, {out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
