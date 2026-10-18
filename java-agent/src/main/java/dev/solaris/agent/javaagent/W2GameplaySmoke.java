package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Command-built fixtures, ordinary interactions, and received entity/dimension state. */
final class W2GameplaySmoke {
    static final Set<String> IDS = Set.of("playable-w2-animals", "playable-w2-vehicles",
        "playable-w2-special-mobs", "playable-w2-nether-portal", "playable-w2-end-dragon-respawn");
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W2GameplaySmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        fixture = new W1SmokeFixture(smoke);
    }

    void run(String id) throws Exception {
        fixture.prepare();
        switch (id) {
            case "playable-w2-animals" -> animals();
            case "playable-w2-vehicles" -> vehicles();
            case "playable-w2-special-mobs" -> specialMobs();
            case "playable-w2-nether-portal" -> netherPortal();
            case "playable-w2-end-dragon-respawn" -> endDragonRespawn();
            default -> throw new IllegalArgumentException(id);
        }
    }

    private void animals() throws Exception {
        Wolf wolf = (Wolf) summon("minecraft:wolf", "{}", 2);
        smoke.give("minecraft:bone", 64);
        for (int attempts = 0; attempts < 64 && !smoke.read(wolf::isTame); attempts++) {
            int before = smoke.client.inventoryCount("minecraft:bone");
            long version = ClientStateEvents.containerPacketVersion();
            interact(wolf);
            require(smoke.await(() -> ClientStateEvents.containerPacketVersion() > version)
                && smoke.client.waitForInventoryCount("minecraft:bone", before - 1, TIMEOUT), "taming bone not authoritatively debited");
        }
        smoke.check(smoke.read(wolf::isTame), "wolf-tamed", "received tame bit after bounded native bone use");
        require(smoke.await(wolf::isInSittingPose), "tamed wolf must first sit");
        smoke.give("minecraft:blue_dye", 1);
        long dyeVersion = ClientStateEvents.containerPacketVersion();
        interact(wolf);
        smoke.check(smoke.await(() -> ClientStateEvents.containerPacketVersion() > dyeVersion
                && wolf.getCollarColor() == net.minecraft.world.item.DyeColor.BLUE)
                && smoke.client.waitForInventoryCount("minecraft:blue_dye", 0, TIMEOUT),
            "wolf-collar-debit", "received blue collar and exact one dye debit (distinct from default red)");
        emptyHand();
        interact(wolf);
        smoke.check(smoke.await(() -> !wolf.isInSittingPose()), "wolf-stand", "received cleared sitting flag");
        interact(wolf);
        smoke.check(smoke.await(wolf::isInSittingPose), "wolf-sit", "received sitting flag");
        Entity pig = summon("minecraft:pig", "{NoAI:1b}", 3);
        smoke.give("minecraft:lead", 1);
        long leashVersion = ClientStateEvents.containerPacketVersion();
        interact(pig);
        smoke.check(smoke.await(() -> ClientStateEvents.containerPacketVersion() > leashVersion
                && ((Mob) pig).getLeashHolder() == minecraft().player)
                && smoke.client.waitForInventoryCount("minecraft:lead", 0, TIMEOUT),
            "pig-leashed", "received leash holder is this player; lead consumed");
        emptyHand();
        interact(pig);
        smoke.check(smoke.await(() -> ((Mob) pig).getLeashHolder() == null),
            "pig-unleashed", "received explicit empty leash link; " + smoke.read(() -> {
                Entity holder = ((Mob) pig).getLeashHolder();
                return "pig=" + pig.getUUID() + " removed=" + pig.isRemoved()
                    + " holder=" + (holder == null ? "none" : holder.getUUID())
                    + " player=" + minecraft().player.getUUID()
                    + " held=" + minecraft().player.getMainHandItem();
            }));
        smoke.give("minecraft:saddle", 1);
        long saddleVersion = ClientStateEvents.containerPacketVersion();
        interact(pig);
        smoke.check(smoke.await(() -> ClientStateEvents.containerPacketVersion() > saddleVersion
                && ((Mob) pig).isSaddled())
                && smoke.client.waitForInventoryCount("minecraft:saddle", 0, TIMEOUT),
            "pig-saddled", "received saddle equipment and debit");
        emptyHand();
        interact(pig);
        smoke.check(smoke.await(() -> minecraft().player.getVehicle() == pig),
            "pig-mounted", "received SetPassengers link identifies same pig UUID=" + pig.getUUID());
        dismount(pig);
    }

    private void vehicles() throws Exception {
        fixture.set(0, 0, 3, "minecraft:rail[shape=north_south]");
        Entity cart = summon("minecraft:minecart", "{}", 3);
        emptyHand();
        interact(cart);
        smoke.check(smoke.await(() -> minecraft().player.getVehicle() == cart), "minecart-mounted", "received passenger link");
        dismount(cart);
        smoke.command("kill @e[type=minecraft:minecart,distance=..16]");
        BlockPos base = fixture.origin();
        smoke.command("fill " + (base.getX() - 2) + " " + base.getY() + " " + (base.getZ() + 1)
            + " " + (base.getX() + 2) + " " + base.getY() + " " + (base.getZ() + 5) + " minecraft:stone");
        smoke.command("fill " + (base.getX() - 1) + " " + base.getY() + " " + (base.getZ() + 2)
            + " " + (base.getX() + 1) + " " + base.getY() + " " + (base.getZ() + 4) + " minecraft:water");
        require(smoke.client.waitForBlock(fixture.target(0, 0, 2, "minecraft:water"), "minecraft:water", TIMEOUT),
            "contained boat water fixture missing");
        Entity boat = summon("minecraft:oak_boat", "{}", 2);
        interact(boat);
        smoke.check(smoke.await(() -> minecraft().player.getVehicle() == boat), "boat-mounted", "received passenger link");
        double before = smoke.read(boat::getZ);
        smoke.read(() -> { minecraft().options.keyUp.setDown(true); return null; });
        try {
            require(fixture.await(Duration.ofSeconds(10), () -> boat.getZ() > before + 0.2),
                "native forward input did not move boat");
        } finally {
            smoke.read(() -> { minecraft().options.keyUp.setDown(false); return null; });
        }
        dismount(boat);
        double beforeEchoY = smoke.read(() -> minecraft().player.getY());
        smoke.command("tp @s ~ ~10 ~");
        smoke.check(smoke.await(() -> minecraft().player.getY() > beforeEchoY + 9
                && minecraft().player.getZ() > before + 0.2),
            "boat-server-position-echo", "relative teleport supplies a fresh server-authoritative player pose after native boat input");
    }

    private void specialMobs() throws Exception {
        smoke.command("time set midnight");
        smoke.command("effect give @s resistance 120 4");
        smoke.command("effect give @s fire_resistance 120 0");
        Set<UUID> before = entityIds();
        Entity blaze = summon("minecraft:blaze", "{}", 7);
        smoke.check(fixture.await(Duration.ofSeconds(30), () -> {
            for (Entity entity : minecraft().level.entitiesForRendering()) {
                if (!before.contains(entity.getUUID()) && type(entity).equals("minecraft:small_fireball")) return true;
            }
            return false;
        }), "blaze-native-projectile", "received small_fireball from live blaze=" + blaze.getUUID());
        smoke.command("kill @e[type=minecraft:blaze,distance=..32]");
        for (String mob : new String[]{"pillager", "witch", "breeze", "shulker", "ghast"}) {
            Entity entity = summon("minecraft:" + mob, "{NoAI:1b}", 3);
            smoke.check(smoke.read(() -> type(entity).equals("minecraft:" + mob) && !entity.isRemoved()),
                mob + "-wire-smoke", "received species identity=" + entity.getUUID() + "; no species AI claim");
            smoke.command("kill @e[type=minecraft:" + mob + ",distance=..32]");
        }
    }

    private void netherPortal() throws Exception {
        for (int x = -1; x <= 2; x++) for (int y = 0; y <= 4; y++) {
            if (x == -1 || x == 2 || y == 0 || y == 4) fixture.set(x, y, 3, "minecraft:obsidian");
        }
        ScenarioHeldItem flint = smoke.give("minecraft:flint_and_steel", 1);
        int wear = smoke.read(() -> minecraft().player.getMainHandItem().getDamageValue());
        smoke.client.useItemOn(fixture.target(0, 0, 3, "minecraft:obsidian"), flint);
        smoke.check(smoke.client.waitForBlock(fixture.target(0, 1, 3, "minecraft:nether_portal"), "minecraft:nether_portal", TIMEOUT)
                && smoke.await(() -> minecraft().player.getMainHandItem().getDamageValue() == wear + 1),
            "nether-native-ignition", "received portal cells and exact flint durability debit");
        teleport(fixture.origin().getX() + 0.5, fixture.origin().getY() + 1, fixture.origin().getZ() + 3.5);
        smoke.check(fixture.await(Duration.ofSeconds(45), () -> minecraft().level.dimension().equals(Level.NETHER)),
            "nether-portal-travel", "received dimension transition through ignited portal");
        smoke.check(smoke.await(() -> minecraft().player != null && minecraft().player.onGround()),
            "nether-arrival", "received live player in Nether with supported footing");
        fixture.ticks(40);
        smoke.check(smoke.read(() -> minecraft().level.dimension().equals(Level.NETHER)),
            "portal-cooldown-no-bounce", "player remains in Nether forty client ticks after arrival");
    }

    private void endDragonRespawn() throws Exception {
        fixture.set(0, 0, 2, "minecraft:end_portal");
        smoke.command("tp @s " + (fixture.origin().getX() + 0.5) + " " + (fixture.origin().getY() + 0.5)
            + " " + (fixture.origin().getZ() + 2.5));
        smoke.check(fixture.await(Duration.ofSeconds(45), () -> minecraft().level.dimension().equals(Level.END)),
            "end-contact-travel", "received End dimension via portal contact (portal fixture, not eye activation)");
        // The canonical four-chunk view from the x=100 arrival platform can miss
        // the central fight. Observe it from a declared, supported arena fixture.
        smoke.command("tp @s 3.5 80 3.5");
        smoke.check(fixture.await(Duration.ofSeconds(20), () -> minecraft().player.onGround()
                && Math.abs(minecraft().player.getX() - 3.5) < 0.5
                && Math.abs(minecraft().player.getZ() - 3.5) < 0.5),
            "end-central-chunk-ready", "received central arrival with native supported footing before resident-only fill");
        smoke.command("fill 0 79 0 6 79 6 minecraft:end_stone");
        smoke.command("fill 0 80 0 6 83 6 minecraft:air");
        BlockPos arenaFloor = new BlockPos(3, 79, 3);
        smoke.check(fixture.await(Duration.ofSeconds(20), () ->
                minecraft().level.getBlockState(arenaFloor).is(net.minecraft.world.level.block.Blocks.END_STONE)),
            "end-arena-floor-received", "received declared End floor in the already-loaded central chunk");
        smoke.command("tp @s 3.5 80 3.5");
        net.minecraft.world.phys.Vec3 arena = new net.minecraft.world.phys.Vec3(3.5, 80, 3.5);
        smoke.check(fixture.await(Duration.ofSeconds(20), () -> minecraft().player.onGround()
                && minecraft().player.position().distanceTo(arena) < 2
                && minecraft().level.getBlockState(arenaFloor).is(net.minecraft.world.level.block.Blocks.END_STONE)),
            "end-arena-fixture", "received supported central End position inside the declared tracking view");
        smoke.check(fixture.await(Duration.ofSeconds(30), () -> {
            for (Entity entity : minecraft().level.entitiesForRendering()) if (type(entity).equals("minecraft:ender_dragon")) return true;
            return false;
        }), "dragon-fight-spawn", "received tracked dragon in End; smoke only, no kill/respawn ritual claim");
        require(smoke.await(() -> minecraft().player.onGround()), "End arrival must have supported footing");
        net.minecraft.world.phys.Vec3 arrival = smoke.read(() -> minecraft().player.position());
        smoke.command("spawnpoint @s");
        smoke.command("kill @s");
        require(smoke.await(() -> minecraft().player.isDeadOrDying()), "End death missing");
        smoke.read(() -> { minecraft().player.respawn(); return null; });
        smoke.check(fixture.await(Duration.ofSeconds(30), () -> minecraft().level.dimension().equals(Level.END)
                && !minecraft().player.isDeadOrDying() && minecraft().player.onGround()
                && minecraft().player.position().distanceTo(arrival) < 2),
            "end-dimensional-respawn", "received alive player at declared supported End arrival respawn");
    }
    Entity summon(String type, String nbt, int dz) throws Exception {
        Set<UUID> before = entityIds();
        BlockPos origin = fixture.origin();
        smoke.command("summon " + type + " " + (origin.getX() + 0.5) + " " + origin.getY() + " "
            + (origin.getZ() + dz + 0.5) + " " + nbt);
        require(smoke.await(() -> findNew(type, before) != null), "summoned entity not received: " + type);
        return smoke.read(() -> findNew(type, before));
    }

    private Entity findNew(String type, Set<UUID> before) {
        for (Entity entity : minecraft().level.entitiesForRendering()) {
            if (!before.contains(entity.getUUID()) && type(entity).equals(type)) return entity;
        }
        return null;
    }

    private Set<UUID> entityIds() throws Exception {
        return smoke.read(() -> {
            Set<UUID> ids = new HashSet<>();
            for (Entity entity : minecraft().level.entitiesForRendering()) ids.add(entity.getUUID());
            return ids;
        });
    }

    private void interact(Entity entity) throws Exception {
        smoke.read(() -> { minecraft().gameMode.interact(minecraft().player, entity,
            new net.minecraft.world.phys.EntityHitResult(entity, entity.position()), InteractionHand.MAIN_HAND); return null; });
    }

    private void emptyHand() throws Exception {
        smoke.read(() -> {
            for (int slot = 0; slot < 9; slot++) if (minecraft().player.getInventory().getItem(slot).isEmpty()) {
                minecraft().player.getInventory().setSelectedSlot(slot);
                return null;
            }
            throw new IllegalStateException("empty hotbar slot missing");
        });
    }

    private void dismount(Entity vehicle) throws Exception {
        smoke.read(() -> { minecraft().options.keyShift.setDown(true); return null; });
        try {
            smoke.check(smoke.await(() -> minecraft().player.getVehicle() == null && vehicle.getPassengers().isEmpty()),
                "native-dismount", "received empty passenger graph for UUID=" + vehicle.getUUID());
        } finally {
            smoke.read(() -> { minecraft().options.keyShift.setDown(false); return null; });
        }
    }

    private void teleport(double x, double y, double z) throws Exception {
        smoke.command("tp @s " + x + " " + y + " " + z);
        require(smoke.await(() -> minecraft().player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z)) < 1),
            "fixture teleport not received");
    }

    static String type(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
