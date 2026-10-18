package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.time.Duration;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Operator-assisted setup only; tested actions still use the vanilla client's native interaction path. */
final class W1SmokeFixture {
    private final VanillaUiSmokeScenario smoke;
    private BlockPos origin;

    W1SmokeFixture(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
    }

    void prepare() throws Exception {
        smoke.command("gamemode survival");
        require(smoke.await(() -> !minecraft().gameMode.getPlayerMode().isCreative()), "survival mode missing");
        smoke.command("clear @s");
        require(smoke.await(() -> minecraft().player.getInventory().isEmpty()), "fixture inventory not clear");
        require(smoke.await(() -> minecraft().player.onGround()), "player must land before fixture setup");
        origin = smoke.read(() -> minecraft().player.blockPosition());
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        smoke.command("fill " + (x - 12) + " " + (y - 1) + " " + (z - 12) + " "
            + (x + 12) + " " + (y - 1) + " " + (z + 12) + " minecraft:stone");
        smoke.command("fill " + (x - 12) + " " + y + " " + (z - 12) + " "
            + (x + 12) + " " + (y + 6) + " " + (z + 12) + " minecraft:air");
        smoke.check(smoke.client.waitForBlock(target(0, -1, 2, "minecraft:stone"), "minecraft:stone", TIMEOUT)
                && smoke.client.waitForBlock(target(0, 0, 2, "minecraft:air"), "minecraft:air", TIMEOUT),
            "operator-fixture-ready", "origin=" + origin + "; survival inventory cleared; received pad and air");
    }

    BlockPos origin() {
        if (origin == null) {
            throw new IllegalStateException("fixture not prepared");
        }
        return origin;
    }

    ScenarioBlockTarget target(int dx, int dy, int dz, String block) {
        BlockPos base = origin();
        return new ScenarioBlockTarget(base.getX() + dx, base.getY() + dy, base.getZ() + dz,
            "up", "operator-w1-smoke", block);
    }

    ScenarioBlockTarget set(int dx, int dy, int dz, String blockState) throws Exception {
        String block = blockState.split("\\[", 2)[0];
        ScenarioBlockTarget target = target(dx, dy, dz, block);
        smoke.command("setblock " + target.x() + " " + target.y() + " " + target.z() + " " + blockState);
        smoke.check(smoke.client.waitForBlock(target, block, TIMEOUT),
            "fixture-" + block.substring(block.indexOf(':') + 1) + "-" + dx + "-" + dy + "-" + dz,
            "received fixture block=" + target + " requested=" + blockState);
        return target;
    }

    ScenarioBlockTarget place(int dx, int dy, int dz, String item) throws Exception {
        ScenarioHeldItem held = smoke.give(item, 1);
        ScenarioBlockTarget support = target(dx, dy - 1, dz, "minecraft:stone");
        ScenarioBlockTarget placed = target(dx, dy, dz, item);
        smoke.client.useItemOn(support, held);
        smoke.check(smoke.client.waitForBlock(placed, item, TIMEOUT),
            item.substring(item.indexOf(':') + 1) + "-placed", "native placement received=" + placed);
        return placed;
    }

    void open(ScenarioBlockTarget target, String screenSimpleName) throws Exception {
        smoke.read(() -> {
            var inventory = minecraft().player.getInventory();
            for (int index = 0; index < 9; index++) {
                if (inventory.getItem(index).isEmpty()) {
                    inventory.setSelectedSlot(index);
                    return null;
                }
            }
            throw new IllegalStateException("no empty hotbar slot for native station interaction");
        });
        smoke.client.useItemOn(target, new ScenarioHeldItem("minecraft:air", 0));
        smoke.check(smoke.client.waitForScreenClassName(
                "net.minecraft.client.gui.screens.inventory." + screenSimpleName, TIMEOUT),
            screenSimpleName + "-opened", "native block use opened vanilla menu");
    }

    void close() throws Exception {
        require(smoke.client.closeCurrentScreen(TIMEOUT), "native menu close failed");
    }

    void ticks(int count) throws Exception {
        require(smoke.client.waitForTicks(count, Duration.ofSeconds(Math.max(8, count / 20 + 5))),
            "client tick observation window did not complete: " + count);
    }

    boolean await(Duration timeout, VanillaUiSmokeScenario.Probe probe) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            long version = ClientStateEvents.tickVersion();
            if (smoke.read(probe::get)) {
                return true;
            }
            if (!ScenarioClientState.awaitClientTick(version, deadline)) {
                return smoke.read(probe::get);
            }
        } while (true);
    }

    static ItemStack slot(int index) {
        return minecraft().player.containerMenu.getSlot(index).getItem();
    }

    static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
