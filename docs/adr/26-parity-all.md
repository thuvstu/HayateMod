# ADR-26: パリティ全潰し（/sp・防具素材・世界生成・工房）

日付: 2026-10-08 / 「全部の要素で負けない」対応。村人店は既存対応済み、
NBT importは不採用（宣言通り）。

## 追加

- **/sp スキルポイント**: ボス+2・ウォッチャー+1で獲得、
  `/solommo sp damage|cooldown|melee` に投下（各+2%/+1%/+2%、上限
  25/20/25）。BuildMods.withPoints でkeystoneと合成。builds.jsonに
  永続化（旧ファイルは0初期化で互換）。
- **素材3種**: content/materials（煤鉄/鉱滓鋼/熾玻璃）＋coreモデル・
  ローダー・検証器（lootのitem検査を拡張）。texcraft gem×3。
  ドロップ: husk→煤鉄、brute→鉱滓鋼、watcher→熾玻璃。
- **灰甲冑4点**: ArmorMaterial直値＋component装備（1.21.11にArmorItem
  クラスなし）。鉄級防御・火耐性セットボーナス（毎秒更新）。
  見た目は革レイヤーをlavaCracks加工（64x32復元手順あり）。
  クラフトは `/solommo craft cinder_*` で鉱滓鋼4/7/6/4。
- **世界生成**: ember_ore（地下Y±32・6箇所・石/深層）＋ember_bud
  （地表レア6・would_survive＋自作canSurvive）。幸運/シルク対応の
  loot_table。is_overworldタグ指定。
- **工房one-off**: `/solommo forge <style> <base> <special> <heavy>
  <material> <名前>`。見た目＝既存11品目、スキル＝図鑑登録済み
  ドナー、素材＝IL8/14/18、費用は素材x6＋汎用x12。ForgedStore が
  世界JSONに永続化、resolveはcontent→runtimeの順で解決。
  UI画面は後続（コマンドで全機能到達可能）。

## 検証

- `:core:test` 74件合格（withPoints新規）、`validateContent` errors=0、
  `:mod:build` 成功、起動スモーク成功（content・実績1591・
  biome modification 54/65）。
- 実装修正の記録: Block PropertiesにもsetId必須（"Block id not set"）、
  foundInOverworldはfinalize時点で1/65しか一致しないため
  is_overworldタグ指定に変更（Fabric実物確認済み）。
- 実機目視は未了: sp強化実感、甲冑見た目＋セット効果、鉱石採掘、
  鍛造品の発動、スポーン村立地。
