# ADR: Phase 0 データ見本の状態

日付: 2026-10-04
状態: 見本作成済み・検証器は Phase 1 で実装

## 置いたもの

- `docs/rules-minimal.md`：ダメージ式・致死ギミック・敵導出の1ページ仕様（将来 `core/` へ移動）
- `content/balance/reference.yaml`：Lv1/5/10/15/20 の基準キャラ表（暫定値）
- `content/balance/damage.yaml`：会心・上限・ランク別目標戦闘時間（暫定値）
- `content/vocabulary/core.yaml`：見本が使う最小語彙＋実行時上限
- `content/rulesets/normal.yaml`：通常難易度（基準・差分なし）
- `content/weapons/ember_branch.yaml`：設計書 §6.3 のユニーク見本（語彙との照合済み）
- `content/enemies/`：雑魚1・ボス1（`EnemyData` の最小項目）
- `content/encounters/flame_golem.yaml`：設計書 §10.1＋§10.2 の概念例（`molten_slam` の STACK 定義含む）
- `content/jobs/knight.yaml`、`content/loot/*.yaml`：最小見本（ボス直ドロップ p=0.077 は §5.10 の例値）

## 意図的な未完（Phase 1 以降が引き取る）

- 検証器がないため、参照切れチェックは目視のみ。以下は未定義のまま置いている：
  - アビリティカード：`flame_breath`、`ember_toss`、`searing_grip`、`lava_spread`、`summon_cinders`、`meltdown`、`cinder_swipe`
  - アリーナテンプレート：`solommo:flame_golem_arena`
  - `interrupt` 能力を持つNPC名簿（`content/npcs/` は空）
  - `mods` のキー語彙（`range`、`cooldown` 等の許容キー表は未作成）
- `content/` はまだ Mod に同梱・読み込みされていない（ローダーは Phase 1）。
- `core/` モジュール分割（Gradle マルチプロジェクト化）は Phase 1 で行う。
  現時点では単一モジュール＋`content/` 直置き＋`docs/rules-minimal.md` で代用する。
