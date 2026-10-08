package com.thuvstu.hayatemod.rpg;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thuvstu.hayatemod.build.BuildOps;
import com.thuvstu.hayatemod.build.PlayerBuilds;
import com.thuvstu.hayatemod.codex.CodexStore;
import com.thuvstu.hayatemod.dungeon.DungeonCommand;
import com.thuvstu.hayatemod.economy.MarketOps;
import com.thuvstu.hayatemod.item.CraftOps;
import com.thuvstu.hayatemod.life.LifeSkills;
import com.thuvstu.hayatemod.core.life.MiningLevels;
import com.thuvstu.hayatemod.life.MiningHooks;
import com.thuvstu.hayatemod.net.UiServer;
import com.thuvstu.hayatemod.town.TownManager;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.LootTable;
import com.thuvstu.hayatemod.item.ModItems;
import com.thuvstu.hayatemod.item.WeaponStack;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** {@code /solommo} operations. Gameplay commands work for every player
 * (survival-safe); creation/debug commands ({@code give}, {@code giverune},
 * {@code shards}, {@code reload}) require op level 2. */
public final class SolommoCommand {
    private SolommoCommand() {
    }

    /** Operator gate for debug/creation commands. */
    public static java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> op2() {
        return src -> src.permissions().hasPermission(
                new net.minecraft.server.permissions.Permission.HasCommandLevel(
                        net.minecraft.server.permissions.PermissionLevel.byId(2)));
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("solommo")
                        .then(Commands.literal("give")
                                .requires(op2())
                                // greedyString: weapon ids contain ':' which word() rejects.
                                .then(Commands.argument("weapon", StringArgumentType.greedyString())
                                        .executes(ctx -> give(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "weapon"), -1))
                                        .then(Commands.argument("item_level", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> give(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "weapon"),
                                                        IntegerArgumentType.getInteger(ctx, "item_level"))))))
                        .then(Commands.literal("shards")
                                .requires(op2())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            int n = IntegerArgumentType.getInteger(ctx, "count");
                                            p.getInventory().add(new ItemStack(ModItems.PITY_SHARD, n));
                                            p.sendSystemMessage(Component.literal(
                                                    "[solommo] (debug) pity_shard x" + n));
                                            return 1;
                                        })))
                        .then(Commands.literal("exchange").executes(ctx -> exchange(
                                ctx.getSource().getPlayerOrException(), "solommo:flame_golem_loot")))
                        .then(Commands.literal("reload").requires(op2()).executes(ctx -> {
                            ContentHolder.load();
                            if (ContentHolder.ready()) {
                                McAdapter.init(ctx.getSource().getServer());
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[solommo] reloaded from "
                                            + ContentHolder.source()),
                                    false);
                            return 1;
                        }))
                        .then(Commands.literal("tavern").executes(ctx -> {
                            UiServer.openTavern(
                                    ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("town").executes(ctx -> {
                            TownManager.teleportTown(
                                    ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("back").executes(ctx -> {
                            TownManager.teleportBack(
                                    ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("locate").executes(ctx -> locate(
                                ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("codex").executes(ctx -> {
                            UiServer.openCodex(
                                    ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("giverune")
                                .requires(op2())
                                .then(Commands.argument("rune", StringArgumentType.greedyString())
                                        .executes(ctx -> giverune(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "rune")))))
                        .then(Commands.literal("socket")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, 1))
                                        .then(Commands.argument("rune", StringArgumentType.greedyString())
                                                .executes(ctx -> socket(
                                                        ctx.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(ctx, "slot"),
                                                        StringArgumentType.getString(ctx, "rune"))))))
                        .then(Commands.literal("keystone")
                                .then(Commands.argument("id", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            toggleKeystone(ctx.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(ctx, "id"));
                                            return 1;
                                        })))
                        .then(Commands.literal("sp")
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    p.sendSystemMessage(Component.literal(
                                            "[solommo] スキルポイント: "
                                                    + PlayerBuilds.spStatus(p.getUUID())
                                                    + "（/solommo sp damage|cooldown|melee）"));
                                    return 1;
                                })
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer p =
                                                    ctx.getSource().getPlayerOrException();
                                            p.sendSystemMessage(Component.literal("[solommo] "
                                                    + PlayerBuilds.spendSp(p.getUUID(),
                                                            StringArgumentType.getString(ctx,
                                                                    "stat"))));
                                            return 1;
                                        })))
                        .then(Commands.literal("loadout")
                                .executes(ctx -> {
                                    UiServer.openLoadout(
                                            ctx.getSource().getPlayerOrException());
                                    return 1;
                                })
                                .then(Commands.literal("save")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    ServerPlayer p =
                                                            ctx.getSource().getPlayerOrException();
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    return saveLoadout(p, name);
                                                })))
                                .then(Commands.literal("load")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> loadout(
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("list").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    p.sendSystemMessage(Component.literal("[solommo] ロードアウト: "
                                            + PlayerBuilds.loadoutNames(
                                                    p.getUUID())));
                                    return 1;
                                })))
                        .then(Commands.literal("salvage").executes(ctx -> salvage(
                                ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("forge")
                                .then(Commands.argument("style", StringArgumentType.word())
                                .then(Commands.argument("base", StringArgumentType.word())
                                .then(Commands.argument("special", StringArgumentType.word())
                                .then(Commands.argument("heavy", StringArgumentType.word())
                                .then(Commands.argument("material", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> forge(
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "style"),
                                                StringArgumentType.getString(ctx, "base"),
                                                StringArgumentType.getString(ctx, "special"),
                                                StringArgumentType.getString(ctx, "heavy"),
                                                StringArgumentType.getString(ctx, "material"),
                                                StringArgumentType.getString(ctx, "name"))))))))))
                        .then(Commands.literal("craft")
                                .executes(ctx -> {
                                    UiServer.openCraft(
                                            ctx.getSource().getPlayerOrException());
                                    return 1;
                                })
                                .then(Commands.argument("weapon", StringArgumentType.greedyString())
                                        .executes(ctx -> craft(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "weapon")))))
                        .then(Commands.literal("build").executes(ctx -> build(
                                ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("market")
                                .executes(ctx -> {
                                    UiServer.openMarket(
                                            ctx.getSource().getPlayerOrException());
                                    return 1;
                                })                                .then(Commands.literal("list").executes(ctx -> marketList(
                                        ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("buy")
                                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                                .executes(ctx -> marketBuy(
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "item"), 1))
                                                .then(Commands.argument("count",
                                                        IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> marketBuy(
                                                                ctx.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(ctx, "item"),
                                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                                .then(Commands.literal("sell")
                                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                                .executes(ctx -> marketSell(
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "item"), 1))
                                                .then(Commands.argument("count",
                                                        IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> marketSell(
                                                                ctx.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(ctx, "item"),
                                                                IntegerArgumentType.getInteger(ctx, "count")))))))
                        .then(Commands.literal("mine").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int xp = LifeSkills.miningXp(p.getUUID());
                            int level = 1;
                            if (ContentHolder.ready() && ContentHolder.get().mining() != null) {
                                level = MiningLevels.levelForXp(xp,
                                        ContentHolder.get().mining());
                            }
                            p.sendSystemMessage(Component.literal(
                                    "[採掘] Lv" + level + " (" + xp + "xp)"));
                            return 1;
                        }))
                        .then(DungeonCommand.literal())));
    }

    private static int give(ServerPlayer player, String weaponId, int itemLevel) {
        if (!ContentHolder.ready()) {
            player.sendSystemMessage(Component.literal("[solommo] content not loaded"));
            return 0;
        }
        var card = ContentHolder.get().weapons().get(weaponId);
        if (card == null) {
            player.sendSystemMessage(Component.literal("[solommo] unknown weapon '" + weaponId + "'"));
            return 0;
        }
        int level = itemLevel < 0 ? card.itemLevel() : itemLevel;
        ItemStack stack = WeaponStack.make(weaponId, level);
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("[solommo] cannot make '" + weaponId + "'"));
            return 0;
        }
        player.getInventory().add(stack);
        CodexStore.record(player.getUUID(), weaponId);
        player.sendSystemMessage(
                Component.literal("[solommo] " + card.name() + " (IL" + level + ") を入手"));
        return 1;
    }

    private static int exchange(ServerPlayer player, String tableId) {        if (!ContentHolder.ready()) {
            return 0;
        }
        LootTable table = ContentHolder.get().loot().get(tableId);
        if (table == null || table.exchangeCost() == null || table.exchangeItem() == null) {
            player.sendSystemMessage(Component.literal("[solommo] no exchange in '" + tableId + "'"));
            return 0;
        }
        int have = WeaponStack.countOf(player.getInventory(), ModItems.PITY_SHARD);
        if (have < table.exchangeCost()) {
            player.sendSystemMessage(Component.literal("[solommo] 欠片不足 (" + have + "/"
                    + table.exchangeCost() + ")"));
            return 0;
        }
        WeaponStack.removeItems(player.getInventory(), ModItems.PITY_SHARD, table.exchangeCost());
        var card = ContentHolder.get().weapons().get(table.exchangeItem());
        ItemStack stack = WeaponStack.make(table.exchangeItem(), card != null ? card.itemLevel() : 1);
        player.getInventory().add(stack);
        CodexStore.record(player.getUUID(), table.exchangeItem());
        player.sendSystemMessage(Component.literal("[solommo] 交換成立: " + table.exchangeItem()));
        return 1;
    }

    private static int giverune(ServerPlayer player, String runeId) {
        if (!ContentHolder.ready() || !ContentHolder.get().runes().containsKey(runeId)) {
            player.sendSystemMessage(Component.literal("[solommo] unknown rune '" + runeId + "'"));
            return 0;
        }
        player.getInventory().add(WeaponStack.makeRune(runeId));
        player.sendSystemMessage(Component.literal("[solommo] ルーン入手: " + runeId));
        return 1;
    }

    private static int socket(ServerPlayer player, int slot, String runeId) {
        if (!ContentHolder.ready() || !ContentHolder.get().runes().containsKey(runeId)) {
            player.sendSystemMessage(Component.literal("[solommo] unknown rune '" + runeId + "'"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (WeaponStack.resolve(held) == null) {
            player.sendSystemMessage(Component.literal("[solommo] 武器を持って"));
            return 0;
        }
        if (WeaponStack.countOf(player.getInventory(), ModItems.RUNE) < 1
                && !runeId.equals(WeaponStack.getRunes(held).get(slot))) {
            player.sendSystemMessage(Component.literal("[solommo] ルーン品を持っていない"));
            return 0;
        }
        String old = WeaponStack.getRunes(held).get(slot);
        WeaponStack.removeItems(player.getInventory(), ModItems.RUNE, 1);
        WeaponStack.setRune(held, slot, runeId);
        if (!old.isEmpty()) {
            player.getInventory().add(WeaponStack.makeRune(old));
        }
        player.sendSystemMessage(
                Component.literal("[solommo] socket" + slot + " <- " + runeId + " (IL" + held.get(ModItems.ITEM_LEVEL) + ")"));
        return 1;
    }

    public static String toggleKeystone(ServerPlayer player, String id) {
        String msg = PlayerBuilds.toggleKeystone(player.getUUID(), id);
        player.sendSystemMessage(Component.literal("[solommo] " + msg));
        return msg;
    }

    public static int saveLoadout(ServerPlayer player, String name) {
        var card = WeaponStack.resolve(player.getMainHandItem());
        PlayerBuilds.saveLoadout(player.getUUID(), name,
                card != null ? card.id() : "");
        player.sendSystemMessage(Component.literal("[solommo] ロードアウト保存: " + name));
        return 1;
    }

    public static int loadout(ServerPlayer player, String name) {
        if (!TownManager.requireTown(player)) {
            return 0;
        }
        var l = PlayerBuilds.loadout(player.getUUID(), name);
        if (l == null) {
            player.sendSystemMessage(Component.literal("[solommo] no such loadout '" + name + "'"));
            return 0;
        }
        PlayerBuilds.toggleOffAll(player.getUUID());
        for (String k : l.keystones) {
            PlayerBuilds.toggleKeystone(player.getUUID(), k);
        }
        player.sendSystemMessage(Component.literal("[solommo] ロードアウト適用: " + name
                + " (武器: " + (l.weapon.isEmpty() ? "-" : l.weapon) + ", 手持ちの同ID品を持ってください)"));
        return 1;
    }

    public static int salvage(ServerPlayer player) {
        return CraftOps.salvage(player);
    }

    public static int craft(ServerPlayer player, String weaponId) {
        if (weaponId.startsWith("cinder_")) {
            return CraftOps.craftArmor(player, weaponId);
        }
        return CraftOps.craft(player, weaponId);
    }

    /**
     * In-game forge: style look + donor skills (codex-unlocked) + material
     * tier + custom name. Costs 6 material + 12 craft_material.
     */
    public static int forge(ServerPlayer player, String style, String base, String special,
            String heavy, String material, String name) {
        if (!TownManager.requireTown(player)) {
            return 0;
        }
        if (!ContentHolder.ready()) {
            return 0;
        }
        var weapons = ContentHolder.get().weapons();
        if (!weapons.containsKey(style) || !weapons.containsKey(base)) {
            player.sendSystemMessage(
                    Component.literal("[forge] style/base は武器IDで指定（solommo:...）"));
            return 0;
        }
        var codex = com.thuvstu.hayatemod.codex.CodexStore.list(player.getUUID());
        for (String donor : new String[] {base, special, heavy}) {
            if (donor != null && !donor.isEmpty() && !donor.equals("-")
                    && !weapons.containsKey(donor)) {
                player.sendSystemMessage(Component.literal("[forge] unknown donor '" + donor + "'"));
                return 0;
            }
            if (donor != null && !donor.isEmpty() && !donor.equals("-")
                    && !codex.contains(donor)) {
                player.sendSystemMessage(
                        Component.literal("[forge] 未登録: " + donor + "（図鑑に登録してから）"));
                return 0;
            }
        }
        if (name == null || name.isBlank()) {
            player.sendSystemMessage(Component.literal("[forge] 名前を付けてください"));
            return 0;
        }
        if (name.length() > 32) {
            player.sendSystemMessage(Component.literal("[forge] 名前は32文字まで"));
            return 0;
        }
        net.minecraft.world.item.Item matItem;
        int itemLevel;
        switch (material) {
            case "cinder_iron" -> {
                matItem = ModItems.CINDER_IRON;
                itemLevel = 8;
            }
            case "slag_steel" -> {
                matItem = ModItems.SLAG_STEEL;
                itemLevel = 14;
            }
            case "ember_glass" -> {
                matItem = ModItems.EMBER_GLASS;
                itemLevel = 18;
            }
            default -> {
                player.sendSystemMessage(
                        Component.literal("[forge] 素材は cinder_iron/slag_steel/ember_glass"));
                return 0;
            }
        }
        if (WeaponStack.countOf(player.getInventory(), matItem) < 6
                || WeaponStack.countOf(player.getInventory(), ModItems.CRAFT_MATERIAL) < 12) {
            player.sendSystemMessage(Component.literal("[forge] 材料不足（素材x6＋汎用素材x12）"));
            return 0;
        }
        WeaponStack.removeItems(player.getInventory(), matItem, 6);
        WeaponStack.removeItems(player.getInventory(), ModItems.CRAFT_MATERIAL, 12);
        var spec = new com.thuvstu.hayatemod.forge.ForgedStore.Spec();
        spec.style = style;
        spec.base = base;
        spec.special = "-".equals(special) ? "" : special;
        spec.heavy = "-".equals(heavy) ? "" : heavy;
        spec.name = name;
        spec.material = material;
        spec.itemLevel = itemLevel;
        String id = com.thuvstu.hayatemod.forge.ForgedStore.put(spec);
        player.getInventory().add(WeaponStack.makeForged(id, style, itemLevel, name));
        player.sendSystemMessage(Component.literal("[forge] 鍛造: " + name + " (" + id + ")"));
        return 1;
    }

    private static int build(ServerPlayer player) {
        BuildOps.printBuild(player);
        return 1;
    }

    /** Prints where the hamlet and the arena stand (structure existence check). */
    private static int locate(ServerPlayer player) {
        var town = TownManager.center();
        var arena = com.thuvstu.hayatemod.dungeon.ArenaManager.origin();
        var p = player.position();
        player.sendSystemMessage(Component.literal(
                "[locate] 集落 (" + (int) town.x + ", " + (int) town.y + ", " + (int) town.z
                        + ") まで" + (int) dist(p, town) + "m"));
        player.sendSystemMessage(Component.literal(
                "[locate] アリーナ (" + arena.getX() + ", " + arena.getY() + ", " + arena.getZ()
                        + ") まで" + (int) dist(p, new net.minecraft.world.phys.Vec3(
                                arena.getX(), arena.getY(), arena.getZ()))
                        + "m（/solommo town で集落へ）"));
        return 1;
    }

    private static double dist(net.minecraft.world.phys.Vec3 a,
            net.minecraft.world.phys.Vec3 b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int marketList(ServerPlayer player) {
        return MarketOps.list(player);
    }

    public static int marketBuy(ServerPlayer player, String item, int count) {
        return MarketOps.buy(player, item, count);
    }

    public static int marketSell(ServerPlayer player, String item, int count) {
        return MarketOps.sell(player, item, count);
    }
}
