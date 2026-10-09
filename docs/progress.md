# 実装進捗ボード（IMPLEMENTATION.md §11.3 対応）

更新: 2026-10-09

> **現行チェックアウト:** 上流 `10d7767` を取り込み、`build` パッケージの欠落は解消済み。
> データ検証・効果予算/条件判定・保存安全性を補完し、補助経路で **278テスト成功、Content検証エラー0**。
> Gradle/Fabric全体ビルドと実機受け入れは未確認です。過去の完了記録と区別してください。
> シールド・回復分配・撃破CD短縮と武器2本を追加。仕様・レビュー・実機手順は [ADR-29](adr/29-shields-and-kill-cooldowns.md)。
> 詳細は [設計差分監査](implementation-audit.md) と [ADR-28](adr/28-safety-and-validation.md)。

## 第4回の接続・監査

難易度、出現時倍率、排他ドロップ、複数装備収集シム、サーバー取引・街ゲートを追加/修正。
仕様と検証は [ADR-30](adr/30-rules-rewards-and-server-transactions.md)。
設計書全体の残作業は [章別対応表](design-coverage.md)。**完全実装は未完了**。

## 第5回の接続・監査

詠唱・中断・次回詠唱短縮・灰詠みの杖を追加。MP/ST・heavy CD・詠唱HUDを接続。
発動時スナップショットで投射のルーン/鍛造効果消失を修正し、固定seed効果ファズCLIを追加。
[ADR-31](adr/31-casting-snapshots-and-effect-fuzz.md)。設計全体・実機受入は引き続き未完了。

## 第6回の接続・監査

効果の優先度・適用元・ID順序、遅延予約順、同期ダメージ因果・撃破snapshotを接続。
死亡実体の重複排除と爆発二重ダメージを修正。37テスト追加、計278件成功。
[ADR-32](adr/32-effect-order-and-causal-damage.md)。Mod境界は実機未確認、DOT/召喚因果等は未完了。

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

まず [設計差分監査](implementation-audit.md) に従い、Fabric全体ビルドを確認し、
ロードアウト/C2S境界・保存ドメイン・効果の因果伝播を補完する。
以下は従来の実機検証バックログ。

1. Phase 2/3 の実機確認（`docs/p23-test.md` の P0〜P5）→ Gate判定
2. S2 スパイク（固定Dimension・アリーナ清掃20回）→ ADR-08
3. S3 スパイク（NPCシグナル: SPREAD/STACK/MOVE_TO_SAFE_SPOT）
4. Phase 4 実装（インスタンス・編成・ボス・チェックポイント）→ G1判定

## 検証コマンド

```powershell
./gradlew :core:test                # 件数は実行結果で確認
./gradlew :tools:validateContent    # Content Pack 検証
./gradlew :mod:build                # Mod ビルド
./gradlew :mod:runClient            # 実機
```
