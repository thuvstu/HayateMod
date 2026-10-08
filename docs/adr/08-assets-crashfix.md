# ADR: クラッシュ修正＋アイテムアセット

日付: 2026-10-05

## クラッシュ `NullPointerException: Item id not set`
- 原因：1.21.11 では `Item` 構築前に `Item.Properties.setId(ResourceKey)` が必須。
  `Registry.register` は id を補完しない。
- 修正：`ModItems.props()` で `ResourceKey.create(Registries.ITEM, hayatemod:<path>)` を設定。
  `Registries`（キー）と `BuiltInRegistries`（実体）の使い分けも同時に修正。
- 教訓：1.21.2+ の登録まわりは記憶の API が通用しない。雛形・実 jar・コンパイラで確認する。

## アセット（MCAssetGen texcraft CLI で生成）
| アイテム | sample | preset | size |
|---|---|---|---|
| spear | sword | magma | 16 |
| sword | sword | enchant | 16 |
| pity_shard | gem | crystal | 16 |
| craft_material | diamond | gold | 16 |

- 出所：ユーザ所有の MCAssetGen（`texture/texcraft`）。自作生成物のため利用権の問題なし。
- 槍は剣シルエットの暫定。槍らしい細身シルエットは AegisBlade 等での作り直し項目とする。
- モデル：武器は `minecraft:item/handheld`、欠片・素材は `minecraft:item/generated`。
