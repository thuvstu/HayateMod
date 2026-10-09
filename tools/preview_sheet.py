#!/usr/bin/env python3
"""Stitch every HayateMod texture into one contact sheet.

This is a review aid, not part of the build: it walks the mod's texture
folders, draws each PNG at its native size inside a fixed cell and writes a
single scaled-up sheet so the whole art set can be eyeballed at once.

    python3 tools/preview_sheet.py out.png

Animated textures (a ``.png.mcmeta`` next to the file) are shown as their
first frame; the sheet only ever reads the first ``width * height`` pixels.
"""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "hayatemod" / "textures"

CELL = 64
GAP = 10
COLS = 6
SCALE = 2
LABEL = 0

# Folders in the order they should appear on the sheet.
GROUPS = ("item", "entity", "entity/equipment/humanoid", "entity/equipment/humanoid_leggings", "block")
EXTRA = ("assets/hayatemod/icon.png",)

# sheet background, cell background, label colour
BG = (18, 22, 28)
CELL_BG = (30, 36, 44)


def read_png(path: Path) -> tuple[int, int, list[list[tuple[int, int, int, int]]]]:
	"""Decode an 8-bit RGB/RGBA or indexed PNG. Good enough for our own files."""
	data = path.read_bytes()
	if data[:8] != b"\x89PNG\r\n\x1a\n":
		raise ValueError(f"{path} is not a PNG")

	pos, idat, palette, trns = 8, bytearray(), b"", b""
	width = height = depth = colour = interlace = 0
	while pos < len(data):
		(length, ctype), body = struct.unpack(">I4s", data[pos:pos + 8]), data[pos + 8:]
		chunk, pos = body[:length], pos + 12 + length
		if ctype == b"IHDR":
			width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
		elif ctype == b"PLTE":
			palette = chunk
		elif ctype == b"tRNS":
			trns = chunk
		elif ctype == b"IDAT":
			idat += chunk
		elif ctype == b"IEND":
			break

	if depth != 8 or interlace:
		raise ValueError(f"{path}: only 8-bit non-interlaced PNGs are supported")
	channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
	stride = width * channels

	raw = zlib.decompress(bytes(idat))
	rows, prev, offset = [], bytearray(stride), 0
	for _ in range(height):
		ftype = raw[offset]
		line = bytearray(raw[offset + 1:offset + 1 + stride])
		offset += 1 + stride
		for i in range(stride):  # undo the PNG filters
			a = line[i - channels] if i >= channels else 0
			b = prev[i]
			c = prev[i - channels] if i >= channels else 0
			if ftype == 1:
				line[i] = (line[i] + a) & 0xFF
			elif ftype == 2:
				line[i] = (line[i] + b) & 0xFF
			elif ftype == 3:
				line[i] = (line[i] + (a + b) // 2) & 0xFF
			elif ftype == 4:
				p = a + b - c
				pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
				line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
		rows.append(line)
		prev = line

	pixels = []
	for line in rows:
		row = []
		for x in range(width):
			vals = line[x * channels:(x + 1) * channels]
			if colour == 3:
				index = vals[0]
				rgb = palette[index * 3:index * 3 + 3]
				alpha = trns[index] if index < len(trns) else 255
				row.append((rgb[0], rgb[1], rgb[2], alpha))
			elif colour == 0:
				row.append((vals[0], vals[0], vals[0], 255))
			elif colour == 4:
				row.append((vals[0], vals[0], vals[0], 255))
			elif colour == 2:
				row.append((vals[0], vals[1], vals[2], 255))
			else:
				row.append((vals[0], vals[1], vals[2], vals[3]))
		pixels.append(row)
	return width, height, pixels


def write_png(path: Path, pixels: list[list[tuple[int, int, int]]]) -> None:
	height = len(pixels)
	width = len(pixels[0]) if height else 0
	raw = bytearray()
	for row in pixels:
		raw.append(0)
		raw += bytes(v for px in row for v in px)

	def chunk(tag: bytes, body: bytes) -> bytes:
		return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body))

	path.write_bytes(
		b"\x89PNG\r\n\x1a\n"
		+ chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(bytes(raw), 9))
		+ chunk(b"IEND", b""))


def collect() -> list[tuple[str, Path]]:
	found = []
	for group in GROUPS:
		directory = TEXTURES / group
		if not directory.is_dir():
			continue
		for png in sorted(directory.glob("*.png")):
			found.append((f"{group}/{png.name}", png))
	for relative in EXTRA:
		candidate = ROOT / "src" / "main" / "resources" / relative
		if candidate.is_file():
			found.append((relative.rsplit("/", 1)[-1], candidate))
	return found


def main(argv: list[str]) -> int:
	out = Path(argv[1]) if len(argv) > 1 else ROOT / "preview_sheet.png"

	entries = []
	for label, path in collect():
		try:
			entries.append((label, *read_png(path)))
		except ValueError as exc:
			print(f"  skipping {label}: {exc}")

	rows = (len(entries) + COLS - 1) // COLS
	sheet_w = COLS * CELL + (COLS + 1) * GAP
	sheet_h = rows * CELL + (rows + 1) * GAP
	sheet = [[BG for _ in range(sheet_w)] for _ in range(sheet_h)]

	for index, (label, width, height, pixels) in enumerate(entries):
		cx, cy = index % COLS, index // COLS
		ox = GAP + cx * (CELL + GAP)
		oy = GAP + cy * (CELL + GAP)
		for y in range(min(CELL, height)):
			for x in range(min(CELL, width)):
				r, g, b, a = pixels[y][x]
				px, py = ox + x, oy + y
				if py >= sheet_h or px >= sheet_w:
					continue
				if a == 0:
					sheet[py][px] = CELL_BG
				elif a == 255:
					sheet[py][px] = (r, g, b)
				else:
					old = sheet[py][px]
					f = a / 255
					sheet[py][px] = (int(old[0] * (1 - f) + r * f),
					                 int(old[1] * (1 - f) + g * f),
					                 int(old[2] * (1 - f) + b * f))

	# nearest-neighbour upscale so the pixels are actually visible
	big = [[px for px in row for _ in range(SCALE)] for row in sheet for _ in range(SCALE)]
	write_png(out, big)
	print(f"sheet: {sheet_w * SCALE} x {sheet_h * SCALE} textures: {len(entries)}")
	for label, width, height, _ in entries:
		print(f"  {label}: {width}x{height}")
	return 0


if __name__ == "__main__":
	raise SystemExit(main(sys.argv))
