package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Real fishing RNG and synchronized bite metadata; neither bites nor loot are commanded. */
final class W1FishingSmoke {
    // An unenchanted rod can wait up to thirty seconds before fish approach; allow slow server ticks too.
    private static final Duration BITE_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration REEL_TIMEOUT = Duration.ofSeconds(20);
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W1FishingSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        fixture = new W1SmokeFixture(smoke);
    }

    void run() throws Exception {
        fixture.prepare();
        BlockPos origin = fixture.origin();
        smoke.command("time set noon");
        smoke.command("weather rain 180");
        smoke.command("kill @e[type=minecraft:item,distance=..32]");
        fill(origin.offset(-11, -4, 0), origin.offset(11, -1, 23), "minecraft:stone");
        fill(origin.offset(-10, -3, 1), origin.offset(10, -1, 22), "minecraft:water");
        fill(origin.offset(-10, 0, 1), origin.offset(10, 8, 22), "minecraft:air");
        // Clear a bounded, fill-limit-safe sky opening over the expected landing area, even underground.
        int top = smoke.read(() -> minecraft().level.getMaxY() - 1);
        for (int y = origin.getY(); y <= top; y += 64) {
            fill(new BlockPos(origin.getX() - 4, y, origin.getZ() + 1),
                new BlockPos(origin.getX() + 4, Math.min(y + 63, top), origin.getZ() + 12), "minecraft:air");
        }
        fixture.set(0, -1, 6, "minecraft:water");
        smoke.check(smoke.await(() -> {
            for (int y = -3; y <= -1; y++) {
                for (int x = -3; x <= 3; x++) {
                    for (int z = 2; z <= 10; z++) {
                        var fluid = minecraft().level.getFluidState(origin.offset(x, y, z));
                        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) return false;
                    }
                }
            }
            return minecraft().level.canSeeSky(origin.offset(0, 0, 6));
        }), "fishing-open-water-fixture", "received source-water pond, three blocks deep, clear sky above cast lane");
        smoke.give("minecraft:fishing_rod", 1);
        Map<String, Integer> before = smoke.read(W1FishingSmoke::inventory);
        int damageBefore = smoke.read(() -> minecraft().player.getMainHandItem().getDamageValue());
        Set<UUID> previousHooks = smoke.read(() -> {
            Set<UUID> ids = new HashSet<>();
            for (Entity entity : minecraft().level.entitiesForRendering()) {
                if (entity instanceof FishingHook) ids.add(entity.getUUID());
            }
            return ids;
        });
        require(smoke.read(() -> minecraft().player.fishing == null), "player already has a fishing hook before cast");
        EntityDataAccessor<Boolean> biting = bitingAccessor();
        Field state = FishingHook.class.getDeclaredField("currentState");
        state.setAccessible(true);
        smoke.read(() -> {
            minecraft().player.setYRot(0);
            minecraft().player.setXRot(20);
            return null;
        });
        fixture.ticks(2);
        useRod();
        FishingHook[] hook = new FishingHook[1];
        try {
            smoke.check(smoke.await(() -> {
                for (Entity entity : minecraft().level.entitiesForRendering()) {
                    if (entity instanceof FishingHook candidate && !candidate.isRemoved()
                        && !previousHooks.contains(candidate.getUUID())
                        && candidate.getPlayerOwner() == minecraft().player
                        && minecraft().player.fishing == candidate) {
                        hook[0] = candidate;
                        return true;
                    }
                }
                return false;
            }), "fishing-native-cast-hook", "new received FishingHook owned by local player; uuid="
                + (hook[0] == null ? "missing" : smoke.read(hook[0]::getUUID)));
            require(hook[0] != null, "no native fishing hook received");
            FishingHook received = hook[0];
            UUID identity = smoke.read(received::getUUID);
            smoke.check(smoke.await(() -> !received.isRemoved()
                    && "BOBBING".equals(((Enum<?>) state.get(received)).name())
                    && minecraft().level.getFluidState(received.blockPosition()).is(FluidTags.WATER)),
                "fishing-hook-bobbing-water", "received hook position=" + smoke.read(received::position)
                    + " state=" + smoke.read(() -> state.get(received)));
            boolean bite = fixture.await(BITE_TIMEOUT, () -> biteReceived(identity, received.getUUID(),
                !received.isRemoved(), received.getPlayerOwner() == minecraft().player,
                ((Enum<?>) state.get(received)).name(), received.getEntityData().get(biting),
                received.getHookedIn() != null));
            smoke.check(bite, "fishing-received-bite-metadata", "hook_uuid=" + identity
                + " DATA_BITING=" + smoke.read(() -> received.getEntityData().get(biting))
                + " state=" + smoke.read(() -> state.get(received))
                + " timeout_seconds=" + BITE_TIMEOUT.toSeconds() + "; no forced bite or commanded loot");
            long inventoryVersion = ClientStateEvents.containerPacketVersion();
            // Re-check the received nibble at the actual native reel point, not merely a previous screenshot.
            smoke.read(() -> {
                require(biteReceived(identity, received.getUUID(), !received.isRemoved(),
                    received.getPlayerOwner() == minecraft().player, ((Enum<?>) state.get(received)).name(),
                    received.getEntityData().get(biting), received.getHookedIn() != null),
                    "received bite window expired before native reel");
                minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND);
                return null;
            });
            boolean reeled = fixture.await(REEL_TIMEOUT, () -> received.isRemoved()
                && minecraft().player.fishing == null
                && ClientStateEvents.containerPacketVersion() > inventoryVersion
                && exactlyOneCatch(before, inventory())
                && minecraft().player.getMainHandItem().is(Items.FISHING_ROD)
                && minecraft().player.getMainHandItem().getDamageValue() == damageBefore + 1);
            Map<String, Integer> after = smoke.read(W1FishingSmoke::inventory);
            smoke.showInventory();
            smoke.check(reeled, "fishing-native-reel-exact-one-item", "before=" + before + " after=" + after
                + " hook_removed=" + smoke.read(received::isRemoved)
                + " rod_damage_before=" + damageBefore + " rod_damage_after="
                + smoke.read(() -> minecraft().player.getMainHandItem().getDamageValue())
                + " received_inventory_packet_after_reel="
                + (ClientStateEvents.containerPacketVersion() > inventoryVersion)
                + smoke.read(W1FishingSmoke::nearbyDrops));
            fixture.close();
            fixture.ticks(40);
            Map<String, Integer> settled = smoke.read(W1FishingSmoke::inventory);
            smoke.showInventory();
            smoke.check(exactlyOneCatch(before, settled) && after.equals(settled),
                "fishing-catch-exact-once-stable", "received inventory forty ticks later=" + settled
                    + "; no second cast or reel was sent");
        } finally {
            fixture.close();
            smoke.read(() -> { minecraft().options.keyUse.setDown(false); return null; });
            // Native cancellation on failure, never a hook-state mutation or a commanded catch.
            if (smoke.read(() -> minecraft().player.fishing != null)) useRod();
            smoke.command("weather clear");
        }
    }

    private void useRod() throws Exception {
        smoke.read(() -> {
            require(minecraft().player.getMainHandItem().is(Items.FISHING_ROD), "native use requires selected fishing rod");
            minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND);
            return null;
        });
    }

    private static String nearbyDrops() {
        StringBuilder details = new StringBuilder(" nearbyDrops=[");
        for (Entity entity : minecraft().level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item && !item.isRemoved()
                    && item.distanceToSqr(minecraft().player) <= 32 * 32) {
                ItemStack stack = item.getItem();
                details.append(item.getUUID()).append(':')
                    .append(W1SmokeFixture.itemId(stack)).append('x')
                    .append(stack.getCount()).append('@').append(item.position()).append(';');
            }
        }
        return details.append(']').toString();
    }

    private void fill(BlockPos min, BlockPos max, String block) throws Exception {
        smoke.command("fill " + min.getX() + " " + min.getY() + " " + min.getZ() + " "
            + max.getX() + " " + max.getY() + " " + max.getZ() + " " + block);
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Boolean> bitingAccessor() throws Exception {
        Field field = FishingHook.class.getDeclaredField("DATA_BITING");
        field.setAccessible(true);
        return (EntityDataAccessor<Boolean>) field.get(null);
    }

    private static Map<String, Integer> inventory() {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) counts.merge(W1SmokeFixture.itemId(stack), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    static boolean biteReceived(UUID castId, UUID observedId, boolean present, boolean localOwner,
                                String state, boolean biting, boolean hookedEntity) {
        return castId != null && castId.equals(observedId) && present && localOwner
            && "BOBBING".equals(state) && biting && !hookedEntity;
    }

    /** One vanilla loot roll, with exact quantity and no loss; ink-sac junk rolls contain ten items. */
    static boolean exactlyOneCatch(Map<String, Integer> before, Map<String, Integer> after) {
        if (before.getOrDefault("minecraft:fishing_rod", 0) != 1
            || after.getOrDefault("minecraft:fishing_rod", 0) < 1) return false;
        int gain = 0;
        for (var entry : before.entrySet()) {
            if (entry.getValue() < 0 || after.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        for (var entry : after.entrySet()) {
            int old = before.getOrDefault(entry.getKey(), 0);
            int delta = entry.getValue() - old;
            if (entry.getValue() < 0 || delta < 0) return false;
            if (delta == 0) continue;
            int expected = entry.getKey().equals("minecraft:ink_sac") ? 10 : 1;
            if (!isFishingLoot(entry.getKey()) || delta != expected) return false;
            gain++;
            if (gain > 1) return false;
        }
        return gain == 1;
    }

    private static boolean isFishingLoot(String item) {
        return switch (item) {
            case "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
                "minecraft:bow", "minecraft:enchanted_book", "minecraft:fishing_rod", "minecraft:name_tag",
                "minecraft:nautilus_shell", "minecraft:saddle", "minecraft:lily_pad", "minecraft:bowl",
                "minecraft:leather", "minecraft:leather_boots", "minecraft:rotten_flesh", "minecraft:stick",
                "minecraft:string", "minecraft:potion", "minecraft:bone", "minecraft:ink_sac",
                "minecraft:tripwire_hook", "minecraft:bamboo" -> true;
            default -> false;
        };
    }
}
