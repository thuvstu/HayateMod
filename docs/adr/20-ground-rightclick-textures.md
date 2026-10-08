# ADR-20: 地上の街・アリーナ、右クリック発動、武器別見た目

日付: 2026-10-07 / ユーザー指摘対応（岩盤Yの街・ダンジョン、右クリック、テクスチャ）

## 決定

- 街もアリーナも固定Yをやめ、陸地の地面を探して建てる。
  `core/build/GroundFinder.java` が純粋な探索（ヒント中心→近い順・決定論的、
  海抜・水・斜度で判定）、`mod/build/GroundSites.java` がゲーム側の
  サンプラー＋キャッシュ。海だけのワールドでは従来座標にフォールバック。
  `content/world.yaml` の座標は探索中心として維持（設計書・正本に手をつけない）。
- 武器の special は右クリックでも発動する。クライアントの
  `WeaponUseHandler` が `UseItemCallback` で同じ C2S（`SkillCastPayload`）を
  送り、検証（CD・所有・special有無）は従来どおりサーバ側。
  クライアントは Content Pack を持たないため、判定は item 型のみ。
  ツールチップに「右クリック / G: special発動」を追加。
- 武器は1種1アイテム化（`hayatemod:<weapon_path>` 11種）。`WeaponStack.make`
  が個別品を返し、旧3種は互換フォールバックとして残す。既存ワールドの
  旧スタックは `weapon_id` 解決でそのまま読める。
- テクスチャは MCAssetGen/texcraft で生成。剣5は sword 素体に preset
  （forge/rusty/ruins/ancient/legendary）、槍3・杖3は既存 strip の先頭
  フレームを切り出して variant（mat_iron/el_fire/mat_wood/el_dark/
  enchanted）。槍は 3D モデルを layer0 差し替えで流用、剣・杖は handheld。
  新規11枚は静止画（アニメは旧3種のみ、後回し）。

## 検証

- `:core:test` 全合格（`GroundFinderTest` 3件: 海回避・斜度棄却・全海empty）。
- `:tools:validateContent` errors=0、`:mod:build` 成功（client含む）。
- `:mod:runServer` 起動成功、content 11/13/7/2/3、レジストリ競合なし。
- 実機目視は未了: 地上の街 `/solommo town`、アリーナ入場、右クリック発動、
  11武器の見た目。空サーバは tick 休止するため、目視はプレイヤー参加が前提。
