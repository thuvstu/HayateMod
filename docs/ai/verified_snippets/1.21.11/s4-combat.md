# 検証済みスニペット (1.21.11 / Mojmap) — S4戦闘

## 1.21.11 の改名（javap で確認。旧名ではコンパイル不可）
- `CustomPayload` → `net.minecraft.network.protocol.common.custom.CustomPacketPayload`
- `ResourceLocation` → `net.minecraft.resources.Identifier`（`fromNamespaceAndPath` あり）
- `Zombie` → `net.minecraft.world.entity.monster.zombie.Zombie`
- `SmallFireball` → `net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball`

## C2S 空ペイロード
```java
public record FireboltRequest() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FireboltRequest> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("hayatemod", "firebolt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FireboltRequest> CODEC =
            StreamCodec.unit(new FireboltRequest());
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
// 両サイドの main init で:
PayloadTypeRegistry.playC2S().register(FireboltRequest.TYPE, FireboltRequest.CODEC);
// サーバー受信:
ServerPlayNetworking.registerGlobalReceiver(FireboltRequest.TYPE, (payload, ctx) -> { ... ctx.player() ... });
// クライアント送信:
ClientPlayNetworking.send(new FireboltRequest());
```

## キーバインド（client のみ）
```java
KeyMapping key = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.hayatemod.firebolt", InputConstants.KEY_G, KeyMapping.Category.GAMEPLAY));
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    while (key.consumeClick()) { /* send */ }
});
```

## ダミー召喚・ボスバー・パーティクル予兆
```java
Zombie dummy = EntityType.ZOMBIE.spawn(level, z -> {
    z.setNoAi(true);
    Objects.requireNonNull(z.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(200.0);
    z.setHealth(200.0F);
    z.setCustomName(Component.literal("訓練用ダミー"));
    z.setCustomNameVisible(true);
    z.setPersistenceRequired();
}, pos, EntitySpawnReason.COMMAND, false, false);
ServerBossEvent bar = new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
bar.addPlayer(player); bar.setProgress(hp / max); bar.removeAllPlayers();
level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0);
// 投射物:
SmallFireball bolt = new SmallFireball(level, owner, look.scale(2.0));
bolt.setPos(x, y, z); level.addFreshEntity(bolt);
```
