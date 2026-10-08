# ADR: Phase 1 土台（core ソースセット＋依存固定）

日付: 2026-10-04
状態: 採用（ビルド＋テストで確認）

## 構成判断
- 設計書 §6.1 の `core/ + mod/ + tools/` 分割は、まず単一プロジェクト内の
  `core` ソースセット（`src/core/java`）で代用する。Loom・run設定・datagen に触らないため。
- `coreCompileClasspath` は `org.yaml:snakeyaml` のみで Minecraft を含まないことを確認。
  「core は Minecraft のクラスに依存させない」は class-path 構造で保証される。
- `main`/`client` は `sourceSets.core.output` に依存し、`jar` に core クラスを含める。
- 将来の `core/` モジュール分割は、core が肥大化した時点で再判断する。

## 固定値（ADR-12 関連）
| 項目 | 値 |
|---|---|
| SnakeYAML | 2.3（安全な LoaderOptions を使う 2.x 系。MC同梱版とは別に明示依存） |
| JUnit | jupiter 5.11.4＋platform-launcher（`useJUnitPlatform`） |

## 実装済み（純粋関数・テスト5件合格）
- `core.rules.DamageRules`：`docs/rules-minimal.md` の式をそのまま実装
- `core.rules.DropSolver`：§5.10 の期待周回式と二分法ソルバ（設計例 G=12/E=8→p≈0.077 をテストで再現）

## 追記（2026-10-05）： SnakeYAML の実行時同梱
- `coreImplementation` だけでは dev 実行・jar に SnakeYAML が乗らず、
  実機で `ClassNotFoundException: org.yaml.snakeyaml.error.YAMLException` になった。
- 対策：`include "org.yaml:snakeyaml:2.3"` を追加（Jar-in-Jar 同梱＋dev 実行 class-path）。
  `build/libs/*.jar` 内 `META-INF/jars/snakeyaml-2.3.jar` で確認。
- 教訓：独自ソースセットの依存は Loom の include 対象に載らない。自前で `include` する。

## 残作業（Phase 1・完了済み）
- Content モデル＋YAML厳格ローダー（重複キー・未知フィールド・型違いをエラー）
- 検証器（§9 のうち見本に適用可能な規則）
- 説明文生成（効果定義→日本語ツールチップ）
- CLI（validate / report / drop_solver を JavaExec 化）
