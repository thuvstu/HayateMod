package com.thuvstu.hayatemod.core.content;

import java.util.List;
import java.util.Map;

import com.thuvstu.hayatemod.core.content.model.Models.AbilityDef;
import com.thuvstu.hayatemod.core.content.model.Models.ArenaData;
import com.thuvstu.hayatemod.core.content.model.Models.DamageTuning;
import com.thuvstu.hayatemod.core.content.model.Models.EnemyData;
import com.thuvstu.hayatemod.core.content.model.Models.EncounterData;
import com.thuvstu.hayatemod.core.content.model.Models.JobDef;
import com.thuvstu.hayatemod.core.content.model.Models.LootTable;
import com.thuvstu.hayatemod.core.content.model.Models.NpcData;
import com.thuvstu.hayatemod.core.content.model.Models.ReferenceEntry;
import com.thuvstu.hayatemod.core.content.model.Models.Ruleset;
import com.thuvstu.hayatemod.core.content.model.Models.RuneDef;
import com.thuvstu.hayatemod.core.content.model.Models.KeystoneDef;
import com.thuvstu.hayatemod.core.content.model.Models.Vocabulary;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;

/** Everything the Content Pack defines, keyed by id. Single vocabulary + balance set. */
public record ContentSet(Map<String, WeaponCard> weapons, Map<String, EnemyData> enemies,
        Map<String, EncounterData> encounters, Map<String, AbilityDef> skills,
        Map<String, LootTable> loot, Map<String, JobDef> jobs, Map<String, NpcData> npcs,
        Map<String, ArenaData> arenas,
        Map<String, RuneDef> runes, Map<String, KeystoneDef> keystones,
        Vocabulary vocabulary, List<ReferenceEntry> reference, DamageTuning tuning,
        Map<String, Ruleset> rulesets,
        com.thuvstu.hayatemod.core.economy.MarketDefs.MarketBook market,
        com.thuvstu.hayatemod.core.life.MiningLevels.MiningBook mining,
        com.thuvstu.hayatemod.core.life.FishingLevels.FishingBook fishing,
        com.thuvstu.hayatemod.core.content.model.Models.WorldLayout world,
        java.util.Map<String, com.thuvstu.hayatemod.core.content.model.Models.StructTemplate> structures,
        java.util.Map<String, com.thuvstu.hayatemod.core.content.model.Models.MaterialDef> materials) {
}
