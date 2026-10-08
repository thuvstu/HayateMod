package com.thuvstu.hayatemod.item;

import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Maps stacks to weapon cards and builds card-backed stacks. */
public final class WeaponStack {
    private WeaponStack() {
    }

    public static WeaponCard resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ContentHolder.ready()) {
            return null;
        }
        String id = stack.get(ModItems.WEAPON_ID);
        if (id == null) {
            return null;
        }
        WeaponCard card = ContentHolder.get().weapons().get(id);
        if (card != null) {
            return card;
        }
        // Player-forged one-offs live outside the Content Pack.
        return com.thuvstu.hayatemod.forge.ForgedCards.assemble(id);
    }

    public static ItemStack make(String weaponId, int itemLevel) {
        if (!ContentHolder.ready()) {
            return ItemStack.EMPTY;
        }
        WeaponCard card = ContentHolder.get().weapons().get(weaponId);
        if (card == null) {
            return ItemStack.EMPTY;
        }
        Item item = ModItems.BY_WEAPON.getOrDefault(weaponId, switch (card.family()) {
            case "spear" -> ModItems.SPEAR;
            case "sword" -> ModItems.SWORD;
            case "staff" -> ModItems.STAFF;
            default -> null;
        });
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(ModItems.WEAPON_ID, weaponId);
        stack.set(ModItems.ITEM_LEVEL, itemLevel);
        return stack;
    }

    public static ItemStack makeRune(String runeId) {
        ItemStack stack = new ItemStack(ModItems.RUNE);
        stack.set(ModItems.RUNE_ID, runeId);
        return stack;
    }

    /** Builds a forged one-off stack on a style vessel item. */
    public static ItemStack makeForged(String forgedId, String styleWeaponId, int itemLevel,
            String name) {
        Item vessel = ModItems.BY_WEAPON.get(styleWeaponId);
        if (vessel == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(vessel);
        stack.set(ModItems.WEAPON_ID, forgedId);
        stack.set(ModItems.ITEM_LEVEL, itemLevel);
        if (name != null && !name.isBlank()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(name));
        }
        return stack;
    }

    /** Socketed rune ids ("" = empty), always {@code SOCKET_COUNT} long. */
    public static java.util.List<String> getRunes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return java.util.List.of("", "");
        }
        java.util.List<String> sockets = stack.get(ModItems.SOCKETS);
        if (sockets == null) {
            return java.util.List.of("", "");
        }
        java.util.List<String> out = new java.util.ArrayList<>(sockets);
        while (out.size() < ModItems.SOCKET_COUNT) {
            out.add("");
        }
        return out.subList(0, ModItems.SOCKET_COUNT);
    }

    public static void setRune(ItemStack stack, int slot, String runeId) {
        java.util.List<String> sockets = new java.util.ArrayList<>(getRunes(stack));
        sockets.set(slot, runeId != null ? runeId : "");
        stack.set(ModItems.SOCKETS, java.util.List.copyOf(sockets));
    }

    public static int countOf(net.minecraft.world.entity.player.Inventory inventory, Item item) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) {
                n += s.getCount();
            }
        }
        return n;
    }

    public static void removeItems(net.minecraft.world.entity.player.Inventory inventory, Item item, int count) {
        int rest = count;
        for (int i = 0; i < inventory.getContainerSize() && rest > 0; i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) {
                int take = Math.min(rest, s.getCount());
                s.shrink(take);
                rest -= take;
            }
        }
    }
}
