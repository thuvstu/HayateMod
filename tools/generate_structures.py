#!/usr/bin/env python3
"""Write the mod's structure NBT files.

Jigsaw structures are data driven - the shape of the build is the only part that
needs a binary file, and those live in ``data/<ns>/structures/**.nbt`` as gzipped
NBT. Authoring them by hand in a structure block is nicer for big builds, but a
small ruin is easier to describe in code, and doing it in code keeps the file in
git reviewable (re-running this script must not change a single byte).

The format is plain enough to write with the standard library:

    {
        size:        [3 ints]        the bounding box
        DataVersion: int             from net.minecraft.SharedConstants#WORLD_VERSION
        palette:     [block states]  {"Name": id, "Properties": {...}}
        blocks:      [entries]       {"pos": [3 ints], "state": index, "nbt": {...}}
        entities:    [entries]       {"blockPos": [3 ints], "pos": [3 doubles], "nbt": {...}}
    }

Run with no arguments to (re)write every file, or with ``--list`` to print what
it would do.
"""

from __future__ import annotations

import gzip
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src" / "main" / "resources" / "data" / "hayatemod"

# net.minecraft.SharedConstants#WORLD_VERSION for 26.3, read out of the real jar
# by .github/workflows/probe.yml (javap -constants).
DATA_VERSION = 5023

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


def _name(tag: int) -> str:
	"""Every payload in this script is named, so the name is written unprefixed."""
	return ""


def payload(tag: int, value) -> bytes:
	if tag == TAG_BYTE:
		return struct.pack(">b", value)
	if tag == TAG_SHORT:
		return struct.pack(">h", value)
	if tag == TAG_INT:
		return struct.pack(">i", value)
	if tag == TAG_LONG:
		return struct.pack(">q", value)
	if tag == TAG_FLOAT:
		return struct.pack(">f", value)
	if tag == TAG_DOUBLE:
		return struct.pack(">d", value)
	if tag == TAG_BYTE_ARRAY:
		return struct.pack(">i", len(value)) + bytes(value)
	if tag == TAG_STRING:
		raw = value.encode("utf-8")
		return struct.pack(">H", len(raw)) + raw
	if tag == TAG_LIST:
		if not value:
			return struct.pack(">Bi", TAG_END, 0)
		item_tag, items = value
		out = struct.pack(">Bi", item_tag, len(items))
		for item in items:
			out += payload(item_tag, item)
		return out
	if tag == TAG_COMPOUND:
		out = b""
		for key, (item_tag, item) in value.items():
			raw = key.encode("utf-8")
			out += struct.pack(">BH", item_tag, len(raw)) + raw + payload(item_tag, item)
		return out + b"\x00"
	if tag == TAG_INT_ARRAY:
		return struct.pack(">i", len(value)) + b"".join(struct.pack(">i", v) for v in value)
	if tag == TAG_LONG_ARRAY:
		return struct.pack(">i", len(value)) + b"".join(struct.pack(">q", v) for v in value)
	raise ValueError(f"unsupported tag {tag}")


def named(tag: int, name: str, value) -> tuple[str, tuple[int, object]]:
	return name, (tag, value)


def compound(*entries) -> dict:
	return dict(entries)


def write_nbt(path: Path, root: dict) -> None:
	body = struct.pack(">BH", TAG_COMPOUND, 0) + payload(TAG_COMPOUND, root)
	path.parent.mkdir(parents=True, exist_ok=True)
	with gzip.GzipFile(path, "wb", mtime=0) as handle:
		handle.write(body)


# --------------------------------------------------------------------------- build

class Build:
	"""Collects blocks into a palette-indexed structure."""

	def __init__(self, size_x: int, size_y: int, size_z: int) -> None:
		self.size = (size_x, size_y, size_z)
		self.palette: list[str] = []
		self.states: list[dict] = []
		self.blocks: list[dict] = []


	def state(self, name: str, **properties) -> int:
		"""Register a block state and return its palette index."""
		entry = {"Name": name}
		if properties:
			entry["Properties"] = {key: str(value) for key, value in properties.items()}
		key = repr(sorted(entry.items()))
		if key not in self.palette:
			self.palette.append(key)
			self.states.append(compound(
				named(TAG_STRING, "Name", name),
				named(TAG_COMPOUND, "Properties",
				      {k: (TAG_STRING, v) for k, v in entry.get("Properties", {}).items()}),
			))
		return self.palette.index(key)

	def block(self, x: int, y: int, z: int, name: str, nbt=None, **properties) -> None:
		entry = compound(named(TAG_INT_ARRAY, "pos", [x, y, z]),
		                 named(TAG_INT, "state", self.state(name, **properties)))
		if nbt is not None:
			entry["nbt"] = (TAG_COMPOUND, nbt)
		self.blocks.append(entry)

	def fill(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, name: str, **properties) -> None:
		for y in range(min(y0, y1), max(y0, y1) + 1):
			for z in range(min(z0, z1), max(z0, z1) + 1):
				for x in range(min(x0, x1), max(x0, x1) + 1):
					self.block(x, y, z, name, **properties)

	def nbt(self) -> dict:
		return compound(
			named(TAG_INT_ARRAY, "size", list(self.size)),
			named(TAG_INT, "DataVersion", DATA_VERSION),
			named(TAG_LIST, "palette", (TAG_COMPOUND, self.states)),
			named(TAG_LIST, "blocks", (TAG_COMPOUND, self.blocks)),
			named(TAG_LIST, "entities", (TAG_COMPOUND, [])),
		)


def gale_ruins() -> Build:
	"""A wind-scoured shrine: a cracked floor, four pillars and a gale lamp."""
	b = Build(7, 4, 7)
	brick, cracked, mossy = (
		"minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks")
	chiseled = "minecraft:chiseled_stone_bricks"

	# floor: a brick rim around a cracked centre dusted with gale ash
	b.fill(0, 0, 0, 6, 0, 6, brick)
	b.fill(1, 0, 1, 5, 0, 5, cracked)
	for x, z in ((2, 2), (4, 4), (2, 4), (4, 2), (3, 3)):
		b.block(x, 0, z, "hayatemod:gale_ash")
	b.block(1, 0, 3, mossy)
	b.block(5, 0, 1, mossy)

	# four pillars, capped with a chiselled block
	for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
		for y in range(1, 4):
			b.block(x, y, z, chiseled if y == 3 else brick)

	# a broken wall along the north edge, so the ruin reads as a ruin
	for x in range(1, 6):
		if x == 3:
			continue
		b.block(x, 1, 0, cracked)
	b.block(2, 2, 0, cracked)

	# the shrine in the middle: a gale block pedestal with a lit lamp on top
	b.block(3, 1, 3, "hayatemod:gale_block")
	b.block(3, 2, 3, "hayatemod:gale_lamp", lit="true")

	# a chest tucked against the south-east pillar, fed by our own loot table
	b.block(5, 1, 2, "minecraft:chest", nbt=compound(
		named(TAG_STRING, "id", "minecraft:chest"),
		named(TAG_STRING, "LootTable", "hayatemod:chests/gale_ruins"),
		named(TAG_LONG, "LootTableSeed", 0),
	), facing="west")
	return b


TARGETS = {
	DATA / "structures" / "gale_ruins" / "ruin_1.nbt": gale_ruins,
}


def main() -> int:
	for path, factory in TARGETS.items():
		if "--list" in sys.argv:
			build = factory()
			print(f"{path.relative_to(ROOT)}: size={build.size} "
			      f"blocks={len(build.blocks)} palette={len(build.states)}")
			continue
		write_nbt(path, factory().nbt())
		print("wrote", path.relative_to(ROOT))
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
