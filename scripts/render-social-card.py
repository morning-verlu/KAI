#!/usr/bin/env python3
"""Render the KAI OS social preview PNG from the current product message."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1200
HEIGHT = 630

BG = (16, 20, 24)
PANEL = (20, 27, 34)
INNER = (24, 34, 44)
COMMAND_BG = (13, 17, 22)
LINE = (45, 56, 68)
TEXT = (245, 247, 251)
MUTED = (168, 179, 193)
MINT = (143, 240, 189)
CYAN = (120, 217, 255)
AMBER = (240, 184, 92)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return ImageFont.truetype(candidate, size=size)
    return ImageFont.load_default()


def mono(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Menlo.ttc",
        "/Library/Fonts/Menlo.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size, index=1 if bold and path.suffix == ".ttc" else 0)
    return ImageFont.load_default()


def draw_card(draw: ImageDraw.ImageDraw, x: int, y: int, title: str, subtitle: str, color: tuple[int, int, int]) -> None:
    draw.rounded_rectangle((x, y, x + 224, y + 92), radius=10, fill=INNER, outline=LINE, width=1)
    draw.text((x + 24, y + 22), title, font=font(24, bold=True), fill=TEXT)
    draw.text((x + 24, y + 52), subtitle, font=mono(18), fill=color)


def render(out: Path) -> None:
    image = Image.new("RGB", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(image)

    draw.rounded_rectangle((56, 56, 1144, 574), radius=28, fill=PANEL, outline=LINE, width=2)
    draw.polygon(
        [(104, 102), (146, 102), (146, 170), (210, 102), (264, 102), (188, 184), (270, 278), (211, 278), (146, 202), (146, 278), (104, 278)],
        fill=MINT,
    )

    draw.text((296, 88), "KAI OS", font=font(68, bold=True), fill=TEXT)
    draw.text((302, 184), "Local-first Evidence OS for agents", font=mono(24), fill=MUTED)

    draw_card(draw, 104, 316, "Trace", "Process", MINT)
    draw_card(draw, 356, 316, "Capsule", "Replay", CYAN)
    draw_card(draw, 608, 316, "Syscall", "Audit", AMBER)
    draw_card(draw, 860, 316, "CI Gate", "Baseline", MINT)

    draw.rounded_rectangle((104, 454, 1096, 522), radius=10, fill=COMMAND_BG, outline=LINE, width=1)
    draw.text((130, 480), "$ kaios tour && kaios evidence --summary", font=mono(22), fill=MINT)

    draw.text((104, 536), "github.com/morning-verlu/KAI", font=mono(22), fill=MUTED)
    draw.text((748, 536), "morning-verlu.github.io/KAI", font=mono(20), fill=CYAN)

    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    print(f"wrote {out} ({out.stat().st_size} bytes)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="docs/assets/kaios-social-card.png")
    args = parser.parse_args()
    render(Path(args.out))


if __name__ == "__main__":
    main()
