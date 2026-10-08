# ADR-08: Phase 2/3 実装方式と暫定値

日付: 2026-10-04
状態: 採用（テスト13件＋ビルドで確認。実機検証はユーザ実施待ち）

## アーキテクチャ
- `core/engine/`（純粋）＋ `WorldAdapter`（mod 実装）の分離を採用。
  エンジンは `CastContext{skillId, chainDepth, prevent, owner, damageMult}` を因果履歴として伝播し、
  条件判定はイベント開始時スナップショット（§6.6 準拠）。
- ダメージは `ALLOW_DAMAGE` でバニラ分をキャンセルしエンジン値だけ適用するが、
  funnel 自体（`hurtServer`）は経由するため S1 の観測・将来の計測は維持される。
- `fire` 投射物の命中は delivery 意味論として `ignite`(100tick) を付与する。
  将来 content 化する（現状はエンジン定数）。
- プレイヤー近接は武器所持時のみエンジン化。素手・Mob 攻撃はバニラのまま。

## 暫定値（シミュレーター・実測で更新する）
- `item_curve = 1 + 0.05 * item_level`、level/rank 曲線 = 1.0、会心なし。
- `core_base`: 突き6.0 / 単発投射5.0 / 強打12.0 / 召喚0.0（`content/balance/damage.yaml`）。
- プレイヤー RPG 最大HP = 基準表 Lv1（100）。レベル連動は Phase 6。
- `heavy_slam` / `summon_minions` の cast は未対応（`UNSUPPORTED_CORE` を返す）。

## アイテム・永続化
- 汎用 `hayatemod:spear/sword`＋独自 Data Component（`weapon_id`/`item_level`、Codec＋StreamCodec）。
  バニラ保存・同期に乗るため S5 の独自方式は不要だった。
- 図鑑はワールドフォルダの JSON（プレイヤーセーブ Mixin 回避）。
- `/solommo reload` は現状どこでも実行可（街限定は街実装時に）。
- content の jar 同梱は `processResources` で実施済みだが、ゲーム側の読込は
  dev 実行の `./content` / `../content` のみ対応。jar 内読込は配布時に実装する。

## 残課題（Phase 4 以降）
- Mob 側 RPG HP・防具/耐性ルール・クリティカル。
- エンカウンター内アビリティ上書きの merge 規則（現状は同ID並存）。
- 死亡ドロップ・チェックポイント・街限定リロード。
- ゾンビ→煤テーブルのスパイク対応を正式紐付けに置換。
