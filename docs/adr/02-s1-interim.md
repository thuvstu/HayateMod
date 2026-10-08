# ADR-07 中間記録: S1 実機ログの分析 (2026-10-04)

対象: `run/logs/latest.log`（ユーザ実機テスト分）
状態: 暫定。T3（連打時の無敵時間）と再生ポーション分類は再試験待ち。

## 確定したこと（ログが証明）

- ダメージ入口の一本化: `fall / lava / onFire / drown / mob_attack / player / arrow / magic / generic / genericKill`
  の全 `src` が `[ALLOW]` に現れた。T1/T2/T4/T5/T6 の funnel 部分は合格。
- 素手攻撃: `src=player amount=0.848` でスケルトンを撃破。`[DEATH-ALLOW]`→`[DEATH]` 正常。
- 死亡経路: ヒカリイカの溺死、コウモリの炎上、溶岩でのプレイヤー死亡すべてで
  `[DEATH-ALLOW]`→`[DEATH]`→死亡メッセージ。ワイプ原型の検出に使える。
- shield: 214ブロック。`/s1 shield on` 中の溶岩浴でも `[SHIELD]` のみで `[AFTER]` なし。
  ＝迎撃は `ALLOW_DAMAGE=false` で完結する。
- wipetest: `/kill`（`genericKill`）2回とも死亡キャンセル＋HP全快。`[WIPE]` 2件。T10 合格。
- 毒: `src=magic` で25tick毎に1.0。毒は funnel 経由。T9の半分は合格。
- heal 経路: `[HEAL]` 59件。Mixin 正常動作。自然回復は `amount=1.0` が約4秒間隔で
  `[ALLOW]/[AFTER]` なしに現れる。T8（経路分離）は合格。
- 馬の自然回復も `[HEAL]` で検出（11件）。プレイヤー以外にも Mixin が効く。

## 未確定（再試験が必要）

- T3（多段ヒットと無敵時間）: `/s1 hurt` 2回は60秒間隔（tick 1235→2436）で連打になっていない。
  1秒以内の連打で `[AFTER]` が付くかを再試験する。
- ダメージ直後の減衰する小数 heal 連発（例: 0.833→0.667→0.5→0.333→0.167）。
  何らかの再生系効果が付いていたと推測。ユーザが何を飲食したか確認中。
  特定できれば T9 の残り（再生ポーション分類）も埋まる。
