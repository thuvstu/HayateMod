# ADR-28: データ検証・効果予算・安全な保存

日付: 2026-10-09
状態: 実装＋補助経路の自動テスト合格。Gradle/Fabricビルド・実機は未確認。
対応: 設計書 §6.2、§6.6、§7.1、§9。

## 1. ソース復旧

上流 `10d7767` を `arena/62167248-hayatemod` にマージ。
7つの未解決参照が解消。上流の `.gitignore` を採用し、Javaの `build` パッケージを保持する。
前回のContent Pipeline変更は別コミットで保持した上で統合した。

## 2. パーサー

- `Maps.opt*` は未指定時だけ既定値を黙って使う。指定値の型不一致（明示null含む）は
  ファイル名/項目パス付きの `ContentError` を追加する。
- エラー収集を継続するため不正値に対しても一時的に既定値を返すが、パックは公開しない。
- int/boolean/string/number間の暗黙変換はしない。numberは有限値のみ。
- 武器とボス能力のmods語彙を共通化し、キーと型を検査する。
- pity.exchangeのnullは「交換なし」として従来どおり許可。非nullならcost/item必須。
- 任意のスキル枠名・マーカー名・ランク名・ロール名といった動的なキーは、固定スキーマの
  未知キー検査とは区別する。すべての意味/座標/参照検査が完了したという主張はしない。
- 正本に存在する `balance/reference.yaml` の `tuning_notes` は、型検査する設計メモとして維持。
- Rulesetの倍率は形を検査するだけで、未実装の挙動まで実装済みとはしない。

## 3. 効果処理

### 語彙データの実行予算

`content/vocabulary/core.yaml` の `limits` に追加:

| キー | 正本の初期値 | 対象 |
|---|---:|---|
| max_effects_per_tick | 256 | 条件判定を行う効果/対象ペア |
| max_actions_per_tick | 512 | アクション（delay/repeat制御も含む） |
| max_tasks_per_tick | 128 | 予約タスクの取り出し・実行 |
| max_pending_tasks | 1024 | 未実行タスクの保持件数 |

旧パックの省略時は同じ初期値で互換性を保つ。Validatorは各予算の1〜65536を検査する。
エンジンの予算は全プレイヤー共有で、`world.gameTime()` が変わった時だけリセット。
同期処理、派生イベント、遅延タスクが別々の予算を持って抜け道になることを防ぐ。

- 上限超過アクションは破棄。タスク上限でまだ取り出していない期限済みタスクは後tickへ繰り越す。
- キュー満杯時の新規scheduleはnullを返す。ボス予兆側も失敗を処理し、架空の予兆を保持しない。
- repeatは発射数上限以内にクランプし、巨大countをループする前に制限する。
- 遅延tickの加算/乗算のoverflowを飽和させる。
- 全体のchain_depthは最大3。キーストーンは効果局所の上限を増やせても全体上限は越えない。
  既に局所上限3の効果に対するchain_bonusの価値は、今後のバランス監査項目。
- `prevent_recursive` と因果履歴が交差する場合は効果全体を抑止する。
- CastContextは履歴Setを防御コピー。cast派生は正しいスキル枠IDを継承。

### 条件判定・順序

命中ではダメージ適用前、他のイベントでは最初のアクション実行前に、対象効果の条件を判定して
実行計画を作る。複数スキル枠や範囲対象も、先行アクションの変数/HP/状態変化を後続判定に混ぜない。
chanceもこの段階で判定し、アクション実行時に再抽選しない。
スキル枠ID順、効果配列順、範囲対象UUID順で処理する。
明示的な効果priority/source/idのモデル化は未実装であり、現行の安定順をそれと同一視しない。
Mod境界から新規コンテキストで再入するダメージイベントの因果伝播も継続課題。

遅延効果のステータス期限は予約時でなく実行時のgameTimeから数える。
timerのarea効果が「周辺人数×周辺人数」回実行される重複も除去した。

## 4. 保存

### v2エンベロープ

```json
{"save_version":2,"content_version":1,"data":{}}
```

`content_version` は**対応するコンテンツスキーマの版**。Modリリース番号やパックのハッシュではない。
現行ペイロードはスキーマ1。将来スキーマ変更時は明示的な変換を追加する。

- v0（裸のobject）/v1（save_version＋data）は、ペイロードを維持して次回保存時にv2へ移行。
- 未来版、負の版、非数値/非整数の版、必須メタデータ欠落、nullペイロードは拒否。
- 読み込みに失敗したパスはプロセス終了まで書き込み禁止にする。
  既存の呼び出し元がnullを空状態として扱っても、壊れた/未来版の原本を消さない。
- load未実行のsaveでも既存エンベロープを事前検査。
- 修復後は再起動して再ロードする。実行中に禁止フラグだけを解除して空状態を書き込まない。
- JSONシリアライズ → 同一ディレクトリの一時ファイル → `FileChannel.force(true)` →
  `ATOMIC_MOVE`。非対応FSでは失敗させ、直接上書きにフォールバックしない。
- この方式はファイル単位。複数の進行ファイルの一括トランザクション、
  ハードウェア障害まで含む完全な耐久性、ドメイン値の完全検証は保証しない。

`AtomicSaveFile` と `SaveVersions` はMinecraft/Gson非依存のcoreへ抽出。
`SaveFiles` はGson/ログと読み込み失敗後の保護を担うアダプターとして維持する。
`PlayerProfileStore` のドメイン抽象化は今後の作業。

## 5. 検証

### 通常のコマンド

```sh
bash gradlew :core:test :tools:validateContent :mod:test :mod:build
```

この環境では `services.gradle.org` への接続が失敗するため、**Gradle成功とは記録しない**。

### 実際に使った補助経路

許可されたPyPI/npm/GitHubから取得し、リポジトリ外の作業キャッシュに配置。
成果物/JRE/依存ソースはGitに入れない。プロジェクトのGradle依存定義を回避用に変更もしない。

- Java: `jdk4py==21.0.8.2` のTemurin 21.0.8+9ランタイム。
- コンパイラー: npm `@vscjava/java-language-server@0.1.2` 同梱の
  `org.eclipse.jdt.core.compiler.batch_3.45.0.v20260224-0835.jar`。`-21` 指定。
- SnakeYAML: GitHubミラーの2.3リリースコミット `01521bc09001` のmainソースをコンパイル。
- JUnit Jupiter/Platform: JUnit `r5.11.4` のAPI/commons/engine/launcherをソースビルド。
  API Guardian `r1.1.2`、OpenTest4J `r1.3.0` を使用。
  公式JRE.javaテンプレートからJava8〜25のenumを展開し、Java21でのchecked exception宣言に
  合わせてテスト基盤のSerializedFormコンストラクターにClassNotFoundException宣言を追加。
  プロジェクトのテストやAssertionsを代替実装に置き換えてはいない。
- 保存アダプター用: 同npm配布物内のGson 2.13.2、SLF4J API 2.0.17。
- コンパイル対象: core main/test全体、tools全体、modのSaveFilesとSaveFilesTest。
- JUnit LauncherからJupiterTestEngineを登録し、`com.thuvstu.hayatemod` を探索して実行。

結果: **131件開始、131件成功、0件失敗、0件スキップ**。
追加43件: StrictParsingTest 13、EffectSafetyTest 17、SaveSafetyTest 5、SaveFilesTest 8。
前回未実行だった14件も含む。補助経路にはFabric本体/クライアントは含まれない。

- Content検証: `loaderErrors=0 issues=9 errors=0`（9件は循環候補の情報）。
- Contentレポート: 成功、参照切れ一覧は空。
- 市場30日シミュレーション: 正常終了。
- 自己レビューで正本tuning_notesの互換性とルート実行時のTestContentパス不具合を検出・修正。
- 同tick再実行、予約飽和、極端なrepeat、条件への副作用混入、保存の置換失敗/未来版/破損を回帰テスト。

**未検証:** Gradleの依存解決・Fabric API型整合、Mod JARビルド、実機保存/再起動、TPS、受け入れA2〜A9。
