# S1 実機検証手順（ダメージ入口の一本化）

前提: `./gradlew runClient` で起動、チートONのサバイバル新規ワールド。
ログ: `run/logs/latest.log` を別エディタで開き `[S1]` で検索する。

## T1 近接・T2 投射物
- 素手/剣で殴る、スケルトンの矢を受ける。
- 合格: 攻撃1回につき `[ALLOW]`→`[AFTER]` が1組だけ出る。`src` が期待通り（`player` / `arrow` 等）。

## T3 多段ヒット（無敵時間の確認）
- `/s1 hurt 3` を素早く2回実行。
- 記録: 2回目の `applied=` が `true` か `false` か、`[AFTER]` が出たか。
- 目的: バニラ無敵時間（hurtTime）が多段ヒットを潰すかを実測する。潰れるなら RPG 側の多段処理は無敵時間を迂回する設計が必要。

## T4 落下・T5 溶岩・T6 Mob攻撃
- 高所から落下、溶岩に触れる、ゾンビに殴られる。
- 合格: すべて `[ALLOW]` に `src=fall / lava / mob_attack` 等で現れる（＝入口が一本化できる）。

## T7 shield（迎撃の証明）
- `/s1 shield on` → 落下・Mob攻撃・`/s1 hurt 5` がすべて無効化され、チャットに `[S1] shield blocked` が出る。
- `/s1 shield off` で元に戻る。

## T8 自然回復・T9 ポーション（heal 経路の分離確認）
- 満腹で待つ → `[HEAL]` が出て `[ALLOW]/[AFTER]` は出ない。
- 毒/再生/即時ダメージのポーション → 毒・即時ダメージは `[ALLOW] src=magic`、再生は `[HEAL]` になる想定。実測値を記録する。
- 合格: 回復がダメージ funnel を勝手に増減させない（＝heal は独立経路として見える）。

## T10 wipetest（死亡→復帰の原型）
- `/s1 wipetest on` → 溶岩や `/kill` で致死ダメージを受ける。
- 合格: 死亡せず HP 全快＋チャットに `wipetest: death cancelled` が出て、`[WIPE]` がログに残る。
- `/s1 wipetest off` に戻すこと。

## 報告フォーマット
- 各Tの 合否＋実測ログの抜粋（`[S1]` 行を貼る）。
- 特に T3（無敵時間）と T9（ポーションの src/HEAL 分類）の実測が ADR-07 の根拠になる。
