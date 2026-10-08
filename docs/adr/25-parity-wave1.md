# ADR-25: パリティwave1（村・実績・食料・復活）

日付: 2026-10-08 / 「全要素で負けない」対応の第1弾。村人店は既存対応済み
（folk右クリック→酒場/工房/市場）のため無改修。

## 追加

- **スポーン村保証**: GroundSites.townSiteSpawn。世界スポーン中心に
  半径160で陸地探索、なければ従来ヒント、海だけならフォールバック。
  スポーン次元判定＋例外時はヒントへフォールバック。
- **実績7件**: data/hayatemod/advancement（root＋集落/初陣/2ボス/
  初クラフト/ウォッチャー）。rootは所持トリガー、他はimpossible＋
  コード付与（AdvancementHelper）。起動1591件（+7）を確認。
  付与点: teleportTown、dungeon enter、onVictory（encounter別）、
  CraftOps.craft、watcher討伐。
- **食料3種**: 串焼き（金/煤/幽）。1.21.11のconsume管轄
  （FoodProperties＋Consumable＋ApplyStatusEffectsConsumeEffect）で
  効果付き。テクスチャはtexcraft生成。FOODタブ登録。
- **second wind**: RPGプール致死ダメージで全快＋消火＋通知＋
  TOTEM演出、10分CD。ワイプ/死亡経路とは独立の安全網。

## 次wave（宣言）

- 防具セット＋素材種別、鉱石/地表ワールド生成、工房one-off鍛造、
  /sp相当（現状keystoneで代替）。NBT importは不採用（fill/set
  テンプレート＋検証器の設計を維持）。

## 検証

- `:core:test` 73件合格、`validateContent` errors=0、`:mod:build` 成功、
  起動スモーク成功（content・実績・アイテム登録）。
- 実機目視は未了: スポーン村立地、実績トースト7種、串焼き効果、
  second wind発動。
