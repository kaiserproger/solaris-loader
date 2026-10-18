package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Operator-built range; every shot, collection and return uses vanilla item use and movement. */
final class W1RangedSmoke {
    private static final Duration FLIGHT_TIMEOUT = Duration.ofSeconds(20);
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;
    private String useWindowName;
    private long useStartGameTime;
    private long useStartTimeVersion;
    private long useStartNanos;

    W1RangedSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        fixture = new W1SmokeFixture(smoke);
    }

    void run() throws Exception {
        fixture.prepare();
        BlockPos origin = fixture.origin();
        fill(origin.offset(-16, -1, -6), origin.offset(16, -1, 20), "minecraft:stone");
        fill(origin.offset(-16, 0, -6), origin.offset(16, 6, 20), "minecraft:air");
        fixture.set(0, -1, 16, "minecraft:stone");
        // Clear only this fixture's old projectiles/items, not unrelated world entities.
        smoke.command("kill @e[type=minecraft:arrow,distance=..32]");
        smoke.command("kill @e[type=minecraft:trident,distance=..32]");
        smoke.command("kill @e[type=minecraft:item,distance=..32]");
        smoke.command("summon minecraft:iron_golem " + (origin.getX() + 0.5) + " " + origin.getY()
            + " " + (origin.getZ() + 8.5) + " {NoAI:1b}");
        try {
            LivingEntity[] target = new LivingEntity[1];
            smoke.check(smoke.await(() -> {
                for (Entity entity : minecraft().level.entitiesForRendering()) {
                    // Bind the received golem by type and this isolated fixture's coordinates.
                    if (entity instanceof LivingEntity living
                        && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                            .toString().equals("minecraft:iron_golem")
                        && entity.position().distanceToSqr(new Vec3(origin.getX() + 0.5,
                            origin.getY(), origin.getZ() + 8.5)) < 1.0) {
                        target[0] = living;
                        return living.getHealth() > 0;
                    }
                }
                return false;
            }), "ranged-mob-received", "received stationary iron golem at native bow range");
            bowHit(target[0]);
            arrowPickup(origin);
            crossbow(origin);
            trident(origin);
            pearl(origin);
        } finally {
            smoke.read(() -> {
                minecraft().options.keyUse.setDown(false);
                minecraft().options.keyUp.setDown(false);
                if (minecraft().player.isUsingItem()) {
                    minecraft().gameMode.releaseUsingItem(minecraft().player);
                }
                return null;
            });
            smoke.command("kill @e[type=minecraft:iron_golem,distance=..32]");
        }
    }

    private void bowHit(LivingEntity mob) throws Exception {
        smoke.give("minecraft:arrow", 4);
        smoke.give("minecraft:bow", 1);
        float before = smoke.read(mob::getHealth);
        int arrowsBefore = smoke.read(mob::getArrowCount);
        UUID identity = smoke.read(mob::getUUID);
        Vec3 aim = smoke.read(() -> mob.position().add(0, mob.getBbHeight() * 0.55, 0));
        aim(aim);
        Set<UUID> old = projectiles();
        long inventoryVersion = ClientStateEvents.containerPacketVersion();
        chargeAndRelease(25, "bow-draw");
        Projectile arrow = receiveProjectile(Arrow.class, old, "bow-arrow-received");
        boolean damaged = smoke.await(() -> healthDropped(identity, mob.getUUID(), before, mob.getHealth())
            && mob.getArrowCount() == arrowsBefore + 1 && !mob.isRemoved());
        float after = smoke.read(mob::getHealth);
        int embedded = smoke.read(mob::getArrowCount);
        String limitation = embedded > arrowsBefore && !healthDropped(identity, identity, before, after)
            ? " LIMITATION: arrow-hit metadata received, but LivingEntity.getHealth() did not decrease; "
                + "the client health oracle is unavailable for this server/client path (no command-health substitute)."
            : "";
        smoke.check(damaged, "bow-hit-client-health-drop", "mob_uuid=" + identity + " arrow_uuid="
            + smoke.read(arrow::getUUID) + " health_before=" + before + " health_after=" + after
            + " embedded_before=" + arrowsBefore + " embedded_after=" + embedded + limitation);
        smoke.check(smoke.await(() -> ClientStateEvents.containerPacketVersion() > inventoryVersion
                && count("minecraft:arrow") == 3),
            "bow-one-arrow-consumed", "received inventory arrows=" + smoke.read(() -> count("minecraft:arrow")));
    }

    private void arrowPickup(BlockPos origin) throws Exception {
        aim(new Vec3(origin.getX() + 4.5, origin.getY() - 0.5, origin.getZ() + 5.5));
        int before = smoke.read(() -> count("minecraft:arrow"));
        Set<UUID> old = projectiles();
        chargeAndRelease(25, "pickup-arrow-draw");
        Arrow arrow = (Arrow) receiveProjectile(Arrow.class, old, "pickup-arrow-received");
        EntityDataAccessor<Boolean> grounded = syncedAccessor(AbstractArrow.class, "IN_GROUND");
        smoke.check(smoke.await(() -> arrow.getEntityData().get(grounded)
                && count("minecraft:arrow") == before - 1),
            "pickup-arrow-grounded", "uuid=" + smoke.read(arrow::getUUID)
                + " position=" + smoke.read(arrow::position) + " arrows_before=" + before);
        fixture.ticks(10); // Vanilla shake/pickup delay must elapse before walking onto the arrow.
        long version = ClientStateEvents.containerPacketVersion();
        try {
            smoke.read(() -> { minecraft().options.keyUp.setDown(true); return null; });
            boolean collected = fixture.await(FLIGHT_TIMEOUT, () -> {
                if (arrow.isRemoved()) {
                    minecraft().options.keyUp.setDown(false);
                    return ClientStateEvents.containerPacketVersion() > version
                        && exactArrowPickup(before, before - 1, count("minecraft:arrow"), true);
                }
                aimOnClient(arrow.position().add(0, 1.5, 0));
                minecraft().player.setXRot(0);
                return false;
            });
            smoke.check(collected, "native-arrow-pickup-exact-one", "before_shot=" + before
                + " after_pickup=" + smoke.read(() -> count("minecraft:arrow"))
                + " arrow_removed=" + smoke.read(arrow::isRemoved));
        } finally {
            smoke.read(() -> { minecraft().options.keyUp.setDown(false); return null; });
        }
        fixture.ticks(20);
        smoke.check(smoke.read(() -> exactArrowPickup(before, before - 1, count("minecraft:arrow"),
                arrow.isRemoved())), "arrow-pickup-no-duplicate", "exact count remains stable after twenty client ticks");
    }

    private void crossbow(BlockPos origin) throws Exception {
        smoke.give("minecraft:crossbow", 1);
        smoke.check(smoke.read(() -> chargedCount() == 0), "crossbow-initially-empty", "received charged_projectiles=empty");
        int before = smoke.read(() -> count("minecraft:arrow"));
        long loadVersion = ClientStateEvents.containerPacketVersion();
        smoke.read(() -> {
            minecraft().options.keyUse.setDown(true);
            minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND);
            return null;
        });
        try {
            smoke.check(smoke.await(() -> {
                boolean loaded = ClientStateEvents.containerPacketVersion() > loadVersion
                    && crossbowLoaded(0, chargedCount(), chargedArrow(), before, count("minecraft:arrow"));
                // Stop holding use before screenshot ticks: auto-repeat must not fire the loaded crossbow.
                if (loaded) minecraft().options.keyUse.setDown(false);
                return loaded;
            }),
                "crossbow-native-loaded-component", "charged_projectiles=" + smoke.read(this::charged)
                    + " inventory_arrows=" + smoke.read(() -> count("minecraft:arrow")));
        } finally {
            release();
        }
        aim(new Vec3(origin.getX() - 4.5, origin.getY() - 0.5, origin.getZ() + 9.5));
        Set<UUID> old = projectiles();
        long fireVersion = ClientStateEvents.containerPacketVersion();
        useOnce();
        Projectile arrow = receiveProjectile(Arrow.class, old, "crossbow-fired-projectile");
        smoke.check(smoke.await(() -> ClientStateEvents.containerPacketVersion() > fireVersion
                && crossbowFired(1, chargedCount(), true, before - 1, count("minecraft:arrow"))),
            "crossbow-native-fire-clears-component", "projectile_uuid=" + smoke.read(arrow::getUUID)
                + " charged_projectiles=" + smoke.read(this::charged)
                + " arrows=" + smoke.read(() -> count("minecraft:arrow")));
    }

    private void trident(BlockPos origin) throws Exception {
        smoke.give("minecraft:trident", 1);
        smoke.command("enchant @s minecraft:loyalty 3");
        smoke.check(smoke.await(() -> loyalty(minecraft().player.getMainHandItem()) == 3),
            "trident-loyalty-component", "received enchantments="
                + smoke.read(() -> minecraft().player.getMainHandItem().getEnchantments()));
        fill(origin.offset(-12, 0, 16), origin.offset(-8, 3, 16), "minecraft:stone");
        fixture.set(-10, 1, 16, "minecraft:stone");
        aim(new Vec3(origin.getX() - 9.5, origin.getY() + 1.5, origin.getZ() + 16));
        Set<UUID> old = projectiles();
        long throwVersion = ClientStateEvents.containerPacketVersion();
        chargeAndRelease(20, "trident-native-draw");
        ThrownTrident trident = (ThrownTrident) receiveProjectile(ThrownTrident.class, old, "trident-thrown-entity");
        EntityDataAccessor<Byte> loyaltyData = syncedAccessor(ThrownTrident.class, "ID_LOYALTY");
        smoke.check(smoke.await(() -> ClientStateEvents.containerPacketVersion() > throwVersion
                && count("minecraft:trident") == 0 && trident.getEntityData().get(loyaltyData) == 3),
            "trident-left-inventory", "received entity loyalty=" + smoke.read(() -> trident.getEntityData().get(loyaltyData))
                + " tridents=" + smoke.read(() -> count("minecraft:trident")));
        boolean[] returning = {false};
        long returnVersion = ClientStateEvents.containerPacketVersion();
        boolean returned = fixture.await(FLIGHT_TIMEOUT, () -> {
            returning[0] |= trident.isNoPhysics() && trident.clientSideReturnTridentTickCount > 0;
            return loyaltyReturned(1, 0, count("minecraft:trident"), returning[0], trident.isRemoved(),
                inventoryLoyalty()) && ClientStateEvents.containerPacketVersion() > returnVersion;
        });
        smoke.showInventory();
        smoke.check(returned, "trident-native-loyalty-return", "uuid=" + smoke.read(trident::getUUID)
            + " returning_state_observed=" + returning[0] + " removed=" + smoke.read(trident::isRemoved)
            + " inventory_tridents=" + smoke.read(() -> count("minecraft:trident"))
            + " received_loyalty=" + smoke.read(this::inventoryLoyalty));
        fixture.close();
        fixture.ticks(20);
        smoke.check(smoke.read(() -> loyaltyReturned(1, 0, count("minecraft:trident"), returning[0],
                trident.isRemoved(), inventoryLoyalty())),
            "loyalty-return-exact-once", "one loyalty-3 trident remains after twenty client ticks");
    }

    private void pearl(BlockPos origin) throws Exception {
        smoke.give("minecraft:ender_pearl", 1);
        // No movement inputs or teleport commands during this oracle: position change must come from the pearl.
        aim(new Vec3(origin.getX() + 10.5, origin.getY() - 0.5, origin.getZ() + 10.5));
        Vec3 before = smoke.read(() -> minecraft().player.position());
        Set<UUID> old = projectiles();
        long inventoryVersion = ClientStateEvents.containerPacketVersion();
        useOnce();
        ThrownEnderpearl pearl = (ThrownEnderpearl) receiveProjectile(ThrownEnderpearl.class, old, "pearl-native-projectile");
        boolean teleported = fixture.await(FLIGHT_TIMEOUT, () -> pearlTeleport(before.x, before.y, before.z,
            minecraft().player.getX(), minecraft().player.getY(), minecraft().player.getZ(),
            pearl.isRemoved(), 1, count("minecraft:ender_pearl"))
            && ClientStateEvents.containerPacketVersion() > inventoryVersion);
        smoke.check(teleported, "pearl-received-teleport", "before=" + before + " after="
            + smoke.read(() -> minecraft().player.position()) + " pearl_removed=" + smoke.read(pearl::isRemoved)
            + " pearls=" + smoke.read(() -> count("minecraft:ender_pearl")));
    }

    private void chargeAndRelease(int ticks, String name) throws Exception {
        smoke.read(() -> {
            useWindowName = name;
            useStartGameTime = ClientStateEvents.serverGameTime();
            useStartTimeVersion = ClientStateEvents.serverTimeVersion();
            useStartNanos = System.nanoTime();
            minecraft().options.keyUse.setDown(true);
            minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND);
            return null;
        });
        try {
            smoke.check(smoke.await(() -> minecraft().player.isUsingItem()
                    && minecraft().player.getTicksUsingItem() >= ticks),
                name, "native use held at least " + ticks + " client ticks");
        } finally {
            release();
        }
    }

    private void release() throws Exception {
        smoke.read(() -> {
            boolean releasing = minecraft().player.isUsingItem();
            long releaseGameTime = 0;
            long releaseTimeVersion = 0;
            long releaseNanos = 0;
            int clientUseTicks = 0;
            if (useWindowName != null) {
                releaseGameTime = ClientStateEvents.serverGameTime();
                releaseTimeVersion = ClientStateEvents.serverTimeVersion();
                clientUseTicks = minecraft().player.getTicksUsingItem();
                releaseNanos = System.nanoTime();
            }
            minecraft().options.keyUse.setDown(false);
            if (releasing) {
                minecraft().gameMode.releaseUsingItem(minecraft().player);
            }
            if (useWindowName != null) {
                smoke.observe("native-use-window: stage=" + useWindowName + " releasing=" + releasing
                    + " clientUseTicks=" + clientUseTicks + " startGameTime=" + useStartGameTime
                    + " endGameTime=" + releaseGameTime + " startTimeVersion=" + useStartTimeVersion
                    + " endTimeVersion=" + releaseTimeVersion
                    + " elapsedMs=" + (releaseNanos - useStartNanos) / 1_000_000);
                useWindowName = null;
            }
            return null;
        });
    }

    private void useOnce() throws Exception {
        smoke.read(() -> {
            minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND);
            return null;
        });
    }

    private Projectile receiveProjectile(Class<? extends Projectile> type, Set<UUID> old, String name)
        throws Exception {
        Projectile[] received = new Projectile[1];
        Projectile[] unmatched = new Projectile[1];
        boolean seen = smoke.await(() -> {
            for (Entity entity : minecraft().level.entitiesForRendering()) {
                if (type.isInstance(entity) && !entity.isRemoved() && !old.contains(entity.getUUID())) {
                    Projectile projectile = (Projectile) entity;
                    if (projectile.getOwner() == minecraft().player) {
                        received[0] = projectile;
                        return true;
                    }
                    unmatched[0] = projectile;
                }
            }
            return false;
        });
        smoke.check(seen, name, "new received " + type.getSimpleName() + " owned by local player; uuid="
            + (received[0] == null ? "missing" : smoke.read(received[0]::getUUID))
            + smoke.read(() -> {
                Entity owner = unmatched[0] == null ? null : unmatched[0].getOwner();
                return " unmatched=" + (unmatched[0] == null ? "none"
                    : unmatched[0].getUUID() + "/entityId=" + unmatched[0].getId()
                        + "/owner=" + (owner == null ? "null" : owner.getUUID()))
                    + " playerId=" + minecraft().player.getId()
                    + " inventoryArrows=" + count("minecraft:arrow")
                    + " heldDamage=" + minecraft().player.getMainHandItem().getDamageValue();
            }));
        require(received[0] != null, "projectile missing: " + name);
        return received[0];
    }

    private Set<UUID> projectiles() throws Exception {
        return smoke.read(() -> {
            Set<UUID> ids = new HashSet<>();
            for (Entity entity : minecraft().level.entitiesForRendering()) {
                if (entity instanceof Projectile) ids.add(entity.getUUID());
            }
            return ids;
        });
    }

    private void aim(Vec3 target) throws Exception {
        smoke.read(() -> { aimOnClient(target); return null; });
        fixture.ticks(2);
    }

    private static void aimOnClient(Vec3 target) {
        Vec3 direction = target.subtract(minecraft().player.getEyePosition());
        minecraft().player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        minecraft().player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y,
            Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
    }

    private void fill(BlockPos min, BlockPos max, String block) throws Exception {
        smoke.command("fill " + min.getX() + " " + min.getY() + " " + min.getZ() + " "
            + max.getX() + " " + max.getY() + " " + max.getZ() + " " + block);
    }

    private static int count(String item) {
        int count = 0;
        for (ItemStack stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (W1SmokeFixture.itemId(stack).equals(item)) count += stack.getCount();
        }
        return count;
    }

    private ChargedProjectiles charged() {
        return minecraft().player.getMainHandItem().getOrDefault(DataComponents.CHARGED_PROJECTILES,
            ChargedProjectiles.EMPTY);
    }

    private int chargedCount() {
        int count = 0;
        for (var projectile : charged().items()) count += projectile.count();
        return count;
    }
    private boolean chargedArrow() { return charged().contains(Items.ARROW); }

    private static int loyalty(ItemStack stack) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.LOYALTY)) return entry.getIntValue();
        }
        return 0;
    }

    private int inventoryLoyalty() {
        for (ItemStack stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.TRIDENT)) return loyalty(stack);
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> syncedAccessor(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (EntityDataAccessor<T>) field.get(null);
    }

    static boolean healthDropped(UUID beforeId, UUID afterId, float before, float after) {
        return beforeId != null && beforeId.equals(afterId) && Float.isFinite(before) && Float.isFinite(after)
            && before > 0 && after > 0 && after < before;
    }

    static boolean exactArrowPickup(int beforeShot, int afterShot, int afterPickup, boolean removed) {
        return beforeShot > 0 && afterShot == beforeShot - 1 && afterPickup == afterShot + 1 && removed;
    }

    static boolean crossbowLoaded(int initialCharge, int loadedCharge, boolean arrow, int before, int after) {
        return initialCharge == 0 && loadedCharge == 1 && arrow && before > 0 && after == before - 1;
    }

    static boolean crossbowFired(int loadedCharge, int afterCharge, boolean newProjectile,
                                 int beforeFireArrows, int afterFireArrows) {
        return loadedCharge == 1 && afterCharge == 0 && newProjectile && beforeFireArrows >= 0
            && afterFireArrows == beforeFireArrows;
    }

    static boolean loyaltyReturned(int before, int thrown, int returned, boolean returningState,
                                   boolean removed, int returnedLoyalty) {
        return before == 1 && thrown == 0 && returned == 1 && returningState && removed && returnedLoyalty == 3;
    }

    static boolean pearlTeleport(double x, double y, double z, double afterX, double afterY, double afterZ,
                                 boolean removed, int beforeCount, int afterCount) {
        double dx = afterX - x, dy = afterY - y, dz = afterZ - z;
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
            && dx * dx + dz * dz >= 9.0 && removed && beforeCount == 1 && afterCount == 0;
    }
}
