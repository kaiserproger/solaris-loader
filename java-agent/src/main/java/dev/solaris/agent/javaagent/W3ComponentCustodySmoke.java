package dev.solaris.agent.javaagent;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;
import static dev.solaris.agent.javaagent.W1SmokeFixture.itemId;

/** Silk hive / filled shulker pickup, accepted custody clicks and refused nested-shulker clicks. */
final class W3ComponentCustodySmoke {
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W3ComponentCustodySmoke(VanillaUiSmokeScenario smoke, W1SmokeFixture fixture) {
        this.smoke = smoke;
        this.fixture = fixture;
    }

    void run() throws Exception {
        ScenarioBlockTarget chest = fixture.set(-2, 0, 2, "minecraft:chest[facing=north]");
        ScenarioBlockTarget hive = fixture.set(0, 0, 2, "minecraft:beehive[honey_level=5,facing=north]");
        fixture.set(0, -1, 4, "minecraft:dirt");
        fixture.set(0, 0, 4, "minecraft:dandelion");
        smoke.command("time set 6000");
        Set<UUID> previous = smoke.read(() -> {
            Set<UUID> ids = new HashSet<>();
            for (var entity : minecraft().level.entitiesForRendering()) ids.add(entity.getUUID());
            return ids;
        });
        var origin = fixture.origin();
        smoke.command("summon minecraft:bee " + (origin.getX() + 0.5) + " " + origin.getY()
            + " " + (origin.getZ() + 3.5) + " {}");
        require(smoke.await(() -> newBee(previous) != null), "component fixture bee not received");
        Bee bee = smoke.read(() -> newBee(previous));
        require(fixture.await(Duration.ofSeconds(60), bee::hasNectar), "component fixture bee did not pollinate flower");
        require(fixture.await(Duration.ofSeconds(30), bee::isRemoved), "component fixture bee did not enter hive");
        smoke.give("minecraft:diamond_axe", 1);
        smoke.command("enchant @s minecraft:silk_touch 1");
        require(smoke.await(() -> minecraft().player.getMainHandItem().isEnchanted()), "silk tool component not received");
        ItemStack hiveStack = mineAndPickUp(hive, "minecraft:beehive");
        smoke.check(hiveStack.get(DataComponents.BEES) != null
                && hiveStack.get(DataComponents.BEES).bees().size() == 1
                && hiveStack.get(DataComponents.BLOCK_STATE) != null
                && "5".equals(hiveStack.get(DataComponents.BLOCK_STATE).properties().get("honey_level")),
            "silk-hive-components", "received native silk drop contains one stored bee and honey level five");
        roundTrip(chest, hiveStack, "silk-hive");

        ScenarioBlockTarget shulker = fixture.set(1, 0, 2, "minecraft:black_shulker_box");
        fixture.open(shulker, "ShulkerBoxScreen");
        smoke.give("minecraft:diamond", 3);
        require(smoke.client.moveSelectedItemToContainerSlot(0, "minecraft:diamond", 3, TIMEOUT), "shulker fixture deposit failed");
        fixture.close();
        require(smoke.client.selectHotbarItem("minecraft:diamond_axe", 1, TIMEOUT).matches("minecraft:diamond_axe", 1),
            "silk axe selection failed");
        ItemStack boxStack = mineAndPickUp(shulker, "minecraft:black_shulker_box");
        var contents = boxStack.get(DataComponents.CONTAINER);
        int diamonds = 0;
        if (contents != null) for (var stack : contents.nonEmptyItems()) {
            require(stack.item().is(Items.DIAMOND.builtInRegistryHolder()), "unexpected copied shulker contents");
            diamonds += stack.count();
        }
        smoke.check(contents != null && diamonds == 3 && itemId(contents.copyOne()).equals("minecraft:diamond"),
            "shulker-copied-container", "received native dropped box retains exactly three diamonds in slot zero");
        roundTrip(chest, boxStack, "filled-shulker");
        rejectNestedShulker(fixture.set(3, 0, 2, "minecraft:shulker_box"), boxStack);
        fixture.ticks(40);
        smoke.check(smoke.client.inventoryCount("minecraft:beehive") == 1
                && smoke.client.inventoryCount("minecraft:black_shulker_box") == 1
                && smoke.client.inventoryCount("minecraft:diamond") == 0 && smoke.read(this::noCustodyDrops),
            "component-custody-exact-once", "settled custody is one silk hive and one filled box, with no duplicate or loose component-fixture drops");
    }

    private Bee newBee(Set<UUID> previous) {
        for (var entity : minecraft().level.entitiesForRendering()) {
            if (entity instanceof Bee bee && !previous.contains(bee.getUUID())
                    && bee.distanceToSqr(fixture.origin().getX() + 0.5, fixture.origin().getY(),
                        fixture.origin().getZ() + 3.5) < 64) return bee;
        }
        return null;
    }

    private boolean noCustodyDrops() {
        var origin = fixture.origin();
        for (var entity : minecraft().level.entitiesForRendering()) {
            if (entity instanceof ItemEntity drop && !drop.isRemoved()
                    && drop.distanceToSqr(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5) < 256) {
                String item = itemId(drop.getItem());
                if (item.equals("minecraft:beehive") || item.equals("minecraft:black_shulker_box")
                        || item.equals("minecraft:diamond")) return false;
            }
        }
        return true;
    }

    private ItemStack mineAndPickUp(ScenarioBlockTarget target, String item) throws Exception {
        var broken = smoke.client.breakBlockUntilDropVisible(target, item, TIMEOUT);
        require(broken.started() && broken.becameAir() && broken.sawDrop(), "native component mining failed: " + broken);
        ItemStack expected = smoke.read(() -> {
            for (var entity : minecraft().level.entitiesForRendering()) {
                if (entity instanceof ItemEntity drop && !drop.isRemoved() && itemId(drop.getItem()).equals(item)
                        && Math.abs(drop.getX() - target.x() - 0.5) < 2
                        && Math.abs(drop.getZ() - target.z() - 0.5) < 2) return drop.getItem().copy();
            }
            throw new IllegalStateException("received copied item drop disappeared before custody snapshot: " + item);
        });
        require(expected.getCount() == 1, "copied block drop must be exactly one: " + item);
        var pickup = smoke.client.collectVisibleItemDropFromInitialCount(target, item, 0, 1, TIMEOUT);
        require(pickup.pickupRestored() && smoke.client.waitForInventoryCount(item, 1, TIMEOUT),
            "native copied item pickup failed: " + pickup);
        return expected;
    }

    private int playerSlot(ItemStack expected) {
        var menu = minecraft().player.containerMenu;
        for (int slot = menu.slots.size() - 36; slot < menu.slots.size(); slot++) {
            if (ItemStack.matches(menu.getSlot(slot).getItem(), expected)) return slot;
        }
        throw new IllegalStateException("copied item components differ after pickup: " + expected);
    }

    private void roundTrip(ScenarioBlockTarget chest, ItemStack expected, String label) throws Exception {
        long opening = smoke.read(ReceivedContainerQa::version);
        fixture.open(chest, "ContainerScreen");
        int source = smoke.read(() -> playerSlot(expected));
        checkImage(opening, source, expected, ItemStack.EMPTY, ItemStack.EMPTY, label + "-pickup-image");
        long receipt = click(source);
        checkImage(receipt, source, ItemStack.EMPTY, ItemStack.EMPTY, expected, label + "-accepted-cursor");
        receipt = click(0);
        checkImage(receipt, source, ItemStack.EMPTY, expected, ItemStack.EMPTY, label + "-accepted-deposit");
        receipt = click(0);
        checkImage(receipt, source, ItemStack.EMPTY, ItemStack.EMPTY, expected, label + "-accepted-withdraw");
        receipt = click(source);
        checkImage(receipt, source, expected, ItemStack.EMPTY, ItemStack.EMPTY, label + "-accepted-return");
        fixture.close();
        opening = smoke.read(ReceivedContainerQa::version);
        fixture.open(chest, "ContainerScreen");
        source = smoke.read(() -> playerSlot(expected));
        checkImage(opening, source, expected, ItemStack.EMPTY, ItemStack.EMPTY, label + "-fresh-reopen-convergence");
        fixture.close();
    }

    private void rejectNestedShulker(ScenarioBlockTarget target, ItemStack expected) throws Exception {
        long opening = smoke.read(ReceivedContainerQa::version);
        fixture.open(target, "ShulkerBoxScreen");
        int source = smoke.read(() -> playerSlot(expected));
        checkImage(opening, source, expected, ItemStack.EMPTY, ItemStack.EMPTY, "nested-shulker-open-image");
        long receipt = click(source);
        checkImage(receipt, source, ItemStack.EMPTY, ItemStack.EMPTY, expected, "nested-shulker-cursor-image");
        receipt = click(0);
        checkImage(receipt, source, ItemStack.EMPTY, ItemStack.EMPTY, expected, "nested-shulker-refused-image");
        receipt = click(source);
        checkImage(receipt, source, expected, ItemStack.EMPTY, ItemStack.EMPTY, "nested-shulker-refusal-return");
        fixture.close();
    }

    private long click(int slot) throws Exception {
        return smoke.read(() -> {
            long before = ReceivedContainerQa.version();
            minecraft().gameMode.handleContainerInput(minecraft().player.containerMenu.containerId,
                slot, 0, ContainerInput.PICKUP, minecraft().player);
            return before;
        });
    }

    private void checkImage(long after, int source, ItemStack player, ItemStack container, ItemStack cursor, String label) throws Exception {
        smoke.check(smoke.await(() -> {
            var menu = minecraft().player.containerMenu;
            if (menu.containerId == 0 || source < 0 || source >= menu.slots.size()) return false;
            return ReceivedContainerQa.slotMatches(menu.containerId, after, source, player)
                && ReceivedContainerQa.slotMatches(menu.containerId, after, 0, container)
                && ReceivedContainerQa.cursorMatches(menu.containerId, after, cursor)
                && ItemStack.matches(menu.getSlot(source).getItem(), player)
                && ItemStack.matches(menu.getSlot(0).getItem(), container)
                && ItemStack.matches(menu.getCarried(), cursor);
        }), label, "fresh raw received menu image and client affected slots/cursor converge with exact copied components; no hash-proof oracle");
    }
}
