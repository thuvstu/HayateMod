# P5 実機検証手順（ビルド・クラフト・分解）

前提: `./gradlew :mod:runClient`、チートONサバイバル。`/solommo give` で武器を持つ。

## B1 ルーン装着
- `/solommo giverune solommo:cinder_rune` → ルーン品が手に入り説明文が出る。
- 灰枝槍を持ち `/solommo socket 0 solommo:cinder_rune` → 装着。
- ゾンビを殴る → 命中ごとに小さな分裂が追加で出る（`[RPG][FX] split`）。
- `/solommo build` → ビルド署名にルーンIDが含まれる。

## B2 キーストーン
- `/solommo keystone solommo:overchannel` → 装備。火弾ダメージが1.25倍。
- 4つ目を付けようとすると「最大3つまで」と断られる。
- `/solommo keystone solommo:chainlord` の連鎖+1は、分裂の連鎖段数が伸びる。

## B3 ロードアウト
- `/solommo loadout save Field` → `/solommo loadout list` に出る。
- キーストーンを変えて `/solommo loadout load Field` → 保存時へ戻る。

## B4 分解・クラフト
- `/solommo give solommo:ashen_call` → 図鑑登録済みなので `/solommo salvage` で
  素材＋欠片x2（重複救済）。未登録品の分解は素材のみ。
- 素材を集めて `/solommo craft solommo:ashen_call`（素材45＋欠片10）→ 確定入手。

## B5 ビルド表示
- `/solommo build` → 署名と所持武器の一致数が並ぶ（目安表示であり最強断定ではない）。
