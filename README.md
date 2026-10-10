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
| ブロック | `hayatemod:gale_ash` | 疾風の灰（ネザーの疾風の渓谷の床を覆う。シャベルで採掘） |
| バイオーム | `hayatemod:gale_hollow` | 疾風の渓谷（ネザーに生成。灰の円盤・疾風鉱石・固有の霧と粒子） |
| トリム | `hayatemod:gale` | 疾風のインゴットが鍛冶台のトリム素材になる（色 `#7FE3F0`） |
| バイオーム | `hayatemod:gale_heights` | 疾風の高み（エンドの外郭島に生成。疾風の精が湧く） |
| アイテム | `hayatemod:gale_fan` | 疾風の扇。右クリックで前方 6 ブロックの mob を吹き飛ばす（耐久128 / クールダウン3秒） |
| エンチャント | `hayatemod:gale_burst` | 剣に付与。攻撃時のノックバックが上がる（最大III） |
| エンチャント | `hayatemod:gale_drop` | 靴に付与。落下ダメージを軽減する（最大III） |
| モブ | `hayatemod:gale_spirit` | 疾風の精。浮遊する中立モブ。疾風の渓谷と疾風の高みに湧く |
| モブ | `hayatemod:azemichi_event` | えんえんあぜみちの出来事マーカー（看板）。走っている間のみ存在 |
| 構造物 | `hayatemod:gale_ruins` | 疾風の遺跡（ネザーに生成。石煉瓦の柱・疾風ランプ・チェスト） |
| ゲーム | `/hayate enen` | 『妖怪ウォッチ3』の「えんえんあぜみち」再現（下記参照） |
| キーバインド | `1` / `2` / `3` | あぜみちの出来事の選択を答える |
| エンチャント | `hayatemod:gale_step` | 靴に付与。レベルごとに移動速度 +4%（最大III） |
| 防具 | `hayatemod:gale_helmet` / `gale_chestplate` / `gale_leggings` / `gale_boots` | 疾風の具足一式（鉄とダイヤの中間程度）。**4部位すべて装備すると移動速度上昇が持続** |
| クリエイティブタブ | `hayatemod:hayate` | 上記をまとめた独自タブ |
| コマンド | `/hayate about` | バージョン表示 |
| コマンド | `/hayate boost [対象] [秒数] [レベル]` | 移動速度上昇を付与（権限レベル2） |
| コマンド | `/loctp <構造物\|#タグ>` | 最寄りを探してテレポート（権限レベル2・探索半径10000）。`hayatemod:gale_ruins` の動作確認用 |
| イベント | `LootTableEvents.MODIFY` | 石炭鉱石のドロップに疾風の粉を追加 |
| イベント | `ServerLifecycleEvents.SERVER_STARTED` | 起動時ログ（テンプレート） |
| イベント | `ServerTickEvents.END_SERVER_TICK` | 疾風の具足のフルセット判定（移動速度 + 風のパーティクル） |
| ワールド生成 | `BiomeModifications.addFeature` | オーバーワールド全バイオームに疾風鉱石を追加 |
| ワールド生成 | `NetherBiomes.addNetherBiome` | 疾風の渓谷をネザーのノイズ空間に登録 |
| ワールド生成 | `TheEndBiomes.addHighlandsBiome` | 疾風の高みをエンドの外郭島に登録 |
| ワールド生成 | `BiomeModifications.addSpawn` | 疾風の精を疾風の渓谷 / 疾風の高みに追加 |

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
疾風の羽根 x4 + インゴット + 棒 → 疾風の扇          (shaped, 扇型)
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
2. `assets/<ns>/equipment/<id>.json` にレイヤー定義を置いて `EquipmentAsset` を登録する
   （クライアントリソース。`data/` 側ではない点に注意。テクスチャは規約で
   `assets/<ns>/textures/entity/equipment/humanoid/<texture>.png` と
   `humanoid_leggings/<texture>.png` から引かれる。どちらも 64x32）
3. `new Item(new Item.Properties().humanoidArmor(material, ArmorType.HELMET))` で登録する
   （`equippable` コンポーネントと防御力の属性はここで付く）

疾風の具足の装備レイヤは、26.3 の実 jar から実測した**バニラ鉄防具のシルエット**を
`tools/generate_textures.py` 内で疾風カラーに再着色して生成しています（矩形データは
`ARMOUR_TEMPLATE`、取得方法は `.github/workflows/probe.yml` 参照）。

### ネザーバイオームの追加（26.x）

26.3 のバイオーム JSON は**トップレベルのキーが変わりました**。色・粒子・スポーンは
`attributes` マップの中に入り、`effects` は `water_color` だけを保持しています。

```jsonc
{
  "attributes": {
    "minecraft:visual/fog_color": "#2A5A66",          // 16進数の文字列
    "minecraft:visual/sky_color": "#0B1C22",
    "minecraft:visual/ambient_particles": {
      "argument": [{ "particle": { "type": "minecraft:white_ash" }, "probability": 0.06 }],
      "modifier": "append"
    },
    "minecraft:gameplay/natural_mob_spawns": { "argument": { ... }, "modifier": "overlay" }
  },
  "features": [[], [], [], [], [], [], [], [], [], [], []],  // 必ず 11 個の配列
  "carvers": ["minecraft:nether_cave"],
  "has_precipitation": false,
  "temperature": 2.0
}
```

`features` は `GenerationStep.Decoration` と 1 対 1 なので**必ず 11 個**（空でも良い）。
鉱石は index 6、地表のディスク類は index 10 に入れるのがバニラの慣習です。

ネザーに配置するには Fabric API の `NetherBiomes` を使います。

```java
public static final ResourceKey<Biome> GALE_HOLLOW =
		ResourceKey.create(Registries.BIOME, HayateMod.id("gale_hollow"));

// temperature, humidity, continentalness, erosion, depth, weirdness, offset
NetherBiomes.addNetherBiome(GALE_HOLLOW,
		Climate.parameters(0.0F, -0.7F, 0.0F, 0.0F, 0.0F, 0.35F, 0.0F));
```

地表を特徴づけるには `minecraft:disk` が手軽です（`minecraft:netherrack` を
`hayatemod:gale_ash` に置き換える）。実物の書式は
`data/minecraft/worldgen/feature/disk_gravel.json` が参考になります。

### 防具のトリム素材（26.x）

鍛冶台で選べる色は**データだけで増やせます**。

1. `data/<ns>/trim_material/<id>.json` を置く
   （`palette_id` は既存のバニラパレットを借りれば新規テクスチャ不用）
2. `new Item.Properties().trimMaterial(ResourceKey.create(Registries.TRIM_MATERIAL, id(...)))`
   を素材アイテムに付ける
3. 説明文を `trim_material.<ns>.<id>` で各言語ファイルに書く

```jsonc
// data/hayatemod/trim_material/gale.json
{
  "palette_id": "minecraft:trim/diamond",
  "description": { "translate": "trim_material.hayatemod.gale", "color": "#7FE3F0" }
}
```

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

### モブの追加（26.x）

`FlyingMob` は **26.3 で消えました**。飛行モブを作るには
`PathfinderMob` を継承して次の3点を揃えます。

```java
public class GaleSpiritEntity extends PathfinderMob {
	public GaleSpiritEntity(EntityType<? extends GaleSpiritEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);                                     // 1. 浮く
		this.moveControl = new FlyingMoveControl<GaleSpiritEntity>(this, 20, true);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {          // 2. 空飛ぶ経路探索
		return new FlyingPathNavigation(this, level);
	}
}
```

3. **スポーン条件と属性は Fabric のビルダー経由で**登録します。ここが 26.3 の
   一番の落とし穴で、`SpawnPlacements.register(...)` が **private になった**ため、
   MOD からは `EntityType.Builder` を直接使うか、Fabric の
   `FabricEntityType.Builder` を使うしかありません。

```java
public static final EntityType<GaleSpiritEntity> GALE_SPIRIT = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		GALE_SPIRIT_KEY,
		FabricEntityType.Builder
				.createMob(GaleSpiritEntity::new, MobCategory.CREATURE, mob -> mob
						.spawnPlacement(SpawnPlacementTypes.NO_RESTRICTIONS,
								Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
								GaleSpiritEntity::checkSpiritSpawnRules)
						.defaultAttributes(GaleSpiritEntity::createAttributes))
				.sized(0.7F, 0.9F)
				.eyeHeight(0.55F)
				.clientTrackingRange(8)
				.build(GALE_SPIRIT_KEY));
```

- `build()` に渡すのは **文字列ではなく `ResourceKey<EntityType<?>>`**
- 属性（`defaultAttributes`）を忘れると AI が `null` を読んで落ちます
- スポーン条件のラムダは `SpawnPredicate<T>` =
  `test(EntityType<T>, ServerLevelAccessor, EntitySpawnReason, BlockPos, RandomSource)`。
  **`EntityType<? extends T>` では不一致になる**ので注意
  （`ServerLevelAccessor` は `world.level` 側。`server.level` ではありません）

**描画は src/client 側**（`splitEnvironmentSourceSets()` で分かれています）。
26.x のレンダラは **エンティティではなくレンダーステート**を引数に取ります。

```java
public class GaleSpiritRenderer
		extends MobRenderer<GaleSpiritEntity, LivingEntityRenderState, GaleSpiritModel> {
	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();        // 26.x はこれが必須
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) { ... }
}

// src/client のエントリポイントで
EntityRendererRegistry.register(ModEntities.GALE_SPIRIT, GaleSpiritRenderer::new);
```

モデル空間は相変わらず **Y が下向き・1 単位が 1/16 ブロック・y=24 が地面** です。
`LayerDefinition.create(mesh, 64, 32).bakeRoot()` で焼いて `EntityModel<LivingEntityRenderState>`
に渡し、`setupAnim(state)` で `state.ageInTicks` などから動かします。

### 構造物（ジグソー）の追加（26.x）

構造物も「ほぼデータ」です。Java は不要で、次の4つを置くだけです。

```
data/hayatemod/worldgen/structure/gale_ruins.json        … "type": "minecraft:jigsaw"
data/hayatemod/worldgen/template_pool/gale_ruins/start.json … ピース一覧
data/hayatemod/worldgen/structure_set/gale_ruins.json    … 配置（間隔・salt）
data/hayatemod/structures/gale_ruins/ruin_1.nbt          … ピースの本体（gzipped NBT）
```

バニラの書式（実 jar から確認したもの）:

```jsonc
// structure（use_expansion_hack は 26.3 の必須キー。小規模遺跡は false）
{ "type": "minecraft:jigsaw", "start_pool": "hayatemod:gale_ruins/start",
  "size": 1, "max_distance_from_center": 40,
  "start_height": { "type": "minecraft:uniform",
                    "max_inclusive": { "below_top": 12 },
                    "min_inclusive": { "absolute": 32 } },
  "biomes": "#hayatemod:has_structure/gale_ruins",
  "step": "underground_decoration", "terrain_adaptation": "beard_thin",
  "use_expansion_hack": false }

// template_pool（element_type は 26.3 でも legacy_ 付きが無難。processors は必須）
{ "fallback": "minecraft:empty", "elements": [ { "weight": 1, "element": {
      "element_type": "minecraft:legacy_single_pool_element",
      "location": "hayatemod:gale_ruins/ruin_1", "processors": "minecraft:empty",
      "projection": "rigid" } } ] }

// structure_set
{ "structures": [ { "structure": "hayatemod:gale_ruins", "weight": 1 } ],
  "placement": { "type": "minecraft:random_spread", "salt": 74591220,
                 "separation": 5, "spacing": 18, "spread_type": "triangular" } }
```

NBT ピースは `tools/generate_structures.py` が生成します（**標準ライブラリだけの
gzip NBT ライター**）。構造ブロックを手で組むほど大きくない建造物なら、コードで
書いて git に置く方が差分が読めて楽です。CI は再生成して差分が出ないことを確認
します。

ピースの中にチェストを置く場合はブロックエンティティを `blocks[].nbt` に書きます
（`{"id": "minecraft:chest", "LootTable": "hayatemod:chests/gale_ruins"}`）。

### データ駆動エンチャント

`data/hayatemod/enchantment/gale_step.json` を置くだけで反映されます（Java 側の登録は不要）。
`supported_items` にバニラのタグ（`#minecraft:enchantable/foot_armor`）を指定し、
`data/minecraft/tags/enchantment/in_enchanting_table.json` を**追記**することで
エンチャントテーブルにも出るようになります（タグは `replace: true` を書かない限りマージされます）。

効果（`effects`）は**キーが単数形**です。ここは 1.21 系の `conditions` / `functions`
から変わっているので、実 jar の `data/minecraft/enchantment/knockback.json` と
`feather_falling.json` を確認して合わせてください。

```jsonc
// ノックバック追加（minecraft:knockback）
"effects": { "minecraft:knockback": [ { "effect": {
    "type": "minecraft:add",
    "value": { "type": "minecraft:linear", "base": 0.5, "per_level_above_first": 0.5 } } } ] }

// 落下ダメージ軽減（minecraft:damage_protection + requirements）
"effects": { "minecraft:damage_protection": [ {
    "effect": { "type": "minecraft:add",
                "value": { "type": "minecraft:linear", "base": 2.0, "per_level_above_first": 2.0 } },
    "requirements": { "type": "minecraft:damage_source_properties",
                      "predicate": { "tags": [ { "expected": true, "id": "#minecraft:is_fall" } ] } } } ] }
```

## 4.1 えんえんあぜみち（ゲーム再現その1：『妖怪ウォッチ3』）

「Modでゲームを再現」シリーズ第1弾として、**『妖怪ウォッチ3』の1日1回ダンジョン
「えんえんあぜみち」**をオーバーワールドで再現しています。田んぼの中をまっすぐ突き
進む一本道、途中で起きる出来事、距離、ゴールの鳥居、そしてボスまで、元のゲームの
構造をそのまま Minecraft 仕様に翻訳しました。

```
/hayate enen          あぜみちの挑戦を開始（誰でも OK・1日1回＝ゲーム内1日）
/hayate enen force    1日1回の制限を無視（テスト用）
/hayate enen exit     脱出（カカシを調べるのと同じ。開始地点に戻る）
/hayate enen status   現在の数字（距離／ゴール／バトル／クリア数）
```

### 遊び方

1. 平らな地面に立って `/hayate enen` を実行すると、足元の周囲に**田んぼのあぜ道**
   が生成されます（1ブロック = 5m、最長 10000m）。入口にテレポート。
2. **まっすぐ前へ**。サイドバーに表示されるのが現在の距離と今日のゴール距離
   （最初の数回は 1500m → 2500m → 3500m → **4949m** → **7979m** … と、
   原作のあの数字で伸びていきます）。距離は一度伸びたら戻りません
   （原作でも引き返せないのと同じ）。**あぜ道の上にいる間だけ**距離が伸び、
   脇の田んぼを横切ったり引き返したりしても距離は変わりません
   （あまりに戻ると「あぜみちは前へ」と一言）。
3. 道の脇に**出来事のマーカー**（`azemichi_event`：看板のエンティティ）が立ちます。
   右クリックで話しかけると質問がチャットに出るので、**1 / 2 / 3 キー**で答えます
   （30秒以内に答えないと通り過ぎてしまいます）。
4. 道の途中には**妖怪**（疾風の精）が飛び回っています。あぜみちの精は原作同様
   **こちらを追いかけて襲ってくる**ので、注意して倒しましょう（バトル数が増え、
   ランクに応じたドロップを入手できます。深いほど強くなります）。
   道の上にはりんご・パン・疾風の粉などの**拾い物**も少し落ちています。
5. **ゴールの鳥居（赤い絨毯ブロック）**をくぐるとクリア！ 報酬と統計が渡され、
   次の日のゴール距離が伸びます。

### 出来事の一覧

| 出来事 | 質問と結果 |
| --- | --- |
| 自販機 | 100 / 1000 / 10000円（エメラルドで支払う）。アイテム、出口が近づく、または妖怪バトル |
| 電話ボックス | 最後まで聞く → 疾風の護符か出口が遠くなる / 受話器を置く → 出口が近づく |
| おばあさん | 話すたびにパンをくれる。**4回目**に顔から蜘蛛の足が…（女郎蜘蛛バトル） |
| 電車 | 乗る → 400m一気に前進するが出口は800m遠くなる / 見送る → 何事もなし |
| ぼんやりした男 | 1問で出口が ±1500m 動く、原作のどでかいイベント |
| カカシ | 調べるとあぜみちから**脱出** |

### 仕様

- **1日1回**（ゲーム内の「日」＝実時間20分）。`lastDay` はプレイヤーNBTに保存。
- クリアが **7回** 到達すると、次の回から鳥居の先に巨大猫 **びしゃがつく**
  （3倍サイズの疾風の精・HP200）が待ち構え、倒さないとクリアになりません
  （原作の7回クリア後のボスラッシュ相当）。出現時にピンクの**ボスバー**が
  画面下部に表示され、戦闘中はHPに追従します。
- ゴール距離は出来事の結果で上下しますが、600m未満・10000m超にはなりません
  （あぜ道は 2000 ブロックまで生成されるため）。
- 生成はコマンド実行時に同期で行います（数秒のフリーズ）。平原に使うと
  一番きれいに出ます。
- 走っている最中に死んでもセッションは保留されます。`/hayate enen exit` で
   手動終了できます。ログアウトすると走っていたあぜ道は片付けられます。

コードは `src/main/java/.../azemichi/` に集めています（`EnenAzemichi` が
セッション管理・イベント処理・ネットワーク、`AzemichiTerrain` が世界生成、
`EnenEventEntity` / `EnenEventModel` / `EnenEventRenderer` / `EnenChoiceKeys`
がマーカー本体とキー入力）。選択のやり取りは小さな C2S ペイロード
（`EnenChoiceC2SPayload`）で、サーバーは質問が開いていないとき無視します。

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
| バイオームの `effects`（色・粒子・スポーン） | `attributes` マップ + `modifier`（`append` / `overlay`） |
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
