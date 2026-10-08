# ADR-09: マルチモジュール化リファクタリング（設計書 §6.1 準拠）

日付: 2026-10-06
状態: 採用（13テスト合格・CLI green・runServer スモーク成功）

## 構成
```
hayatemod/
├─ settings.gradle          # core / mod / tools を include
├─ build.gradle             # 共通座標＋ルートの便利タスク
├─ core/                    # 純Java。Minecraft依存ゼロ（構造で保証）
│  └─ src/main, src/test
├─ mod/                     # Fabric Mod（Loom 1.14.10 / MC 1.21.11）
│  └─ src/main, src/client, runDir=../run（クライアント）
├─ tools/                   # CLI（validateContent / contentReport / dropSolve）
├─ content/                 # 正本データ（従来位置のまま）
└─ run-server/              # サーバー検証用（run/ と分離。.gitignore 追加）
```

## 判断
- **srcSet 方式を廃止**：`src/core` + `coreImplementation` + `include` のハックは
  依存が dev 実行に乗らない不具合（`ClassNotFoundException: YAMLException`）を生んだ。
  正式モジュールでは `implementation project(':core')` により、core の `implementation`
  依存（snakeyaml）が mod の runtimeClasspath に自動で乗る。jar へは `include` で同梱。
- **CLI は tools へ移設**（`core.cli` → `tools` パッケージ）。core はゲームデータ・
  ルール・検証までを担い、実行エントリを持たない。
- **テストは core に同居**。content パスは `TestContent.dir()` が
  `content / ../content / ../../content` を探索する。
- **run ディレクトリ**：クライアントは従来の `run/` を維持（ユーザーのワールド保全）。
  サーバーは `run-server/` に分離（クライアント起動中のロック事故を防ぐ）。
  Loom の `runDir` は絶対パス文字列を受け付けないため相対 `../run` で指定する。

## スパイク整理（G0/ADR-07 の卒業）
- `s1.S1DamageFunnel` → `debug.DamageDebug`（`/s1` は維持しつつ `log on|off` を追加）
- `s4.S4Combat` → `debug.CombatDebug`（`/s4 dummy` は訓練用として維持）
- `s4.FireboltRequest` → `net.SkillCastPayload`（チャンネル `hayatemod:skill_cast`）
- `client.S4KeyHandler` → `client.SkillKeyHandler`
- **ログ抑制**：`DebugFlags.damageLog` を導入し、`ALLOW/AFTER/HEAL` の全件ログは
  `/s1 log on` のときだけ出力。通常プレイのログ汚染を解消（従来は1セッション405行超）。
- `HealLoggerMixin` は生産コードとして維持（heal の RPG 転送は ADR-07 の中核）。

## 整理
- `Parsers` / `ContentSet` / `Validator` / テストの全限定名（`java.util.*`・`Models.*`）を
  インポートに統一。`ContentPack` の重複 import を削除。
- `Hayatemod` は登録配線のみの薄いエントリポイントに整理。コンテンツ読込は
  creative タブ構築（サーバー構築時）より前に実行する必要があるため `onInitialize` 先頭。

## 検証
- `./gradlew :core:test` 13件合格
- `./gradlew :tools:validateContent` errors=0
- `./gradlew :mod:build` 成功。jar に core クラス・`content/`・`META-INF/jars/snakeyaml-2.3.jar`
- `./gradlew :mod:runServer` → `[content] loaded` → `Done`。例外なし
