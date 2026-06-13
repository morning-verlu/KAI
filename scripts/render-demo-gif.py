#!/usr/bin/env python3
"""Render the KAI OS CLI demo GIF used by README and launch pages."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1000
HEIGHT = 560
PADDING = 30
LINE_HEIGHT = 24
FONT_SIZE = 18

BG = (16, 20, 24)
BAR = (24, 32, 41)
PANEL = (13, 17, 22)
LINE = (45, 56, 68)
TEXT = (245, 247, 251)
MUTED = (168, 179, 193)
MINT = (143, 240, 189)
CYAN = (120, 217, 255)
AMBER = (240, 184, 92)


SCENES = [
    (
        "$ build/install/kaios-cli/bin/kaios run \"analyze crypto market\"",
        [
            "run_id: run-112c2188",
            "success: true",
            "snapshot: .kaios/runs/run-112c2188.json",
            "",
            "validate:350c4677 accepted result from after executor",
            "syscall echo: validated:350c4677",
        ],
    ),
    (
        "$ build/install/kaios-cli/bin/kaios ps run-112c2188",
        [
            "RUN run-112c2188  workflow=default  success=true",
            "PID     AGENT         STATE       TOKENS    MEMORY    SYSCALLS  DURATION",
            "1       planner       SUCCEEDED   13        137b      1         9ms",
            "2       executor      SUCCEEDED   24        282b      1         0ms",
            "3       validator     SUCCEEDED   27        273b      1         0ms",
        ],
    ),
    (
        "$ build/install/kaios-cli/bin/kaios inspect run-112c2188",
        [
            "RUN run-112c2188",
            "workflow: default",
            "task: analyze crypto market",
            "success: true",
            "",
            "events:",
            "pid=1 agent=planner   SPAWNED  spawned 'planner'",
            "pid=1 agent=planner   TOOL_CALLED syscall echo -> ok",
            "pid=1 agent=planner   SUCCEEDED succeeded",
            "pid=2 agent=executor  TOOL_CALLED syscall mock-http -> ok",
            "pid=2 agent=executor  SUCCEEDED succeeded",
            "pid=3 agent=validator TOOL_CALLED syscall echo -> ok",
            "pid=3 agent=validator SUCCEEDED succeeded",
        ],
    ),
]


def font(size: int = FONT_SIZE, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Menlo.ttc",
        "/Library/Fonts/Menlo.ttf",
        "/System/Library/Fonts/SFNSMono.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size, index=1 if bold and path.suffix == ".ttc" else 0)
    return ImageFont.load_default()


MONO = font()
MONO_BOLD = font(bold=True)


def draw_window(command: str, output: list[str], reveal_lines: int, cursor: bool) -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(image)

    x0, y0 = 24, 24
    x1, y1 = WIDTH - 24, HEIGHT - 24
    draw.rounded_rectangle((x0, y0, x1, y1), radius=18, fill=PANEL, outline=LINE, width=2)
    draw.rounded_rectangle((x0, y0, x1, y0 + 52), radius=18, fill=BAR)
    draw.rectangle((x0, y0 + 34, x1, y0 + 52), fill=BAR)
    for index, color in enumerate(((255, 95, 87), (255, 189, 46), (40, 200, 64))):
        draw.ellipse((x0 + 22 + index * 24, y0 + 18, x0 + 34 + index * 24, y0 + 30), fill=color)

    draw.text((WIDTH // 2 - 82, y0 + 16), "kaios demo", font=MONO, fill=MUTED)
    draw.text((x0 + PADDING, y0 + 82), command, font=MONO_BOLD, fill=MINT)

    y = y0 + 122
    for line in output[:reveal_lines]:
        color = TEXT
        if "SUCCEEDED" in line or "success: true" in line or "ok" in line:
            color = MINT
        elif line.startswith("PID") or line.startswith("events") or line.startswith("RUN"):
            color = CYAN
        elif "syscall" in line or "snapshot" in line:
            color = AMBER
        draw.text((x0 + PADDING, y), line, font=MONO, fill=color)
        y += LINE_HEIGHT

    if cursor:
        draw.rectangle((x0 + PADDING, y + 3, x0 + PADDING + 10, y + 20), fill=MINT)

    draw.text((x0 + PADDING, y1 - 38), "Agent = Process  |  Workflow = Scheduler  |  Tool = Syscall", font=MONO, fill=MUTED)
    return image


def build_frames() -> list[Image.Image]:
    frames: list[Image.Image] = []
    for command, output in SCENES:
        for reveal in range(0, len(output) + 1):
            image = draw_window(command, output, reveal, cursor=reveal == len(output))
            frames.extend([image] * (5 if reveal == len(output) else 2))
    return frames


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="docs/assets/kaios-demo.gif", help="Output GIF path.")
    args = parser.parse_args()

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    frames = build_frames()
    frames[0].save(
        out,
        save_all=True,
        append_images=frames[1:],
        duration=95,
        loop=0,
        optimize=True,
    )
    print(f"wrote {out} ({out.stat().st_size} bytes, {len(frames)} frames)")


if __name__ == "__main__":
    main()
