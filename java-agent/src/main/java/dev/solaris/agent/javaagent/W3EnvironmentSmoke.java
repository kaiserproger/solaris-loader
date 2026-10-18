package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.time.Duration;
import java.util.Set;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Declared structures, native vibration/swim input, and received effect/raid transitions. */
final class W3EnvironmentSmoke {
    static final Set<String> IDS = Set.of("playable-w3-conduit-dolphin", "playable-w3-sculk-warden", "playable-w3-raid-hero");
    private static final Set<String> RAIDERS = Set.of("minecraft:pillager", "minecraft:vindicator", "minecraft:witch",
        "minecraft:evoker", "minecraft:ravager", "minecraft:illusioner");
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W3EnvironmentSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        fixture = new W1SmokeFixture(smoke);
    }

    void run(String id) throws Exception {
        fixture.prepare();
        switch (id) {
            case "playable-w3-conduit-dolphin" -> conduitDolphin();
            case "playable-w3-sculk-warden" -> sculkWarden();
            case "playable-w3-raid-hero" -> raidHero();
            default -> throw new IllegalArgumentException(id);
        }
    }

    private void conduitDolphin() throws Exception {
        BlockPos origin = fixture.origin();
        int x = origin.getX(), y = origin.getY() + 1, z = origin.getZ() + 4;
        smoke.command("fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " "
            + (x + 3) + " " + (y + 3) + " " + (z + 3) + " minecraft:water");
        // Three intersecting 5x5 rings: the verified 42 frame cells, centre clear for water.
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            if ((dx == 0 && (Math.abs(dy) == 2 || Math.abs(dz) == 2))
                || (dy == 0 && (Math.abs(dx) == 2 || Math.abs(dz) == 2))
                || (dz == 0 && (Math.abs(dx) == 2 || Math.abs(dy) == 2))) {
                smoke.command("setblock " + (x + dx) + " " + (y + dy) + " " + (z + dz) + " minecraft:prismarine");
            }
        }
        smoke.command("setblock " + x + " " + y + " " + z + " minecraft:conduit[waterlogged=true]");
        smoke.command("effect clear @s");
        smoke.command("tp @s " + (x + 1.5) + " " + y + " " + (z + 0.5));
        smoke.check(fixture.await(Duration.ofSeconds(15), () -> minecraft().player.hasEffect(MobEffects.CONDUIT_POWER)),
            "conduit-native-effect", "received Conduit Power from water and 42-cell frame; effect not commanded");
        smoke.command("summon minecraft:dolphin " + (x + 1.5) + " " + y + " " + (z + 1.5) + " {}");
        smoke.read(() -> {
            minecraft().options.keySprint.setDown(true);
            minecraft().options.keyUp.setDown(true);
            return null;
        });
        try {
            smoke.check(fixture.await(Duration.ofSeconds(20), () -> minecraft().player.hasEffect(MobEffects.DOLPHINS_GRACE)),
                "dolphin-swim-effect", "received Dolphin's Grace from nearby dolphin under native swimming input");
        } finally {
            smoke.read(() -> {
                minecraft().options.keySprint.setDown(false);
                minecraft().options.keyUp.setDown(false);
                return null;
            });
        }
        smoke.command("setblock " + x + " " + y + " " + z + " minecraft:air");
        smoke.check(fixture.await(Duration.ofSeconds(25), () -> !minecraft().player.hasEffect(MobEffects.CONDUIT_POWER)),
            "conduit-removal-expires", "effect expires after structure removed; no commanded effect clear");
    }

    private void sculkWarden() throws Exception {
        ScenarioBlockTarget sensor = fixture.set(0, 0, 3, "minecraft:sculk_sensor");
        ScenarioBlockTarget lever = fixture.set(1, 0, 1, "minecraft:lever[face=floor,powered=false]");
        fixture.ticks(20);
        require(smoke.client.waitForBlockProperty(sensor, "sculk_sensor_phase", "inactive", TIMEOUT),
            "sensor fixture baseline did not settle");
        smoke.client.useItemOn(lever, new ScenarioHeldItem("minecraft:air", 0));
        require(smoke.client.waitForBlockProperty(lever, "powered", "true", TIMEOUT), "native lever state not received");
        smoke.check(smoke.client.waitForBlockProperty(sensor, "sculk_sensor_phase", "active", TIMEOUT),
            "sculk-native-vibration", "received sensor active after native lever click, not commanded sensor state");
        require(smoke.client.waitForBlockProperty(sensor, "sculk_sensor_phase", "inactive", TIMEOUT), "sensor did not return inactive");
        ScenarioBlockTarget shrieker = fixture.set(0, 0, 2, "minecraft:sculk_shrieker[can_summon=true]");
        require(smoke.client.waitForBlockProperty(shrieker, "shrieking", "false", TIMEOUT), "shrieker fixture already active");
        BlockPos origin = fixture.origin();
        smoke.command("tp @s " + (origin.getX() + 0.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 2.5));
        smoke.check(smoke.client.waitForBlockProperty(shrieker, "shrieking", "true", TIMEOUT),
            "shrieker-contact", "received shrieking after player contact");
        smoke.command("summon minecraft:warden " + (origin.getX() + 2.5) + " " + origin.getY() + " "
            + (origin.getZ() + 3.5) + " {}");
        smoke.check(fixture.await(Duration.ofSeconds(20), () -> {
            for (Entity entity : minecraft().level.entitiesForRendering()) if (W2GameplaySmoke.type(entity).equals("minecraft:warden")) return true;
            return false;
        }), "warden-wire-smoke", "received warden species; fixture summon, not four-warning emergence parity");
        smoke.check(fixture.await(Duration.ofSeconds(20), () -> minecraft().player.hasEffect(MobEffects.DARKNESS)),
            "warden-darkness", "received Darkness from nearby warden, not an effect command");
    }

    private void raidHero() throws Exception {
        smoke.command("difficulty easy");
        smoke.command("time set midnight");
        fixture.set(2, 0, 2, "minecraft:red_bed[facing=north,part=foot]");
        fixture.set(2, 0, 1, "minecraft:red_bed[facing=north,part=head]");
        fixture.set(3, 0, 2, "minecraft:bell");
        BlockPos origin = fixture.origin();
        smoke.command("summon minecraft:villager " + (origin.getX() + 2.5) + " " + origin.getY() + " "
            + (origin.getZ() + 2.5) + " {}");
        fixture.ticks(100);
        // Scripted-clear raid smoke is not combat QA: preserve the observer and village through capture latency.
        smoke.command("effect give @s resistance 300 4 true");
        smoke.command("effect give @e[type=minecraft:villager] resistance 300 4 true");
        smoke.check(smoke.await(() -> minecraft().player.hasEffect(MobEffects.RESISTANCE)
                && minecraft().player.getEffect(MobEffects.RESISTANCE).getAmplifier() == 4),
            "raid-observer-protected", "received declared resistance-five fixture; wave clears are not earned combat");
        smoke.command("effect give @s bad_omen 120 0");
        smoke.check(fixture.await(Duration.ofSeconds(30), () -> minecraft().player.hasEffect(MobEffects.RAID_OMEN)
                && !minecraft().player.hasEffect(MobEffects.BAD_OMEN)),
            "village-raid-omen", "native occupied village converts Bad Omen to Raid Omen");
        for (int wave = 1; wave <= 3; wave++) {
            require(fixture.await(Duration.ofSeconds(wave == 1 ? 90 : 45), this::hasRaiders), "raid wave not received: " + wave);
            smoke.check(true, "raid-wave-" + wave, "received live raider wave; operator clearing is declared, not earned combat");
            for (String raider : RAIDERS) smoke.command("kill @e[type=" + raider + "]");
            require(fixture.await(Duration.ofSeconds(15), () -> !hasRaiders()), "raid wave did not clear: " + wave);
        }
        smoke.check(fixture.await(Duration.ofSeconds(30), () -> minecraft().player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)),
            "raid-victory-hero", "received Hero from native raid victory after three scripted wave clears; never effect-given");
    }

    private boolean hasRaiders() {
        for (Entity entity : minecraft().level.entitiesForRendering()) {
            if (RAIDERS.contains(W2GameplaySmoke.type(entity)) && !entity.isRemoved()) return true;
        }
        return false;
    }
}
