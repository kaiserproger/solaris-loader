package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.properties.PistonType;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Native redstone inputs and native menu transfers; operator commands only build unpowered fixtures. */
final class W1RedstoneSmoke {
    private static final String DIAMOND = "minecraft:diamond";
    private static final int TRANSFER_COUNT = 5;
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W1RedstoneSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        this.fixture = new W1SmokeFixture(smoke);
    }

    void run() throws Exception {
        fixture.prepare();
        leverCircuit();
        buttonPiston();
        hopperComparator();
    }

    private void leverCircuit() throws Exception {
        ScenarioBlockTarget lever = fixture.set(-2, 0, 1, "minecraft:lever[face=floor,facing=east]");
        fixture.set(-1, 0, 1, "minecraft:redstone_wire");
        // Vanilla diode FACING points toward its INPUT, not its output: input west, output east.
        fixture.set(0, 0, 1, "minecraft:repeater[facing=west,delay=1]");
        fixture.set(1, 0, 1, "minecraft:redstone_wire");
        fixture.set(2, 0, 1, "minecraft:redstone_lamp");
        checkCircuit(false, "lever-circuit-initially-off");
        useEmptyHand(lever);
        checkCircuit(true, "lever-wire-repeater-lamp-on");
        useEmptyHand(lever);
        checkCircuit(false, "lever-wire-repeater-lamp-off");
    }

    private void checkCircuit(boolean powered, String step) throws Exception {
        boolean received = smoke.await(() -> circuitMatches(powered, circuit()));
        smoke.check(received, step, "received=" + smoke.read(this::circuit));
    }

    private CircuitSnapshot circuit() {
        BlockState lever = state(-2, 0, 1);
        BlockState input = state(-1, 0, 1);
        BlockState repeater = state(0, 0, 1);
        BlockState output = state(1, 0, 1);
        BlockState lamp = state(2, 0, 1);
        boolean fixtureValid = lever.is(Blocks.LEVER) && input.is(Blocks.REDSTONE_WIRE)
            && repeater.is(Blocks.REPEATER) && output.is(Blocks.REDSTONE_WIRE)
            && lamp.is(Blocks.REDSTONE_LAMP)
            && repeater.getValue(RepeaterBlock.FACING) == Direction.WEST
            && !repeater.getValue(RepeaterBlock.LOCKED);
        return new CircuitSnapshot(fixtureValid, lever.is(Blocks.LEVER) && lever.getValue(LeverBlock.POWERED),
            wirePower(input), repeater.is(Blocks.REPEATER) && repeater.getValue(DiodeBlock.POWERED),
            wirePower(output), lamp.is(Blocks.REDSTONE_LAMP) && lamp.getValue(RedstoneLampBlock.LIT));
    }

    static boolean circuitMatches(boolean powered, CircuitSnapshot state) {
        return state != null && state.validFixture() && state.leverPowered() == powered
            && state.inputPower() == (powered ? 15 : 0) && state.repeaterPowered() == powered
            && state.outputPower() == (powered ? 15 : 0) && state.lampLit() == powered;
    }

    record CircuitSnapshot(boolean validFixture, boolean leverPowered, int inputPower,
                           boolean repeaterPowered, int outputPower, boolean lampLit) {}

    private void buttonPiston() throws Exception {
        ScenarioBlockTarget button = fixture.set(-1, 0, -2, "minecraft:stone_button[face=floor,facing=east]");
        fixture.set(0, 0, -2, "minecraft:piston[facing=east]");
        fixture.set(1, 0, -2, "minecraft:stone");
        smoke.check(smoke.await(() -> state(0, 0, -2).is(Blocks.PISTON)
                && state(0, 0, -2).getValue(PistonBaseBlock.FACING) == Direction.EAST
                && !state(0, 0, -2).getValue(PistonBaseBlock.EXTENDED)
                && state(1, 0, -2).is(Blocks.STONE) && state(2, 0, -2).isAir()),
            "button-piston-initially-retracted", "unpowered east-facing piston; stone payload in front");
        useEmptyHand(button);
        smoke.check(smoke.await(() -> pistonMatches(true, piston())), "button-piston-extends",
            "received=" + smoke.read(this::piston));
        smoke.check(smoke.await(() -> pistonMatches(false, piston())), "button-piston-retracts",
            "received=" + smoke.read(this::piston));
    }

    private PistonSnapshot piston() {
        BlockState button = state(-1, 0, -2);
        BlockState piston = state(0, 0, -2);
        BlockState head = state(1, 0, -2);
        boolean valid = button.is(Blocks.STONE_BUTTON) && piston.is(Blocks.PISTON)
            && piston.getValue(PistonBaseBlock.FACING) == Direction.EAST;
        boolean eastHead = head.is(Blocks.PISTON_HEAD)
            && head.getValue(PistonHeadBlock.FACING) == Direction.EAST
            && head.getValue(PistonHeadBlock.TYPE) == PistonType.DEFAULT;
        return new PistonSnapshot(valid, button.is(Blocks.STONE_BUTTON) && button.getValue(ButtonBlock.POWERED),
            piston.is(Blocks.PISTON) && piston.getValue(PistonBaseBlock.EXTENDED), eastHead, head.isAir(),
            state(2, 0, -2).is(Blocks.STONE));
    }

    static boolean pistonMatches(boolean extended, PistonSnapshot state) {
        return state != null && state.validFixture() && state.buttonPowered() == extended
            && state.extended() == extended && state.eastNormalHead() == extended
            && state.headCellAir() != extended && state.payloadAtDestination();
    }

    record PistonSnapshot(boolean validFixture, boolean buttonPowered, boolean extended,
                          boolean eastNormalHead, boolean headCellAir, boolean payloadAtDestination) {}

    private void hopperComparator() throws Exception {
        ScenarioBlockTarget source = fixture.set(-2, 0, 4, "minecraft:hopper[facing=east]");
        ScenarioBlockTarget intermediate = fixture.set(-1, 0, 4, "minecraft:hopper[facing=east]");
        ScenarioBlockTarget chest = fixture.set(0, 0, 4, "minecraft:chest[facing=north,type=single]");
        fixture.set(1, 0, 4, "minecraft:comparator[facing=west,mode=compare]");
        fixture.set(2, 0, 4, "minecraft:redstone_wire");
        ScenarioBlockTarget sourceLock = fixture.set(-2, 0, 3, "minecraft:lever[face=floor,facing=east]");
        ScenarioBlockTarget intermediateLock = fixture.set(-1, 0, 3, "minecraft:lever[face=floor,facing=east]");
        useEmptyHand(sourceLock);
        useEmptyHand(intermediateLock);
        smoke.check(smoke.await(() -> hopperState(-2, false) && hopperState(-1, false)),
            "hopper-inputs-locked", "both hoppers disabled by native lever use, east-facing into chest");
        checkMenu(source, 5, 0, "hopper-source-initially-empty");
        checkMenu(intermediate, 5, 0, "hopper-intermediate-initially-empty");
        checkMenu(chest, 27, 0, "hopper-output-initially-empty");
        checkComparator(0, "comparator-empty-chest-zero");

        smoke.give(DIAMOND, TRANSFER_COUNT);
        open(source, 5);
        int insertionState = smoke.read(() -> minecraft().player.containerMenu.getStateId());
        boolean inserted = smoke.client.moveSelectedItemToContainerSlot(0, DIAMOND, TRANSFER_COUNT, TIMEOUT);
        smoke.check(inserted && smoke.await(() -> minecraft().player.containerMenu.getStateId() != insertionState
                && inventoryMatches(menuSnapshot(5), 5, TRANSFER_COUNT)),
            "hopper-native-five-diamond-insertion", "received_source=" + smoke.read(() -> menuSnapshot(5)));
        fixture.close();

        // A locked downstream hopper still accepts its upstream hopper's pushes. Stage the entire
        // batch in the intermediate menu before releasing it, proving both hops rather than one shortcut.
        useEmptyHand(sourceLock);
        smoke.check(smoke.await(() -> hopperState(-2, true) && hopperState(-1, false)),
            "hopper-source-released", "native lever release enables source while intermediate stays locked");
        checkMenu(intermediate, 5, TRANSFER_COUNT, "hopper-intermediate-received-five");
        checkMenu(source, 5, 0, "hopper-source-drained");
        checkMenu(chest, 27, 0, "hopper-output-held-empty-before-release");
        useEmptyHand(intermediateLock);
        smoke.check(smoke.await(() -> hopperState(-2, true) && hopperState(-1, true)),
            "hopper-intermediate-released", "both east-facing hoppers now enabled by native use");
        InventorySnapshot output = checkMenu(chest, 27, TRANSFER_COUNT, "hopper-chest-received-exactly-five");
        InventorySnapshot sourceAfter = checkMenu(source, 5, 0, "hopper-source-remains-empty");
        InventorySnapshot intermediateAfter = checkMenu(intermediate, 5, 0, "hopper-intermediate-drained");
        smoke.check(transferMatches(sourceAfter, intermediateAfter, output), "hopper-chain-conserves-five",
            "source=" + sourceAfter + " intermediate=" + intermediateAfter + " output=" + output);
        checkComparator(1, "comparator-five-diamond-chest-output-one");

        // Observe every sampled client tick over four seconds with the output menu open. Do not
        // accept just a final count after a delay: any excess, deficit, cursor stack or player copy fails.
        open(chest, 27);
        smoke.check(staysExactForTicks(27, TRANSFER_COUNT, 80), "hopper-output-stable-no-duplicates",
            "80 client game ticks; received_output=" + smoke.read(() -> menuSnapshot(27)));
        fixture.close();
        checkMenu(source, 5, 0, "hopper-source-stable-empty");
        checkMenu(intermediate, 5, 0, "hopper-intermediate-stable-empty");
        open(chest, 27);
        require(smoke.client.moveContainerSlotToInventory(0, DIAMOND, TRANSFER_COUNT, TIMEOUT),
            "native output chest withdrawal failed");
        smoke.check(smoke.await(() -> {
            InventorySnapshot snapshot = menuSnapshot(27);
            return snapshot != null && snapshot.diamonds() == 0 && snapshot.otherItems() == 0
                && snapshot.playerDiamonds() == TRANSFER_COUNT && snapshot.cursorEmpty();
        }), "hopper-native-output-withdrawal", "received=" + smoke.read(() -> menuSnapshot(27)));
        fixture.close();
        checkComparator(0, "comparator-drained-chest-zero");
    }

    private boolean hopperState(int dx, boolean enabled) {
        BlockState hopper = state(dx, 0, 4);
        return hopper.is(Blocks.HOPPER) && hopper.getValue(HopperBlock.FACING) == Direction.EAST
            && hopper.getValue(HopperBlock.ENABLED) == enabled;
    }

    private void checkComparator(int expectedPower, String step) throws Exception {
        smoke.check(smoke.await(() -> {
            BlockState comparator = state(1, 0, 4);
            return comparator.is(Blocks.COMPARATOR)
                && comparator.getValue(ComparatorBlock.FACING) == Direction.WEST
                && comparator.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE
                && comparatorMatches(expectedPower, comparator.getValue(DiodeBlock.POWERED),
                    wirePower(state(2, 0, 4)));
        }), step, "received_comparator=" + smoke.read(() -> state(1, 0, 4))
            + " adjacent_output_wire=" + smoke.read(() -> state(2, 0, 4)));
    }

    static boolean comparatorMatches(int expectedPower, boolean powered, int adjacentWirePower) {
        return expectedPower >= 0 && expectedPower <= 15 && powered == (expectedPower > 0)
            && adjacentWirePower == expectedPower;
    }

    private InventorySnapshot checkMenu(ScenarioBlockTarget target, int slots, int diamonds, String step)
        throws Exception {
        open(target, slots);
        boolean received = smoke.await(() -> inventoryMatches(menuSnapshot(slots), slots, diamonds));
        InventorySnapshot snapshot = smoke.read(() -> menuSnapshot(slots));
        smoke.check(received && inventoryMatches(snapshot, slots, diamonds), step, "received_menu=" + snapshot);
        fixture.close();
        return snapshot;
    }

    private void open(ScenarioBlockTarget target, int slots) throws Exception {
        require(smoke.client.approachBlock(target, TIMEOUT), "native container approach failed: " + target);
        fixture.open(target, slots == 5 ? "HopperScreen" : "ContainerScreen");
    }

    private void useEmptyHand(ScenarioBlockTarget target) throws Exception {
        require(smoke.client.approachBlock(target, TIMEOUT), "native redstone input approach failed: " + target);
        smoke.read(() -> {
            var inventory = minecraft().player.getInventory();
            for (int index = 0; index < 9; index++) {
                if (inventory.getItem(index).isEmpty()) {
                    inventory.setSelectedSlot(index);
                    return null;
                }
            }
            throw new IllegalStateException("no empty hotbar slot for native redstone input");
        });
        smoke.client.useItemOn(target, new ScenarioHeldItem("minecraft:air", 0));
    }

    private boolean staysExactForTicks(int slots, int diamonds, int ticks) throws Exception {
        long start = smoke.read(() -> minecraft().level.getGameTime());
        boolean[] exact = {true};
        boolean completed = smoke.await(() -> {
            exact[0] &= inventoryMatches(menuSnapshot(slots), slots, diamonds);
            return !exact[0] || minecraft().level.getGameTime() - start >= ticks;
        });
        return completed && exact[0];
    }

    private InventorySnapshot menuSnapshot(int expectedSlots) {
        var menu = minecraft().player.containerMenu;
        if ((expectedSlots == 5 && !(menu instanceof HopperMenu))
            || (expectedSlots == 27 && !(menu instanceof ChestMenu chest && chest.getRowCount() == 3))) {
            return null;
        }
        int diamonds = 0, other = 0, playerDiamonds = 0;
        for (int index = 0; index < expectedSlots; index++) {
            ItemStack stack = menu.getSlot(index).getItem();
            if (DIAMOND.equals(W1SmokeFixture.itemId(stack))) {
                diamonds += stack.getCount();
            } else if (!stack.isEmpty()) {
                other += stack.getCount();
            }
        }
        for (int index = expectedSlots; index < menu.slots.size(); index++) {
            ItemStack stack = menu.getSlot(index).getItem();
            if (DIAMOND.equals(W1SmokeFixture.itemId(stack))) {
                playerDiamonds += stack.getCount();
            }
        }
        return new InventorySnapshot(expectedSlots, diamonds, other, playerDiamonds, menu.getCarried().isEmpty());
    }

    static boolean inventoryMatches(InventorySnapshot state, int slots, int diamonds) {
        return state != null && (slots == 5 || slots == 27) && diamonds >= 0
            && state.slots() == slots && state.diamonds() == diamonds && state.otherItems() == 0
            && state.playerDiamonds() == 0 && state.cursorEmpty();
    }

    static boolean transferMatches(InventorySnapshot source, InventorySnapshot intermediate, InventorySnapshot output) {
        return inventoryMatches(source, 5, 0) && inventoryMatches(intermediate, 5, 0)
            && inventoryMatches(output, 27, TRANSFER_COUNT);
    }

    record InventorySnapshot(int slots, int diamonds, int otherItems, int playerDiamonds, boolean cursorEmpty) {}

    private BlockState state(int dx, int dy, int dz) {
        BlockPos origin = fixture.origin();
        return minecraft().level.getBlockState(origin.offset(dx, dy, dz));
    }

    private static int wirePower(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) ? state.getValue(RedstoneWireBlock.POWER) : -1;
    }
}
