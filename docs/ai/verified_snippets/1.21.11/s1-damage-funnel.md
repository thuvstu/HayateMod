# 検証済みスニペット (1.21.11 / Mojmap)

## コマンドの権限要求（opレベル2）
`CommandSourceStack#hasPermission(int)` は 1.21.11 に存在しない。
新権限制を使う（`minecraft-common` の javap で確認）。

```java
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

Commands.literal("s1").requires(src -> src.permissions()
        .hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2))))
```

## サーバー側ダメージイベント（fabric-entity-events-v1 1.8.0）
sources jar でシグネチャ確認済み。

```java
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> true);
ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {});
ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> true);
ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {});
// source.getMsgId() で "fall" / "lava" / "mob_attack" 等が取れる
```

注意: `ALLOW_DEATH` をキャンセルしても次 tick に HP<=0 で再死亡する。
復帰させる側で `setHealth(getMaxHealth())` 等を行うこと（sources jar の javadoc に明記）。

## ダメージ入口（Mojmap）
- `LivingEntity#hurtServer(ServerLevel, DamageSource, float)`
- `Entity#damageSources()` → `DamageSources#generic()` 等
- 回復は別経路: `LivingEntity#heal(float)`（Mixin で `@Inject(method = "heal")` 可）
