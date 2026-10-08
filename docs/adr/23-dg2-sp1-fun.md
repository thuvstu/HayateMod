# ADR-23: DG2実戦化＋SP1の面白化

日付: 2026-10-07 / 「もっと楽しく、完璧に近づける」対応。

## DG2: slag_colossus が酒場から選べる第2ボスに

- 酒場画面に討伐クエスト一覧（名前・敵Lv・目安時間・雇用費）。
  `TavernOpen` に quests 追加、`TavernEnter` に encounter id 追加。
  不正idは flame_golem にフォールバック、街ゲートは維持。
- 導入口上（slag専用/ golem /汎用）、フェーズ変化通知
  （チェックポイント表示付き）、ボスバー色（elite=黄、boss=紫）。
- colossus_loot に slag_cleaver 直ドロ 7.7%（golemのember_branchと同率）。
  V12合計0.077、交換なしのためV13対象外。
- 召喚雑魚の確認済み: sessionタグ付き（掃引対象）＋no-loot＋通常AIで
  パーティを襲う。修正不要。

## SP1: 野良は群れで来て、夜は荒武者が混ざる

- 群れ狩り: 2〜4匹パック（夜+1）、上限も夜5に緩和。
- バイオーム味付け: 砂漠系→husk、森林系→crawler、それ以外→imp
  （バイオームID文字列判定。マッピング依存なし）。
- 夜の先導35%で slag_brute（Lv18荒武者）が混ざる。
- 討伐pity: 野良15キルで pyre_watcher 確定＋欄外警告「強大な気配…」。
  旧2%ガチャは撤去（決定論的面白さへ）。
- TRASH定数・namedNear判定の撤去。

## 検証

- `:core:test` 64件合格、`validateContent` errors=0（一部V05情報のみ）、
  `:mod:build` 成功、起動スモーク成功。
- 実機目視は未了: 酒場2クエスト選択→slag入場→p2召喚/meltdown、
  勝利報酬、夜パック、pity watcher。
