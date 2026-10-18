#!/usr/bin/env python3
"""Generate a placeholder dataset mimicking the real layout: <root>/zh, <root>/en."""
import argparse, random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

PALETTES = [
    ("FB01 Aurora Dawn", [(30, 20, 80), (80, 40, 120), (160, 60, 130), (240, 120, 90)]),
    ("FB02 Neon Alley", [(10, 15, 35), (40, 20, 70), (180, 30, 120), (255, 140, 0)]),
    ("FB03 Snow Fox", [(200, 210, 230), (150, 170, 210), (90, 120, 180), (50, 70, 120)]),
    ("FB04 Bamboo Dream", [(15, 60, 40), (40, 110, 60), (90, 160, 90), (170, 210, 140)]),
    ("FB05 Silent Sea", [(10, 40, 70), (20, 80, 110), (50, 130, 150), (120, 190, 190)]),
    ("FB06 Crimson Chapel", [(60, 10, 10), (110, 20, 20), (170, 40, 30), (230, 100, 50)]),
    ("FB07 Star Ocean", [(5, 10, 40), (20, 30, 80), (60, 60, 140), (130, 110, 200)]),
    ("FB08 Amber Field", [(80, 50, 10), (140, 100, 20), (200, 160, 50), (245, 220, 120)]),
]

ZH = {
    "FB01 Aurora Dawn": "极光黎明",
    "FB02 Neon Alley": "霓虹小巷",
    "FB03 Snow Fox": "雪狐",
    "FB04 Bamboo Dream": "竹梦",
    "FB05 Silent Sea": "静海",
    "FB06 Crimson Chapel": "赤红教堂",
    "FB07 Star Ocean": "星海",
    "FB08 Amber Field": "琥珀原野",
}

# which side each gallery exists on
SHARED = [0, 1, 2]
ZH_ONLY = [3, 4]
EN_ONLY = [5, 6, 7]

ASPECTS = [(2, 3), (3, 4), (1, 1), (4, 3), (3, 5), (9, 16)]


def make_image(path: Path, w: int, h: int, colors, label: str, index: int):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    # vertical gradient
    for y in range(h):
        t = y / max(h - 1, 1)
        c0 = colors[index % len(colors)]
        c1 = colors[(index + 1) % len(colors)]
        # simple 2-color blend, shifting pair every 3 steps
        if (index // 3) % 2:
            c0, c1 = c1, c0
        col = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=col)
    # fake "content" blocks
    rnd = random.Random(f"{label}-{index}")
    for _ in range(6):
        x0 = rnd.randint(0, w - w // 4)
        y0 = rnd.randint(0, h - h // 5)
        bw = rnd.randint(w // 8, w // 3)
        bh = rnd.randint(h // 10, h // 4)
        alpha = rnd.randint(20, 70)
        overlay = tuple(min(255, c + alpha) for c in col)
        d.rectangle([x0, y0, x0 + bw, y0 + bh], fill=overlay)
    try:
        font = ImageFont.truetype("arial.ttf", max(18, h // 12))
    except OSError:
        font = ImageFont.load_default()
    text = f"{label}\n#{index:03d}"
    d.multiline_text((16, 16), text, fill=(255, 255, 255), font=font, stroke_width=2, stroke_fill=(0, 0, 0))
    img.save(path, "PNG")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="output root (will contain zh/ and en/)")
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    random.seed(args.seed)

    root = Path(args.out)
    zh = root / "zh"
    en = root / "en"

    for idx, (name, colors) in enumerate(PALETTES):
        sides = []
        if idx in SHARED or idx in ZH_ONLY:
            sides.append(zh)
        if idx in SHARED or idx in EN_ONLY:
            sides.append(en)
        n = random.randint(5, 9)
        gdir_name = name  # e.g. "FB01 Aurora Dawn"
        for side in sides:
            g = side / gdir_name
            g.mkdir(parents=True, exist_ok=True)
            for i in range(n):
                aw, ah = random.choice(ASPECTS)
                w = random.randint(360, 640)
                h = int(w * ah / aw)
                # some galleries start numbering at 000
                start = 0 if idx % 2 == 0 else 1
                make_image(g / f"{start + i:03d}.png", w, h, colors, gdir_name, start + i)

    # zh titles map for the packer
    import json
    (root / "titles_zh.json").write_text(
        json.dumps(ZH, ensure_ascii=False, indent=2), encoding="utf-8")
    print("placeholder dataset written to", root)


if __name__ == "__main__":
    main()
