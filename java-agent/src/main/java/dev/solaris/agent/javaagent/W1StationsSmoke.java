package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.W1SmokeFixture.itemId;
import static dev.solaris.agent.javaagent.W1SmokeFixture.slot;

/** Native stonecutting, netherite smithing, loom pattern selection and guaranteed compost insertion. */
final class W1StationsSmoke {
    private static final String COBBLE = "minecraft:cobblestone";
    private static final String SLAB = "minecraft:cobblestone_slab";
    private static final String TEMPLATE = "minecraft:netherite_upgrade_smithing_template";
    private static final String DIAMOND = "minecraft:diamond_sword";
    private static final String INGOT = "minecraft:netherite_ingot";
    private static final String UPGRADE = "minecraft:netherite_sword";
    private static final String BANNER = "minecraft:white_banner";
    private static final String DYE = "minecraft:red_dye";
    private static final String PATTERN = "minecraft:stripe_bottom";
    private static final String CAKE = "minecraft:cake";
    private final VanillaUiSmokeScenario smoke;
    private final ScenarioClient client;
    private final W1SmokeFixture fixture;

    W1StationsSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        this.client = smoke.client;
        this.fixture = new W1SmokeFixture(smoke);
    }

    void run() throws Exception {
        fixture.prepare();
        stonecutter();
        smithing();
        loom();
        composter();
        smoke.showInventory();
        smoke.check(smoke.read(() -> inventoryCount(SLAB) == 2 && inventoryCount(UPGRADE) == 1
                && inventoryCount(BANNER) == 1 && patternedBanners() == 1
                && inventoryCount(COBBLE) == 0 && inventoryCount(TEMPLATE) == 0
                && inventoryCount(DIAMOND) == 0 && inventoryCount(INGOT) == 0
                && inventoryCount(DYE) == 0 && inventoryCount(CAKE) == 0),
            "stations-inventory-conserved", "received slabs=2 netherite_sword=1 red-bottom-striped banner=1; all inputs consumed");
        fixture.close();
    }

    private void stonecutter() throws Exception {
        ScenarioBlockTarget station = fixture.place(0, 0, 2, "minecraft:stonecutter");
        smoke.give(COBBLE, 1);
        fixture.open(station, "StonecutterScreen");
        smoke.check(smoke.read(() -> minecraft().player.containerMenu instanceof StonecutterMenu
                && slotsEmpty(2) && inventoryCount(COBBLE) == 1 && inventoryCount(SLAB) == 0),
            "stonecutter-menu-open", "native stonecutter, one cobblestone input and no slab credit");
        moveInput(0, COBBLE, "stonecutter-input");
        boolean offers = smoke.await(() -> minecraft().player.containerMenu instanceof StonecutterMenu menu
            && menu.getNumberOfVisibleRecipes() > 0 && slot(1).isEmpty());
        smoke.check(offers, "stonecutter-native-offers", "received visible recipe count="
            + smoke.read(() -> ((StonecutterMenu) minecraft().player.containerMenu).getNumberOfVisibleRecipes()));
        int offerCount = smoke.read(() -> ((StonecutterMenu) minecraft().player.containerMenu).getNumberOfVisibleRecipes());
        int selected = -1;
        // As in PlayableStationStage, discover the offer from received output, not a hard-coded recipe index.
        for (int button = 0; button < offerCount; button++) {
            boolean acknowledged = client.clickContainerButton(button, TIMEOUT);
            if (acknowledged && smoke.read(() -> exactStack(itemId(slot(1)), slot(1).getCount(), SLAB, 2))) {
                selected = button;
                break;
            }
        }
        int selectedOffer = selected;
        smoke.check(selected >= 0 && smoke.read(() -> minecraft().player.containerMenu instanceof StonecutterMenu menu
                && menu.getSelectedRecipeIndex() == selectedOffer
                && exactStack(itemId(slot(0)), slot(0).getCount(), COBBLE, 1)
                && exactStack(itemId(slot(1)), slot(1).getCount(), SLAB, 2) && inventoryCount(SLAB) == 0),
            "stonecutter-two-slab-preview", "native offer=" + selected + " cobblestone=1 -> slabs=2");
        boolean taken = client.moveContainerSlotToInventory(1, SLAB, 2, TIMEOUT);
        boolean conserved = smoke.await(() -> stonecutterConserved(inventoryCount(COBBLE) + slot(0).getCount(),
            inventoryCount(SLAB), slotsEmpty(2), minecraft().player.containerMenu.getCarried().isEmpty()));
        smoke.check(taken && conserved, "stonecutter-result-conserved", "input consumed exactly once, received inventory slabs=2");
        fixture.close();
    }

    private void smithing() throws Exception {
        ScenarioBlockTarget station = fixture.place(-2, 0, 0, "minecraft:smithing_table");
        smoke.give(TEMPLATE, 1);
        smoke.give(DIAMOND, 1);
        smoke.give(INGOT, 1);
        fixture.open(station, "SmithingScreen");
        smoke.check(smoke.read(() -> minecraft().player.containerMenu instanceof SmithingMenu
                && slotsEmpty(4) && inventoryCount(TEMPLATE) == 1 && inventoryCount(DIAMOND) == 1
                && inventoryCount(INGOT) == 1 && inventoryCount(UPGRADE) == 0),
            "smithing-menu-open", "native smithing menu; exactly one template, diamond sword and ingot");
        moveInput(0, TEMPLATE, "smithing-template-input");
        moveInput(1, DIAMOND, "smithing-diamond-input");
        moveInput(2, INGOT, "smithing-netherite-input");
        smoke.check(smoke.await(() -> minecraft().player.containerMenu instanceof SmithingMenu menu
                && !menu.hasRecipeError() && exactStack(itemId(slot(3)), slot(3).getCount(), UPGRADE, 1)
                && exactStack(itemId(slot(0)), slot(0).getCount(), TEMPLATE, 1)
                && exactStack(itemId(slot(1)), slot(1).getCount(), DIAMOND, 1)
                && exactStack(itemId(slot(2)), slot(2).getCount(), INGOT, 1)
                && inventoryCount(UPGRADE) == 0),
            "smithing-netherite-preview", "received output is one netherite sword; inputs unchanged before take");
        boolean taken = client.moveContainerSlotToInventory(3, UPGRADE, 1, TIMEOUT);
        boolean conserved = smoke.await(() -> smithingConserved(inventoryCount(TEMPLATE) + slot(0).getCount(),
            inventoryCount(DIAMOND) + slot(1).getCount(), inventoryCount(INGOT) + slot(2).getCount(),
            inventoryCount(UPGRADE), slotsEmpty(4), minecraft().player.containerMenu.getCarried().isEmpty()));
        smoke.check(taken && conserved, "smithing-upgrade-conserved",
            "template=1 diamond_sword=1 ingot=1 consumed; received netherite_sword=1");
        fixture.close();
    }

    private void loom() throws Exception {
        ScenarioBlockTarget station = fixture.place(2, 0, 0, "minecraft:loom");
        smoke.give(BANNER, 1);
        smoke.give(DYE, 1);
        fixture.open(station, "LoomScreen");
        smoke.check(smoke.read(() -> minecraft().player.containerMenu instanceof LoomMenu
                && slotsEmpty(4) && inventoryCount(BANNER) == 1 && patternedBanners() == 0
                && inventoryCount(DYE) == 1),
            "loom-menu-open", "native loom, one plain white banner and red dye");
        moveInput(0, BANNER, "loom-banner-input");
        moveInput(1, DYE, "loom-dye-input");
        boolean offers = smoke.await(() -> minecraft().player.containerMenu instanceof LoomMenu menu
            && !menu.getSelectablePatterns().isEmpty() && menu.getSelectedBannerPatternIndex() == -1
            && slot(2).isEmpty() && slot(3).isEmpty());
        smoke.check(offers, "loom-native-patterns", "native selectable patterns="
            + smoke.read(() -> ((LoomMenu) minecraft().player.containerMenu).getSelectablePatterns().size()));
        int patternIndex = smoke.read(() -> {
            LoomMenu menu = (LoomMenu) minecraft().player.containerMenu;
            for (int index = 0; index < menu.getSelectablePatterns().size(); index++) {
                String id = menu.getSelectablePatterns().get(index).unwrapKey()
                    .map(key -> key.identifier().toString()).orElse("");
                if (PATTERN.equals(id)) {
                    return index;
                }
            }
            return -1;
        });
        smoke.check(patternIndex >= 0, "loom-bottom-stripe-offer", "received pattern=" + PATTERN + " button=" + patternIndex);
        boolean clicked = client.clickContainerButton(patternIndex, TIMEOUT);
        smoke.check(clicked && smoke.await(() -> minecraft().player.containerMenu instanceof LoomMenu menu
                && menu.getSelectedBannerPatternIndex() == patternIndex && patternedBanner(slot(3))
                && exactStack(itemId(slot(0)), slot(0).getCount(), BANNER, 1)
                && exactStack(itemId(slot(1)), slot(1).getCount(), DYE, 1)
                && inventoryCount(BANNER) == 0 && inventoryCount(DYE) == 0),
            "loom-selected-pattern-preview", "received BANNER_PATTERNS contains exactly stripe_bottom/red; "
                + smoke.read(() -> {
                    if (!(minecraft().player.containerMenu instanceof LoomMenu menu)) {
                        return "unexpectedMenu=" + minecraft().player.containerMenu.getClass().getSimpleName();
                    }
                    ItemStack output = slot(3);
                    return "clicked=" + clicked + " selected=" + menu.getSelectedBannerPatternIndex()
                        + " expected=" + patternIndex + " output=" + output
                        + " components=" + output.get(DataComponents.BANNER_PATTERNS)
                        + " bannerInput=" + slot(0) + " dyeInput=" + slot(1)
                        + " inventoryBanner=" + inventoryCount(BANNER) + " inventoryDye=" + inventoryCount(DYE);
                }));
        boolean taken = client.moveContainerSlotToInventory(3, BANNER, 1, TIMEOUT);
        boolean conserved = smoke.await(() -> loomConserved(inventoryCount(BANNER), patternedBanners(),
            inventoryCount(DYE) + slot(1).getCount(), slotsEmpty(4), minecraft().player.containerMenu.getCarried().isEmpty()));
        smoke.check(taken && conserved, "loom-patterned-banner-conserved",
            "one banner retained with exact pattern component; one red dye consumed; no extra output");
        fixture.close();
    }

    private void composter() throws Exception {
        ScenarioBlockTarget station = fixture.place(0, 0, -2, "minecraft:composter");
        BlockPos pos = new BlockPos(station.x(), station.y(), station.z());
        ScenarioHeldItem cake = smoke.give(CAKE, 1);
        int beforeLevel = smoke.read(() -> composterLevel(pos));
        int beforeCake = smoke.read(() -> inventoryCount(CAKE));
        smoke.check(beforeLevel == 0 && beforeCake == 1, "composter-empty-with-cake",
            "received LEVEL=" + beforeLevel + " cake=" + beforeCake + "; cake compost chance=100%");
        client.useItemOn(station, cake);
        boolean inserted = smoke.await(() -> composterIncrement(beforeLevel, composterLevel(pos), beforeCake, inventoryCount(CAKE)));
        smoke.check(inserted, "composter-native-level-increment", "received LEVEL="
            + smoke.read(() -> composterLevel(pos)) + " cake=" + smoke.read(() -> inventoryCount(CAKE)));
    }

    private void moveInput(int index, String item, String step) throws Exception {
        boolean moved = client.moveSelectedItemToContainerSlot(index, item, 1, TIMEOUT);
        smoke.check(moved && smoke.read(() -> exactStack(itemId(slot(index)), slot(index).getCount(), item, 1)
                && inventoryCount(item) == 0 && minecraft().player.containerMenu.getCarried().isEmpty()),
            step, "native transfer: slot=" + index + " item=" + item + " count=1; inventory remaining=0");
    }

    static boolean exactStack(String actualItem, int actualCount, String expectedItem, int expectedCount) {
        return expectedCount > 0 && expectedItem.equals(actualItem) && actualCount == expectedCount;
    }

    static boolean stonecutterConserved(int inputRemaining, int outputCount, boolean slotsEmpty, boolean cursorEmpty) {
        return inputRemaining == 0 && outputCount == 2 && slotsEmpty && cursorEmpty;
    }

    static boolean smithingConserved(int templateRemaining, int diamondRemaining, int ingotRemaining,
                                     int upgradedCount, boolean slotsEmpty, boolean cursorEmpty) {
        return templateRemaining == 0 && diamondRemaining == 0 && ingotRemaining == 0
            && upgradedCount == 1 && slotsEmpty && cursorEmpty;
    }

    static boolean bannerPatternMatches(String item, int count, int layerCount, String pattern, String color) {
        return BANNER.equals(item) && count == 1 && layerCount == 1 && PATTERN.equals(pattern) && "red".equals(color);
    }

    static boolean loomConserved(int bannerCount, int patternedCount, int dyeRemaining, boolean slotsEmpty, boolean cursorEmpty) {
        return bannerCount == 1 && patternedCount == 1 && dyeRemaining == 0 && slotsEmpty && cursorEmpty;
    }

    static boolean composterIncrement(int beforeLevel, int afterLevel, int beforeItems, int afterItems) {
        return beforeLevel >= 0 && beforeLevel < 7 && afterLevel == beforeLevel + 1
            && beforeItems > 0 && afterItems == beforeItems - 1;
    }

    private static int composterLevel(BlockPos pos) {
        var state = minecraft().level.getBlockState(pos);
        return state.is(Blocks.COMPOSTER) ? state.getValue(ComposterBlock.LEVEL) : -1;
    }

    private static boolean patternedBanner(ItemStack stack) {
        BannerPatternLayers patterns = stack.get(DataComponents.BANNER_PATTERNS);
        if (patterns == null || patterns.layers().size() != 1) {
            return false;
        }
        var layer = patterns.layers().get(0);
        String pattern = layer.pattern().unwrapKey().map(key -> key.identifier().toString()).orElse("");
        return bannerPatternMatches(itemId(stack), stack.getCount(), patterns.layers().size(), pattern, layer.color().getName());
    }

    private static int patternedBanners() {
        int count = 0;
        for (ItemStack stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (patternedBanner(stack)) {
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

    private static boolean slotsEmpty(int size) {
        for (int index = 0; index < size; index++) {
            if (!slot(index).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
