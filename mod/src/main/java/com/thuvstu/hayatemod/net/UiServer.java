package com.thuvstu.hayatemod.net;

import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.build.PlayerBuilds;
import com.thuvstu.hayatemod.codex.CodexStore;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.build.TagDeriver;
import com.thuvstu.hayatemod.core.economy.MarketSim;
import com.thuvstu.hayatemod.dungeon.EncounterRunner;
import com.thuvstu.hayatemod.dungeon.NpcParty;
import com.thuvstu.hayatemod.economy.MarketStore;
import com.thuvstu.hayatemod.item.ModItems;
import com.thuvstu.hayatemod.item.WeaponStack;
import com.thuvstu.hayatemod.net.UiPayloads.CodexEntry;
import com.thuvstu.hayatemod.net.UiPayloads.CodexOpen;
import com.thuvstu.hayatemod.net.UiPayloads.CraftOpen;
import com.thuvstu.hayatemod.net.UiPayloads.CraftRow;
import com.thuvstu.hayatemod.net.UiPayloads.KeystoneRow;
import com.thuvstu.hayatemod.net.UiPayloads.LoadoutOpen;
import com.thuvstu.hayatemod.net.UiPayloads.MarketOpen;
import com.thuvstu.hayatemod.net.UiPayloads.MarketRow;
import com.thuvstu.hayatemod.net.UiPayloads.PartyMember;
import com.thuvstu.hayatemod.net.UiPayloads.PartyState;
import com.thuvstu.hayatemod.net.UiPayloads.SkillState;
import com.thuvstu.hayatemod.net.UiPayloads.TavernNpc;
import com.thuvstu.hayatemod.net.UiPayloads.TavernOpen;
import com.thuvstu.hayatemod.net.UiPayloads.TavernQuest;
import com.thuvstu.hayatemod.rpg.RpgHealth;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Builds S2C screen payloads from authoritative server state and sends them. */
public final class UiServer {
    private UiServer() {
    }

    public static void openMarket(ServerPlayer player) {
        if (!ContentHolder.ready() || ContentHolder.get().market() == null) {
            return;
        }
        var book = ContentHolder.get().market();
        List<MarketRow> rows = new ArrayList<>();
        for (var l : book.listings()) {
            double stock = MarketStore.stockOf(l.item());
            String name = displayName(l.item());
            rows.add(new MarketRow(l.item(), name,
                    (int) Math.ceil(MarketSim.ask(book, l, stock)),
                    (int) Math.floor(MarketSim.bid(book, l, stock + 1)),
                    (int) stock));
        }
        ServerPlayNetworking.send(player, new MarketOpen(rows));
    }

    public static void openCodex(ServerPlayer player) {
        if (!ContentHolder.ready()) {
            return;
        }
        var owned = CodexStore.list(player.getUUID());
        var sig = buildSigSet(player);
        List<CodexEntry> entries = new ArrayList<>();
        for (var card : ContentHolder.get().weapons().values()) {
            if (!owned.contains(card.id())) {
                continue;
            }
            entries.add(new CodexEntry(card.id(), card.name(),
                    TagDeriver.matchCount(TagDeriver.itemTags(card), sig), card.rarity(),
                    card.itemLevel()));
        }
        ServerPlayNetworking.send(player, new CodexOpen(entries));
    }

    public static void openCraft(ServerPlayer player) {
        if (!ContentHolder.ready()) {
            return;
        }
        List<CraftRow> rows = new ArrayList<>();
        for (var card : ContentHolder.get().weapons().values()) {
            int mats = card.itemLevel() * 3;
            int pity = card.rarity().equals("unique") ? 10 : 0;
            boolean ok = WeaponStack.countOf(player.getInventory(),
                            ModItems.CRAFT_MATERIAL) >= mats
                    && WeaponStack.countOf(player.getInventory(),
                            ModItems.PITY_SHARD) >= pity;
            rows.add(new CraftRow(card.id(), card.name(), card.itemLevel(), mats, pity, ok));
        }
        var held = WeaponStack.resolve(player.getMainHandItem());
        ServerPlayNetworking.send(player,
                new CraftOpen(rows, held != null ? held.name() : "-"));
    }

    public static void openLoadout(ServerPlayer player) {
        if (!ContentHolder.ready()) {
            return;
        }
        var equipped = new java.util.HashSet<>(PlayerBuilds.keystones(player.getUUID()));
        List<KeystoneRow> rows = new ArrayList<>();
        for (var k : ContentHolder.get().keystones().values()) {
            rows.add(new KeystoneRow(k.id(), k.name(), k.desc(), equipped.contains(k.id())));
        }
        ServerPlayNetworking.send(player, new LoadoutOpen(rows,
                new ArrayList<>(PlayerBuilds.loadoutNames(player.getUUID())),
                buildSigSet(player).toString()));
    }

    public static void openTavern(ServerPlayer player) {
        if (!ContentHolder.ready()) {
            return;
        }
        List<TavernNpc> npcs = new ArrayList<>();
        for (var n : ContentHolder.get().npcs().values()) {
            npcs.add(new TavernNpc(n.name(), n.role(), n.proficiency()));
        }
        List<TavernQuest> quests = new ArrayList<>();
        for (var enc : ContentHolder.get().encounters().values()) {
            var foe = ContentHolder.get().enemies().get(enc.id());
            quests.add(new TavernQuest(enc.id(), enc.name(),
                    foe != null ? foe.level() : 0, enc.targetMinutes()));
        }
        ServerPlayNetworking.send(player, new TavernOpen(npcs, 5, EncounterRunner.isActive(),
                quests));
    }

    /** Periodic authoritative correction: kill-based CD reductions must reach the HUD too. */
    public static void sendCombatState(ServerPlayer player) {
        var engine = com.thuvstu.hayatemod.rpg.McAdapter.engine();
        if (engine == null) {
            return;
        }
        var card = WeaponStack.resolve(player.getMainHandItem());
        var mods = PlayerBuilds.mods(player.getUUID());
        var adapter = com.thuvstu.hayatemod.rpg.McAdapter.adapter();
        ServerPlayNetworking.send(player, new UiPayloads.CombatState(
                (float) engine.shieldAmount(player.getUUID()), (int) engine.shieldRemainingTicks(player.getUUID()),
                card == null ? 0 : (int) engine.remainingCooldownTicks(player.getUUID(), card, "heavy", mods),
                card == null || !card.skills().containsKey("heavy") ? 0 : (int) engine.cooldownTicks(card, "heavy", mods),
                adapter == null ? 0 : (float) adapter.resourceLevel(player.getUUID(), "mana"),
                adapter == null ? 0 : (float) adapter.resourceLevel(player.getUUID(), "stamina")));
        ServerPlayNetworking.send(player, new UiPayloads.CastingState(engine.castingSlot(player.getUUID()),
                (int) engine.castRemainingTicks(player.getUUID()), (int) engine.castTotalTicks(player.getUUID())));
        if (card == null || !card.skills().containsKey("special")) {
            sendSkill(player, "", "", 0, 0);
            return;
        }
        sendSkill(player, card.name(), com.thuvstu.hayatemod.core.describe.Describer.describeSkill(card.skills().get("special")),
                (int) engine.remainingCooldownTicks(player.getUUID(), card, "special", mods),
                (int) engine.cooldownTicks(card, "special", mods));
    }

    public static void sendSkill(ServerPlayer player, String weaponName, String specialName,
            int left, int total) {
        ServerPlayNetworking.send(player, new SkillState(weaponName, specialName, left, total));
    }

    public static void sendParty(ServerPlayer player, net.minecraft.server.level.ServerLevel level) {
        List<PartyMember> members = new ArrayList<>();
        for (NpcParty.Member m : NpcParty.members()) {
            float frac = 1.0F;
            boolean downed = m.downed();
            var entity = level.getEntity(m.id());
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                frac = living.getHealth() / living.getMaxHealth();
            }
            members.add(new PartyMember(m.data().name(), m.data().role(), frac, downed));
        }
        ServerPlayNetworking.send(player,
                new PartyState(members, (float) (RpgHealth.get(player.getUUID()) / RpgHealth.maxHp())));
    }

    private static String displayName(String itemId) {
        if (itemId.equals("solommo:craft_material")) {
            return "素材";
        }
        var goods = com.thuvstu.hayatemod.life.MiningHooks.tradables().get(itemId);
        if (goods != null) {
            return new net.minecraft.world.item.ItemStack(goods).getHoverName().getString();
        }
        String[] parts = itemId.split(":");
        String path = parts.length > 1 ? parts[1] : itemId;
        return path.replace('_', ' ');
    }

    static java.util.Set<String> buildSigSet(ServerPlayer player) {
        var keys = PlayerBuilds.keystones(player.getUUID());
        var card = WeaponStack.resolve(player.getMainHandItem());
        var cores = card != null ? TagDeriver.skillCoresOf(card) : java.util.Set.<String>of();
        java.util.List<String> runes = card != null
                ? new java.util.ArrayList<>(WeaponStack.getRunes(player.getMainHandItem()))
                : java.util.List.of();
        var sig = TagDeriver.buildTags("solommo:knight", cores,
                runes.stream().filter(s -> !s.isEmpty()).toList(), keys);
        return TagDeriver.matchSet(sig, cores);
    }
}
