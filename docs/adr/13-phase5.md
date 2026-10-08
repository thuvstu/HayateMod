# ADR-13: Phase 5（ビルド・クラフト・分解）

日付: 2026-10-07
状態: 採用（テスト31件＋ビルド＋起動確認。実機は `docs/p5-test.md` で検証待ち）

## ルーン（6種）
- `content/runes/` に効果定義。武器ソケット（2枠、`rune_sockets` Component）に装着し、
  キャスト時にスキルへ効果をマージする（`WeaponSkillMerger`）。
- 既存語彙のみ：on_hit/on_kill × spawn_projectiles の組み合わせ。
- ルーン品は汎用 `hayatemod:rune`＋`rune_id` Component。クリエイティブに全種自動表示。

## キーストーン（4種・最大3装備）
- `chain_bonus / damage_mult / cooldown_mult / melee_mult` を持ち、`BuildMods` に合成
  （chain加算、倍率乗算）してキャストへ渡す。ビルド選択が戦い方に影響する。
- `/solommo keystone <id>` で切替、`/solommo loadout save|load` で保存・復元
  （武器ID＋キーストーン。`builds.json` に永続化）。

## 分解・クラフト（決定論的・§5.9準拠）
- 分解：素材 `IL/5+1` 個。図鑑登録済み（重複）のみ欠片x2（重複救済）。
- クラフト：素材 `IL×3`＋ユニークは欠片10。必須工程にランダムなし。
- リソース系modsは未導入のため、スタミナ等の消費検証は行わない（将来）。

## タグ・ビルド署名
- `TagDeriver`：アイテムタグ（delivery/element/status/extra）とビルド署名
  （ジョブ＋スキル核＋ルーン＋キーストーン）。一致数は目安表示。
- マッチ用に delivery 展開した `matchSet` を別途用意（署名自体は設計どおり）。

## 残課題（Phase 6 以降）
- アフィックス生成・第3アフィックスの小ランダム（MVP範囲だが未着手）。
- ロードアウトの街限定・戦闘中切替不可（現状どこでも可）。
- リソース（スタミナ等）の実装と検証。
