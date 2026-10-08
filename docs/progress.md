# 実装進捗ボード（IMPLEMENTATION.md §11.3 対応）

更新: 2026-10-06

## フェーズ状況

| 段階 | 内容 | 状態 | 根拠 |
|---|---|---|---|
| Phase 0 | 雛形・ビルド固定・core/rules最小仕様・データ見本 | ✅ 完了 | `docs/adr/00-phase0-versions.md`, `01-phase0-content.md` |
| Phase 0.5 | S1 / S4 / S5 | ✅ 完了 | S1: `docs/adr/03-adr07-rpg-hp.md` / S4: `docs/adr/04-g0.md` / S5: `docs/ai/verified_snippets/1.21.11/s5-items.md` |
| Phase 1 | core・ローダー・検証器・説明文・CLI | ✅ 完了（A1合格） | `docs/adr/06-phase1-done.md` |
| Phase 2 | スキル核・効果イベント・RPG体力・デバッグ計測 | 🟡 実装済み・実機確認待ち | 核4種＋on_hit/on_kill・ADR-07体力・`/s1 log`・`/s4 dummy`（`docs/adr/07-p23-engine-items.md`） |
| Phase 3 | 武器・ドロップ・欠片交換・表示・図鑑 | 🟡 実装済み・実機確認待ち | give/ドロップ/交換/図鑑/ツールチップ。テスト `docs/p23-test.md` |
| Phase 4 | 固定インスタンス・NPC編成・ボス・チェックポイント | 🟡 実装済み・実機確認待ち（G1/S2/S3） | アリーナ・NPC3人・ゴーレム2フェーズ・ワイプ・報酬（`docs/adr/11-phase4.md`）。テスト `docs/g1-test.md` |
| Phase 5 | ルーン・キーストーン・ロードアウト・分解クラフト | 🟡 実装済み・実機確認待ち | ルーン6・キー4・ソケット・ビルド署名・タグ一致（`docs/adr/13-phase5.md`）。テスト `docs/p5-test.md` |
| Phase 6 | NPC市場・生活スキル・街ループ | 🟡 ゲームロジック＋UI＋街＋量産まで実装・実機確認待ち | 市場画面・工房・ロードアウト・酒場・図鑑・HUD、レア7＋U3本目、釣り、集落（`docs/adr/15-ui-content-town.md`）。テスト `docs/p6-test.md`＋`docs/ui-test.md` |

## リファクタリング（設計書§6.1対応）

✅ 完了（`docs/adr/09-refactor-modules.md`）: core / mod / tools の3モジュール化、
スパイクの debug パッケージ集約、ログゲーティング（DebugFlags）。

## アセット（アドオン作業）

✅ 64px・アニメ・3Dモデル導入（`docs/adr/10-assets-3d.md`）
- 槍: 専用3Dモデル（58要素）+ 炎アニメ（32x192, 6f）
- 剣: SkyForge画＋エンチャントアニメ合成（64x384, 6f）
- 杖: Spellforge火杖アニメ（64x384, 6f）＋検証用2本目ユニーク `solommo:ashen_call`
- 欠片・素材: 64px静止

## 直近の作業

1. Phase 2/3 の実機確認（`docs/p23-test.md` の P0〜P5）→ Gate判定
2. S2 スパイク（固定Dimension・アリーナ清掃20回）→ ADR-08
3. S3 スパイク（NPCシグナル: SPREAD/STACK/MOVE_TO_SAFE_SPOT）
4. Phase 4 実装（インスタンス・編成・ボス・チェックポイント）→ G1判定

## 検証コマンド

```powershell
./gradlew :core:test                # 13テスト
./gradlew :tools:validateContent    # Content Pack 検証
./gradlew :mod:build                # Mod ビルド
./gradlew :mod:runClient            # 実機
```
