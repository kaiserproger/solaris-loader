package dev.solaris.agent.javaagent;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Native ignition and placement; light, fire, TNT entities and blast damage are received vanilla state. */
final class W1TntFireLightSmoke {
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W1TntFireLightSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        fixture = new W1SmokeFixture(smoke);
    }

    void run() throws Exception {
        torchLight();
        tntExplosion();
        fireSpreadAndExtinguish();
    }

    private void torchLight() throws Exception {
        fixture.prepare();
        BlockPos torch = fixture.origin().offset(0, 0, 2);
        BlockPos nearby = torch.east();
        // BLOCK, not combined/sky light: daytime brightness cannot satisfy the assertion.
        fixture.ticks(10);
        int before = smoke.read(() -> minecraft().level.getBrightness(LightLayer.BLOCK, nearby));
        smoke.check(smoke.read(() -> minecraft().level.getBlockState(torch).isAir()) && before < 15,
            "torch-light-baseline", "sample=" + nearby + " block_light_before=" + before);
        fixture.place(0, 0, 2, "minecraft:torch");
        boolean rose = smoke.await(() -> lightRose(before,
            minecraft().level.getBrightness(LightLayer.BLOCK, nearby),
            minecraft().level.getBlockState(torch).is(Blocks.TORCH)));
        int after = smoke.read(() -> minecraft().level.getBrightness(LightLayer.BLOCK, nearby));
        smoke.check(rose, "torch-received-block-light-rise",
            "sample=" + nearby + " block_light_before=" + before + " block_light_after=" + after);
    }

    private void fireSpreadAndExtinguish() throws Exception {
        fixture.prepare();
        smoke.command("weather clear");
        for (int x = -1; x <= 1; x++) {
            for (int z = 2; z <= 3; z++) {
                if (x != 0 || z != 3) {
                    fixture.set(x, 0, z, "minecraft:white_wool");
                }
            }
        }
        ScenarioBlockTarget waterSupport = fixture.set(0, 0, 3, "minecraft:stone");
        BlockPos source = fixture.origin().offset(0, 1, 2);
        BlockPos[] neighbors = {source.west(), source.east(), source.south()};
        boolean sourceWasAir = smoke.read(() -> minecraft().level.getBlockState(source).isAir());
        boolean[] neighborsWereAir = smoke.read(() -> {
            boolean[] result = new boolean[neighbors.length];
            for (int i = 0; i < neighbors.length; i++) {
                result[i] = minecraft().level.getBlockState(neighbors[i]).isAir();
            }
            return result;
        });
        smoke.check(sourceWasAir && neighborsWereAir[0] && neighborsWereAir[1] && neighborsWereAir[2],
            "fire-unlit-neighbor-baseline", "source=" + source + "; all three neighboring cells received air");
        smoke.give("minecraft:flint_and_steel", 1);
        ScenarioUseResult ignition = use(fixture.target(0, 0, 2, "minecraft:white_wool"), Direction.UP);
        boolean ignitionAck = smoke.client.waitForUseAcknowledgement(ignition, TIMEOUT);
        boolean sourceIgnited = ignitionAck && smoke.await(() -> isFire(source));
        smoke.check(sourceIgnited, "fire-native-ignition", "source=" + source + " native_use_ack=" + ignitionAck);

        int[] spreadIndex = {-1};
        boolean spread = fixture.await(Duration.ofSeconds(45), () -> {
            for (int i = 0; i < neighbors.length; i++) {
                if (fireSpread(sourceWasAir, neighborsWereAir[i], sourceIgnited,
                        source.distManhattan(neighbors[i]), isFire(neighbors[i]))) {
                    spreadIndex[0] = i;
                    return true;
                }
            }
            return false;
        });
        smoke.check(spread, "fire-spread-to-previously-unlit-neighbor",
            "source=" + source + " newly_lit_neighbor=" + (spread ? neighbors[spreadIndex[0]] : "none"));

        // A nonflammable pedestal makes water placement possible even if the wool has already burned.
        smoke.give("minecraft:water_bucket", 1);
        ScenarioUseResult waterUse = use(waterSupport, Direction.UP);
        boolean waterAck = smoke.client.waitForUseAcknowledgement(waterUse, TIMEOUT);
        BlockPos water = fixture.origin().offset(0, 1, 3);
        boolean waterReceived = waterAck && smoke.await(() -> minecraft().level.getBlockState(water).is(Blocks.WATER));
        smoke.check(waterReceived, "fire-native-water-placed", "water_source=" + water + " native_use_ack=" + waterAck);
        // Flow extinguishes the fixture; any isolated residual flame must burn out within this bound.
        boolean extinguished = fixture.await(Duration.ofSeconds(90), () -> fixtureFireCount() == 0);
        smoke.check(extinguished && spread && waterReceived, "fire-fixture-extinguished",
            "previously_observed_spread=" + spread + " remaining_received_fire=" + smoke.read(this::fixtureFireCount));
        fixture.ticks(20);
        smoke.check(smoke.read(() -> fixtureFireCount() == 0), "fire-extinguished-stays-out",
            "entire isolated fixture remained free of fire for another 20 client ticks");
    }

    private void tntExplosion() throws Exception {
        fixture.prepare();
        BlockPos origin = fixture.origin();
        // The escape lane and blast floor cannot be removed by the explosion. No blast outcome is commanded.
        smoke.command("fill " + (origin.getX() - 4) + " " + (origin.getY() - 1) + " " + (origin.getZ() - 12)
            + " " + (origin.getX() + 4) + " " + (origin.getY() - 1) + " " + (origin.getZ() + 6)
            + " minecraft:obsidian");
        require(smoke.await(() -> minecraft().level.getBlockState(origin.offset(0, -1, 3)).is(Blocks.OBSIDIAN)
                && minecraft().level.getBlockState(origin.offset(0, -1, -7)).is(Blocks.OBSIDIAN)),
            "received blast-safe floor and escape lane missing");
        smoke.give("minecraft:tnt", 1);
        ScenarioUseResult placement = use(fixture.target(0, -1, 3, "minecraft:obsidian"), Direction.UP);
        require(smoke.client.waitForUseAcknowledgement(placement, TIMEOUT), "native TNT placement was not acknowledged");
        BlockPos tnt = origin.offset(0, 0, 3);
        smoke.check(smoke.await(() -> minecraft().level.getBlockState(tnt).is(Blocks.TNT)),
            "tnt-native-placement", "received_tnt=" + tnt);
        BlockPos[] victims = {origin.offset(-1, 0, 3), origin.offset(1, 0, 3), origin.offset(0, 0, 4)};
        fixture.set(-1, 0, 3, "minecraft:dirt");
        fixture.set(1, 0, 3, "minecraft:dirt");
        fixture.set(0, 0, 4, "minecraft:dirt");
        int before = smoke.read(() -> countVictims(victims, false));
        smoke.check(before == victims.length && smoke.read(() -> primedNear(tnt) == null),
            "tnt-intact-blast-fixture", "received_intact_fixture_blocks=" + before + "; no preexisting nearby primed TNT");
        smoke.give("minecraft:flint_and_steel", 1);
        use(fixture.target(0, 0, 3, "minecraft:tnt"), Direction.NORTH);
        UUID[] primedId = {null};
        boolean primedObserved = smoke.await(() -> {
            PrimedTnt primed = primedNear(tnt);
            if (primed == null || primed.getFuse() <= 0) {
                return false;
            }
            primedId[0] = primed.getUUID();
            return true;
        });
        // Retreat immediately, before screenshot I/O. Only vanilla movement keys move the player.
        boolean safe = retreat(origin);
        smoke.check(primedObserved && safe, "tnt-native-ignition-primed-entity",
            "received_primed_uuid=" + primedId[0] + " blast_safe_distance=" + smoke.read(() -> minecraft().player.distanceToSqr(Vec3.atCenterOf(tnt))));
        boolean exploded = smoke.await(() -> explosionRemovedFixture(primedObserved,
            primedId[0] != null && minecraft().level.getEntity(primedId[0]) == null,
            before, countVictims(victims, true), minecraft().level.getBlockState(tnt).isAir()));
        smoke.check(exploded && smoke.read(() -> minecraft().player.isAlive()), "tnt-explosion-removed-fixture-blocks",
            "primed_uuid=" + primedId[0] + " fixture_before=" + before
                + " removed_fixture_blocks=" + smoke.read(() -> countVictims(victims, true))
                + "; no operator command ran after native ignition");
    }

    private boolean retreat(BlockPos origin) throws Exception {
        smoke.read(() -> {
            minecraft().player.setYRot(0.0F);
            minecraft().player.setXRot(0.0F);
            minecraft().options.keyDown.setDown(true);
            return null;
        });
        try {
            return fixture.await(Duration.ofSeconds(3), () -> minecraft().player.getZ() <= origin.getZ() - 6.0
                && minecraft().player.onGround() && minecraft().player.isAlive());
        } finally {
            smoke.read(() -> { minecraft().options.keyDown.setDown(false); return null; });
        }
    }

    private ScenarioUseResult use(ScenarioBlockTarget target, Direction face) throws Exception {
        return smoke.read(() -> {
            BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
            Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            double reach = minecraft().player.blockInteractionRange();
            require(minecraft().player.getEyePosition().distanceToSqr(point) <= reach * reach,
                "native interaction face is out of survival reach: " + target);
            BlockHitResult hit = new BlockHitResult(point, face, pos, false);
            minecraft().player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
            minecraft().hitResult = hit;
            long ackBefore = ClientStateEvents.blockChangeAckVersion();
            if (minecraft().player.getMainHandItem().getItem() instanceof BucketItem) {
                Vec3 from = minecraft().player.getEyePosition();
                Vec3 to = from.add(minecraft().player.calculateViewVector(
                    minecraft().player.getXRot(), minecraft().player.getYRot()).scale(reach));
                // Match Item.getPlayerPOVHitResult without calling its protected helper.
                BlockHitResult pov = minecraft().level.clip(new ClipContext(
                    from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft().player));
                smoke.observe("bucket-native-pov: player=" + minecraft().player.position()
                    + " from=" + minecraft().player.getEyePosition()
                    + " yaw=" + minecraft().player.getYRot() + " pitch=" + minecraft().player.getXRot()
                    + " hit_type=" + pov.getType() + " hit_block=" + pov.getBlockPos()
                    + " hit_face=" + pov.getDirection() + " hit_location=" + pov.getLocation()
                    + " hit_state=" + minecraft().level.getBlockState(pov.getBlockPos())
                    + " declared_block=" + pos + " declared_face=" + face);
            }
            // Buckets use the vanilla view ray in Item.use, not Item.useOn.
            var result = minecraft().player.getMainHandItem().getItem() instanceof BucketItem
                ? minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND)
                : minecraft().gameMode.useItemOn(minecraft().player, InteractionHand.MAIN_HAND, hit);
            minecraft().player.swing(InteractionHand.MAIN_HAND);
            return new ScenarioUseResult(result.toString(), ackBefore);
        });
    }

    private static boolean isFire(BlockPos pos) {
        return minecraft().level.getBlockState(pos).is(Blocks.FIRE);
    }

    private int fixtureFireCount() {
        int count = 0;
        BlockPos origin = fixture.origin();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = 0; z <= 6; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (isFire(pos)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static PrimedTnt primedNear(BlockPos fixtureTnt) {
        Vec3 center = Vec3.atCenterOf(fixtureTnt);
        for (var entity : minecraft().level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt primed && !primed.isRemoved()
                    && primed.distanceToSqr(center) <= 4.0) {
                return primed;
            }
        }
        return null;
    }

    private static int countVictims(BlockPos[] victims, boolean removed) {
        int count = 0;
        for (BlockPos victim : victims) {
            var state = minecraft().level.getBlockState(victim);
            if (removed ? state.isAir() : state.is(Blocks.DIRT)) {
                count++;
            }
        }
        return count;
    }

    static boolean lightRose(int before, int after, boolean receivedTorch) {
        return receivedTorch && before >= 0 && before <= 15 && after > before && after <= 15;
    }

    static boolean explosionRemovedFixture(boolean nativePrimedObserved, boolean samePrimedGone,
                                           int fixtureBefore, int removedAfter, boolean tntBlockGone) {
        return nativePrimedObserved && samePrimedGone && tntBlockGone
            && fixtureBefore >= 2 && removedAfter == fixtureBefore;
    }

    static boolean fireSpread(boolean sourceWasAir, boolean neighborWasAir, boolean nativeSourceIgnited,
                              int neighborDistance, boolean neighborNowFire) {
        return sourceWasAir && neighborWasAir && nativeSourceIgnited && neighborDistance == 1 && neighborNowFire;
    }
}
