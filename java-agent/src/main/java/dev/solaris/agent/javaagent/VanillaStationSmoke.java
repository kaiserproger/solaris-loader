package dev.solaris.agent.javaagent;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;

/** Native block use, native menu transfers and the native anvil text responder; no raw wire probe. */
final class VanillaStationSmoke {
    private static final String TOOL = "minecraft:diamond_pickaxe";
    private static final String NAME = "UI Smoke Pick";
    private final VanillaUiSmokeScenario smoke;
    private final ScenarioClient client;

    VanillaStationSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        this.client = smoke.client;
    }

    void run() throws Exception {
        smoke.command("gamemode survival");
        require(smoke.await(() -> !minecraft().gameMode.getPlayerMode().isCreative()), "survival mode missing");
        smoke.command("clear @s");
        require(smoke.await(() -> minecraft().player.getInventory().isEmpty()), "station inventory not clear");
        require(smoke.await(() -> minecraft().player.onGround()), "player has not landed before station placement");
        BlockPos origin = smoke.read(() -> minecraft().player.blockPosition());
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        smoke.command("fill " + (x - 4) + " " + (y - 1) + " " + (z - 3) + " "
            + (x + 4) + " " + (y - 1) + " " + (z + 6) + " minecraft:stone");
        smoke.command("fill " + (x - 4) + " " + y + " " + (z - 3) + " "
            + (x + 4) + " " + (y + 3) + " " + (z + 6) + " minecraft:air");
        require(client.waitForBlock(target(x, y - 1, z + 2, "minecraft:stone"), "minecraft:stone", TIMEOUT),
            "op-built station pad missing");
        smoke.command("experience set @s 40 levels");
        require(smoke.await(() -> minecraft().player.experienceLevel == 40), "level-40 XP setup missing");
        ScenarioBlockTarget table = place(x, y, z + 2, "minecraft:enchanting_table");
        // Fifteen vanilla-valid ring locations, leaving the player's position open.
        int shelves = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || (dx == 0 && dz == -2)) {
                    continue;
                }
                ScenarioBlockTarget shelf = target(x + dx, y, z + 2 + dz, "minecraft:bookshelf");
                smoke.command("setblock " + shelf.x() + " " + shelf.y() + " " + shelf.z() + " minecraft:bookshelf");
                require(client.waitForBlock(shelf, "minecraft:bookshelf", TIMEOUT), "bookshelf not received: " + shelf);
                shelves++;
            }
        }
        smoke.check(shelves == 15, "bookshelf-ring", "client_observed_bookshelves=" + shelves);
        smoke.give(TOOL, 1);
        smoke.give("minecraft:lapis_lazuli", 3);
        open(table, "EnchantmentScreen");
        smoke.check(true, "enchanting-menu-open", "native enchanting screen opened");
        require(client.moveSelectedItemToContainerSlot(0, TOOL, 1, TIMEOUT), "tool transfer failed");
        require(client.moveSelectedItemToContainerSlot(1, "minecraft:lapis_lazuli", 3, TIMEOUT), "lapis transfer failed");
        boolean offersReady = smoke.await(() -> minecraft().player.containerMenu instanceof EnchantmentMenu menu
            && threeOffers(menu.costs, menu.enchantClue, menu.levelClue));
        String offers = smoke.read(() -> {
            EnchantmentMenu menu = (EnchantmentMenu) minecraft().player.containerMenu;
            return "costs=" + Arrays.toString(menu.costs) + " clues=" + Arrays.toString(menu.enchantClue)
                + " levels=" + Arrays.toString(menu.levelClue);
        });
        smoke.check(offersReady, "three-enchanting-offers", offers);
        int levelBefore = client.experienceLevel();
        boolean clicked = client.clickContainerButton(0, TIMEOUT);
        smoke.check(clicked && smoke.await(() -> slot(0).isEnchanted()
                && minecraft().player.experienceLevel == levelBefore - 1),
            "enchant-picked-offer", "tool_enchantments=" + smoke.read(() -> slot(0).getEnchantments())
                + " level_before=" + levelBefore + " level_after=" + client.experienceLevel());
        require(client.moveContainerSlotToInventory(0, TOOL, 1, TIMEOUT), "enchanted tool return failed");
        require(client.closeCurrentScreen(TIMEOUT), "enchanting screen close failed");

        // Repair stations stay outside the bookshelf ring, leaving their native placement targets air.
        ScenarioBlockTarget anvil = place(x + 3, y, z, "minecraft:anvil");
        open(anvil, "AnvilScreen");
        smoke.check(true, "anvil-menu-open", "native anvil screen opened");
        require(client.moveSelectedItemToContainerSlot(0, TOOL, 1, TIMEOUT), "anvil tool transfer failed");
        int renameState = smoke.read(() -> minecraft().player.containerMenu.getStateId());
        smoke.read(() -> {
            VanillaUiSmokeScenario.field(minecraft().gui.screen(), "name", EditBox.class).setValue(NAME);
            return null;
        });
        boolean renamed = smoke.await(() -> minecraft().player.containerMenu.getStateId() != renameState
            && slot(2).getCustomName() != null && NAME.equals(slot(2).getCustomName().getString()));
        smoke.check(renamed, "anvil-rename-result", "received_state_id="
            + smoke.read(() -> minecraft().player.containerMenu.getStateId()) + " initial_state_id=" + renameState
            + " result_name=" + smoke.read(() -> slot(2).getCustomName()));
        require(client.moveContainerSlotToInventory(2, TOOL, 1, TIMEOUT), "renamed result take failed");
        require(client.closeCurrentScreen(TIMEOUT), "anvil screen close failed");
        smoke.showInventory();
        smoke.check(smoke.read(() -> minecraft().player.getInventory().contains(stack -> stack.isEnchanted()
            && stack.getCustomName() != null && NAME.equals(stack.getCustomName().getString()))),
            "renamed-enchanted-tool-inventory", "received inventory retains custom name and enchantments");
        require(client.closeCurrentScreen(TIMEOUT), "inventory screen close failed");

        ScenarioBlockTarget grindstone = place(x - 3, y, z, "minecraft:grindstone");
        open(grindstone, "GrindstoneScreen");
        smoke.check(true, "grindstone-menu-open", "native grindstone screen opened");
        require(client.moveSelectedItemToContainerSlot(0, TOOL, 1, TIMEOUT), "grindstone input transfer failed");
        smoke.check(smoke.await(() -> !slot(2).isEmpty() && !slot(2).isEnchanted()),
            "grindstone-disenchanted-preview", "received result enchantments=" + smoke.read(() -> slot(2).getEnchantments()));
        Set<UUID> before = orbIds();
        require(client.moveContainerSlotToInventory(2, TOOL, 1, TIMEOUT), "grindstone result take failed");
        boolean orbObserved = smoke.await(() -> {
            for (var entity : minecraft().level.entitiesForRendering()) {
                if (entity instanceof ExperienceOrb orb && !before.contains(orb.getUUID()) && orb.getValue() > 0) {
                    return true;
                }
            }
            return false;
        });
        String orbs = smoke.read(() -> {
            StringBuilder detail = new StringBuilder();
            for (var entity : minecraft().level.entitiesForRendering()) {
                if (entity instanceof ExperienceOrb orb && !before.contains(orb.getUUID())) {
                    detail.append("id=").append(orb.getId()).append(" uuid=").append(orb.getUUID())
                        .append(" value=").append(orb.getValue()).append(" position=").append(orb.position()).append(';');
                }
            }
            return detail.toString();
        });
        require(client.closeCurrentScreen(TIMEOUT), "grindstone close failed");
        smoke.check(orbObserved, "grindstone-xp-orbs", "new vanilla ExperienceOrb entities: " + orbs);
        smoke.showInventory();
        smoke.check(smoke.read(() -> minecraft().player.getInventory().contains(stack -> !stack.isEnchanted()
                && stack.getCustomName() != null && NAME.equals(stack.getCustomName().getString()))),
            "disenchanted-renamed-tool-inventory", "received inventory tool retains rename but no enchantments");
    }

    static boolean threeOffers(int[] costs, int[] clues, int[] levels) {
        if (costs.length != 3 || clues.length != 3 || levels.length != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (costs[i] <= 0 || clues[i] < 0 || levels[i] <= 0) {
                return false;
            }
        }
        return true;
    }

    private ScenarioBlockTarget place(int x, int y, int z, String item) throws Exception {
        ScenarioHeldItem held = smoke.give(item, 1);
        ScenarioBlockTarget support = target(x, y - 1, z, "minecraft:stone");
        ScenarioBlockTarget station = target(x, y, z, item);
        client.useItemOn(support, held);
        smoke.check(client.waitForBlock(station, item, TIMEOUT), item.substring("minecraft:".length()) + "-placed",
            "client block=" + station + " via native item use");
        return station;
    }

    private void open(ScenarioBlockTarget station, String screen) throws Exception {
        // Empty hand avoids accidental placement of another held station against the one being opened.
        smoke.read(() -> {
            var inventory = minecraft().player.getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getItem(slot).isEmpty()) {
                    inventory.setSelectedSlot(slot);
                    return null;
                }
            }
            throw new IllegalStateException("no empty hotbar slot for native station use");
        });
        client.useItemOn(station, new ScenarioHeldItem("minecraft:air", 0));
        require(client.waitForScreenClassName("net.minecraft.client.gui.screens.inventory." + screen, TIMEOUT),
            "station did not open " + screen);
    }

    private Set<UUID> orbIds() throws Exception {
        return smoke.read(() -> {
            Set<UUID> ids = new HashSet<>();
            for (var entity : minecraft().level.entitiesForRendering()) {
                if (entity instanceof ExperienceOrb) {
                    ids.add(entity.getUUID());
                }
            }
            return ids;
        });
    }

    private static ItemStack slot(int index) {
        return minecraft().player.containerMenu.getSlot(index).getItem();
    }

    private static ScenarioBlockTarget target(int x, int y, int z, String block) {
        return new ScenarioBlockTarget(x, y, z, "up", "operator-ui-smoke", block);
    }
}
