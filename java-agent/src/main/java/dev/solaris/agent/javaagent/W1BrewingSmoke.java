package dev.solaris.agent.javaagent;

import net.minecraft.core.component.DataComponents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import java.time.Duration;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.W1SmokeFixture.itemId;
import static dev.solaris.agent.javaagent.W1SmokeFixture.slot;

/** Water-to-awkward brewing through native placement, menu input and received potion components. */
final class W1BrewingSmoke {
    private static final String POTION = "minecraft:potion";
    private static final String WART = "minecraft:nether_wart";
    private static final String POWDER = "minecraft:blaze_powder";
    private static final Duration BREW_TIMEOUT = Duration.ofSeconds(40);
    private final VanillaUiSmokeScenario smoke;
    private final ScenarioClient client;
    private final W1SmokeFixture fixture;

    W1BrewingSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        this.client = smoke.client;
        this.fixture = new W1SmokeFixture(smoke);
    }

    void run() throws Exception {
        fixture.prepare();
        ScenarioBlockTarget stand = fixture.place(0, 0, 2, "minecraft:brewing_stand");
        // Supply an empty bottle and water source; the native use action creates the input potion.
        ScenarioBlockTarget water = fixture.set(1, -1, 2, "minecraft:water[level=0]");
        smoke.give("minecraft:glass_bottle", 1);
        smoke.read(() -> {
            minecraft().player.lookAt(EntityAnchorArgument.Anchor.EYES,
                new Vec3(water.x() + 0.5, water.y() + 0.5, water.z() + 0.5));
            return null;
        });
        fixture.ticks(2);
        long fillContentsVersion = ClientStateEvents.containerPacketVersion();
        smoke.read(() -> {
            minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND);
            return null;
        });
        boolean waterReceived = smoke.await(() -> ClientStateEvents.containerPacketVersion() > fillContentsVersion
            && inventoryPotions("minecraft:water") == 1 && inventoryCount("minecraft:glass_bottle") == 0);
        smoke.check(waterReceived, "brewing-water-fixture",
            smoke.read(() -> "received waterPotions=" + inventoryPotions("minecraft:water")
                + " potionItems=" + inventoryCount(POTION)
                + " emptyBottles=" + inventoryCount("minecraft:glass_bottle")
                + " contentsVersion=" + ClientStateEvents.containerPacketVersion()
                + " beforeUse=" + fillContentsVersion));
        smoke.give(WART, 1);
        smoke.give(POWDER, 1);
        long openingContentsVersion = ClientStateEvents.containerPacketVersion();
        fixture.open(stand, "BrewingStandScreen");
        boolean initialized = smoke.await(() -> ClientStateEvents.containerPacketVersion() > openingContentsVersion
                && minecraft().player.containerMenu instanceof BrewingStandMenu menu
                && menu.getBrewingTicks() == 0 && menu.getFuel() == 0
                && emptySlots() && inventoryCount(POTION) == 1
                && inventoryCount(WART) == 1 && inventoryCount(POWDER) == 1);
        smoke.check(initialized, "brewing-menu-open",
            "native stand menu, empty inputs, zero progress and fuel; " + smoke.read(this::menuDetails)
                + smoke.read(() -> " inventoryPotion=" + inventoryCount(POTION)
                    + " inventoryWart=" + inventoryCount(WART) + " inventoryPowder=" + inventoryCount(POWDER)
                    + " contentsVersion=" + ClientStateEvents.containerPacketVersion()
                    + " beforeOpen=" + openingContentsVersion));

        boolean bottleMoved = client.moveSelectedItemToContainerSlot(0, POTION, 1, TIMEOUT);
        smoke.check(bottleMoved && smoke.read(() -> potionMatches(slot(0), "minecraft:water")
                && slot(1).isEmpty() && slot(2).isEmpty() && inventoryCount(POTION) == 0),
            "brewing-water-input", "native bottle slot 0 holds exactly one water potion");
        boolean wartMoved = client.moveSelectedItemToContainerSlot(3, WART, 1, TIMEOUT);
        smoke.check(wartMoved && smoke.read(() -> itemId(slot(3)).equals(WART)
                && slot(3).getCount() == 1 && inventoryCount(WART) == 0),
            "brewing-wart-input", "native ingredient slot holds one nether wart");

        int containerId = smoke.read(() -> minecraft().player.containerMenu.containerId);
        // Fuel may be consumed on the very next server tick, so do not require a transient fuel-slot stack.
        smoke.read(() -> {
            var menu = minecraft().player.containerMenu;
            for (int index = 5; index < menu.slots.size(); index++) {
                ItemStack stack = slot(index);
                if (itemId(stack).equals(POWDER) && stack.getCount() == 1) {
                    minecraft().gameMode.handleContainerInput(menu.containerId, index, 0,
                        ContainerInput.QUICK_MOVE, minecraft().player);
                    return null;
                }
            }
            throw new IllegalStateException("native brewing fuel source missing");
        });
        boolean active = smoke.await(() -> minecraft().player.containerMenu instanceof BrewingStandMenu menu
            && menu.containerId == containerId && activeBrew(menu.getBrewingTicks(), menu.getFuel())
            && potionMatches(slot(0), "minecraft:water") && slot(3).getCount() == 1
            && slot(4).isEmpty() && inventoryCount(POWDER) == 0);
        smoke.check(active, "brewing-active-progress-fuel", smoke.read(this::menuDetails));

        boolean finished = fixture.await(BREW_TIMEOUT, () ->
            minecraft().player.containerMenu instanceof BrewingStandMenu menu
                && menu.containerId == containerId && menu.getBrewingTicks() == 0 && menu.getFuel() == 19
                && potionMatches(slot(0), "minecraft:awkward") && slot(1).isEmpty() && slot(2).isEmpty()
                && slot(3).isEmpty() && slot(4).isEmpty()
                && inventoryCount(POTION) == 0 && inventoryCount(WART) == 0 && inventoryCount(POWDER) == 0);
        smoke.check(finished, "brewing-awkward-completed", smoke.read(this::menuDetails));
        boolean taken = client.moveContainerSlotToInventory(0, POTION, 1, TIMEOUT);
        boolean conserved = smoke.await(() -> minecraft().player.containerMenu instanceof BrewingStandMenu menu
            && completedBrew(menu.getBrewingTicks(), menu.getFuel(), inventoryCount(WART),
                inventoryCount(POWDER), inventoryCount(POTION), inventoryPotions("minecraft:awkward"),
                emptySlots(), minecraft().player.containerMenu.getCarried().isEmpty()));
        smoke.check(taken && conserved, "brewing-result-taken-conserved",
            "water=1 wart=1 powder=1 -> awkward=1; inputs and bottle slots empty; fuel=19");
        fixture.close();
        smoke.showInventory();
        smoke.check(smoke.read(() -> inventoryCount(POTION) == 1 && inventoryPotions("minecraft:awkward") == 1
                && inventoryPotions("minecraft:water") == 0 && inventoryCount(WART) == 0
                && inventoryCount(POWDER) == 0),
            "brewing-awkward-inventory", "received inventory retains exactly one actual awkward potion");
        fixture.close();
    }

    static boolean activeBrew(int remainingTicks, int fuel) {
        return remainingTicks > 0 && remainingTicks <= 400 && fuel == 19;
    }

    static boolean potionMatches(String item, int count, String actualPotion, int customEffectCount,
                                 String expectedPotion) {
        return POTION.equals(item) && count == 1 && expectedPotion.equals(actualPotion) && customEffectCount == 0;
    }

    static boolean completedBrew(int remainingTicks, int fuel, int wartRemaining, int powderRemaining,
                                 int potionCount, int awkwardCount, boolean slotsEmpty, boolean cursorEmpty) {
        return remainingTicks == 0 && fuel == 19 && wartRemaining == 0 && powderRemaining == 0
            && potionCount == 1 && awkwardCount == 1 && slotsEmpty && cursorEmpty;
    }

    private static boolean potionMatches(ItemStack stack, String expectedPotion) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && potionMatches(itemId(stack), stack.getCount(), potionId(contents),
            contents.customEffects().size(), expectedPotion);
    }

    private static String potionId(PotionContents contents) {
        return contents.potion().flatMap(holder -> holder.unwrapKey())
            .map(key -> key.identifier().toString()).orElse("");
    }

    private static int inventoryPotions(String potion) {
        int count = 0;
        for (ItemStack stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (potionMatches(stack, potion)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int inventoryCount(String item) {
        int count = 0;
        for (ItemStack stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (itemId(stack).equals(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean emptySlots() {
        for (int index = 0; index < 5; index++) {
            if (!slot(index).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String menuDetails() {
        if (!(minecraft().player.containerMenu instanceof BrewingStandMenu menu)) {
            return "unexpected_menu=" + minecraft().player.containerMenu.getClass().getSimpleName();
        }
        PotionContents contents = slot(0).get(DataComponents.POTION_CONTENTS);
        return "container=" + menu.containerId + " ticks=" + menu.getBrewingTicks() + " fuel=" + menu.getFuel()
            + " bottle=" + itemId(slot(0)) + " x" + slot(0).getCount()
            + " potion=" + (contents == null ? "missing" : potionId(contents))
            + " wart=" + slot(3).getCount() + " powder=" + slot(4).getCount();
    }
}
