# G1 / S2 / S3 実機検証手順（Phase 4）

前提: `./gradlew :mod:runClient`、チートONサバイバル、オーバーワールド。
装備: `/solommo give solommo:ember_branch` を持つ。`/s1 log on` でログを見やすくする。
**S1 の wipetest は off**にしておくこと（ダンジョンのワイプ処理と競合するため）。

## S2 固定アリーナ清掃（`/solommo dungeon stress 20`）
- 合格: 20回の enter/exit で `[DUNGEON][S2]` の各 cycle の residue が `[]`、
  最後に `S2 stress complete` が出る。
- 失敗時: residue の中身（Entity名）を報告。

## S3 NPCシグナル（`/solommo dungeon enter` 後に `/s4 signal <名>`）
- `STACK` → 3人が闘技場中央（stack_point）に集まる。致死扱いのためミスなし。
- `SPREAD` → ボスから離れる。熟練度により時々ずれる（FUMBLEログ）。
- `MOVE_TO_SAFE_SPOT` / `AVOID_AREA` → セーフ地点の1番目へ移動。
- `FOCUS_ADDS` → p2突入後の煤モブへ向かう（いなければボス）。
- `BRACE` → ヒーラー中心に集まり全体回復。
- 合格: 各シグナルで目標地点へ移動すること（S3の「目標時間内に行動」）。

## G1 ボス戦（灰燼のゴーレム）
1. `/solommo dungeon enter` → NPC3人＋ボス出現、ボスバー表示。
2. p1（50秒ループ）：火炎・残り火・束縛・強打。`molten_slam`（STACK・致死・分担）は
   中央に集まって4人分担すること。
3. p2（HP60%以下）：煤モブ召喚（FOCUS_ADDSでNPCが向かう）、溶岩散布（SPREAD）、
   炉心暴走（SPREAD・致死・分担なし＝範囲外へ）。
4. 死亡 → ワイプ：チェックポイント（p2到達済みならp2）から全快再開。
5. 撃破 → 報酬（欠片・素材・7.7%で直ドロップ）→ `/solommo dungeon exit` で撤退。

## 報告フォーマット（G1判定材料）
- 戦闘時間、ワイプ回数、NPCの変な動き（あればログ抜粋）。
- 核心：**「NPCパーティと戦う価値があるか」Yes/No＋理由**。
- 期待所要：設計式どおり約8〜12分（実測値を A4 の材料にする）。
