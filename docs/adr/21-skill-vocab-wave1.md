# ADR-21: スキル語彙 第1弾拡張（mythic参照）

日付: 2026-10-07 / mythicforge（新基盤・Fabric上位互換）と
mythiccraft（MythicMobs YAMLブリッジ）のカタログを参照し、
既存基盤で完結する11語を取り込んだ。全35種の一括移植は見送り
（変数・delay/repeatメタ・ホーミングミサイル等は第2弾）。

## 追加語彙（content/vocabulary/core.yaml）

- trigger: `on_break`（採掘proc）、`on_death`（死に際バースト）、
  `on_timer`（オーラ。skill mods `interval` 秒＋`radius` m）
- condition: `chance`（value 確率0-1）、`sneaking`（振った本人の屈み）、
  `health_below`（対象HP割合、value 0-1）
- target: `look`（視線方向へ直進＝スキルショット。従来はホーミングのみ）
- action: `apply_status`（対象にstatus付与。エンジン側status、無害マーカー）、
  `blink`（対象位置へ瞬間移動）、`leap`（前方跳躍。distance＋固定上昇）、
  `knockback`（対象を外向きに弾く。威力＝damage_mult）
- 発動枠: `heavy` スロット（Shift+右クリック。mythic shiftSkillId相当）。
  `SkillCastPayload` に slot 追加、サーバは special/heavy のみ受理。

## 配線

- core: Models（ConditionDef.value、ActionDef.status）、Parsers、
  Validator（chance/health_below範囲V03、target既知値・apply_statusの
  status存在V02、on_timerのinterval必須V06）、Describer（V10対応）、
  EffectEngine（onBreak/onDeath/onTimer、条件・4 action・look照準）、
  WorldAdapter（launch/isSneaking/healthRatio/rollChance）。
- mod: McAdapter 実装＋3フック（BlockBreak AFTER、AFTER_DEATH拡張、
  毎秒tickのon_timer）、RpgSkills.castHeldSlot、ツールチップ2行。
- 適用は既存AoEと同基準（自分・クリ以外）。apply_status は
  ダメージなしのエンジン内マーカーで、村人等への副作用なし。

## 武器への配置（各語に使用例）

- ember_edge: heavy＝強打＋knockback／zephyr_edge: heavy＝突き＋blink
- slag_cleaver: on_break chance0.3→heal4／ashen_call: on_death→6連バースト
- cinder_staff: on_timer interval6→igniteオーラ／soot_spear: sneaking奇襲弾
- earthshaker: HP30%未満へknockback処刑／ember_branch special: look直進弾
- cinder_pike: 被弾時leap離脱

## 検証

- `:core:test` 全合格（新規8件: break確立・deathバースト・timer間隔・
  sneaking/healthゲート・look直進・blink/leap）。
- `:tools:validateContent` errors=0、`:mod:build` 成功、
  `:mod:runServer` 起動成功（content 11/13/7/2/3）。
- 実機目視は未了: heavy発動、採掘proc、deathバースト、オーラ、
  スニーク奇襲、処刑ノックバック、直進弾、leap。
