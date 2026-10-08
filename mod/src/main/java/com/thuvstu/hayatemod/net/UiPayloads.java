package com.thuvstu.hayatemod.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * UI screen payloads (S2C open-with-data, C2S actions). Server authoritative:
 * screens display server data and send back action requests.
 */
public final class UiPayloads {
    private UiPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("hayatemod", path);
    }

    static <T, B extends io.netty.buffer.ByteBuf> StreamCodec<B, List<T>> listOf(
            StreamCodec<B, T> element) {
        return ByteBufCodecs.collection(size -> new ArrayList<T>(), element);
    }

    // ---- S2C: market ----

    public record MarketRow(String item, String name, int ask, int bid, int stock) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MarketRow> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, MarketRow::item,
                        ByteBufCodecs.STRING_UTF8, MarketRow::name,
                        ByteBufCodecs.INT, MarketRow::ask,
                        ByteBufCodecs.INT, MarketRow::bid,
                        ByteBufCodecs.INT, MarketRow::stock,
                        MarketRow::new);
    }

    public record MarketOpen(List<MarketRow> rows) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MarketOpen> TYPE =
                new CustomPacketPayload.Type<>(id("market_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MarketOpen> CODEC =
                StreamCodec.composite(UiPayloads.listOf(MarketRow.CODEC), MarketOpen::rows,
                        MarketOpen::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C: skill state (HUD cooldown) ----

    public record SkillState(String weaponName, String specialName, int left, int total)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SkillState> TYPE =
                new CustomPacketPayload.Type<>(id("skill_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SkillState> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SkillState::weaponName,
                        ByteBufCodecs.STRING_UTF8, SkillState::specialName,
                        ByteBufCodecs.INT, SkillState::left,
                        ByteBufCodecs.INT, SkillState::total,
                        SkillState::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C: party frames ----

    public record PartyMember(String name, String role, float hpFrac, boolean downed) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PartyMember> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, PartyMember::name,
                        ByteBufCodecs.STRING_UTF8, PartyMember::role,
                        ByteBufCodecs.FLOAT, PartyMember::hpFrac,
                        ByteBufCodecs.BOOL, PartyMember::downed,
                        PartyMember::new);
    }

    public record PartyState(List<PartyMember> members, float playerFrac)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PartyState> TYPE =
                new CustomPacketPayload.Type<>(id("party_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PartyState> CODEC =
                StreamCodec.composite(UiPayloads.listOf(PartyMember.CODEC), PartyState::members,
                        ByteBufCodecs.FLOAT, PartyState::playerFrac, PartyState::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C: tavern ----

    public record TavernNpc(String name, String role, double proficiency) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TavernNpc> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, TavernNpc::name,
                        ByteBufCodecs.STRING_UTF8, TavernNpc::role,
                        ByteBufCodecs.DOUBLE, TavernNpc::proficiency,
                        TavernNpc::new);
    }

    public record TavernQuest(String id, String name, int enemyLevel, int targetMinutes) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TavernQuest> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, TavernQuest::id,
                        ByteBufCodecs.STRING_UTF8, TavernQuest::name,
                        ByteBufCodecs.INT, TavernQuest::enemyLevel,
                        ByteBufCodecs.INT, TavernQuest::targetMinutes,
                        TavernQuest::new);
    }

    public record TavernOpen(List<TavernNpc> npcs, int fee, boolean inSession,
            List<TavernQuest> quests)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TavernOpen> TYPE =
                new CustomPacketPayload.Type<>(id("tavern_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TavernOpen> CODEC =
                StreamCodec.composite(UiPayloads.listOf(TavernNpc.CODEC), TavernOpen::npcs,
                        ByteBufCodecs.INT, TavernOpen::fee,
                        ByteBufCodecs.BOOL, TavernOpen::inSession,
                        UiPayloads.listOf(TavernQuest.CODEC), TavernOpen::quests,
                        TavernOpen::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C: codex ----
    public record CodexEntry(String id, String name, int match, String rarity, int itemLevel) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CodexEntry> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, CodexEntry::id,
                        ByteBufCodecs.STRING_UTF8, CodexEntry::name,
                        ByteBufCodecs.INT, CodexEntry::match,
                        ByteBufCodecs.STRING_UTF8, CodexEntry::rarity,
                        ByteBufCodecs.INT, CodexEntry::itemLevel,
                        CodexEntry::new);
    }

    public record CodexOpen(List<CodexEntry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CodexOpen> TYPE =
                new CustomPacketPayload.Type<>(UiPayloads.id("codex_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CodexOpen> CODEC =
                StreamCodec.composite(UiPayloads.listOf(CodexEntry.CODEC), CodexOpen::entries,
                        CodexOpen::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C: craft ----

    public record CraftRow(String weaponId, String name, int itemLevel, int matCost, int pityCost,
            boolean affordable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftRow> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, CraftRow::weaponId,
                        ByteBufCodecs.STRING_UTF8, CraftRow::name,
                        ByteBufCodecs.INT, CraftRow::itemLevel,
                        ByteBufCodecs.INT, CraftRow::matCost,
                        ByteBufCodecs.INT, CraftRow::pityCost,
                        ByteBufCodecs.BOOL, CraftRow::affordable,
                        CraftRow::new);
    }

    public record CraftOpen(List<CraftRow> rows, String heldName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CraftOpen> TYPE =
                new CustomPacketPayload.Type<>(id("craft_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftOpen> CODEC =
                StreamCodec.composite(UiPayloads.listOf(CraftRow.CODEC), CraftOpen::rows,
                        ByteBufCodecs.STRING_UTF8, CraftOpen::heldName, CraftOpen::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C: loadout ----

    public record KeystoneRow(String id, String name, String desc, boolean equipped) {
        public static final StreamCodec<RegistryFriendlyByteBuf, KeystoneRow> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, KeystoneRow::id,
                        ByteBufCodecs.STRING_UTF8, KeystoneRow::name,
                        ByteBufCodecs.STRING_UTF8, KeystoneRow::desc,
                        ByteBufCodecs.BOOL, KeystoneRow::equipped,
                        KeystoneRow::new);
    }

    public record LoadoutOpen(List<KeystoneRow> keystones, List<String> loadouts, String buildSig)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LoadoutOpen> TYPE =
                new CustomPacketPayload.Type<>(id("loadout_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutOpen> CODEC =
                StreamCodec.composite(UiPayloads.listOf(KeystoneRow.CODEC), LoadoutOpen::keystones,
                        UiPayloads.listOf(ByteBufCodecs.STRING_UTF8), LoadoutOpen::loadouts,
                        ByteBufCodecs.STRING_UTF8, LoadoutOpen::buildSig, LoadoutOpen::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- C2S actions ----

    public record MarketBuy(String item, int count) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MarketBuy> TYPE =
                new CustomPacketPayload.Type<>(id("market_buy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MarketBuy> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, MarketBuy::item,
                        ByteBufCodecs.INT, MarketBuy::count, MarketBuy::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MarketSell(String item, int count) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MarketSell> TYPE =
                new CustomPacketPayload.Type<>(id("market_sell"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MarketSell> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, MarketSell::item,
                        ByteBufCodecs.INT, MarketSell::count, MarketSell::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CraftMake(String weapon) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CraftMake> TYPE =
                new CustomPacketPayload.Type<>(id("craft_make"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftMake> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CraftMake::weapon, CraftMake::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SalvageHeld() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SalvageHeld> TYPE =
                new CustomPacketPayload.Type<>(id("salvage_held"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SalvageHeld> CODEC =
                StreamCodec.unit(new SalvageHeld());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LoadoutSave(String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LoadoutSave> TYPE =
                new CustomPacketPayload.Type<>(id("loadout_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutSave> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LoadoutSave::name, LoadoutSave::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LoadoutApply(String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LoadoutApply> TYPE =
                new CustomPacketPayload.Type<>(id("loadout_apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutApply> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LoadoutApply::name, LoadoutApply::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record KeystoneToggle(String id) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<KeystoneToggle> TYPE =
                new CustomPacketPayload.Type<>(UiPayloads.id("keystone_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, KeystoneToggle> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, KeystoneToggle::id, KeystoneToggle::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TavernEnter(String encounter) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TavernEnter> TYPE =
                new CustomPacketPayload.Type<>(id("tavern_enter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TavernEnter> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, TavernEnter::encounter,
                        TavernEnter::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
