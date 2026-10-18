package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;
import static dev.solaris.agent.javaagent.W1SmokeFixture.itemId;

/** Native creature interactions and exact received inventory/metadata outcomes. */
final class W3CreatureSmoke {
    static final Set<String> IDS = Set.of("playable-w3-bees-hive", "playable-w3-allay-jukebox",
        "playable-w3-golden-dandelion", "playable-w3-mace");
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W3CreatureSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        fixture = new W1SmokeFixture(smoke);
    }

    void run(String id) throws Exception {
        fixture.prepare();
        switch (id) {
            case "playable-w3-bees-hive" -> bees();
            case "playable-w3-allay-jukebox" -> allayJukebox();
            case "playable-w3-golden-dandelion" -> dandelion();
            case "playable-w3-mace" -> mace();
            default -> throw new IllegalArgumentException(id);
        }
    }

    private void bees() throws Exception {
        ScenarioBlockTarget hive = fixture.set(0, 1, 2, "minecraft:beehive[honey_level=5,facing=north]");
        fixture.set(0, 0, 2, "minecraft:campfire[lit=true]");
        smoke.give("minecraft:glass_bottle", 1);
        smoke.client.useItemOn(hive, new ScenarioHeldItem("minecraft:glass_bottle", 1));
        smoke.check(smoke.client.waitForInventoryCount("minecraft:honey_bottle", 1, TIMEOUT)
                && smoke.client.waitForInventoryCount("minecraft:glass_bottle", 0, TIMEOUT)
                && smoke.client.waitForBlockProperty(hive, "honey_level", "0", TIMEOUT),
            "hive-native-honey", "full fixture hive becomes empty and bottle replacement occurs once");
        fixture.set(0, -1, 4, "minecraft:dirt");
        fixture.set(0, 0, 4, "minecraft:dandelion");
        smoke.command("time set 6000");
        net.minecraft.world.entity.animal.bee.Bee bee = (net.minecraft.world.entity.animal.bee.Bee) summon("minecraft:bee", "{}");
        UUID beeUuid = bee.getUUID();
        smoke.check(fixture.await(Duration.ofSeconds(60), bee::hasNectar), "bee-native-pollination",
            "received nectar metadata after live bee visits declared flower; nectar never commanded");
        require(fixture.await(Duration.ofSeconds(30), bee::isRemoved), "pollinated bee did not enter hive");
        smoke.command("gamemode creative");
        require(smoke.await(() -> minecraft().gameMode.getPlayerMode().isCreative()), "creative break fixture mode not received");
        smoke.read(() -> {
            minecraft().gameMode.startDestroyBlock(new BlockPos(hive.x(), hive.y(), hive.z()), net.minecraft.core.Direction.NORTH);
            return null;
        });
        require(smoke.client.waitForBlock(hive, "minecraft:air", TIMEOUT), "native hive break not received");
        smoke.check(smoke.await(() -> {
            int count = 0;
            for (Entity entity : minecraft().level.entitiesForRendering()) if (entity.getUUID().equals(beeUuid)) count++;
            return count == 1;
        }), "bee-hive-custody-release", "same received bee UUID reappears exactly once after native hive destruction: " + beeUuid);
        fixture.ticks(40);
        smoke.check(smoke.client.inventoryCount("minecraft:honey_bottle") == 1,
            "honey-exact-once", "one honey bottle remains after settling");
    }

    private void allayJukebox() throws Exception {
        Allay allay = (Allay) summon("minecraft:allay", "{}");
        smoke.give("minecraft:diamond", 1);
        long giveReceipt = smoke.read(ReceivedEntityQa::version);
        long giveInventoryReceipt = ClientStateEvents.containerPacketVersion();
        interact(allay);
        smoke.check(smoke.await(() -> ReceivedEntityQa.handMatches(allay.getId(), giveReceipt, "minecraft:diamond", 1)
                && ClientStateEvents.containerPacketVersion() > giveInventoryReceipt
                && itemId(allay.getMainHandItem()).equals("minecraft:diamond") && allay.getMainHandItem().getCount() == 1)
                && smoke.client.waitForInventoryCount("minecraft:diamond", 0, TIMEOUT),
            "allay-held-stack", "received one diamond in allay hand and player debit");
        selectEmpty();
        long returnReceipt = smoke.read(ReceivedEntityQa::version);
        long returnInventoryReceipt = ClientStateEvents.containerPacketVersion();
        interact(allay);
        smoke.check(smoke.await(() -> ReceivedEntityQa.handMatches(allay.getId(), returnReceipt, "minecraft:air", 0)
                && ClientStateEvents.containerPacketVersion() > returnInventoryReceipt && allay.getMainHandItem().isEmpty())
                && smoke.client.waitForInventoryCount("minecraft:diamond", 1, TIMEOUT),
            "allay-native-return", "received empty allay hand and exact returned diamond");
        ScenarioBlockTarget jukebox = fixture.set(0, 0, 2, "minecraft:jukebox");
        smoke.give("minecraft:music_disc_13", 1);
        smoke.client.useItemOn(jukebox, new ScenarioHeldItem("minecraft:music_disc_13", 1));
        smoke.check(smoke.client.waitForInventoryCount("minecraft:music_disc_13", 0, TIMEOUT)
                && smoke.client.waitForBlockProperty(jukebox, "has_record", "true", TIMEOUT)
                && smoke.await(() -> allay.isDancing()),
            "jukebox-allay-dance", "native disc insertion received as record state, inventory debit and dance metadata");
        selectEmpty();
        smoke.client.useItemOn(jukebox, new ScenarioHeldItem("minecraft:air", 0));
        smoke.check(smoke.client.waitForBlockProperty(jukebox, "has_record", "false", TIMEOUT)
                && smoke.await(() -> !allay.isDancing()),
            "jukebox-eject-stops-dance", "received record removal and cleared allay dance flag");
    }

    private void dandelion() throws Exception {
        AgeableMob baby = (AgeableMob) summon("minecraft:pig", "{Age:-24000,NoAI:1b}");
        require(smoke.read(baby::isBaby), "fixture pig not baby");
        smoke.give("minecraft:golden_dandelion", 2);
        long lockReceipt = smoke.read(ReceivedEntityQa::version);
        long lockInventoryReceipt = ClientStateEvents.containerPacketVersion();
        long lockServerTime = ClientStateEvents.serverGameTime();
        long lockServerTimeVersion = ClientStateEvents.serverTimeVersion();
        long lockStartedNanos = System.nanoTime();
        interact(baby);
        smoke.check(smoke.await(() -> ReceivedEntityQa.ageMatches(baby.getId(), lockReceipt, true)
                && ClientStateEvents.containerPacketVersion() > lockInventoryReceipt && baby.isAgeLocked())
                && smoke.client.waitForInventoryCount("minecraft:golden_dandelion", 1, TIMEOUT),
            "baby-age-locked", "received age-lock bit and one flower debit");
        interact(baby);
        fixture.ticks(20);
        smoke.check(smoke.read(baby::isAgeLocked) && smoke.client.inventoryCount("minecraft:golden_dandelion") == 1,
            "dandelion-cooldown-no-debit", "early second use neither unlocks nor consumes remaining flower");
        fixture.ticks(40);
        long unlockReceipt = smoke.read(ReceivedEntityQa::version);
        long unlockInventoryReceipt = ClientStateEvents.containerPacketVersion();
        long unlockServerTime = ClientStateEvents.serverGameTime();
        long unlockServerTimeVersion = ClientStateEvents.serverTimeVersion();
        long unlockStartedNanos = System.nanoTime();
        interact(baby);
        boolean unlocked = smoke.await(() -> ReceivedEntityQa.ageMatches(baby.getId(), unlockReceipt, false)
                && ClientStateEvents.containerPacketVersion() > unlockInventoryReceipt && !baby.isAgeLocked())
                && smoke.client.waitForInventoryCount("minecraft:golden_dandelion", 0, TIMEOUT);
        smoke.observe("dandelion-use-window: lockStartServerGameTime=" + lockServerTime
            + " unlockStartServerGameTime=" + unlockServerTime
            + " endServerGameTime=" + ClientStateEvents.serverGameTime()
            + " serverTimePacketVersions=" + lockServerTimeVersion + "->" + unlockServerTimeVersion
            + "->" + ClientStateEvents.serverTimeVersion()
            + " lockToUnlockElapsedMs=" + (unlockStartedNanos - lockStartedNanos) / 1_000_000L
            + " unlockWaitElapsedMs=" + (System.nanoTime() - unlockStartedNanos) / 1_000_000L
            + " rawMetadataVersion=" + unlockReceipt + "->" + smoke.read(ReceivedEntityQa::version)
            + " inventoryPacketVersion=" + unlockInventoryReceipt + "->" + ClientStateEvents.containerPacketVersion()
            + " receivedLocked=" + smoke.read(baby::isAgeLocked)
            + " remainingFlowers=" + smoke.client.inventoryCount("minecraft:golden_dandelion"));
        smoke.check(unlocked, "baby-age-unlocked", "received cleared lock and second debit after cooldown");
    }

    private void mace() throws Exception {
        LivingEntity target = (LivingEntity) summon("minecraft:iron_golem", "{NoAI:1b}");
        smoke.give("minecraft:mace", 1);
        fixture.ticks(40);
        float before = smoke.read(target::getHealth);
        BlockPos origin = fixture.origin();
        smoke.command("tp @s " + (origin.getX() + 0.5) + " " + (origin.getY() + 5) + " " + (origin.getZ() + 3.5));
        require(fixture.await(Duration.ofSeconds(10), () -> minecraft().player.fallDistance > 2.5
            && minecraft().player.distanceTo(target) < 3.0), "mace falling attack window missing");
        smoke.read(() -> { minecraft().gameMode.attack(minecraft().player, target); return null; });
        smoke.check(smoke.await(() -> target.getHealth() < before - 9), "mace-smash-damage",
            "received golem damage greater than critical-only ceiling nine during >2.5-block native fall; before=" + before
                + " after=" + smoke.read(target::getHealth));
        fixture.ticks(20);
        smoke.check(smoke.read(() -> minecraft().player.isAlive()), "mace-smash-survives", "alive after accepted smash landing");
    }

    private Entity summon(String type, String nbt) throws Exception {
        Set<UUID> before = smoke.read(() -> {
            Set<UUID> ids = new HashSet<>();
            for (Entity entity : minecraft().level.entitiesForRendering()) ids.add(entity.getUUID());
            return ids;
        });
        BlockPos origin = fixture.origin();
        smoke.command("summon " + type + " " + (origin.getX() + 0.5) + " " + origin.getY()
            + " " + (origin.getZ() + 3.5) + " " + nbt);
        require(smoke.await(() -> find(type, before) != null), "summoned creature missing: " + type);
        return smoke.read(() -> find(type, before));
    }

    private Entity find(String type, Set<UUID> before) {
        for (Entity entity : minecraft().level.entitiesForRendering()) {
            if (!before.contains(entity.getUUID()) && W2GameplaySmoke.type(entity).equals(type)) return entity;
        }
        return null;
    }

    private void interact(Entity entity) throws Exception {
        smoke.read(() -> {
            minecraft().gameMode.interact(minecraft().player, entity, new EntityHitResult(entity, entity.position()), InteractionHand.MAIN_HAND);
            return null;
        });
    }

    private void selectEmpty() throws Exception {
        smoke.read(() -> {
            for (int slot = 0; slot < 9; slot++) if (minecraft().player.getInventory().getItem(slot).isEmpty()) {
                minecraft().player.getInventory().setSelectedSlot(slot);
                return null;
            }
            throw new IllegalStateException("empty hotbar slot missing");
        });
    }
}
