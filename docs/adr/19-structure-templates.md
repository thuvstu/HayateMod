# ADR-19: データ駆動の構造物テンプレート (ST1)

日付: 2026-10-07 / 対象: IMPLEMENTATION.md ST1（構造物テンプレート配置基盤＋移行＋見栄え）

## 決定

- `content/structures/*.yaml` に `fill` / `set` の op 列だけを書く。座標は原点相対、
  `y=0` が床面（アリーナは床上端、集落は地表面）。適用は記載順。
- 使えるブロックはパレット制（16種）。`core/build/StructurePlanner.java` の
  `ALLOWED_BLOCKS` が正本で、検証器（V02）と mod 側配置器が同じ集合を参照する。
- アリーナのマーカー定義（`content/arenas/*.yaml`）に `structure:` を追加し、
  どの block テンプレートを建てるかを遭遇戦データから辿れるようにした。
  存在しない structure 参照は V04 エラー。遭遇戦ごとのアリーナ分岐は不要
  （`slag_colossus` も `flame_golem_arena` を再利用）。
- 配置の実行だけが `mod/build/StructureBuilder.java`。core 側は
  `StructurePlanner.materialize()` で座標展開するのみ（純粋・テスト可能）。
- `ArenaManager` / `TownManager` の手書きループは削除し、テンプレート配置＋
  既存の仕上げ（たいまつ補充・住民・スポナー維持）に縮小した。
  テンプレート欠落時は入場を中断する（`ensureArena` が boolean を返す）。

## 内容物

- `solommo:flame_arena`: bedrock 箱＋obsidian 縁取り＋南門アーチ＋隅 glowstone＋床たいまつ。
- `solommo:hamlet`: cobble 基盤＋砂利十字路＋屋台3＋小屋2＋井戸（着地点を避けて (5,5)）
  ＋畑（水心付きで farmland 維持）＋南門つきフェンス＋たいまつ。
- テンプレート体積上限 200000（V03）。現状アリーナ約 5.1k、集落約 5.4k。

## 検証

- `:core:test` 全合格（`StructurePlannerTest`: fill 展開＋実テンプレートの
  ロード/検証）。
- `:tools:validateContent`: loaderErrors=0 errors=0（ついでに SK2 の
  zephyr_edge 由来 V10 `distance` mod の説明文欠落を修正、`ContentPackTest`
  の件数も 11/13/7/2 に更新）。
- `:mod:build` 成功。
- `:mod:runServer` 起動成功（content 11/13/7/2/3 をロード、例外なし）。
  空サーバの tick 休止により、tick 2400 発火の集落自動建設は本スモークでは
  未観測。実機での `/solommo town` 目視を `docs/survive-test.md` 側で確認する。
