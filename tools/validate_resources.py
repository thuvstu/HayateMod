#!/usr/bin/env python3
"""Sanity-checks the resource pack and data pack of HayateMod.

The game only complains about broken references at runtime (missing texture,
silent data pack error, black-pink checkerboard), so this script checks the
things that can be checked statically:

  * every json file parses
  * models: parent references, texture references and texture files exist
  * blockstates / item definitions point at models that exist
  * every registered block/item has a model, an item definition, a loot table
    and both translations
  * recipes, loot tables and worldgen only reference ids this mod registers

Run it with no arguments; a non-zero exit status means something is broken.
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "hayatemod"
DATA = RESOURCES / "data" / "hayatemod"
JAVA = ROOT / "src" / "main" / "java"
MOD_ID = "hayatemod"

problems: list[str] = []


def fail(message: str) -> None:
	problems.append(message)


def load_json(path: pathlib.Path):
	try:
		return json.loads(path.read_text(encoding="utf-8"))
	except Exception as exc:  # noqa: BLE001 - report and continue
		fail(f"{path.relative_to(ROOT)}: invalid json ({exc})")
		return None


def all_json() -> list[pathlib.Path]:
	return sorted(RESOURCES.rglob("*.json"))


# --------------------------------------------------------------------- ids

def registered_ids() -> tuple[set[str], set[str]]:
	"""Returns (item ids, block ids) parsed out of the two id holder classes."""
	item_ids: set[str] = set()
	block_ids: set[str] = set()

	item_file = JAVA / "com" / "thuvstu" / "hayatemod" / "item" / "ModItemIds.java"
	block_file = JAVA / "com" / "thuvstu" / "hayatemod" / "block" / "ModBlockItemIds.java"

	for path in (item_file, block_file):
		if path.exists():
			item_ids |= set(re.findall(r'\bcreate\("([a-z0-9_]+)"\)', path.read_text()))
	if block_file.exists():
		block_ids |= set(re.findall(r'\bcreate\("([a-z0-9_]+)"\)', block_file.read_text()))

	# every block of this mod also has a BlockItem
	return item_ids | block_ids, block_ids


def data_only_ids() -> set[str]:
	found = set()
	for directory in ("enchantment", "worldgen/feature", "worldgen/placed_feature"):
		for path in sorted((DATA / directory).rglob("*.json")):
			found.add(path.stem)
	return found


# ------------------------------------------------------------------ models

def check_model(path: pathlib.Path, known_ids: set[str]) -> None:
	model = load_json(path)
	if model is None:
		return

	parent = model.get("parent")
	if isinstance(parent, str) and parent.startswith(f"{MOD_ID}:"):
		parent_path = ASSETS / "models" / parent.split(":", 1)[1]
		if not parent_path.with_suffix(".json").exists():
			fail(f"{path.name}: parent model {parent} does not exist")

	textures = model.get("textures", {})
	for key, value in textures.items():
		if not isinstance(value, str):
			continue
		if value.startswith("#"):
			continue  # resolved within this file (or by a vanilla parent)
		if value.startswith(f"{MOD_ID}:"):
			texture = ASSETS / "textures" / (value.split(":", 1)[1] + ".png")
			if not texture.exists():
				fail(f"{path.name}: texture {value} is missing ({texture.name})")
		elif value.startswith("minecraft:"):
			continue
		else:
			fail(f"{path.name}: texture {value} has no namespace")

	for element in model.get("elements", []):
		for face in element.get("faces", {}).values():
			texture = face.get("texture")
			if isinstance(texture, str) and texture.startswith("#") and texture[1:] not in textures:
				# may be inherited from a vanilla parent, only complain for our own chains
				if isinstance(parent, str) and parent.startswith(f"{MOD_ID}:"):
					fail(f"{path.name}: face references undefined texture {texture}")


def check_blockstates(known_ids: set[str]) -> None:
	directory = ASSETS / "blockstates"
	for path in sorted(directory.glob("*.json")):
		state = load_json(path)
		if state is None:
			continue
		models = []
		for variant in state.get("variants", {}).values():
			if isinstance(variant, list):
				models += [entry.get("model") for entry in variant]
			else:
				models.append(variant.get("model"))
		for model in models:
			if not isinstance(model, str) or not model.startswith(f"{MOD_ID}:"):
				fail(f"blockstates/{path.name}: unexpected model reference {model!r}")
				continue
			target = ASSETS / "models" / (model.split(":", 1)[1] + ".json")
			if not target.exists():
				fail(f"blockstates/{path.name}: model {model} does not exist")


def check_item_definitions() -> None:
	directory = ASSETS / "items"
	for path in sorted(directory.glob("*.json")):
		definition = load_json(path)
		if definition is None:
			continue
		model = definition.get("model", {}).get("model")
		if not isinstance(model, str):
			fail(f"items/{path.name}: no model reference")
			continue
		if model.startswith(f"{MOD_ID}:"):
			target = ASSETS / "models" / (model.split(":", 1)[1] + ".json")
			if not target.exists():
				fail(f"items/{path.name}: model {model} does not exist")


# --------------------------------------------------------------- languages

def check_translations(item_ids: set[str], block_ids: set[str]) -> None:
	languages = {}
	for path in sorted((ASSETS / "lang").glob("*.json")):
		content = load_json(path)
		if content is not None:
			languages[path.stem] = content

	if not languages:
		fail("no language files found")

	for name, entries in languages.items():
		for item in sorted(item_ids | block_ids):
			key_block = f"block.{MOD_ID}.{item}"
			key_item = f"item.{MOD_ID}.{item}"
			if key_block not in entries and key_item not in entries:
				fail(f"{name}.json: missing translation for {item}")

	# the enchantment description is easy to forget
	for enchantment in sorted((DATA / "enchantment").glob("*.json")):
		key = f"enchantment.{MOD_ID}.{enchantment.stem}"
		for name, entries in languages.items():
			if key not in entries:
				fail(f"{name}.json: missing translation for enchantment {enchantment.stem}")


# -------------------------------------------------------------------- data

def check_data_references(item_ids: set[str], block_ids: set[str]) -> None:
	known = item_ids | block_ids

	def check_id(value: str, where: str) -> None:
		if not isinstance(value, str) or not value.startswith(f"{MOD_ID}:"):
			return
		name = value.split(":", 1)[1]
		if name not in known and not (DATA / "enchantment" / f"{name}.json").exists():
			fail(f"{where}: unknown id {value}")

	def check_entries(entries: object, where: str) -> None:
		"""Loot entries nest: alternatives / group / sequence all carry children."""
		if not isinstance(entries, list):
			return
		for entry in entries:
			if not isinstance(entry, dict):
				continue
			check_id(entry.get("name"), where)
			check_entries(entry.get("children"), where)

	# worldgen: the placed feature must exist and match ModWorldgen
	placed = DATA / "worldgen" / "placed_feature"
	for path in sorted(placed.glob("*.json")):
		content = load_json(path)
		if content is None:
			continue
		feature = content.get("feature")
		if not isinstance(feature, str) or not feature.startswith(f"{MOD_ID}:"):
			fail(f"worldgen/placed_feature/{path.stem}: feature {feature!r} is not ours")
			continue
		target = DATA / "worldgen" / "feature" / (feature.split(":", 1)[1] + ".json")
		if not target.exists():
			fail(f"worldgen/placed_feature/{path.stem}: feature {feature} does not exist")

	java = JAVA / "com" / "thuvstu" / "hayatemod" / "worldgen" / "ModWorldgen.java"
	source = java.read_text() if java.exists() else ""
	from_biomes = set()
	for path in sorted((DATA / "worldgen" / "biome").glob("*.json")):
		content = load_json(path) or {}
		for step in content.get("features", []):
			if isinstance(step, list):
				from_biomes.update(entry for entry in step if isinstance(entry, str))
	for path in sorted(placed.glob("*.json")):
		in_java = f'HayateMod.id("{path.stem}")' in source
		in_biome = f"{MOD_ID}:{path.stem}" in from_biomes
		if not in_java and not in_biome:
			fail(f"placed feature {path.stem} is referenced neither by ModWorldgen.java "
			     f"nor by any biome")

	# loot tables: one per block, and only known item drops
	for path in sorted((DATA / "loot_table" / "blocks").glob("*.json")):
		content = load_json(path)
		if content is None:
			continue
		check_id(path.stem, f"loot_table/blocks/{path.name}")
		for pool in content.get("pools", []):
			check_entries(pool.get("entries"), f"loot_table/blocks/{path.name}")
		if path.stem not in block_ids:
			fail(f"loot_table/blocks/{path.name}: no such block")

	for block in sorted(block_ids):
		if not (DATA / "loot_table" / "blocks" / f"{block}.json").exists():
			fail(f"block {block} has no loot table")

	# chests and mobs: the drops must be items we actually add
	for kind in ("chests", "entities"):
		for path in sorted((DATA / "loot_table" / kind).glob("*.json")):
			content = load_json(path)
			if content is None:
				continue
			for pool in content.get("pools", []):
				check_entries(pool.get("entries"), f"loot_table/{kind}/{path.name}")

	# ... and an entity loot table only makes sense for a registered entity id
	registered = set()
	for java_file in sorted(JAVA.rglob("*.java")):
		registered.update(re.findall(r'HayateMod\.id\("([a-z0-9_]+)"\)',
		                             java_file.read_text(errors="replace")))
	for path in sorted((DATA / "loot_table" / "entities").glob("*.json")):
		if path.stem not in registered:
			fail(f"loot_table/entities/{path.name}: no entity registered under that id")

	# recipes
	for path in sorted((DATA / "recipe").glob("*.json")):
		content = load_json(path)
		if content is None:
			continue
		result = content.get("result", {})
		check_id(result.get("id"), f"recipe/{path.name}")
		for entry in content.get("ingredients", []):
			check_id(entry.get("item") if isinstance(entry, dict) else entry, f"recipe/{path.name}")
		ingredient = content.get("ingredient")
		check_id(ingredient.get("item") if isinstance(ingredient, dict) else ingredient, f"recipe/{path.name}")
		# shaped recipes put theirs in "key": {"S": {"item": ...}}
		for symbol, value in (content.get("key") or {}).items():
			if isinstance(value, dict):
				check_id(value.get("item"), f"recipe/{path.name} (key {symbol})")
			else:
				check_id(value, f"recipe/{path.name} (key {symbol})")

	# every block of ours should be harvestable with some tool
	tagged = set()
	for tag in sorted((RESOURCES / "data" / "minecraft" / "tags" / "block" / "mineable").glob("*.json")):
		content = load_json(tag)
		if content is not None:
			tagged.update(value for value in content.get("values", []) if isinstance(value, str))
	for block in sorted(block_ids):
		if f"{MOD_ID}:{block}" not in tagged:
			fail(f"block {block} is not in any mineable/* tag")


def png_size(path: pathlib.Path) -> tuple[int, int]:
	data = path.read_bytes()
	if data[:8] != b"\x89PNG\r\n\x1a\n":
		fail(f"{path.name}: not a png")
		return 0, 0
	return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def check_animations() -> None:
	"""Animations need a .mcmeta and a texture whose height is a frame multiple."""
	for meta in sorted(RESOURCES.rglob("*.mcmeta")):
		content = load_json(meta)
		texture = pathlib.Path(str(meta)[: -len(".mcmeta")])
		if not texture.exists():
			fail(f"{meta.relative_to(RESOURCES)}: {texture.name} does not exist")
			continue
		width, height = png_size(texture)
		if not width:
			continue
		if height % width:
			fail(f"{texture.name}: height {height} is not a multiple of the frame width {width}")
			continue
		animation = (content or {}).get("animation", {})
		frames = animation.get("frames")
		if isinstance(frames, list):
			for frame in frames:
				if isinstance(frame, int) and frame >= height // width:
					fail(f"{meta.name}: frame {frame} is outside the texture ({height // width} frames)")


def check_biomes() -> None:
	"""Biomes must only point at placed features that exist."""
	for path in sorted((DATA / "worldgen" / "biome").glob("*.json")):
		content = load_json(path)
		if content is None:
			continue
		for step in content.get("features", []):
			if not isinstance(step, list):
				fail(f"worldgen/biome/{path.name}: features must be a list of lists")
				continue
			for feature in step:
				if not isinstance(feature, str) or not feature.startswith(f"{MOD_ID}:"):
					continue
				target = DATA / "worldgen" / "placed_feature" / (feature.split(":", 1)[1] + ".json")
				if not target.exists():
					fail(f"worldgen/biome/{path.name}: unknown placed feature {feature}")



def check_structures() -> None:
	"""Jigsaw structures need a template pool, and every pool element an NBT file."""
	pools = DATA / "worldgen" / "template_pool"
	for path in sorted(pools.glob("**/*.json")):
		content = load_json(path)
		if content is None:
			continue
		for element in content.get("elements", []):
			location = element.get("element", {}).get("location")
			if not isinstance(location, str) or not location.startswith(f"{MOD_ID}:"):
				continue
			target = DATA / "structures" / (location.split(":", 1)[1] + ".nbt")
			if not target.exists():
				fail(f"template_pool/{path.relative_to(pools).as_posix()}: "
				     f"missing structure NBT for {location}")

	structures = DATA / "worldgen" / "structure"
	for path in sorted(structures.glob("*.json")):
		content = load_json(path)
		if content is None:
			continue
		start_pool = content.get("start_pool")
		if isinstance(start_pool, str) and start_pool.startswith(f"{MOD_ID}:"):
			target = DATA / "worldgen" / "template_pool" / (start_pool.split(":", 1)[1] + ".json")
			if not target.exists():
				fail(f"structure/{path.name}: missing start pool {start_pool}")

	sets = DATA / "worldgen" / "structure_set"
	for path in sorted(sets.glob("*.json")):
		content = load_json(path)
		if content is None:
			continue
		for entry in content.get("structures", []):
			name = entry.get("structure")
			if not isinstance(name, str) or not name.startswith(f"{MOD_ID}:"):
				continue
			target = structures / (name.split(":", 1)[1] + ".json")
			if not target.exists():
				fail(f"structure_set/{path.name}: unknown structure {name}")


def check_trim_materials() -> None:
	directory = DATA / "trim_material"
	if not directory.exists():
		return
	languages = {path.stem: load_json(path) for path in sorted((ASSETS / "lang").glob("*.json"))}
	for path in sorted(directory.glob("*.json")):
		content = load_json(path)
		if content is None:
			continue
		key = f"trim_material.{MOD_ID}.{path.stem}"
		for name, entries in languages.items():
			if entries is not None and key not in entries:
				fail(f"{name}.json: missing translation for trim material {path.stem}")


def check_equipment() -> None:
	"""Every equipment asset needs a 64x32 humanoid and humanoid_leggings texture."""
	directory = DATA / "equipment"
	if not directory.exists():
		return
	for path in sorted(directory.glob("*.json")):
		load_json(path)
		for kind in ("humanoid", "humanoid_leggings"):
			texture = ASSETS / "textures" / "entity" / "equipment" / kind / f"{path.stem}.png"
			if not texture.exists():
				fail(f"equipment/{path.name}: missing {kind} texture ({texture.name})")
				continue
			width, height = png_size(texture)
			if (width, height) != (64, 32):
				fail(f"{kind}/{texture.name}: armour layers are 64x32, got {width}x{height}")


def main() -> int:
	for path in all_json():
		load_json(path)

	for path in sorted((ASSETS / "models").rglob("*.json")):
		check_model(path, set())

	check_blockstates(set())
	check_item_definitions()
	check_animations()
	check_equipment()
	check_biomes()
	check_structures()
	check_trim_materials()

	item_ids, block_ids = registered_ids()
	check_translations(item_ids, block_ids)
	check_data_references(item_ids, block_ids)

	if problems:
		print(f"{len(problems)} problem(s) found:")
		for problem in problems:
			print("  -", problem)
		return 1

	print(f"resources ok: {len(all_json())} json files, "
	      f"{len(item_ids)} item ids, {len(block_ids)} block ids")
	return 0


if __name__ == "__main__":
	sys.exit(main())
