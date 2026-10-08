package com.thuvstu.hayatemod.item;

import java.util.Map;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.describe.Describer;
import com.thuvstu.hayatemod.core.describe.MissingTemplateException;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Generic weapon items + currencies. Game rules live in the Content Pack;
 * stacks carry only identity ({@code weapon_id}) and rolls.
 */
public final class ModItems {
    public static final DataComponentType<String> WEAPON_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath("hayatemod", "weapon_id"),
            DataComponentType.<String>builder().persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static final DataComponentType<Integer> ITEM_LEVEL = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath("hayatemod", "item_level"),
            DataComponentType.<Integer>builder().persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT).build());

    public static final DataComponentType<String> RUNE_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath("hayatemod", "rune_id"),
            DataComponentType.<String>builder().persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    /** Two rune sockets per weapon; "" means empty. */
    public static final int SOCKET_COUNT = 2;

    public static final DataComponentType<java.util.List<String>> SOCKETS = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath("hayatemod", "rune_sockets"),
            DataComponentType.<java.util.List<String>>builder()
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.<io.netty.buffer.ByteBuf, String, java.util.List<String>>collection(
                            size -> new java.util.ArrayList<String>(), ByteBufCodecs.STRING_UTF8))
                    .build());

    public static Item SPEAR;
    public static Item SWORD;
    public static Item STAFF;
    public static Item RUNE;
    public static Item PITY_SHARD;
    public static Item CRAFT_MATERIAL;
    public static Item GOLDEN_SKEWER;
    public static Item CINDER_SKEWER;
    public static Item SPECTRAL_SKEWER;
    public static Item CINDER_IRON;
    public static Item SLAG_STEEL;
    public static Item EMBER_GLASS;
    public static Item CINDER_HELM;
    public static Item CINDER_CHEST;
    public static Item CINDER_LEGS;
    public static Item CINDER_BOOTS;
    /** Per-weapon items (weapon id -> item), registered from the Content Pack. */
    public static final java.util.Map<String, Item> BY_WEAPON = new java.util.LinkedHashMap<>();

    private ModItems() {
    }

    public static void register() {
        SPEAR = Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("hayatemod", "spear"),
                new WeaponItem(props("spear").stacksTo(1)));
        SWORD = Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("hayatemod", "sword"),
                new WeaponItem(props("sword").stacksTo(1)));
        STAFF = Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("hayatemod", "staff"),
                new WeaponItem(props("staff").stacksTo(1)));
        RUNE = Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("hayatemod", "rune"),
                new RuneItem(props("rune")));
        PITY_SHARD = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "pity_shard"),
                new Item(props("pity_shard")));
        CRAFT_MATERIAL = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "craft_material"),
                new Item(props("craft_material")));
        GOLDEN_SKEWER = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "golden_skewer"),
                new Item(props("golden_skewer").food(foodValues(6, 0.8F),
                        consumable("strength", 20, 0))));
        CINDER_SKEWER = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "cinder_skewer"),
                new Item(props("cinder_skewer").food(foodValues(8, 0.6F),
                        consumable("fire_resistance", 60, 0))));
        SPECTRAL_SKEWER = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "spectral_skewer"),
                new Item(props("spectral_skewer").food(foodValues(6, 1.0F),
                        consumable("regeneration", 10, 0))));
        CINDER_IRON = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "cinder_iron"),
                new Item(props("cinder_iron")));
        SLAG_STEEL = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "slag_steel"),
                new Item(props("slag_steel")));
        EMBER_GLASS = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "ember_glass"),
                new Item(props("ember_glass")));
        CINDER_HELM = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "cinder_helm"),
                new ArmorPiece(props("cinder_helm"), net.minecraft.world.item.equipment.ArmorType.HELMET,
                        165));
        CINDER_CHEST = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "cinder_chest"),
                new ArmorPiece(props("cinder_chest"),
                        net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 240));
        CINDER_LEGS = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "cinder_legs"),
                new ArmorPiece(props("cinder_legs"),
                        net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 225));
        CINDER_BOOTS = Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", "cinder_boots"),
                new ArmorPiece(props("cinder_boots"), net.minecraft.world.item.equipment.ArmorType.BOOTS,
                        195));
        // Data-driven: one item per Content Pack weapon so each has its own
        // model/texture. The generic family items stay as compat fallbacks.
        if (ContentHolder.ready()) {
            var ids = new java.util.ArrayList<>(ContentHolder.get().weapons().keySet());
            java.util.Collections.sort(ids);
            for (String id : ids) {
                String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                Item item = Registry.register(BuiltInRegistries.ITEM,
                        Identifier.fromNamespaceAndPath("hayatemod", path),
                        new WeaponItem(props(path).stacksTo(1)));
                BY_WEAPON.put(id, item);
            }
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(SPEAR);
            entries.accept(SWORD);
            entries.accept(STAFF);
            entries.accept(CINDER_HELM);
            entries.accept(CINDER_CHEST);
            entries.accept(CINDER_LEGS);
            entries.accept(CINDER_BOOTS);
            // Data-driven: every Content Pack weapon shows up automatically with its tooltip.
            if (ContentHolder.ready()) {
                for (var card : ContentHolder.get().weapons().values()) {
                    ItemStack stack = WeaponStack.make(card.id(), card.itemLevel());
                    if (!stack.isEmpty()) {
                        entries.accept(stack);
                    }
                }
            }
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries -> {
            entries.accept(GOLDEN_SKEWER);
            entries.accept(CINDER_SKEWER);
            entries.accept(SPECTRAL_SKEWER);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(PITY_SHARD);
            entries.accept(CRAFT_MATERIAL);
            entries.accept(CINDER_IRON);
            entries.accept(SLAG_STEEL);
            entries.accept(EMBER_GLASS);
            entries.accept(RUNE);            // Data-driven: every rune card shows up automatically.
            if (ContentHolder.ready()) {
                for (var rune : ContentHolder.get().runes().values()) {
                    ItemStack stack = new ItemStack(RUNE);
                    stack.set(RUNE_ID, rune.id());
                    entries.accept(stack);
                }
            }
        });
    }

    private static Item.Properties props(String path) {
        // 1.21.11 requires the id on Properties before constructing the Item.
        return new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("hayatemod", path)));
    }

    private static net.minecraft.world.food.FoodProperties foodValues(int nutrition,
            float saturation) {
        return new net.minecraft.world.food.FoodProperties.Builder().nutrition(nutrition)
                .saturationModifier(saturation).build();
    }

    /** 1.21.11 consume pipeline: food values plus an on-eat status effect. */
    private static net.minecraft.world.item.component.Consumable consumable(String effect,
            int seconds, int amplifier) {
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> holder =
                switch (effect) {
                    case "strength" -> net.minecraft.world.effect.MobEffects.STRENGTH;
                    case "fire_resistance" ->
                            net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE;
                    case "regeneration" -> net.minecraft.world.effect.MobEffects.REGENERATION;
                    default -> null;
                };
        java.util.List<net.minecraft.world.item.consume_effects.ConsumeEffect> fx =
                new java.util.ArrayList<>();
        if (holder != null) {
            fx.add(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(
                    new net.minecraft.world.effect.MobEffectInstance(holder, seconds * 20,
                            amplifier),
                    1.0F));
        }
        return new net.minecraft.world.item.component.Consumable(1.6F,
                net.minecraft.world.item.ItemUseAnimation.EAT,
                net.minecraft.sounds.SoundEvents.GENERIC_EAT, true, fx);
    }

    /** Generic rune item: tooltip is generated from the rune card. */
    public static final class RuneItem extends Item {
        public RuneItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> lines, TooltipFlag flag) {
            String runeId = stack.get(RUNE_ID);
            if (runeId == null || !ContentHolder.ready()
                    || !ContentHolder.get().runes().containsKey(runeId)) {
                lines.accept(Component.literal("不明なルーン").withStyle(s -> s.withColor(0xAAAAAA)));
                return;
            }
            var rune = ContentHolder.get().runes().get(runeId);
            try {
                for (String line : Describer.describeRune(rune)) {
                    lines.accept(Component.literal(line).withStyle(s -> s.withColor(0xDDDDDD)));
                }
            } catch (MissingTemplateException e) {
                lines.accept(Component.literal("説明文エラー: " + e.getMessage())
                        .withStyle(s -> s.withColor(0xFF5555)));
            }
        }
    }

    /** Cinder mail: iron-tier ember armor with a full-set fire ward. */
    public static final net.minecraft.world.item.equipment.ArmorMaterial CINDER_MAIL =
            new net.minecraft.world.item.equipment.ArmorMaterial(20,
                    Map.of(net.minecraft.world.item.equipment.ArmorType.HELMET, 2,
                            net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 5,
                            net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 4,
                            net.minecraft.world.item.equipment.ArmorType.BOOTS, 2),
                    9, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 0.5F, 0.0F,
                    net.minecraft.tags.TagKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("hayatemod", "cinder_repair")),
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
                            Identifier.fromNamespaceAndPath("hayatemod", "cinder_mail")));

    /** Generic armor piece backed by a material (no custom class needed in 1.21.11). */
    public static final class ArmorPiece extends Item {
        private final String setId;

        public ArmorPiece(Properties properties,
                net.minecraft.world.item.equipment.ArmorType type, int durability) {
            super(properties.durability(durability)
                    .attributes(CINDER_MAIL.createAttributes(type))
                    .equippable(switch (type) {
                        case HELMET -> net.minecraft.world.entity.EquipmentSlot.HEAD;
                        case CHESTPLATE -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                        case LEGGINGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                        case BOOTS -> net.minecraft.world.entity.EquipmentSlot.FEET;
                        default -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                    })
                    .repairable(SLAG_STEEL));
            this.setId = "cinder";
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> lines, TooltipFlag flag) {
            lines.accept(Component.literal("灰甲冑（4点装備で炎の加護）")
                    .withStyle(s -> s.withColor(0xFF9D5C)));
        }
    }

    /** Generic weapon item: tooltip is generated from the Content Pack card. */
    public static final class WeaponItem extends Item {
        public WeaponItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> lines, TooltipFlag flag) {
            String weaponId = stack.get(WEAPON_ID);
            Integer level = stack.get(ITEM_LEVEL);
            if (weaponId == null || !ContentHolder.ready()
                    || !ContentHolder.get().weapons().containsKey(weaponId)) {
                lines.accept(Component.literal("不明な武器").withStyle(s -> s.withColor(0xAAAAAA)));
                return;
            }
            var card = ContentHolder.get().weapons().get(weaponId);
            lines.accept(Component.literal(card.name()).withStyle(s -> s.withColor(0xFFD700)));
            lines.accept(Component.literal("IL" + (level != null ? level : card.itemLevel()))
                    .withStyle(s -> s.withColor(0x888888)));
            if (card.skills().containsKey("special")) {
                lines.accept(Component.literal("右クリック / G: special発動")
                        .withStyle(s -> s.withColor(0x7FD4FF)));
            }
            if (card.skills().containsKey("heavy")) {
                lines.accept(Component.literal("Shift+右クリック: heavy発動")
                        .withStyle(s -> s.withColor(0xFF9D5C)));
            }
            try {
                for (String line : Describer.describeWeapon(card)) {
                    lines.accept(Component.literal(line).withStyle(s -> s.withColor(0xDDDDDD)));
                }
            } catch (MissingTemplateException e) {
                lines.accept(Component.literal("説明文エラー: " + e.getMessage())
                        .withStyle(s -> s.withColor(0xFF5555)));
            }
        }
    }
}
