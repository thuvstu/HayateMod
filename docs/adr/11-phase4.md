# ADR-11: Phase 4 方式決定（アリーナ・NPC・エンカウンター）

日付: 2026-10-06
状態: 採用（テスト23件＋ビルド＋起動確認。実機 G1/S2/S3 はユーザ検証待ち）

## S2: 固定Dimensionを見送り、専用アリーナ方式を採用
- オーバーワールド (10000, 100, 10000) に bedrock 25x25 箱（壁高6）をコード生成。
  構造物API・Dimension JSON・動的生成を使わない。
- セッション Entity には `solommo_session` タグを付け、exit 時に範囲スイープ。
  `/solommo dungeon stress N` で enter/exit を繰り返し、残骸ゼロをログ検証する。
- Dimension 方式は将来の高難度・複数並列が必要になったら再検討。

## NPCパーティ（最小脳）
- 村人＋NoAI＋手動ステップ移動（直線のみ。闘技場は空き箱なので経路探索不要）。
  200tick動けなければ最終手段スナップ（§5.11準拠）。
- タンク/ヒーラー/DPS の3役。ダメージ・回復量は基準値スケール暫定
  （タンク54・DPS90/秒、ヒール120/60tick、HP 60/40/40）。
- シグナル対応：STACK / SPREAD / FOCUS_ADDS / MOVE_TO_SAFE_SPOT / AVOID_AREA /
  BRACE（集結＋全体回復）。TANK_SWAP は単タンクのため no-op。
- 非致死のみ `mistake_rate = (1-p)×0.25` で fumble（式どおり、ログ付き）。
- 戦闘不能は恒久消失なし（透明＋停止、チェックポイント/ワイプで復帰）。ADR-10準拠。

## EncounterRunner（単一セッション）
- フェーズ解決は core `PhaseLogic`、敵HPは `EnemyStats` 導出（§5.6式どおり。
  ゴーレム 64800 / DPS 1.5）。
- ボス詠唱（`cast_time`）は予兆＋engine予約実行。`interrupt` 能力者が6m以内なら
  中断（CD30秒）。
- lethal は `lethal_ratio × プレイヤー基準HP(100)` のスパイク換算。
  `split_damage` は範囲内人数で割る（§5.5準拠の式、基準値のみ暫定）。
  プレイヤーレベル導入（Phase 6）で基準値を置き換える。
- ワイプ＝チェックポイントのフェーズ開始へ（ボス全快、MVPルール）。
  死亡画面は出るが、リスポーン後に闘技場へ戻して全快再開する。
- 報酬は `flame_golem_loot` をそのまま振る（直ドロップは武器ILで生成、図鑑記録あり）。

## スキル追加（今回）
- 新規カード4：cinder_bite / ash_spit / slag_cleave / pyre_nova。
- 新規敵4：cinder_imp / ash_crawler（雑魚）/ slag_brute（精鋭）/ pyre_watcher（ネームド）。
- ネームド報酬は直ドロなし・欠片 Fountain（共有通貨としてゴーレム表で消化）。
- ember_branch の primary に on_kill 分裂を追加（設計§5.7の例「撃破で…」の最小形）。
- エンジン追加：on_kill（AFTER_DEATH経由・間接魔法で帰属付け）、heavy_slam、
  summon_minions、予約実行の cancel、tick ポンプ。
- `summon_cinders` に `mods.enemy` を追加（召喚対象の明示）。
