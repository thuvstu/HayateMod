# ADR-24: mythic丸パクリv1（マナ・式・repeat・誘導弾）

日付: 2026-10-08 / mythicforge（新版 no-coding-fabric-mod-builder 含む）の
ランタイム意味論を HayateMod へ移植。負けは認めた。設計は勝った
（新版forgeも「外見ごとに起動時登録の本物Item＋個体差はcomponent」であり、
当modの個別Item化と同型）。

## 移植物

- **リソース強制**: stamina/mana プール（100上限、stamina 15/s・mana 2/s
  再生、mythic既定値）。castSkill が mods resource/resource_cost を
  消費し、不足時は NO_RESOURCE（チャット通知）。ember_branch の
  スタミナ10と pyre_staff special のマナ15（新設）が初適用。
  非プレイヤーは常時成功（ボスは免除）。
- **数値式**: core/engine/Formula.java（安全な式評価器。四則・括弧・
  単項・min/max/clamp、除算ゼロ封じ）。束縛は lastDamage/health(0-100)/
  mana/stamina/var_*。ActionDef.formula が amount を置換（heal・ignite・
  potion・feed・xp・setvar/addvar・delay・repeat間隔）。ローダーが即時
  失敗、検証器がV06＋未知名V02。例: zephyr撃破回復 `10+mana*0.2`。
  damage_mult は定数のまま（次弾）。
- **repeat**: 直前action群を count回・amount間隔で再実行（mythic
  repeatEvery のflat-list適応。delay/repeatの入れ子は除外）。
  例: ashen_call の三連雷。
- **誘導弾 missile**: 発射時に1体ロック、tick毎に0.3補間で追尾、
  lifetime切れで破棄＋追跡解除。命中は既存bolt経路（同一ctx）。
  例: cinder_pike の追尾2連。

## 実装口（追加コードの受け入れ先として維持）

- 式の文法拡張→Formula.java のみ。束縛追加→evalAmount。
- プール実体→McAdapter（POOLS）。HUD表示は未着手（通知のみ）。
- 誘導→McAdapter.tickMissiles。発射→spawnMissile 一本。

## 検証

- `:core:test` 73件全合格（FormulaTest 4＋engine 5新規）。
- `:tools:validateContent` errors=0、`:mod:build` 成功、起動スモーク成功。
- 実機目視は未了: マナ枯渇通知、式回復量、三連雷、誘導弾の追尾、
  リソース自然回復。
