# core/rules 最小仕様（Phase 0・1ページ）

> 正本候補。将来 `core/` モジュールに移すまでの暫定置き場。
> 係数の実数値は `content/balance/`、難易度差分は `content/rulesets/` を見る。

## ダメージ式（§5.4）

```text
core_power =
    skill_core_base
  × player_level_curve
  × item_level_curve
  × skill_rank_curve

raw_damage =
    core_power
  × damage_modifiers
  × critical_multiplier

after_resistance =
    raw_damage × (1 - target_resistance)

final_damage =
    after_resistance
  × (1 - armor_mitigation)
  × (1 - guard_reduction)
```

- 内部計算は浮動小数点。表示時のみ丸める（二重丸め禁止）。
- `damage_mult` は通常スキルの倍率。致死ギミックには使わない。
- 上下限・会心・曲線の実数値は `content/balance/damage.yaml`。
- ゲーム実行とシミュレーターで同一実装を使う。

## 致死ギミック（§5.5）

- `lethal_ratio` で指定。`1.0` = 基準キャラの基準HP相当。
- `center`・範囲・分担解決後に対象へ配分。`split_damage: true` は総量を成立人数で割る。
- 成功時分担ダメージは基準HPの 0.6 以下が目標。
- 標準防具での軽減方法は一意に定め、検証器とゲーム実装で共有する。

## 敵数値の導出（§5.6）

```text
enemy HP ≈ reference DPS × rank別目標戦闘時間 × ruleset倍率
enemy 実効DPS ≈ reference HP × 目標被ダメ割合 / 目標戦闘時間
```

- 基準値は `content/balance/reference.yaml`（レベル→HP_ref/DPS_ref/MIT_ref/HPS_ref）。
- 敵に手入力するのはレベル・ランク・種族・タグ・スキル・報酬のみ。
- 床回避・NPC位置取りはシムで保証しない。実機テスト必須。
