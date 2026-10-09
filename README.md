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
| アイテム | `hayatemod:greater_gale_charm` | 移動速度上昇III（45秒）+ 低速落下。使い切り |
| アイテム | `hayatemod:gale_feather` | 羽根 + 疾風の粉から作れる中間素材 |
| アイテム | `hayatemod:gale_orb` | 疾風の圧縮球。道具の中核素材 |
| アイテム | `hayatemod:gale_blade` | 疾風の刃（ダイヤ相当・やや高速な剣） |
| アイテム | `hayatemod:gale_pickaxe` | 疾風のつるはし |
| アイテム | `hayatemod:gale_staff` | 右クリックで視線方向にダッシュ。耐久256 / クールダウン1.5秒 |
| ブロック | `hayatemod:gale_block` | 金属系の建築ブロック（ツルハシ必須） |
| ブロック | `hayatemod:gale_lamp` | 右クリックで点灯/消灯するランプ（ブロックステート `lit`） |
| ブロック | `hayatemod:gale_ore` | オーバーワールドに生成される鉱石（Y=-48〜88、台形分布） |
| ブロック | `hayatemod:deepslate_gale_ore` | 深層岩バリアント |
| エンチャント | `hayatemod:gale_step` | 靴に付与。レベルごとに移動速度 +4%（最大III） |
| 防具 | `hayatemod:gale_helmet` / `gale_chestplate` / `gale_leggings` / `gale_boots` | 疾風の具足一式（鉄とダイヤの中間程度）。**4部位すべて装備すると移動速度上昇が持続** |
| クリエイティブタブ | `hayatemod:hayate` | 上記をまとめた独自タブ |
| コマンド | `/hayate about` | バージョン表示 |
| コマンド | `/hayate boost [対象] [秒数] [レベル]` | 移動速度上昇を付与（権限レベル2） |
| イベント | `LootTableEvents.MODIFY` | 石炭鉱石のドロップに疾風の粉を追加 |
| イベント | `ServerLifecycleEvents.SERVER_STARTED` | 起動時ログ（テンプレート） |
| イベント | `ServerTickEvents.END_SERVER_TICK` | 疾風の具足のフルセット判定（移動速度 + 風のパーティクル） |
| ワールド生成 | `BiomeModifications.addFeature` | オーバーワールド全バイオームに疾風鉱石を追加 |

```
# レシピ
羽根 + ブレイズパウダー      → 疾風の粉 x2        (shapeless)
疾風の粉 (精錬 / 溶鉱)       → 疾風のインゴット
疾風のインゴット x9          → 疾風ブロック       (shaped / 逆も shapeless)
疾風のインゴット x4 + グロウストーン → 疾風ランプ x4
疾風のインゴット x3 + 疾風の粉     → 疾風の護符
リンゴ + 疾風の粉 + 砂糖      → 嵐の果実           (shapeless)
羽根 + 疾風の粉               → 疾風の羽根 x2      (shapeless)
疾風のインゴット x4 + 疾風の粉 x4 → 疾風の宝珠      (shapeless)
宝珠 / インゴット / 羽根       → 疾風の刃           (shaped, 縦一列)
インゴット x3 + 棒 x2         → 疾風のつるはし      (shaped)
宝珠 / インゴット / 羽根       → 疾風の杖           (shaped, 斜め)
疾風の護符 + 宝珠 + 羽根       → 疾風の護符・大     (shapeless)
疾風のインゴット x5            → 疾風の兜           (shaped)
疾風のインゴット x8            → 疾風の胸当て       (shaped)
疾風のインゴット x7            → 疾風の腿当て       (shaped)
疾風のインゴット x4            → 疾風のブーツ       (shaped)
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
  data/hayatemod/loot_table/      ブロックのドロップ定義（26.x は単数形）
  data/hayatemod/worldgen/
    feature/gale_ore.json         何を置くか（ore feature）
    placed_feature/gale_ore.json  どこに置くか（個数・高さ範囲）
  data/hayatemod/enchantment/     エンチャント定義（データ駆動）
tools/generate_textures.py        テクスチャ生成スクリプト（標準ライブラリのみ）
tools/validate_resources.py       リソースの参照切れ・翻訳漏れチェック（CI でも実行）
```

テクスチャはスクリプトで生成しています（編集したら再実行してください）。

```bash
python3 tools/generate_textures.py              # png を書き出す
python3 tools/generate_textures.py --preview    # 端末に ascii プレビューを出す
python3 tools/validate_resources.py             # モデル・データの参照切れを検査
```

CI では「生成結果がコミット済みか」も見ているので、スクリプトを変えたら png の再生成を忘れずに。

### 3D モデル

| 対象 | モデル |
| --- | --- |
| `gale_block` | 全面立方体 + 中央の一段高いプレート（エレメント2個の段差付き） |
| `gale_lamp` | くり抜いた枠（切り抜きテクスチャ）＋内側の発光コア。点灯時はコアに `light_emission: 15` |
| `gale_charm` | 3D ペンダント。紐・留め金・宝石の3エレメントで、宝石は Y 軸 45° 回転させたダイヤ形 |

アイテムモデルは `minecraft:block/block` を親にしています（`gui_light: side` と標準の
`display` 変換を継承できるため）。

### 防具の作り方（26.x）

`ArmorItem` クラスは存在しません。手順は以下の3つだけです。

1. `ArmorMaterial` レコードを作る（耐久倍率・部位ごとの防御力・エンチャント適性・装備音・靭性・ノックバック耐性・修理タグ・`EquipmentAsset` のキー）
2. `data/<ns>/equipment/<id>.json` に `{}` を置いて `EquipmentAsset` を登録する
   （テクスチャは規約で `assets/<ns>/textures/entity/equipment/humanoid/<id>.png` と
   `humanoid_leggings/<id>.png` から引かれる。どちらも 64x32）
3. `new Item(new Item.Properties().humanoidArmor(material, ArmorType.HELMET))` で登録する
   （`equippable` コンポーネントと防御力の属性はここで付く）

疾風の具足の装備レイヤは、26.3 の実 jar から実測した**バニラ鉄防具のシルエット**を
`tools/generate_textures.py` 内で疾風カラーに再着色して生成しています（矩形データは
`ARMOUR_TEMPLATE`、取得方法は `.github/workflows/probe.yml` 参照）。

### ワールド生成の3点セット

26.x のワールド生成は**完全にデータ駆動**です。`ConfiguredFeature` / `OreConfiguration` /
`BuiltInRegistries.CONFIGURED_FEATURE` は 26.3 で消え、`Feature` インターフェースと
`BuiltInRegistries.FEATURE_TYPE` に置き換わりました。Java 側でやることは「どのバイオームに
置くか」の指定だけです。

```
data/hayatemod/worldgen/feature/gale_ore.json          … "type": "minecraft:ore"（config ラッパー不要）
data/hayatemod/worldgen/placed_feature/gale_ore.json   … "feature" + "placement"
src/main/java/.../worldgen/ModWorldgen.java            … BiomeModifications.addFeature(...)
```

`placed_feature` の ID と `ModWorldgen#GALE_ORE_PLACED` の `ResourceKey` は一致させてください。

### データ駆動エンチャント

`data/hayatemod/enchantment/gale_step.json` を置くだけで反映されます（Java 側の登録は不要）。
`supported_items` にバニラのタグ（`#minecraft:enchantable/foot_armor`）を指定し、
`data/minecraft/tags/enchantment/in_enchanting_table.json` を**追記**することで
エンチャントテーブルにも出るようになります（タグは `replace: true` を書かない限りマージされます）。

## 5. 「非難読化」対応で変わったところ

| 旧（〜1.21.11） | 新（26.1〜） |
| --- | --- |
| `fabric-loom` / `net.fabricmc.fabric-loom-remap` | `net.fabricmc.fabric-loom` |
| `mappings loom.officialMojangMappings()` / Yarn | **不要**（ゲーム本体の名前がそのまま API） |
| `modImplementation` / `modCompileOnly` | `implementation` / `compileOnly` |
| `remapJar` タスクが成果物 | 通常の `jar` タスクが成果物 |
| Java 21 | Java 25 |
| `data/<ns>/loot_tables/...` | `data/<ns>/loot_table/...` |
| `worldgen/configured_feature` | `worldgen/feature`（`config` ラッパーが消滅） |
| `ConfiguredFeature` / `OreConfiguration` | `Feature`（インターフェース）/ `BlockReplacement` |

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
