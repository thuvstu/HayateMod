# ADR: Phase 0 バージョン固定 (S0)

日付: 2026-10-04
状態: 採用（実ビルドで確認）

## 固定値

| 項目 | 値 | 出典 |
|---|---|---|
| Minecraft | 1.21.11 | `gradle.properties` / `build.gradle` |
| Fabric Loader | 0.19.5 | `gradle.properties` |
| Fabric API | 0.141.6+1.21.11 | `gradle.properties`（`./gradlew build` 成功で存在確認） |
| Fabric Loom (要求) | 1.14-SNAPSHOT | `build.gradle` (`id 'fabric-loom' version '1.14-SNAPSHOT'`) |
| Fabric Loom (解決) | **1.14.10** | ビルドログ `Fabric Loom: 1.14.10` / `buildEnvironment` で `net.fabricmc:fabric-loom:1.14.10` に解決 |
| Gradle Wrapper | 9.6.1 | `gradle/wrapper/gradle-wrapper.properties`（`Welcome to Gradle 9.6.1!` で確認） |
| Java | Temurin 21.0.12.1+1-LTS | `org.gradle.java.home=C:/Users/gogok/.jdks/temurin-21.0.12.1` にピン留め |
| マッピング | Mojang Official (`loom.officialMojangMappings()`) | `build.gradle` |

## 設計書との差分（雛形を優先）

- IMPLEMENTATION.md §3.1 はプラグインIDを `net.fabricmc.fabric-loom-remap` と記載しているが、
  生成された雛形は `id 'fabric-loom' version '1.14-SNAPSHOT'` であり、こちらでビルド成功を確認した。
  設計書の「実際の構文・配置は生成された雛形を優先」に従い、雛形側を正とする。
  設計書は正本として変更せず、差分は本ADRのみに記録する（変更はユーザー承認制）。

## 注意（Snapshot の可変性）

- `1.14-SNAPSHOT` は可変なので、解決値 `1.14.10`（確認日 2026-10-04）を本ファイルに記録する。
  Loom 更新時は本ファイルの日付・解決値を更新し、更新前に `./gradlew build` を通す。
- デフォルトの `java`（`C:\Users\gogok\.jdks\latest`）は Temurin 1.8 だったため、
  プロジェクト側で `org.gradle.java.home` に Temurin 21 をピン留めした。
  `latest` シンボリックリンク自体は変更していない。

## 検証

- `$env:JAVA_HOME` を Temurin 21 にして `./gradlew build -x test` → `BUILD SUCCESSFUL` (9 tasks)。
- `./gradlew buildEnvironment` で Daemon JVM が `Eclipse Temurin JDK 21 (21.0.12.1)` であることを確認。
- 実機起動 (`runClient`) は未実施。ユーザ環境で `./gradlew runClient` が起動することを Phase 0 Gate とする。

## 残作業（Phase 0 Gate）

- [ ] `./gradlew runClient` でタイトル画面まで起動する（ユーザ側で実施・報告）
- [ ] `content/` 見本と `core/rules` 最小仕様の作成
