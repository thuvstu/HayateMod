# ADR-27: 全点検・locate・新武器・解説書

日付: 2026-10-08 / 設計書§14.2（無検証で採用しない）に沿う総点検と、
「何があるかわからない」問題の解消。

## 点検で見つけた問題と処置

1. **ReportMainのsignal収集がencountersのみ**→ set.skills()も走査。
   condition収集も追加。結果、語彙の使用状況が正確に:
   未使用は予約語のみ（altitude、dash action、giveitem、INTERRUPT_CAST）。
2. **未使用signal 3種を配線**: searing_grip→TANK_SWAP、
   flame_breath→MOVE_TO_SAFE_SPOT、ember_toss→AVOID_AREA。
   INTERRUPT_CASTは能力経路で使用中のため据え置き。
3. **セーブにsave_versionなし（§7.1違反）**→ SaveFiles共通化で
   5ファイル versioned 化（codex/builds/forged/lifeskills/market）。
   旧形式はv0として読込＋次回保存で移行。
4. **WALL_TOP死に定数**→削除。
5. **設計逸脱の記録（コード変更なし、判断はユーザーへ）**:
   - §5.7「汎用アイテム」に対し武器別Item登録（見た目個別化のため）
   - §5.13「固定Dimension」に対し地上アリーナ方式（S2で代替案採択済みの経緯）
   - §6.1の構成表と現行core/mod/tools/contentの差異

## 構造物の存在確認

- `/solommo locate` 追加：集落・アリーナの座標＋距離を表示。
  （`/solommo town` で集落へ直行可）

## スキル追加（新武器2振）

- `solommo:slag_maul`（剣rare14）：燃焼追撃・範囲薙ぎ・残骸召喚heavy。
- `solommo:wisp_lantern`（杖unique16）：誘導鬼火・自爆跳躍heavy。
- テクスチャはtexcraft（magma剣・overheat杖）。武器13振に。
  ContentPackTestの件数更新。

## 解説書

- `docs/GUIDE.md`: 世界・操作・武器13・街・ダンジョン・敵・経済・
  生活・野良・鍛造・実績・コマンド・困ったときは、の総合マニュアル。

## 検証

- `:core:test` 74件合格、`validateContent` errors=0、
  V14は予約語のみ、`:mod:build` 成功、起動スモーク成功
  （content・実績1591・biome 108=2機能×54）。
- 実機目視は未了: locate表示、新武器の発動と見た目、signal3種のNPC応答。
