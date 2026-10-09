package com.thuvstu.hayatemod.item;

import com.thuvstu.hayatemod.codex.CodexStore;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Deterministic crafting and salvage (§5.9). Used by chat commands and screen packets. */
public final class CraftOps {
    private CraftOps() {
    }

    public record Cost(int materials, int pity) {
    }

    public static Cost costOf(WeaponCard card) {
        var cost = com.thuvstu.hayatemod.core.rules.CraftRules.cost(card);
        return new Cost(cost.materials(), cost.pity());
    }

    public static int craft(ServerPlayer player, String weaponId) {
        if (!com.thuvstu.hayatemod.town.TownManager.requireTown(player)) return 0;
        if (!ContentHolder.ready()) {
            return 0;
        }
        WeaponCard card = ContentHolder.get().weapons().get(weaponId);
        if (card == null) {
            player.sendSystemMessage(Component.literal("[solommo] unknown weapon '" + weaponId + "'"));
            return 0;
        }
        ItemStack result = WeaponStack.make(weaponId, card.itemLevel());
        if (result.isEmpty()) return 0;
        Cost cost = costOf(card);
        int haveMat = WeaponStack.countOf(player.getInventory(), ModItems.CRAFT_MATERIAL);
        int havePity = WeaponStack.countOf(player.getInventory(), ModItems.PITY_SHARD);
        if (haveMat < cost.materials() || havePity < cost.pity()) {
            player.sendSystemMessage(Component.literal("[solommo] 素材不足 (素材 " + haveMat + "/"
                    + cost.materials() + ", 欠片 " + havePity + "/" + cost.pity() + ")"));
            return 0;
        }
        WeaponStack.removeItems(player.getInventory(), ModItems.CRAFT_MATERIAL, cost.materials());
        if (cost.pity() > 0) {
            WeaponStack.removeItems(player.getInventory(), ModItems.PITY_SHARD, cost.pity());
        }
        WeaponStack.giveOrDrop(player, result);
        CodexStore.record(player.getUUID(), weaponId);
        com.thuvstu.hayatemod.progress.AdvancementHelper.grant(player, "first_craft");
        player.sendSystemMessage(Component.literal("[solommo] クラフト成立: " + card.name()));
        return 1;
    }

    /** Cinder mail crafting (slag_steel sink). Costs: helm 4, chest 7, legs 6, boots 4. */
    public static int craftArmor(ServerPlayer player, String piece) {
        if (!com.thuvstu.hayatemod.town.TownManager.requireTown(player)) return 0;
        Item item = switch (piece) {
            case "cinder_helm" -> ModItems.CINDER_HELM;
            case "cinder_chest" -> ModItems.CINDER_CHEST;
            case "cinder_legs" -> ModItems.CINDER_LEGS;
            case "cinder_boots" -> ModItems.CINDER_BOOTS;
            default -> null;
        };
        if (item == null) {
            return craft(player, piece);
        }
        int cost = switch (piece) {
            case "cinder_chest" -> 7;
            case "cinder_legs" -> 6;
            default -> 4;
        };
        int have = WeaponStack.countOf(player.getInventory(), ModItems.SLAG_STEEL);
        if (have < cost) {
            player.sendSystemMessage(
                    Component.literal("[solommo] 鉱滓鋼不足 (" + have + "/" + cost + ")"));
            return 0;
        }
        WeaponStack.removeItems(player.getInventory(), ModItems.SLAG_STEEL, cost);
        WeaponStack.giveOrDrop(player, new ItemStack(item));
        player.sendSystemMessage(Component.literal("[solommo] 灰甲冑作成: " + piece));
        return 1;
    }

    public static int salvage(ServerPlayer player) {
        if (!com.thuvstu.hayatemod.town.TownManager.requireTown(player)) return 0;
        ItemStack held = player.getMainHandItem();
        WeaponCard card = WeaponStack.resolve(held);
        if (card == null) {
            player.sendSystemMessage(Component.literal("[solommo] 武器を持って"));
            return 0;
        }
        held.shrink(1);
        int mats = com.thuvstu.hayatemod.core.rules.CraftRules.salvageMaterials(card);
        WeaponStack.giveOrDrop(player, new ItemStack(ModItems.CRAFT_MATERIAL, mats));
        String extra = "";
        if (CodexStore.list(player.getUUID()).contains(card.id())) {
            WeaponStack.giveOrDrop(player, new ItemStack(ModItems.PITY_SHARD, 2));
            extra = "＋重複救済: 欠片x2";
        } else {
            CodexStore.record(player.getUUID(), card.id());
        }
        player.sendSystemMessage(Component.literal("[solommo] 分解: 素材x" + mats + extra));
        return 1;
    }
}
