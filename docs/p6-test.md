# P6 実機検証手順（市場・採掘・進行ループ）

前提: `./gradlew :mod:runClient`、チートONサバイバル。

## M1 市場
- `/solommo market list` → 5品目の買値/売値/在庫が出る。
- 素材を持ち `/solommo market sell solommo:craft_material 4` → エメラルド入手＋在庫増。
- 同じ品を `/solommo market buy` で買い戻す → 差額損（往復で増えないこと）。
- 連続売却で価格が下がること（A7の片鱗）。

## M2 採掘
- 石・石炭を掘る → XP加算（ログなし、`/solommo mine` で確認）。
- 鉄鉱石を掘ろうとする → Lv5未満なら拒否メッセージ＋破壊されない。
- （時間短縮用）クリエイティブで鉱石を置いて掘るのも可。

## M3 進行ループ
- 採掘・Mob狩り → 市場売却 → エメラルドで素材購入 → `/solommo craft` →
  `/solommo dungeon enter`（雇用費5）→ ボス報酬 → `/solommo codex` 更新。
- ワールド再起動後も `/solommo codex`・市場在庫・採掘Lvが残る（A9の片鱗）。

## 報告
- 価格の動きのおかしさ、進行が止まる箇所があればログ抜粋つきで。
