#!/usr/bin/env python3
"""Generates the 16x16 textures (and the pack icon) for HayateMod.

Plain standard library only - no Pillow needed. Keeps the repository free of
binary blobs that have to be fetched from somewhere: run

    python3 tools/generate_textures.py

after changing a texture.
"""

import math
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "hayatemod"

# --------------------------------------------------------------------------- png


class Canvas:
    def __init__(self, size: int):
        self.size = size
        self.pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]

    def set(self, x: int, y: int, colour) -> None:
        if 0 <= x < self.size and 0 <= y < self.size:
            self.pixels[y][x] = colour

    def fill(self, colour) -> None:
        for y in range(self.size):
            for x in range(self.size):
                self.pixels[y][x] = colour

    def rect(self, x0: int, y0: int, x1: int, y1: int, colour) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, colour)

    def circle(self, cx: float, cy: float, r: float, colour) -> None:
        for y in range(self.size):
            for x in range(self.size):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    self.set(x, y, colour)

    def ring(self, cx: float, cy: float, r: float, width: float, colour) -> None:
        for y in range(self.size):
            for x in range(self.size):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r - width <= d <= r:
                    self.set(x, y, colour)

    def write_png(self, path: pathlib.Path) -> None:
        raw = bytearray()
        for row in self.pixels:
            raw.append(0)  # filter type 0
            for r, g, b, a in row:
                raw += bytes((r, g, b, a))

        def chunk(tag: bytes, data: bytes) -> bytes:
            out = struct.pack(">I", len(data)) + tag + data
            return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.size, self.size, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")

        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png)


def rgb(value: str, alpha: int = 255):
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


# ---------------------------------------------------------------------- palette

PALE = rgb("A8E6F0")       # pale gale cyan
MID = rgb("5FB8C9")        # gale cyan
DEEP = rgb("2E6E7E")       # dark gale
SHINE = rgb("E8FCFF")      # highlight
METAL = rgb("7A8A99")
METAL_LIGHT = rgb("9FB4C4")
METAL_DARK = rgb("46545F")
INK = rgb("16232B")
GOLD = rgb("E8C56A")
GOLD_DARK = rgb("9A7B2E")
FRUIT = rgb("6C4FB2")
FRUIT_LIGHT = rgb("9B7CE0")
LEAF = rgb("4FA86B")

# ----------------------------------------------------------------------- items


def gale_dust() -> Canvas:
    c = Canvas(16)
    # a small heap of powder
    for y in range(9, 15):
        span = min(6, (y - 8) + 2)
        for x in range(8 - span, 8 + span):
            c.set(x, y, PALE)
    for y in range(11, 15):
        for x in range(4, 12):
            if c.pixels[y][x] == PALE:
                c.set(x, y, MID)
    # sparkles
    for x, y in ((3, 7), (6, 5), (10, 6), (12, 9), (5, 12), (11, 13)):
        c.set(x, y, SHINE)
    c.set(4, 8, PALE)
    c.set(12, 8, PALE)
    return c


def gale_ingot() -> Canvas:
    c = Canvas(16)
    for y in range(5, 11):
        inset = 2 if y in (5, 10) else 0
        for x in range(2 + inset, 14 - inset):
            c.set(x, y, MID)
    for y in range(6, 10):
        for x in range(2, 14):
            c.set(x, y, METAL_LIGHT if y <= 7 else MID)
    for x in range(2, 14):
        c.set(x, 10, DEEP)
    for y in range(5, 11):
        c.set(13, y, DEEP)
    for x in range(4, 8):
        c.set(x, 7, SHINE)
    return c


def storm_fruit() -> Canvas:
    c = Canvas(16)
    c.circle(8, 10, 5, FRUIT)
    c.circle(8, 10, 4, FRUIT_LIGHT)
    c.circle(6, 8, 1.6, SHINE)
    c.set(8, 4, LEAF)
    c.set(9, 4, LEAF)
    c.set(10, 3, LEAF)
    c.set(7, 5, rgb("6B4A2A"))
    for x in range(6, 10):
        c.set(x, 6, FRUIT)
    return c


def gale_charm() -> Canvas:
    c = Canvas(16)
    c.ring(8, 8, 5.5, 1.2, GOLD_DARK)
    c.ring(8, 8, 5.0, 0.9, GOLD)
    c.circle(8, 8, 2.6, DEEP)
    c.circle(8, 8, 2.0, MID)
    c.set(7, 7, SHINE)
    c.set(8, 3, GOLD)
    c.set(7, 2, GOLD)
    return c


# ---------------------------------------------------------------------- blocks


def gale_block() -> Canvas:
    c = Canvas(16)
    c.fill(METAL)
    for y in range(16):
        for x in range(16):
            # diagonal wind streaks
            if (x + y) % 7 == 0:
                c.set(x, y, METAL_LIGHT)
            if (x - y) % 11 == 0:
                c.set(x, y, MID)
            if (x + y) % 7 == 3:
                c.set(x, y, METAL_DARK)
    c.rect(0, 0, 15, 0, METAL_LIGHT)
    c.rect(0, 0, 0, 15, METAL_LIGHT)
    c.rect(0, 15, 15, 15, METAL_DARK)
    c.rect(15, 0, 15, 15, METAL_DARK)
    c.set(4, 4, SHINE)
    c.set(11, 10, SHINE)
    return c


def lamp(lit: bool) -> Canvas:
    c = Canvas(16)
    frame = GOLD if lit else GOLD_DARK
    glass = SHINE if lit else rgb("3C5A63")
    core = rgb("FFE9A8") if lit else rgb("476C77")
    c.fill(INK)
    c.rect(1, 1, 14, 14, frame)
    c.rect(3, 3, 12, 12, glass)
    c.rect(5, 5, 10, 10, core)
    # window bars
    for i in range(3, 13):
        c.set(i, 8, frame)
        c.set(8, i, frame)
    if lit:
        for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
            c.set(x, y, SHINE)
    return c


def icon() -> Canvas:
    c = Canvas(128)
    for y in range(128):
        for x in range(128):
            t = (x + y) / 254
            r = int(22 + 30 * t)
            g = int(32 + 90 * t)
            b = int(48 + 110 * t)
            c.set(x, y, (r, g, b, 255))
    # three wind arcs
    for i, radius in enumerate((46, 34, 22)):
        colour = (168, 230, 240, 255) if i != 1 else (232, 252, 255, 255)
        for deg in range(0, 260, 1):
            rad = math.radians(deg)
            cx, cy = 64.0, 72.0
            for w in range(0, 5 if i != 1 else 7):
                rr = radius - w
                px = int(round(cx + rr * math.cos(rad)))
                py = int(round(cy - rr * math.sin(rad) * 0.72))
                c.set(px, py, colour)
    c.rect(60, 8, 67, 15, (232, 252, 255, 255))
    return c


def main() -> None:
    targets = {
        ASSETS / "textures" / "item" / "gale_dust.png": gale_dust(),
        ASSETS / "textures" / "item" / "gale_ingot.png": gale_ingot(),
        ASSETS / "textures" / "item" / "storm_fruit.png": storm_fruit(),
        ASSETS / "textures" / "item" / "gale_charm.png": gale_charm(),
        ASSETS / "textures" / "block" / "gale_block.png": gale_block(),
        ASSETS / "textures" / "block" / "gale_lamp_off.png": lamp(False),
        ASSETS / "textures" / "block" / "gale_lamp_on.png": lamp(True),
        ASSETS / "icon.png": icon(),
    }

    for path, canvas in targets.items():
        canvas.write_png(path)
        print("wrote", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
