package dev.solaris.agent.javaagent;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.EntityHitResult;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;
import static dev.solaris.agent.javaagent.W1SmokeFixture.itemId;
import static dev.solaris.agent.javaagent.W1SmokeFixture.slot;

/** Egg, job-site claim and one trade through vanilla interactions, with a fresh-menu persistence oracle. */
final class W1VillagerTradeSmoke {
    private static final String EGG = "minecraft:villager_spawn_egg";
    // Vanilla's job-site search has randomized delays; the normal eight-second UI wait is too short.
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(60);
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W1VillagerTradeSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        this.fixture = new W1SmokeFixture(smoke);
    }

    void run() throws Exception {
        fixture.prepare();
        smoke.command("time set 6000");
        boolean receivedDaytime = smoke.await(() -> Math.floorMod(minecraft().level.getOverworldClockTime(), 24000L) >= 6000
                && Math.floorMod(minecraft().level.getOverworldClockTime(), 24000L) < 8000);
        long clock = smoke.read(() -> minecraft().level.getOverworldClockTime());
        long gameTime = smoke.read(() -> minecraft().level.getGameTime());
        smoke.check(receivedDaytime, "villager-working-daytime",
            "received daytime permits vanilla job-site assignment; overworldClock=" + clock + " gameTime=" + gameTime);

        // Operator-built containment is fixture setup, not an operator-assigned profession or entity.
        for (int dy = 0; dy < 2; dy++) {
            fixture.set(-1, dy, 2, "minecraft:glass");
            fixture.set(0, dy, 3, "minecraft:glass");
            fixture.set(1, dy, 3, "minecraft:glass");
            fixture.set(2, dy, 2, "minecraft:glass");
            fixture.set(1, dy, 1, "minecraft:glass");
        }
        Set<UUID> existing = smoke.read(() -> {
            Set<UUID> ids = new HashSet<>();
            for (var entity : minecraft().level.entitiesForRendering()) {
                if (entity instanceof Villager) ids.add(entity.getUUID());
            }
            return ids;
        });
        ScenarioHeldItem egg = smoke.give(EGG, 1);
        smoke.client.useItemOn(fixture.target(0, -1, 2, "minecraft:stone"), egg);
        BlockPos spawn = fixture.origin().offset(0, 0, 2);
        boolean spawned = smoke.await(() -> newVillager(existing, spawn) != null && inventoryCount(EGG) == 0);
        smoke.check(spawned, "villager-native-spawn-egg", "new received Villager near=" + spawn
            + " egg_inventory=" + smoke.read(() -> inventoryCount(EGG)));
        UUID villagerId = smoke.read(() -> newVillager(existing, spawn).getUUID());
        smoke.check(smoke.read(() -> villager(villagerId).getVillagerData().profession().is(VillagerProfession.NONE)
                && !villager(villagerId).isBaby()),
            "villager-unassigned-adult", "received villager_uuid=" + villagerId + " data="
                + smoke.read(() -> villager(villagerId).getVillagerData()));
        // One-block front lip retains the villager while leaving its upper body visible and reachable.
        fixture.set(0, 0, 1, "minecraft:glass");
        ScenarioBlockTarget workstation = fixture.place(1, 0, 2, "minecraft:fletching_table");
        smoke.check(fixture.await(JOB_TIMEOUT, () -> {
                Villager villager = villager(villagerId);
                return villager != null && villager.getVillagerData().profession().is(VillagerProfession.FLETCHER);
            }), "villager-native-job-assignment", "workstation=" + workstation + " villager_uuid=" + villagerId
                + " received_data=" + smoke.read(() -> villager(villagerId).getVillagerData()));

        // Night prevents legitimate job-site restocking from resetting uses before the reopen assertion.
        smoke.command("time set 13000");
        boolean receivedNight = smoke.await(() -> Math.floorMod(minecraft().level.getOverworldClockTime(), 24000L) >= 13000
                && Math.floorMod(minecraft().level.getOverworldClockTime(), 24000L) < 23000);
        smoke.check(receivedNight, "villager-restock-disabled-night",
            "received night before native trade; overworldClock="
                + smoke.read(() -> minecraft().level.getOverworldClockTime()));

        open(villagerId, "villager-merchant-open");
        int offerIndex = smoke.read(() -> {
            var offers = menu().getOffers();
            // Stable first eligible received offer, not a hard-coded/random trade recipe.
            for (int index = 0; index < offers.size(); index++) {
                if (eligibleOffer(snapshot(index))) return index;
            }
            return -1;
        });
        smoke.check(offerIndex >= 0, "villager-affordable-one-input-offer", "received_offers="
            + smoke.read(() -> menu().getOffers().size()) + " chosen_index=" + offerIndex);
        OfferSnapshot chosen = smoke.read(() -> snapshot(offerIndex));
        selectOffer(offerIndex);
        smoke.check(smoke.read(() -> VanillaUiSmokeScenario.field(minecraft().gui.screen(), "shopItem", Integer.class)
                == offerIndex && slot(0).isEmpty() && slot(1).isEmpty() && menu().getCarried().isEmpty()),
            "villager-native-offer-selection", "selected received offer=" + chosen);

        smoke.give(chosen.inputId(), chosen.inputCount());
        TradeState before = smoke.read(() -> tradeState(chosen, offerIndex));
        smoke.check(before.input() == chosen.inputCount() && before.output() == 0
                && before.uses() == chosen.uses() && before.maxUses() == chosen.maxUses(),
            "villager-exact-payment-fixture", "before=" + before + " offer=" + chosen);
        long paymentServerTime = ClientStateEvents.serverGameTime();
        long paymentServerTimeVersion = ClientStateEvents.serverTimeVersion();
        long paymentStartedNanos = System.nanoTime();
        int insertedState = smoke.read(() -> {
            int source = inventorySlot(chosen.inputId(), chosen.inputCount());
            int state = menu().getStateId();
            click(source);
            click(0);
            return state;
        });
        smoke.check(smoke.await(() -> menu().getStateId() != insertedState
                && matches(slot(0), chosen.inputId(), chosen.inputCount()) && slot(1).isEmpty()
                && menu().getCarried().isEmpty() && matches(slot(2), chosen.outputId(), chosen.outputCount())
                && ItemStack.isSameItemSameComponents(slot(2), menu().getOffers().get(offerIndex).getResult())
                && inventoryCount(chosen.inputId()) == 0),
            "villager-native-payment-preview", "received payment and result slots after native pickup clicks; offer=" + chosen
                + smoke.read(() -> " stateBefore=" + insertedState + " stateAfter=" + menu().getStateId()
                    + " slot0=" + slot(0) + " slot1=" + slot(1) + " result=" + slot(2)
                    + " cursor=" + menu().getCarried() + " inventoryInput=" + inventoryCount(chosen.inputId()))
                + " receivedServerGameTime=" + paymentServerTime + "->" + ClientStateEvents.serverGameTime()
                + " serverTimePacketVersion=" + paymentServerTimeVersion + "->" + ClientStateEvents.serverTimeVersion()
                + " elapsedMs=" + ((System.nanoTime() - paymentStartedNanos) / 1_000_000L));

        // A normal pickup takes ONE recipe result. Shift-click can execute several trades and is intentionally not used.
        int takenState = smoke.read(() -> {
            int destination = emptyInventorySlot();
            int state = menu().getStateId();
            click(2);
            click(destination);
            return state;
        });
        boolean traded = smoke.await(() -> menu().getStateId() != takenState
            && menu().getCarried().isEmpty() && slot(0).isEmpty() && slot(1).isEmpty() && slot(2).isEmpty()
            && tradedExactlyOnce(before, tradeState(chosen, offerIndex), chosen.inputCount(), chosen.outputCount()));
        smoke.check(traded, "villager-native-single-trade", "before=" + before + " after="
            + smoke.read(() -> tradeState(chosen, offerIndex)) + " offer=" + chosen);
        fixture.ticks(20);
        smoke.check(smoke.read(() -> tradedExactlyOnce(before, tradeState(chosen, offerIndex),
                chosen.inputCount(), chosen.outputCount()) && menu().getCarried().isEmpty()
                && slot(0).isEmpty() && slot(1).isEmpty() && slot(2).isEmpty()),
            "villager-single-trade-stable", "no duplicate input debit, result credit or offer use after 20 ticks");
        fixture.close();
        smoke.showInventory();
        smoke.check(smoke.read(() -> inventoryCount(chosen.inputId()) == before.input() - chosen.inputCount()
                && inventoryCount(chosen.outputId()) == before.output() + chosen.outputCount()),
            "villager-trade-inventory", "received input=" + smoke.read(() -> inventoryCount(chosen.inputId()))
                + " output=" + smoke.read(() -> inventoryCount(chosen.outputId())));
        fixture.close();
        open(villagerId, "villager-same-merchant-reopened");
        smoke.check(smoke.await(() -> offerIndex < menu().getOffers().size()
                && persistedOffer(chosen, snapshot(offerIndex))),
            "villager-offer-use-persisted", "same villager_uuid=" + villagerId + " original=" + chosen
                + " reopened=" + smoke.read(() -> offerIndex < menu().getOffers().size() ? snapshot(offerIndex) : null));
        smoke.check(smoke.read(() -> menu().getCarried().isEmpty() && slot(0).isEmpty() && slot(1).isEmpty()
                && tradedExactlyOnce(before, tradeState(chosen, offerIndex), chosen.inputCount(), chosen.outputCount())),
            "villager-reopened-inventory-stable", "fresh received MerchantOffers and inventory retain exactly one trade");
        fixture.close();
    }

    private void open(UUID id, String step) throws Exception {
        smoke.read(() -> {
            Villager target = villager(id);
            require(target != null && target.isAlive() && !target.isBaby(), "same adult villager disappeared: " + id);
            require(minecraft().player.distanceToSqr(target) <= 9, "villager outside native survival interaction reach");
            var inventory = minecraft().player.getInventory();
            boolean emptyHand = false;
            for (int index = 0; index < 9; index++) {
                if (inventory.getItem(index).isEmpty()) {
                    inventory.setSelectedSlot(index);
                    emptyHand = true;
                    break;
                }
            }
            require(emptyHand, "no empty hotbar slot for villager interaction");
            minecraft().player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            minecraft().gameMode.interact(minecraft().player, target,
                new EntityHitResult(target, target.getEyePosition()), InteractionHand.MAIN_HAND);
            return null;
        });
        smoke.check(smoke.await(() -> minecraft().gui.screen() instanceof MerchantScreen
                && minecraft().player.containerMenu instanceof MerchantMenu merchant && !merchant.getOffers().isEmpty()),
            step, "native entity interaction; same villager_uuid=" + id + " received MerchantScreen and MerchantOffers");
    }

    private void selectOffer(int index) throws Exception {
        smoke.read(() -> {
            MerchantScreen screen = (MerchantScreen) minecraft().gui.screen();
            Button[] buttons = VanillaUiSmokeScenario.field(screen, "tradeOfferButtons", Button[].class);
            int scroll = VanillaUiSmokeScenario.field(screen, "scrollOff", Integer.class);
            if (index >= buttons.length) {
                screen.mouseScrolled(0, 0, 0, -index);
                scroll = VanillaUiSmokeScenario.field(screen, "scrollOff", Integer.class);
            }
            int visible = index - scroll;
            require(visible >= 0 && visible < buttons.length && buttons[visible].active,
                "chosen native trade button unavailable: " + index);
            buttons[visible].onPress(new KeyEvent(257, 0, 0));
            return null;
        });
    }

    private static Villager newVillager(Set<UUID> existing, BlockPos spawn) {
        Villager found = null;
        for (var entity : minecraft().level.entitiesForRendering()) {
            if (entity instanceof Villager candidate && !existing.contains(candidate.getUUID())
                    && candidate.blockPosition().distSqr(spawn) <= 4) {
                require(found == null, "spawn egg produced ambiguous nearby villager identities");
                found = candidate;
            }
        }
        return found;
    }

    private static Villager villager(UUID id) {
        for (var entity : minecraft().level.entitiesForRendering()) {
            if (entity instanceof Villager candidate && candidate.getUUID().equals(id)) return candidate;
        }
        return null;
    }

    private static MerchantMenu menu() {
        return (MerchantMenu) minecraft().player.containerMenu;
    }

    private static void click(int index) {
        minecraft().gameMode.handleContainerInput(menu().containerId, index, 0, ContainerInput.PICKUP, minecraft().player);
    }

    private static int inventorySlot(String id, int count) {
        for (int index = 3; index < menu().slots.size(); index++) {
            if (matches(slot(index), id, count)) return index;
        }
        throw new IllegalStateException("exact payment stack absent from native merchant inventory: " + id + " x" + count);
    }

    private static int emptyInventorySlot() {
        for (int index = 3; index < menu().slots.size(); index++) {
            if (slot(index).isEmpty()) return index;
        }
        throw new IllegalStateException("no empty native inventory slot for single trade output");
    }

    private static boolean matches(ItemStack stack, String id, int count) {
        return !stack.isEmpty() && itemId(stack).equals(id) && stack.getCount() == count;
    }

    private static int inventoryCount(String id) {
        int count = 0;
        for (ItemStack stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && itemId(stack).equals(id)) count += stack.getCount();
        }
        return count;
    }

    private static OfferSnapshot snapshot(int index) {
        MerchantOffer offer = menu().getOffers().get(index);
        ItemStack input = offer.getCostA();
        ItemStack output = offer.getResult();
        return new OfferSnapshot(itemId(input), input.getCount(), offer.getBaseCostA().getCount(), input.getMaxStackSize(),
            offer.getCostB().getCount(), itemId(output), output.getCount(), output.getMaxStackSize(),
            offer.getUses(), offer.getMaxUses());
    }

    private static TradeState tradeState(OfferSnapshot offer, int index) {
        int input = inventoryCount(offer.inputId());
        for (int slotIndex = 0; slotIndex < 2; slotIndex++) {
            ItemStack payment = slot(slotIndex);
            if (!payment.isEmpty() && itemId(payment).equals(offer.inputId())) input += payment.getCount();
        }
        MerchantOffer received = menu().getOffers().get(index);
        return new TradeState(input, inventoryCount(offer.outputId()), received.getUses(), received.getMaxUses());
    }

    static boolean eligibleOffer(OfferSnapshot offer) {
        return offer != null && offer.inputId() != null && offer.outputId() != null
            && !offer.inputId().equals("minecraft:air") && !offer.outputId().equals("minecraft:air")
            && !offer.inputId().equals(offer.outputId()) && offer.secondInputCount() == 0
            && offer.inputCount() > 0 && offer.inputCount() <= offer.inputMaxStack()
            && offer.baseInputCount() > 0 && offer.outputCount() > 0 && offer.outputCount() <= offer.outputMaxStack()
            && offer.uses() >= 0 && offer.maxUses() > offer.uses();
    }

    static boolean tradedExactlyOnce(TradeState before, TradeState after, int cost, int result) {
        return cost > 0 && result > 0 && before.input() >= cost && before.output() >= 0
            && before.uses() >= 0 && before.maxUses() > before.uses()
            && after.input() == before.input() - cost && after.output() == before.output() + result
            && after.uses() == before.uses() + 1 && after.maxUses() == before.maxUses()
            && after.maxUses() - after.uses() == before.maxUses() - before.uses() - 1;
    }

    static boolean persistedOffer(OfferSnapshot original, OfferSnapshot reopened) {
        return eligibleOffer(original) && reopened != null
            && original.inputId().equals(reopened.inputId()) && original.baseInputCount() == reopened.baseInputCount()
            && original.secondInputCount() == reopened.secondInputCount()
            && original.outputId().equals(reopened.outputId()) && original.outputCount() == reopened.outputCount()
            && reopened.maxUses() == original.maxUses() && reopened.uses() == original.uses() + 1
            && reopened.maxUses() - reopened.uses() == original.maxUses() - original.uses() - 1;
    }

    record OfferSnapshot(String inputId, int inputCount, int baseInputCount, int inputMaxStack,
                         int secondInputCount, String outputId, int outputCount, int outputMaxStack,
                         int uses, int maxUses) { }

    record TradeState(int input, int output, int uses, int maxUses) { }
}
