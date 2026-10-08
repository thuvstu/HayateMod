# ADR: Phase 1 完了（Content Pack パイプライン）

日付: 2026-10-04
状態: 採用。受け入れ基準 **A1 合格**（武器・敵・Encounter のサンプルが検証器を通り、説明文が生成される）。

## 成果物
- `core/content/`：厳格YAMLローダー（SafeConstructor・重複キー拒否・未知キー/型違いを収集）
- `core/validate/`：§9 規則のうち V02/V03/V04/V05/V06/V07/V08/V09/V12/V13
  （V01はローダー側、V10は説明文dry-run、V11は対象機構なし、V14はレポート側）
- `core/describe/`：効果定義→日本語ツールチップ（テンプレ欠落は `MissingTemplateException`→V10）
- CLI：`validateContent` / `contentReport` / `dropSolve`（`./gradlew` タスク化）
- テスト9件合格（正常系＋異常系パック。異常系は V02/V03/V04/V06/V07/V08/V09/V12 を全コード確認）
- データ：スキル8・闘技場1・NPC3を追加し `validateContent` は errors=0（INFO 1件のみ）

## 採用した決定
- 参照IDは完全修飾（`solommo:xxx`）に統一。設計書 §10 の概念例の bare 記法は不採用。
- エンカウンター内同名アビリティは全体カードへの上書きとして扱う（優先順位の厳密な merge 規則は Phase 2）。
- pity 通貨・素材は閉世界（`pity_shard` / `craft_material`）。交換品は武器参照。
- 武器 family・NPC capability は Phase 1 では語彙検査しない（将来の語彙化項目）。

## レビュー所見（次へ進む前の確認）
- `Parsers` の `java.util.ArrayList` 完全修飾が数カ所に残存。動作に影響なし、Phase 2 で整理。
- 未使用語彙（`on_kill`・未使用シグナル7種）は MVP コンテンツの不足を示す正常な報告。故障ではない。
- `contentReport` のコンソール文字化けは PowerShell の表示問題（テストで日本文を検証済み）。
