package com.thuvstu.hayatemod.core.validate;

import java.util.Set;

/**
 * Closed vocabularies for string-referenced game objects (mythic-catalog
 * wave 2). The mod mirrors these sets when resolving ids; anything outside
 * is rejected here (V02) so the game never looks up an unknown id.
 */
public final class Sets {
    private Sets() {
    }

    /** Vanilla status effects usable by potion / has_effect. */
    public static final Set<String> EFFECTS = Set.of(
            "speed", "slowness", "haste", "strength", "regeneration", "resistance",
            "fire_resistance", "weakness", "poison", "absorption", "glowing", "night_vision");

    /** Particles usable by the particles action. */
    public static final Set<String> PARTICLES = Set.of(
            "flame", "soul_fire_flame", "enchant", "crit", "cloud", "lava", "portal", "witch",
            "note", "heart", "smoke", "explosion");

    /** Sounds usable by the sound action. */
    public static final Set<String> SOUNDS = Set.of(
            "entity_player_levelup", "entity_generic_explode", "entity_lightning_bolt_thunder",
            "entity_enderman_teleport", "block_anvil_land", "entity_firework_rocket_launch",
            "block_beacon_activate", "item_firecharge_use");

    /**
     * Items the giveitem/dropitem actions may move. pity_shard is excluded
     * by design (progression integrity, same rule as the market).
     */
    public static final Set<String> GIFTS = Set.of("craft_material", "emerald");
}
