# 検証済みスニペット (1.21.11 / Mojmap) — S5: Item Component・ツールチップ・クリエイティブ

すべて実コードでコンパイル・起動確認済み（`mod/src/main/java/com/thuvstu/hayatemod/item/ModItems.java`）。

## 0. 最重要：アイテムモデル定義が必須（1.21.4以降）
`models/item/*.json` だけでは紫黒になる。**`assets/<ns>/items/<id>.json` が必須。**
この見落としで全アイテムが紫黒になった（2026-10-07発覚）。以後アセット追加時は定義ファイルを必須チェック。

```json
// assets/hayatemod/items/spear.json
{
  "model": {
    "type": "minecraft:model",
    "model": "hayatemod:item/spear"
  }
}
```

## 1.21.11 の重要変更: Item id 必須
`Registry.register` は id を補完しない。**Item 構築前に `Properties.setId` が必須**。
欠けると起動時 `NullPointerException: Item id not set`。

```java
private static Item.Properties props(String path) {
    return new Item.Properties().setId(
            ResourceKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("hayatemod", path)));
}
// Registries = ResourceKey 置き場 / BuiltInRegistries = 実レジストリ。混同注意。
SPEAR = Registry.register(BuiltInRegistries.ITEM,
        Identifier.fromNamespaceAndPath("hayatemod", "spear"),
        new WeaponItem(props("spear").stacksTo(1)));
```

## 2. 独自 Data Component（保存＋同期）

```java
public static final DataComponentType<String> WEAPON_ID = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        Identifier.fromNamespaceAndPath("hayatemod", "weapon_id"),
        DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

stack.set(ModItems.WEAPON_ID, "solommo:ember_branch");
String id = stack.get(ModItems.WEAPON_ID);
```

- `.persistent` を付けた Component はバニラのアイテム保存・同期に自動で乗る
  （独自セーブ機構は不要。ADR-11 の「バニラ優先」方針と一致）。
- `Identifier` = 旧 `ResourceLocation` の 1.21.11 名。

## 3. ツールチップ（説明文の差し込み）

```java
@Override
public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
        Consumer<Component> lines, TooltipFlag flag) {
    lines.accept(Component.literal("...").withStyle(s -> s.withColor(0xFFD700)));
}
```
署名は 1.21.11 実 jar の `javap` で確認（`TooltipDisplay` が追加されている）。

## 4. クリエイティブタブへの追加（Fabric ItemGroup API）

```java
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;

ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
    entries.accept(SPEAR);                 // ItemLike
    entries.accept(stackWithComponents);   // ItemStack（Component込みで登録）
});
```
- メソッド名は `accept`（`add` ではない）。`CreativeModeTab.Output` 由来。
- **注意**: タブ内容はサーバー構築時に組まれるため、データ駆動アイテムを載せるには
  Content Pack 読込を `onInitialize`（サーバー構築より前）で済ませておく必要がある。
