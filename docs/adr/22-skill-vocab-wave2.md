# ADR-22: スキル語彙 第2弾＝フルポート（mythic全量）

日付: 2026-10-07 / 「modとして全部あったほうが良い要素」として
mythicカタログの残りを移植。第1弾11語と合わせ語彙は40語超。

## 追加語彙

- trigger: `on_join`（参加時バフ）
- condition: `holding`（装備ID一致）、`has_effect`（バニラ効果所持）、
  `variable`（変数範囲。max省略で上限なし）、`altitude`（高度以上）、
  `burning`、`day`/`night`/`raining`、`entity_type`
- action: `strike`（直接ダメージ。DoT/処刑の核）、`ignite`/`extinguish`
  （バニラ炎）、`potion`/`cleanse`、`summon`（手下召喚）、`lightning`
  （視覚雷＋直撃）、`explosion`（地形破壊なし。power上限4）、
  `particles`/`sound`（演出）、`message`（欄外通知）、`feed`/`xp`、
  `dropitem`/`giveitem`（通貨3種のみ。pity_shard除外）、`setvar`/`addvar`
  （所有者スコープ変数）、`cast`（同武器スロット連動）、`delay`
  （後続actionをNtick延期。ネスト禁止）
- effect範囲: `scope: area`＋`radius`（範囲内全対象へ適用。オーラ/爆心地用）

## 見送り（理由付き）

- `command`/`setblock`/`remove`: 安全上のため不採用
- `missile`（誘導弾）/`beam`/`repeat`: 子action・誘導基盤が要るため第3弾
- `velocity`/`push`/`pull`/`teleport`: leap/knockback/blink/dashと重複
- `freeze`/`actionbar`/`title`/`sethealth`: potion/message/healで代替可
- `on_eat`: 終了検知にmixinが要るため不採用
- `on_interact`/`on_place`/`on_step`/`on_swing`/`wear`: 戦闘modの核外
- `giveitem`/`altitude`: 実装・検証済みだが配置見送り（前者は経済、
  後者は状況依存）。validatorは両方受け付ける

## 安全装置

- イベント深度guard（語彙上限＋2）。strike応酬・cast循環の無限再入を絶対阻止。
  FakeWorldで往復ファネルを再現し、上限以下で停止することを検証。
- explosionは `ExplosionInteraction.NONE`＋自前ダメージ（オーナー除外、
  クリ/観戦除外）。giveitem系は通貨固定集合。

## 検証

- `:core:test` 64件全合格（新規17件）。
- `:tools:validateContent` errors=0、`:mod:build` 成功、起動スモーク成功。
- Mojmap実名の確認に loom jar の javap を使用（STRENGTH/RESISTANCE、
  SoundEvent直値、getDayTime）。
- 実機目視は未了（第1弾分に加え、直撃・雷・爆発・召喚・変数連携・
  範囲爆心・参加バフ等）。
