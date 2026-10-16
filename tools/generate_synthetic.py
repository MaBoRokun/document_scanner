#!/usr/bin/env python3
"""Generates the synthetic fixtures used by tests and demos. No real documents, ever.

    pip install pillow
    python tools/generate_synthetic.py [output_dir]

Output (default: fixtures/synthetic):
  receipts/receipt_N.png     flat fake receipts (invented merchants and amounts)
  receipts/expected.json     ground truth for the receipt parser / OCR checks
  pages/page_N.png           a white "page" photographed on a dark desk, rotated and in perspective
  pages/corners.json         ground-truth page corners, normalized 0..1, clockwise from the top-left

Everything is seeded, so re-running produces identical files.
"""

import json
import random
import sys
from datetime import date, timedelta
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

MERCHANTS = ["Blue Bakery", "Fresh Market", "Corner Cafe", "Green Pharmacy", "Night Owl Books"]
ITEMS = ["Bread", "Milk", "Coffee", "Notebook", "Soap", "Apples", "Tea", "Pasta", "Pen", "Juice"]
CURRENCIES = [("EUR", "EUR"), ("USD", "$"), ("GBP", "GBP")]


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.load_default(size=size)


def money(minor: int) -> str:
    return f"{minor // 100}.{minor % 100:02d}"


def make_receipt(rng: random.Random, index: int) -> tuple[Image.Image, dict]:
    merchant = MERCHANTS[index % len(MERCHANTS)]
    currency, _symbol = CURRENCIES[index % len(CURRENCIES)]
    day = date(2026, 1, 1) + timedelta(days=rng.randint(0, 270))
    lines = [(rng.choice(ITEMS), rng.randint(99, 1999)) for _ in range(rng.randint(3, 7))]
    total = sum(price for _, price in lines)

    img = Image.new("RGB", (600, 900), "white")
    d = ImageDraw.Draw(img)
    y = 40
    d.text((300, y), merchant.upper(), font=font(36), fill="black", anchor="ma")
    y += 70
    d.text((300, y), day.isoformat(), font=font(24), fill="black", anchor="ma")
    y += 60
    for name, price in lines:
        d.text((40, y), name, font=font(26), fill="black")
        d.text((560, y), money(price), font=font(26), fill="black", anchor="ra")
        y += 44
    y += 20
    d.line((40, y, 560, y), fill="black", width=2)
    y += 20
    d.text((40, y), "TOTAL", font=font(32), fill="black")
    d.text((560, y), f"{currency} {money(total)}", font=font(32), fill="black", anchor="ra")
    truth = {
        "file": f"receipt_{index}.png",
        "merchant": merchant,
        "date": day.isoformat(),
        "totalMinor": total,
        "currency": currency,
        "items": [{"name": n, "priceMinor": p} for n, p in lines],
    }
    return img, truth


def solve(matrix: list[list[float]], vector: list[float]) -> list[float]:
    """Gaussian elimination for the 8x8 perspective system."""
    n = len(vector)
    m = [row[:] + [vector[i]] for i, row in enumerate(matrix)]
    for col in range(n):
        pivot = max(range(col, n), key=lambda r: abs(m[r][col]))
        m[col], m[pivot] = m[pivot], m[col]
        for r in range(n):
            if r != col:
                f = m[r][col] / m[col][col]
                m[r] = [a - f * b for a, b in zip(m[r], m[col])]
    return [m[i][n] / m[i][i] for i in range(n)]


def perspective_coeffs(dst: list[tuple[float, float]], src: list[tuple[float, float]]) -> list[float]:
    """Coefficients for Image.transform(PERSPECTIVE): maps output points (dst) back to source points (src)."""
    rows, vec = [], []
    for (x, y), (u, v) in zip(dst, src):
        rows.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        vec.append(u)
        rows.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        vec.append(v)
    return solve(rows, vec)


def make_page(rng: random.Random, index: int, size=(1280, 960)) -> tuple[Image.Image, list[list[float]]]:
    w, h = size
    page_w, page_h = 600, 800
    page = Image.new("RGB", (page_w, page_h), (250, 250, 245))
    d = ImageDraw.Draw(page)
    d.text((40, 40), f"SYNTHETIC PAGE {index}", font=font(36), fill=(30, 30, 30))
    for row in range(12):
        width = rng.randint(300, 520)
        d.rectangle((40, 120 + row * 52, 40 + width, 136 + row * 52), fill=(60, 60, 60))

    cx, cy = w * rng.uniform(0.45, 0.55), h * rng.uniform(0.45, 0.55)
    half_w, half_h = rng.uniform(0.22, 0.30) * w, rng.uniform(0.30, 0.38) * h
    jitter = lambda s: rng.uniform(-s, s)  # noqa: E731
    quad = [
        (cx - half_w + jitter(60), cy - half_h + jitter(40)),
        (cx + half_w + jitter(60), cy - half_h + jitter(40)),
        (cx + half_w + jitter(60), cy + half_h + jitter(40)),
        (cx - half_w + jitter(60), cy + half_h + jitter(40)),
    ]
    coeffs = perspective_coeffs(quad, [(0, 0), (page_w, 0), (page_w, page_h), (0, page_h)])
    desk = Image.new("RGB", size, (45, 55, 70))
    mask = Image.new("L", (page_w, page_h), 255)
    warped = page.transform(size, Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    warped_mask = mask.transform(size, Image.PERSPECTIVE, coeffs, Image.BILINEAR)
    desk.paste(warped, (0, 0), warped_mask)
    return desk, [[round(x / w, 5), round(y / h, 5)] for x, y in quad]


def main() -> None:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "fixtures/synthetic")
    (out / "receipts").mkdir(parents=True, exist_ok=True)
    (out / "pages").mkdir(parents=True, exist_ok=True)

    rng = random.Random(20261015)
    truths = []
    for i in range(5):
        img, truth = make_receipt(rng, i)
        img.save(out / "receipts" / f"receipt_{i}.png", optimize=True)
        truths.append(truth)
    (out / "receipts" / "expected.json").write_text(json.dumps(truths, indent=2) + "\n")

    corners = {}
    for i in range(8):
        img, quad = make_page(rng, i)
        img.save(out / "pages" / f"page_{i}.png", optimize=True)
        corners[f"page_{i}.png"] = quad
    (out / "pages" / "corners.json").write_text(json.dumps(corners, indent=2) + "\n")
    print(f"wrote fixtures to {out}")


if __name__ == "__main__":
    main()
