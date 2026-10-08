# ADR-16: ネットワーク登録の一本化とOps集約リファクタ

日付: 2026-10-07
状態: 採用（ビルド＋起動確認）

## クラッシュ修正
- 事象：クライアント起動時に `IllegalArgumentException: Packet type
  Type[id=hayatemod:market_open] is already registered!`
- 原因：S2C型登録を main init（NetRegistry）と client init（ClientPackets）の
  両方で行っていた。main は物理クライアントでも実行されるため二重登録になった。
- 対策：型登録は NetRegistry（main init）のみに集約。client 側は
  receiver の attach のみ行う。C2S/S2C とも両物理サイドで main init が
  走るため、送受信の両端で型が揃う。

## Ops集約（重複排除）
- `CraftOps`（クラフト・分解のコスト式含む）：SolommoCommand と画面パケットが共有。
- `BuildOps`（署名・候補一致）：チャット表示と図鑑画面が共有。
- `MarketOps`（売買・一覧）：チャットと画面パケットが共有。
- 全 java モジュールの完全修飾参照を import に統一（曖昧な `core.Vec3` のみFQN維持）。

## 残課題
- UiServer の表示名解決（displayName）は暫定実装。lang 連携は将来。
