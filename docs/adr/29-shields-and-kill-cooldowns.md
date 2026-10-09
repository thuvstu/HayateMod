# ADR-29: 期限付きシールド・回復分配・撃破CD短縮

日付: 2026-10-09。設計根拠: `IMPLEMENTATION.md` §5.7 / §6.6 / §6.2。
状態: コード・content・core回帰テスト実装済み。Fabric全体ビルド・実機受け入れは未確認。

## 今回遊べる形にした構成

| 武器 | special（G / 右クリック） | heavy（Shift＋右クリック） | 固有の連携 |
|---|---|---|---|
| 灰守の誓剣 / `solommo:cinder_aegis` | 耐久90、5秒、CD10秒、スタミナ20 | 回復量100をHP40＋耐久60/6秒に分配、CD18秒、マナ25 | シールド残存中のprimary命中で0.35倍追撃 |
| 残響の追撃剣 / `solommo:echo_reaver` | 火弾＋命中0.5倍追撃、CD8秒、スタミナ15 | 半径4の強打、CD12秒、スタミナ25 | 撃破で同じ武器のspecial残りCDを2秒短縮 |

- 灰守はcolossusテーブル4%、残響はslag_bruteテーブル3%の直接ドロップ。
- 自動アイテム登録・クリエイティブ一覧・工房に追加。既存クラフト式で灰守は素材60＋欠片10、残響は素材48＋欠片10。
- 新しいitem/model JSON、日英名称を追加。見た目は既存の鉱滓大鉈・灰の烙印剣モデルを再利用。専用テクスチャは未制作。
- §5.7の「回復の一部をシールド化」は、この武器のheavy回復に対する明示的な分配として実装。
  あらゆる回復源を横取りする常時変換パッシブではない。
- **撃破CD短縮は追加の遊び。設計例の「次のスキルの詠唱時間短縮」とは別物**で、詠唱時間・詠唱待機システムは今回未実装。

## イベント・スキーマ契約

- `self_buff`: 武器のspecial/heavy用。直接のダメージ配送はなく、成功した発動の効果だけを実行。
- `on_cast`: 対応核・CD・リソース検査を通過してCDを開始した直接発動で1回。
  通常攻撃・子cast・投射命中では発火しない。拒否時は効果なし。
  条件はイベントの先行スナップショットで評価し、通常配送前に実行する。
- `self_has_shield`: 所有者の未失効シールドが正の耐久を持つ場合。
- `grant_shield`: 必須 `amount`, `duration_ticks`。所有者に付与。
- `heal_with_shield`: 必須 `amount`, `duration_ticks`, `shield_ratio`（0〜1）。
  `amount*(1-ratio)`をHP回復、`amount*ratio`をシールドへ。HP上限で捨てた回復分の再変換はしない。
- `reduce_cooldown`: 必須 `amount`（秒）、`ref`（special/heavy）。同じ所有者・同じ武器IDの既存CDのみ。
  0未満にせず、未発動/再使用可能なスロットには貯蓄しない。別の武器・別の所有者には作用しない。
- 3アクションは固定amount/所有者対象。formula、target、area scopeは拒否する。
  シールド専用キーの誤用・必須項目欠落・非有限値・範囲逸脱・武器内の不正slot参照も拒否。
- vocabularyの上限: shield/recovery amount 200、shield duration 1200tick、CD短縮30秒/アクション。
  Runtimeでも上限を適用し、既存の効果/アクション/タスク予算を共有する。
- タグ: `delivery:self`, `utility:shield`, `utility:healing`, `utility:cooldown`。
  日本語説明は共通Describerから生成。

## シールド・被ダメージ・生存期間

- 耐久は加算しない。現在残量以上の付与は置換＋期限更新、弱い付与は残量も期限も変更しない。
- `expiresAt <= now`で失効。cleanup tickを待たず条件判定・吸収・HUD照会に反映する。
- MCではRpgHealthの最終HP減算箇所で一度だけ吸収する。
  McAdapterの通常被弾/second-wind判定では非消費previewだけを使う。
- restore、致死経路、AFTER_DEATH、disconnectでクリア。保存しない。
  クリア時は所有者のdelay/repeat継続も世代番号で無効化し、旧ライフの回復が復活後に届かないようにした。
  無効タスクは予定tickで除去されるまで既存pending上限に含まれる。
- **既存制約**: engine damage再入時のsecond-wind早期return、Mod境界の完全な因果伝播、
  飛翔体のロードアウトスナップショットは今回の修正対象外。旧飛翔体そのものはクリア処理では消さない。

## HUD

- S2C `CombatState`にシールド残量・残りtickを追加。クライアントは表示だけを行う。
- 5 server tickごとにシールドとspecialの実際の残りCDを再送し、撃破短縮を補正する。
- 発動直後も同期。heavyを誤ってGのCDとして表示しない。heavy専用のCDバーは未追加。
- 持ち替えてもシールドは期限まで存続する。非武器への持ち替えでspecialバーを消去。
  ワールド未接続時はクライアントHUDを消去する。

## レビューと検証

- レビューで修正: 不正核でのリソース消費、heavyのG表示、死亡者なし/加害者なしでのシールド消去漏れ、
  delay/repeatによる復活後の再付与、死亡中/観戦中のC2S発動を防止。
- **Java21 + ECJ + JUnit Jupiter 5.11.4による補助検証: 175件成功、0失敗/スキップ**。
  追加44件 = ShieldStoreTest 11、CombatEffectsTest 19、CombatContentTest 14。
- expiry境界、非消費preview、部分吸収、弱/強/同値付与、上限、リソース/CD拒否、条件先行評価、
  子cast、遅延・反復無効化、CD所有者/武器分離、貯蓄禁止、スキーマ負例、説明/タグ/ドロップを検査。
- Content検証: loaderErrors=0、errors=0、既存の循環候補INFO 9件。
- この補助コンパイル対象はcore/tools/SaveFiles/テスト。**MC API・ネットワークcodec・描画をコンパイル/実行した証明ではない**。
- 通常環境では `bash gradlew :core:test :tools:validateContent :mod:test :mod:build` と実機確認を行う。

## 実機受け入れ（未実施）

1. バックアップ済みワールドのサバイバルで `/solommo give solommo:cinder_aegis`。
   Gで耐久90表示、5秒で消去。CD中の連打・リソース不足で再付与/費用消費が増えないこと。
2. シールド有無でprimaryの追撃差を確認。小ダメージは耐久だけ減り、超過分だけHPが減ること。
   環境ダメージとengine経由のダメージの両方を確認し、二重吸収しないこと。
3. 負傷してShift＋右クリック: HP40回復/耐久60（上限や既存の強いシールドを考慮）。
   強いシールドの期限は延長しないこと。死亡・復帰・ログアウト再接続で残留しないこと。
4. `/solommo give solommo:echo_reaver`、G発動後に敵を倒す。
   special残りCDが2秒減ってHUDも補正され、heavy CDや別武器CDは変わらないこと。
   発動前の撃破・残り2秒未満の撃破で次回CDが割引されないこと。
5. 武器持ち替え、HUD、ツールチップ、モデル欠損、工房の素材消費・取得、各loot表を確認。
6. 専用サーバー＋クライアントでもcodec登録・受信・切断後HUD消去を確認。
