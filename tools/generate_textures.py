#!/usr/bin/env python3
"""Generates the pixel art textures (and the pack icon) for HayateMod.

Plain standard library only - no Pillow needed. Keeps the repository free of
binary blobs that have to be fetched from somewhere: run

    python3 tools/generate_textures.py               # write the png files
    python3 tools/generate_textures.py --preview     # ascii preview instead

after changing a texture.

The sprites are built from a couple of tiny primitives (mask -> fill -> shade
-> outline) so every texture stays deterministic and diffable.
"""

import json
import math
import pathlib
import random
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "hayatemod"

# --------------------------------------------------------------------------- png


class Canvas:
	"""A width x height pixel buffer.

	A canvas taller than it is wide is written as an animation strip: the game
	plays it back frame by frame when a ``.mcmeta`` sits next to the png.
	"""

	def __init__(self, width: int, height: int | None = None):
		self.width = width
		self.height = height or width
		self.pixels = [[(0, 0, 0, 0)] * self.width for _ in range(self.height)]

	@property
	def size(self) -> int:
		return self.width

	@property
	def frames(self) -> int:
		return max(1, self.height // self.width)

	def set(self, x: int, y: int, colour) -> None:
		if 0 <= x < self.width and 0 <= y < self.height:
			self.pixels[y][x] = colour

	def get(self, x: int, y: int):
		if 0 <= x < self.width and 0 <= y < self.height:
			return self.pixels[y][x]
		return (0, 0, 0, 0)

	def fill(self, colour) -> None:
		for y in range(self.height):
			for x in range(self.width):
				self.pixels[y][x] = colour

	def rect(self, x0: int, y0: int, x1: int, y1: int, colour) -> None:
		for y in range(y0, y1 + 1):
			for x in range(x0, x1 + 1):
				self.set(x, y, colour)

	def hline(self, x0: int, x1: int, y: int, colour) -> None:
		self.rect(x0, y, x1, y, colour)

	def vline(self, x: int, y0: int, y1: int, colour) -> None:
		self.rect(x, y0, x, y1, colour)

	def circle(self, cx: float, cy: float, r: float, colour) -> None:
		for y in range(self.height):
			for x in range(self.width):
				if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
					self.set(x, y, colour)

	def ring(self, cx: float, cy: float, r: float, width: float, colour) -> None:
		for y in range(self.height):
			for x in range(self.width):
				d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
				if r - width <= d <= r:
					self.set(x, y, colour)

	def line(self, x0: int, y0: int, x1: int, y1: int, colour) -> None:
		steps = max(abs(x1 - x0), abs(y1 - y0))
		for i in range(steps + 1):
			t = i / steps if steps else 0
			self.set(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), colour)

	def blit(self, other: "Canvas", ox: int, oy: int) -> None:
		for y in range(other.height):
			for x in range(other.width):
				r, g, b, a = other.pixels[y][x]
				if a:
					self.set(ox + x, oy + y, other.pixels[y][x])

	def opaque(self, x: int, y: int) -> bool:
		return self.get(x, y)[3] > 0

	def is_colour(self, x: int, y: int, colour) -> bool:
		return self.get(x, y) == colour

	# -- post processing ------------------------------------------------------

	def outline(self, colour) -> None:
		"""Adds a one pixel outline around every drawn shape (alpha aware)."""
		additions = []
		for y in range(self.height):
			for x in range(self.width):
				if self.opaque(x, y):
					continue
				for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
					nx, ny = x + dx, y + dy
					if 0 <= nx < self.width and 0 <= ny < self.height and self.opaque(nx, ny):
						additions.append((x, y))
						break
		for x, y in additions:
			self.set(x, y, colour)

	def shade(self, condition, colour) -> None:
		"""Re-colours every opaque pixel for which condition(x, y) is true."""
		for y in range(self.height):
			for x in range(self.width):
				if self.opaque(x, y) and condition(x, y):
					self.set(x, y, colour)

	def speckle(self, colour, every: int, seed: int) -> None:
		rng = random.Random(seed)
		for y in range(self.height):
			for x in range(self.width):
				if rng.randrange(every) == 0:
					self.set(x, y, colour)

	def preview(self) -> str:
		ramp = " .:-=+*#%@"
		rows = []
		for row in self.pixels:
			line = ""
			for r, g, b, a in row:
				if a == 0:
					line += " "
				else:
					lum = (r * 299 + g * 587 + b * 114) // 1000
					line += ramp[min(len(ramp) - 1, lum * len(ramp) // 256)]
			rows.append(line)
		return "\n".join(rows)

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
		png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0))
		png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
		png += chunk(b"IEND", b"")

		path.parent.mkdir(parents=True, exist_ok=True)
		path.write_bytes(png)


def rgb(value: str, alpha: int = 255):
	return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def mix(a, b, t: float):
	return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


# ---------------------------------------------------------------------- palette

SHINE = rgb("E8FCFF")         # specular white
PALE = rgb("B9EFF7")          # pale gale cyan
MID = rgb("6FCBD9")           # gale cyan
DEEP = rgb("2E6E7E")          # dark gale
OUTLINE = rgb("132A33")
METAL = rgb("7A8A99")
METAL_LIGHT = rgb("A9BCC9")
METAL_DARK = rgb("46545F")
METAL_EDGE = rgb("2B343B")
GOLD = rgb("E8C56A")
GOLD_DARK = rgb("9A7B2E")
FRUIT = rgb("6C4FB2")
FRUIT_LIGHT = rgb("9B7CE0")
FRUIT_DARK = rgb("3D2B70")
LEAF = rgb("4FA86B")
STEM = rgb("6B4A2A")
LEATHER = rgb("6B4B2E")
LEATHER_DARK = rgb("3F2B19")
STONE = rgb("8A8A8E")
STONE_DARK = rgb("6E6E73")
STONE_LIGHT = rgb("A2A2A7")
DEEPSLATE = rgb("4A4A52")
DEEPSLATE_DARK = rgb("33333A")
DEEPSLATE_LIGHT = rgb("5B5B65")
CRYSTAL = rgb("7FE3F0")
CRYSTAL_DARK = rgb("1F4E59")

# ----------------------------------------------------------------------- items


def gale_dust() -> Canvas:
	"""A little heap of gale dust with a couple of sparkles."""
	c = Canvas(16)
	# mound: parabolic profile, flat-ish bottom
	for x in range(3, 13):
		height = 4.6 - ((x - 7.5) ** 2) / 5.0
		top = int(round(14 - max(1.0, height)))
		for y in range(top, 15):
			c.set(x, y, PALE)
	# body shading
	c.shade(lambda x, y: y >= 13, MID)
	c.shade(lambda x, y: y >= 14, DEEP)
	# sparkles on and above the heap
	for x, y in ((4, 8), (7, 6), (11, 9), (3, 11), (12, 12), (9, 12)):
		c.set(x, y, SHINE)
	c.set(6, 9, SHINE)
	c.set(10, 7, PALE)
	c.outline(DEEP)
	return c


def gale_ingot() -> Canvas:
	"""A bevelled metal ingot, as seen in the inventory."""
	c = Canvas(16)

	def inside(x: int, y: int) -> bool:
		if not 5 <= y <= 10:
			return False
		inset = {5: 4, 6: 3, 7: 2, 8: 2, 9: 3, 10: 4}[y]
		return inset <= x <= 15 - inset

	for y in range(16):
		for x in range(16):
			if inside(x, y):
				c.set(x, y, METAL)
	# top face highlight, bottom face shadow
	c.shade(lambda x, y: y in (5, 6), METAL_LIGHT)
	c.shade(lambda x, y: y == 10, METAL_DARK)
	c.shade(lambda x, y: x >= 12 and y >= 8, METAL_DARK)
	# diagonal shine
	c.line(5, 9, 8, 6, SHINE)
	c.set(6, 8, METAL_LIGHT)
	# a gale tinted rune in the middle
	c.set(9, 8, MID)
	c.set(10, 8, MID)
	c.set(9, 9, DEEP)
	c.outline(METAL_EDGE)
	return c


def storm_fruit() -> Canvas:
	"""A storm coloured fruit with a stem and a leaf."""
	c = Canvas(16)
	c.circle(8, 10, 5.2, FRUIT_LIGHT)
	# radial shading: bright towards the upper left
	for y in range(16):
		for x in range(16):
			if not c.opaque(x, y):
				continue
			d = math.hypot(x - 5.5, y - 7.0) / 8.0
			c.set(x, y, mix(FRUIT_LIGHT, FRUIT_DARK, min(1.0, d)))
	c.circle(6.2, 7.4, 1.7, SHINE)
	c.set(7, 6, SHINE)
	# stem + leaf
	c.set(8, 4, STEM)
	c.set(8, 5, STEM)
	c.set(9, 3, LEAF)
	c.set(10, 3, LEAF)
	c.set(10, 4, LEAF)
	c.set(11, 4, LEAF)
	c.set(9, 4, mix(LEAF, SHINE, 0.4))
	c.outline(FRUIT_DARK)
	return c


def charm_gem() -> Canvas:
	"""Facet texture for the 3D gale charm."""
	c = Canvas(16)
	c.fill(MID)
	c.shade(lambda x, y: x + y <= 14, PALE)
	c.shade(lambda x, y: x + y >= 20, DEEP)
	c.line(8, 0, 8, 15, DEEP)
	c.line(0, 8, 15, 8, DEEP)
	c.rect(0, 0, 15, 0, PALE)
	c.rect(0, 0, 0, 15, PALE)
	c.rect(0, 15, 15, 15, DEEP)
	c.rect(15, 0, 15, 15, DEEP)
	c.set(5, 4, SHINE)
	c.set(4, 5, SHINE)
	c.set(5, 5, SHINE)
	return c


def charm_cord() -> Canvas:
	"""Braided leather cord, wrapped around the top of the charm."""
	c = Canvas(16)
	c.fill(LEATHER)
	for y in range(0, 16, 3):
		c.hline(0, 15, y, LEATHER_DARK)
	for y in range(1, 16, 3):
		c.hline(0, 15, y, mix(LEATHER, SHINE, 0.25))
	return c


def gale_feather() -> Canvas:
	"""A wind tinted feather: curved spine with barbs on both sides."""
	c = Canvas(16)
	# spine, slightly curved
	for step in range(13):
		t = step / 12
		x = int(round(3 + 9 * t))
		y = int(round(13 - 10 * t + 1.6 * math.sin(t * math.pi)))
		c.set(x, y, PALE)
		c.set(x, y - 1, SHINE)
	# barbs, perpendicular to the spine
	for step in range(3, 13):
		t = step / 12
		x = int(round(3 + 9 * t))
		y = int(round(13 - 10 * t + 1.6 * math.sin(t * math.pi)))
		length = int(round(1 + 3 * math.sin(t * math.pi)))
		for offset in range(1, length + 1):
			c.set(x - offset, y - offset // 2, MID)
			c.set(x + offset, y + offset // 2 + 1, PALE if offset % 2 else MID)
	# glowing tip
	c.set(11, 3, SHINE)
	c.set(12, 2, PALE)
	c.set(12, 3, MID)
	c.outline(DEEP)
	return c


def gale_orb() -> Canvas:
	"""A sphere of compressed wind with a specular highlight."""
	c = Canvas(16)
	c.circle(8, 8, 5.4, MID)
	for y in range(16):
		for x in range(16):
			if not c.opaque(x, y):
				continue
			d = math.hypot(x - 5.8, y - 5.8) / 8.0
			c.set(x, y, mix(SHINE, DEEP, min(1.0, 0.15 + d * 1.1)))
	c.circle(6.4, 6.4, 1.6, SHINE)
	c.set(5, 5, rgb("FFFFFF"))
	# a rim of swirling wind
	for deg in range(0, 360, 30):
		rad = math.radians(deg)
		px = int(round(8 + 6.6 * math.cos(rad)))
		py = int(round(8 + 6.6 * math.sin(rad)))
		c.set(px, py, PALE)
	c.outline(DEEP)
	return c


def gale_blade() -> Canvas:
	"""A tapered gale blade with a gold guard and a wrapped handle."""
	c = Canvas(16)
	# the blade as a tapered quad: centre line (4, 12) -> (12, 4),
	# width measured along the perpendicular (1, 1)
	def inside(point, polygon) -> bool:
		x, y = point
		hits = 0
		for index, (x0, y0) in enumerate(polygon):
			x1, y1 = polygon[(index + 1) % len(polygon)]
			if (y0 > y) != (y1 > y) and x < x0 + (y - y0) / (y1 - y0) * (x1 - x0):
				hits += 1
		return hits % 2 == 1

	polygon = [(2.45, 10.45), (11.43, 3.43), (12.57, 4.57), (5.55, 13.55)]
	spine = (2.45 + 10.45 + 5.55 + 13.55) / 2  # (x + y) of the blade centre line
	for y in range(16):
		for x in range(16):
			if not inside((x + 0.5, y + 0.5), polygon):
				continue
			# distance from the blade centre, negative on the cutting edge
			offset = (x + 0.5 + y + 0.5) - spine
			if offset < -0.8:
				c.set(x, y, SHINE)
			elif offset > 1.4:
				c.set(x, y, METAL_DARK)
			else:
				c.set(x, y, METAL_LIGHT)
	# wind tinted edge
	for x, y in ((10, 2), (11, 2), (9, 3), (12, 1)):
		c.set(x, y, PALE)
	# guard, drawn across the blade
	for k in range(-3, 4):
		c.set(int(round(4 + k * 0.71)), int(round(12 + k * 0.71)), GOLD)
		c.set(int(round(4 + k * 0.71)) + 1, int(round(12 + k * 0.71)) + 1, GOLD_DARK)
	# handle continuing past the guard
	for step in range(1, 4):
		x, y = 4 - step, 12 + step
		c.set(x, y, LEATHER)
		c.set(x + 1, y, LEATHER_DARK)
	c.set(2, 14, LEATHER)
	c.set(1, 15, GOLD)
	c.outline(METAL_EDGE)
	return c


def gale_staff() -> Canvas:
	"""A staff: leather wrapped shaft holding a gale gem."""
	c = Canvas(16)
	# shaft
	c.rect(7, 5, 8, 15, LEATHER)
	c.vline(7, 5, 15, LEATHER_DARK)
	c.vline(8, 5, 15, LEATHER)
	for y in range(7, 15, 3):
		c.rect(6, y, 9, y, GOLD_DARK)
	# prongs holding the gem
	for x, y in ((6, 5), (9, 5), (6, 4), (9, 4)):
		c.set(x, y, GOLD_DARK)
	# gem: a diamond shape
	for y in range(1, 6):
		for x in range(4, 12):
			if abs(x - 7.5) + abs(y - 3) <= 3:
				t = (x - 4) / 8 + (y - 1) / 5
				c.set(x, y, mix(CRYSTAL, DEEP, min(1.0, t * 0.55)))
	c.set(6, 2, SHINE)
	c.set(6, 3, SHINE)
	c.outline(METAL_EDGE)
	return c


def greater_gale_charm() -> Canvas:
	"""An upgraded charm: a heavy ring with a bigger gem and two wind wings."""
	c = Canvas(16)
	c.ring(8, 8, 6.2, 1.6, GOLD_DARK)
	c.ring(8, 8, 5.6, 1.0, GOLD)
	c.circle(8, 8, 3.2, DEEP)
	for y in range(16):
		for x in range(16):
			if c.get(x, y) == DEEP and math.hypot(x - 8, y - 8) <= 3.2:
				t = math.hypot(x - 6.5, y - 6.5) / 6
				c.set(x, y, mix(CRYSTAL, DEEP, min(1.0, t)))
	c.set(7, 6, SHINE)
	c.set(6, 7, SHINE)
	# wings: two short strokes either side of the ring
	for offset in range(1, 4):
		c.set(1 + offset, 8 - offset, PALE)
		c.set(14 - offset, 8 - offset, PALE)
	c.set(2, 6, SHINE)
	c.set(13, 6, SHINE)
	c.outline(rgb("5A4218"))
	return c


# ---------------------------------------------------------------------- blocks


def gale_block() -> Canvas:
	"""A riveted metal panel: bevel, four rivets and a wind sheen."""
	c = Canvas(16)
	c.fill(METAL)
	c.speckle(METAL_DARK, 11, seed=7)
	c.speckle(METAL_LIGHT, 13, seed=11)
	# bevelled border
	c.hline(0, 15, 0, METAL_LIGHT)
	c.vline(0, 0, 15, METAL_LIGHT)
	c.hline(0, 15, 15, METAL_DARK)
	c.vline(15, 0, 15, METAL_DARK)
	c.rect(1, 1, 14, 14, METAL)
	c.rect(2, 2, 13, 13, mix(METAL, METAL_LIGHT, 0.25))
	# wind streaks
	for i in range(-16, 16, 6):
		c.line(i, 15, i + 15, 0, mix(METAL, METAL_LIGHT, 0.55))
	# rivets
	for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
		c.rect(cx - 1, cy - 1, cx, cy, METAL_DARK)
		c.set(cx, cy, SHINE)
		c.set(cx - 1, cy, METAL_LIGHT)
		c.set(cx, cy - 1, METAL_LIGHT)
	return c


def lamp_frame() -> Canvas:
	"""Cut-out cage of the gale lamp; the middle is transparent."""
	c = Canvas(16)
	c.rect(0, 0, 15, 15, METAL_DARK)
	c.rect(1, 1, 14, 14, METAL)
	c.rect(2, 2, 13, 13, METAL_LIGHT)
	# punch the window out: everything from 3..12 becomes transparent
	for y in range(3, 13):
		for x in range(3, 13):
			c.set(x, y, (0, 0, 0, 0))
	# corner braces so the cage reads as a solid frame
	for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
		c.set(cx, cy, METAL_LIGHT)
	for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (0, 0), (15, 0), (0, 15), (15, 15)):
		c.set(x, y, METAL_EDGE)
	return c


def _core_frame(body, inner, edge, dots) -> Canvas:
	c = Canvas(16)
	c.fill(body)
	c.shade(lambda x, y: (x + y) % 5 == 0, inner)
	c.rect(6, 6, 9, 9, inner)
	c.rect(7, 7, 8, 8, SHINE)
	c.hline(0, 15, 0, edge)
	c.hline(0, 15, 15, edge)
	c.vline(0, 0, 15, edge)
	c.vline(15, 0, 15, edge)
	for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
		c.set(x, y, dots)
	return c


def lamp_core(lit: bool) -> Canvas:
	"""The core inside the cage - a three frame pulse when the lamp is lit."""
	if not lit:
		return _core_frame(rgb("3C5A63"), rgb("476C77"), rgb("22363C"), rgb("4A6670"))

	strip = Canvas(16, 16 * 3)
	phases = (0.0, 0.5, 1.0)
	for index, phase in enumerate(phases):
		frame = _core_frame(
			mix(CRYSTAL, SHINE, 0.10 + phase * 0.55),
			mix(PALE, SHINE, 0.15 + phase * 0.55),
			DEEP,
			mix(CRYSTAL, SHINE, 0.6),
		)
		strip.blit(frame, 0, index * 16)
	return strip


def ore(base, light, dark, speck: str, shadow: str) -> Canvas:
	"""Stone-ish base with irregular gale crystal clumps."""
	c = Canvas(16)
	shadow_colour = rgb(shadow)
	c.fill(base)
	c.speckle(light, 7, seed=3)
	c.speckle(dark, 9, seed=5)
	c.speckle(base, 5, seed=9)

	clumps = ((3, 4), (10, 3), (7, 9), (12, 11), (4, 12))
	for index, (cx, cy) in enumerate(clumps):
		rng = random.Random(index)
		# irregular blob: a plus shape with randomly missing corners
		cells = [(0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)]
		if rng.random() < 0.7:
			cells.append((-1, -1))
		if rng.random() < 0.5:
			cells.append((1, 1))
		if rng.random() < 0.4:
			cells.append((-1, 1))
		if rng.random() < 0.4:
			cells.append((1, -1))
		for dx, dy in cells:
			c.set(cx + dx, cy + dy, shadow_colour)
		c.set(cx, cy, rgb(speck))
		if rng.random() < 0.8:
			c.set(cx - 1, cy - 1, rgb("CFF7FF"))
	return c


# ------------------------------------------------------------------------ icon


def icon() -> Canvas:
	c = Canvas(128)
	# background gradient
	for y in range(128):
		for x in range(128):
			t = (x + y) / 254
			c.set(x, y, (int(18 + 26 * t), int(28 + 86 * t), int(46 + 118 * t), 255))
	# soft vignette
	for y in range(128):
		for x in range(128):
			d = math.hypot(x - 64, y - 64) / 90
			if d > 1:
				c.set(x, y, mix(c.get(x, y), (10, 16, 24, 255), min(1.0, (d - 1) * 2)))
	# three wind arcs
	for index, radius in enumerate((46, 34, 22)):
		colour = (SHINE if index == 1 else PALE)[:3] + (255,)
		width = 7 if index == 1 else 5
		for deg in range(-20, 250):
			rad = math.radians(deg)
			for w in range(width):
				rr = radius - w
				px = int(round(64 + rr * math.cos(rad)))
				py = int(round(70 - rr * math.sin(rad) * 0.72))
				c.set(px, py, colour)
	# the gale gem in the middle
	for y in range(52, 84):
		for x in range(52, 84):
			if abs(x - 68) + abs(y - 68) <= 15:
				t = (x - 52) / 32 + (y - 52) / 32
				c.set(x, y, mix(CRYSTAL, DEEP, min(1.0, t * 0.6)))
	for x, y in ((62, 60), (63, 61), (62, 61)):
		c.set(x, y, (255, 255, 255, 255))
	# border
	for i in range(128):
		for j in (0, 1, 126, 127):
			c.set(i, j, (10, 16, 24, 255))
			c.set(j, i, (10, 16, 24, 255))
	return c


# ------------------------------------------------------------------------ main


def targets():
	return {
		ASSETS / "textures" / "item" / "gale_dust.png": gale_dust(),
		ASSETS / "textures" / "item" / "gale_ingot.png": gale_ingot(),
		ASSETS / "textures" / "item" / "storm_fruit.png": storm_fruit(),
		ASSETS / "textures" / "item" / "gale_charm_gem.png": charm_gem(),
		ASSETS / "textures" / "item" / "gale_charm_cord.png": charm_cord(),
		ASSETS / "textures" / "item" / "gale_feather.png": gale_feather(),
		ASSETS / "textures" / "item" / "gale_orb.png": gale_orb(),
		ASSETS / "textures" / "item" / "gale_blade.png": gale_blade(),
		ASSETS / "textures" / "item" / "gale_staff.png": gale_staff(),
		ASSETS / "textures" / "item" / "greater_gale_charm.png": greater_gale_charm(),
		ASSETS / "textures" / "block" / "gale_block.png": gale_block(),
		ASSETS / "textures" / "block" / "gale_lamp_frame.png": lamp_frame(),
		ASSETS / "textures" / "block" / "gale_lamp_core_off.png": lamp_core(False),
		ASSETS / "textures" / "block" / "gale_lamp_core_on.png": lamp_core(True),
		ASSETS / "textures" / "block" / "gale_ore.png": ore(STONE, STONE_LIGHT, STONE_DARK, "7FE3F0", "1F4E59"),
		ASSETS / "textures" / "block" / "deepslate_gale_ore.png":
			ore(DEEPSLATE, DEEPSLATE_LIGHT, DEEPSLATE_DARK, "7FE3F0", "17131A"),
		ASSETS / "icon.png": icon(),
	}


def main() -> None:
	if "--preview" in sys.argv:
		for path, canvas in targets().items():
			print("=" * 20, path.name)
			print(canvas.preview())
		return

	for path, canvas in targets().items():
		canvas.write_png(path)
		print("wrote", path.relative_to(ROOT))
		if canvas.frames > 1:
			meta = path.with_suffix(".png.mcmeta")
			meta.write_text(json.dumps(
				{"animation": {"frametime": 8, "frames": list(range(canvas.frames))}},
				indent="\t") + "\n")
			print("wrote", meta.relative_to(ROOT))


if __name__ == "__main__":
	main()
