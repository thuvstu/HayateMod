# ADR-10: アセット生成パイプライン v2（64px・アニメ・3Dモデル）

日付: 2026-10-06
状態: 採用（ビルド・JSON検証済み。実機の見た目は runClient で確認待ち）

## 生成元（すべて自作 MCAssetGen の出力。外部素材なし＝§1.2準拠）

| 生成物 | ツール | コマンド | 結果 |
|---|---|---|---|
| 槍 3Dモデル | advanced-minecraft-asset-generator | `model --shape spear --material infernal` | 58要素・display変換付きの完全なJavaモデル |
| 槍 テクスチャ（アニメ） | 同上 | `anim --shape spear --material infernal --layers fire,sparkle --frames 6` | 32x192ストリップ＋mcmeta |
| 槍 予備（静止64px） | 同上 | `render --shape spear --material infernal --size 64` | 差分検証用 |
| 剣 テクスチャ（アニメ） | texcraft | `variant --id enchanted --frame strip --size 64` | 64x384・6フレーム |
| 欠片・素材（64px） | texcraft | `render --preset crystal/gold --size 64` | 静止画 |

## 統合
- `mod/src/main/resources/assets/hayatemod/models/item/spear.json`
  - advancedの出力をそのまま使用し、`textures.layer0/particle` のみ
    `hayatemod:item/spear` に書き換え。
  - `display` はthirdperson/firstperson/gui込み（バニラ互換）。
  - `credit` フィールドは出所記録として残置（バニラは未知キーを無視する）。
- `textures/item/spear.png` = 32x192アニメ＋ `spear.png.mcmeta`（frametime 2）
- `textures/item/sword.png` = 64x384アニメ＋ `sword.png.mcmeta`（frametime 4）
- `pity_shard.png` / `craft_material.png` = 64px静止

## CLIの使い方（再生成用メモ）
advanced生成器は環境変数方式：
```powershell
$env:MCASSET_CMD = "anim"
# パスはJSON内なのでバックスラッシュ不可（フォワードスラッシュを使う）
$env:MCASSET_ARGS = '["--shape","spear","--material","infernal","--layers","fire,sparkle","--frames","6","--out",".../spear.png"]'
node .cli/adv.cjs
```

## 判断
- 槍は剣シルエット暫定を卒業し、専用形状＋3Dモデル化。**テクスチャとUVは32px基準**
  （モデルの `texture_size` と一致させること。64pxに差し替える場合はモデルのUV正規化が必要）。
- 剣は2Dアニメのまま（量産装備のため）。ユニーク級のみ3Dモデルを充てる運用とする。
## 追記（2026-10-06）：他スタジオ比較と杖の追加

- 剣を差し替え：texcraft単体 → **SkyForge `shadow_fury`（64px）＋ texcraft `enchanted` アニメ合成**
  （`variant --id enchanted --in shadow.png --frame strip`）。絵の質×アニメの両立を確認。
- 欠片・素材は現状維持（候補：SkyForge `ENCHANTED_DIAMOND` / `ENCHANTED_GOLD` 系あり。必要時に再検討）。
- **Spellforge（Arcane Forge）の杖を採用**：`minecraft-magic-staff-generator` はCLIを持たないが、
  `src/engine` が純粋TS（`renderFrame(cfg, frame): ImageData`）のため、esbuildでバンドルした
  headlessドライバ（`staff-driver.ts`→`.mjs`）で生成可能。texcraftの `pngCodec.encodePng` を流用。
  - `fire` + `wizard`、64px、6フレーム（`frametime: 2`）
  - アイテム `hayatemod:staff` を登録し、検証用2本目のユニーク `solommo:ashen_call`（灰呼びの杖、IL15、
    分裂なし・CD5秒・射程28の素直な火弾＋近接射程2）を追加。複数武器の動作証明が目的。
    リソース系modsはエンジン未対応のため付けていない（ADR-07-p23参照）。
- SkyBlock系ジェネレータの絵はジェネリック画としてのみ利用し、固有名・設定は使わない（§1.2準拠）。
- `forge`（skyblock-texture-pack-generator）の `REGAL_WAR_SPEAR` は将来の槍バリエーション候補として温存。
  アニメ付きrender（`--anim --strip`）にも対応している。
