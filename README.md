# HayateMod

**Minecraft 26.3 + Fabric** 用のモッド開発テンプレート兼サンプルです（疾風 = *gale* テーマ）。
26.1 以降の Minecraft は**非難読化（unobfuscated）**されて出荷されているため、旧来の
「Yarn / intermediary マッピングを当ててリマップする」工程がありません。このプロジェクトはその
**新しい Loom（リマップ無し・`net.fabricmc.fabric-loom`）構成**で書かれています。

| 要素 | 値 |
| --- | --- |
| Minecraft | `26.3`（最新安定版・非難読化） |
| Fabric Loader | `0.19.5` |
| Fabric API | `0.162.0+26.3` |
| Loom | `1.18-SNAPSHOT`（非リマップ版プラグイン `net.fabricmc.fabric-loom`） |
| Gradle | `9.7.1`（wrapper 同梱） |
| Java | **25**（Minecraft 26.x は Java 25 以上が必須） |

---

## 1. 必要環境

* **JDK 25**（[Temurin](https://adoptium.net/) など）。`java -version` が 25 であること。
* （任意）IntelliJ IDEA **2025.3 以上**（それ以前のバージョンは Mixin が正しく動きません）

## 2. ビルドと起動

```bash
./gradlew build          # build/libs/hayatemod-1.0.0.jar が出来る
./gradlew runClient      # 開発用クライアントを起動（run/ にセーブデータが作られる）
./gradlew runServer      # 開発用サーバーを起動
./gradlew genSources     # Minecraft 本体のソースを生成して IDE で参照できるようにする
```

IDE から開く場合は `build.gradle` を **Gradle プロジェクトとして**開いてください。

## 3. 入っているもの

| 種類 | ID | 内容 |
| --- | --- | --- |
| アイテム | `hayatemod:gale_dust` | 羽根 + ブレイズパウダーから作れる素材 |
| アイテム | `hayatemod:gale_ingot` | 疾風の粉を精錬/溶鉱したインゴット |
| アイテム | `hayatemod:storm_fruit` | 食べると移動速度上昇II（15秒）が付く食べ物 |
| アイテム | `hayatemod:gale_charm` | 右クリックで移動速度上昇II（30秒）。使い切りの消費アイテム |
| ブロック | `hayatemod:gale_block` | 金属系の建築ブロック（ツルハシ必須） |
| ブロック | `hayatemod:gale_lamp` | 右クリックで点灯/消灯するランプ（ブロックステート `lit`） |
| クリエイティブタブ | `hayatemod:hayate` | 上記をまとめた独自タブ |
| コマンド | `/hayate about` | バージョン表示 |
| コマンド | `/hayate boost [対象] [秒数] [レベル]` | 移動速度上昇を付与（権限レベル2） |
| イベント | `LootTableEvents.MODIFY` | 石炭鉱石のドロップに疾風の粉を追加 |
| イベント | `ServerLifecycleEvents.SERVER_STARTED` | 起動時ログ（テンプレート） |

```
# レシピ
羽根 + ブレイズパウダー      → 疾風の粉 x2        (shapeless)
疾風の粉 (精錬 / 溶鉱)       → 疾風のインゴット
疾風のインゴット x9          → 疾風ブロック       (shaped / 逆も shapeless)
疾風のインゴット x4 + グロウストーン → 疾風ランプ x4
疾風のインゴット x3 + 疾風の粉     → 疾風の護符
リンゴ + 疾風の粉 + 砂糖      → 嵐の果実           (shapeless)
```

日本語（`ja_jp.json`）と英語（`en_us.json`）の翻訳を同梱しています。

## 4. プロジェクト構成

```
src/main/     サーバー・クライアント共通コード（アイテム、ブロック、コマンド、イベント）
src/client/   クライアント専用コード（Loom の splitEnvironmentSourceSets による分離）
src/main/resources/
  fabric.mod.json                 モッド定義（エントリポイント・依存関係）
  assets/hayatemod/               lang / models / blockstates / items / textures
  data/hayatemod/recipe/          レシピ JSON
  data/hayatemod/loot_tables/     ブロックのドロップ定義
tools/generate_textures.py        テクスチャ生成スクリプト（標準ライブラリのみ）
```

テクスチャはバイナリをコミットせずスクリプトで生成できます（編集後は再実行してください）。

```bash
python3 tools/generate_textures.py
```

## 5. 「非難読化」対応で変わったところ

| 旧（〜1.21.11） | 新（26.1〜） |
| --- | --- |
| `fabric-loom` / `net.fabricmc.fabric-loom-remap` | `net.fabricmc.fabric-loom` |
| `mappings loom.officialMojangMappings()` / Yarn | **不要**（ゲーム本体の名前がそのまま API） |
| `modImplementation` / `modCompileOnly` | `implementation` / `compileOnly` |
| `remapJar` タスクが成果物 | 通常の `jar` タスクが成果物 |
| Java 21 | Java 25 |

コード側も、26.2 でブロック＋アイテムの ID が `net.minecraft.references.BlockItemId` に
統合されたり、クリエイティブタブの `displayItems(...)` が
`CreativeModeTab.DisplayItemsGenerator` になったりと、API 名が少しずつ変わっています
（このリポジトリのコードは 26.3 実物の jar に対してビルド検証済み）。

### 参考リンク

* [Fabric 26.3 のアナウンス](https://fabricmc.net/2026/09/15/263.html)
* [Fabric 26.1（非難読化対応）のアナウンス](https://fabricmc.net/2026/03/14/261.html)
* [Fabric Docs（Loom）](https://docs.fabricmc.net/develop/loom/)
* [バージョン一覧](https://fabricmc.net/develop/)

## 6. 1.21.11 を対象にしたい場合

1.21.11 は**最後の難読化版**です（`1.21.11_unobfuscated` という実験ビルドも別途公開されています）。
素直に 1.21.11 を狙うなら、次のように旧構成へ戻します。

```gradle
// settings.gradle はそのまま
// build.gradle
plugins {
    id 'net.fabricmc.fabric-loom-remap' version "${loom_version}"   // -remap 付き
}

dependencies {
    minecraft "com.mojang:minecraft:1.21.11"
    mappings loom.officialMojangMappings()                          // マッピングが必要
    modImplementation "net.fabricmc:fabric-loader:0.19.5"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11"
}
```

* Java は 21（`options.release = 21`）、`fabric.mod.json` の `depends` は `~1.21.11` / `>=21`
* ブロック登録は `BlockItemId` が無いので `ResourceKey<Block>` + `new BlockItem(...)` を
  `Registry.register` する 26.1 以前の書き方になります
* Yarn は 1.21.11 で更新終了なので、新規開発は Mojang マッピング推奨です

## 7. ライセンス

`LICENSE.txt` に従います。
