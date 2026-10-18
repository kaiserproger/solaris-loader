package dev.solaris.agent.javaagent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.util.Set;

import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.TIMEOUT;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.minecraft;
import static dev.solaris.agent.javaagent.VanillaUiSmokeScenario.require;
import static dev.solaris.agent.javaagent.W1SmokeFixture.itemId;

/** Exact native container custody, crafter toggle/pulse and single-use vault key probes. */
final class W3StorageSmoke {
    static final Set<String> IDS = Set.of("playable-w3-crafter", "playable-w3-shelf",
        "playable-w3-copper-storage", "playable-w3-trial-vault", "playable-w3-component-custody");
    private final VanillaUiSmokeScenario smoke;
    private final W1SmokeFixture fixture;

    W3StorageSmoke(VanillaUiSmokeScenario smoke) {
        this.smoke = smoke;
        fixture = new W1SmokeFixture(smoke);
    }

    void run(String id) throws Exception {
        fixture.prepare();
        switch (id) {
            case "playable-w3-crafter" -> crafter();
            case "playable-w3-shelf" -> shelf();
            case "playable-w3-copper-storage" -> copper();
            case "playable-w3-trial-vault" -> trialVault();
            case "playable-w3-component-custody" -> new W3ComponentCustodySmoke(smoke, fixture).run();
            default -> throw new IllegalArgumentException(id);
        }
    }

    private void crafter() throws Exception {
        ScenarioBlockTarget block = fixture.set(0, 0, 2, "minecraft:crafter[orientation=north_up]");
        fixture.open(block, "CrafterScreen");
        require(smoke.read(() -> minecraft().player.containerMenu instanceof CrafterMenu), "crafter native menu missing");
        smoke.read(() -> {
            minecraft().gameMode.handleSlotStateChanged(8, minecraft().player.containerMenu.containerId, false);
            return null;
        });
        smoke.check(smoke.await(() -> ((CrafterMenu) minecraft().player.containerMenu).isSlotDisabled(8)),
            "crafter-disabled-slot", "received disabled-slot data after native toggle");
        fixture.close();
        fixture.open(block, "CrafterScreen");
        smoke.check(smoke.read(() -> ((CrafterMenu) minecraft().player.containerMenu).isSlotDisabled(8)),
            "crafter-toggle-reopen", "fresh server menu retains disabled slot; no local-prediction-only pass");
        smoke.give("minecraft:oak_log", 1);
        require(smoke.client.moveSelectedItemToContainerSlot(0, "minecraft:oak_log", 1, TIMEOUT), "crafter input transfer failed");
        fixture.close();
        fixture.set(1, 0, 2, "minecraft:redstone_block");
        smoke.check(fixture.await(Duration.ofSeconds(10), () -> planksInCustody() == 4),
            "crafter-pulse-output", "received exactly four planks across ejected items and player pickup, from one log");
        fixture.open(block, "CrafterScreen");
        smoke.check(smoke.client.waitForContainerSlotEmpty(0, TIMEOUT), "crafter-input-consumed", "received empty log slot after pulse");
        fixture.close();
        fixture.ticks(40);
        smoke.check(smoke.read(() -> planksInCustody() == 4), "crafter-output-exact-once",
            "forty client ticks later total output custody remains four planks");
    }

    private int planksInCustody() {
        int total = 0;
        for (var stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (itemId(stack).equals("minecraft:oak_planks")) total += stack.getCount();
        }
        for (var entity : minecraft().level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && !item.isRemoved()
                    && itemId(item.getItem()).equals("minecraft:oak_planks")) total += item.getItem().getCount();
        }
        return total;
    }

    private void shelf() throws Exception {
        ScenarioBlockTarget shelf = fixture.set(0, 1, 2, "minecraft:oak_shelf[facing=north]");
        smoke.give("minecraft:diamond", 1);
        shelfUse(shelf);
        smoke.check(smoke.await(() -> shelfCount(shelf, "minecraft:diamond") == 1)
                && smoke.client.waitForInventoryCount("minecraft:diamond", 0, TIMEOUT),
            "shelf-deposit", "received BE contains exactly one diamond and inventory debit");
        selectEmpty();
        shelfUse(shelf);
        smoke.check(smoke.client.waitForInventoryCount("minecraft:diamond", 1, TIMEOUT)
                && smoke.await(() -> shelfCount(shelf, "minecraft:diamond") == 0),
            "shelf-withdraw", "received empty shelf plus exact one returned diamond");
        fixture.ticks(40);
        smoke.check(smoke.client.inventoryCount("minecraft:diamond") == 1,
            "shelf-exact-once", "forty client ticks later no duplicate diamond");
    }

    private int shelfCount(ScenarioBlockTarget target, String item) {
        if (!(minecraft().level.getBlockEntity(new BlockPos(target.x(), target.y(), target.z())) instanceof ShelfBlockEntity shelf)) return -1;
        int count = 0;
        for (var stack : shelf.getItems()) if (itemId(stack).equals(item)) count += stack.getCount();
        return count;
    }

    private void shelfUse(ScenarioBlockTarget target) throws Exception {
        smoke.read(() -> {
            BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
            minecraft().gameMode.useItemOn(minecraft().player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ()), Direction.NORTH, pos, false));
            return null;
        });
    }

    private void copper() throws Exception {
        ScenarioBlockTarget chest = fixture.set(0, 0, 2, "minecraft:copper_chest[facing=north]");
        fixture.open(chest, "ContainerScreen");
        smoke.give("minecraft:diamond", 3);
        require(smoke.client.moveSelectedItemToContainerSlot(0, "minecraft:diamond", 3, TIMEOUT), "copper deposit failed");
        fixture.close();
        fixture.open(chest, "ContainerScreen");
        smoke.check(smoke.client.waitForContainerSlot(0, "minecraft:diamond", 3, TIMEOUT)
                && smoke.client.inventoryCount("minecraft:diamond") == 0,
            "copper-chest-reopen", "fresh menu contains exact deposited three diamonds");
        require(smoke.client.moveContainerSlotToInventory(0, "minecraft:diamond", 3, TIMEOUT), "copper withdrawal failed");
        fixture.close();
        smoke.check(smoke.client.inventoryCount("minecraft:diamond") == 3,
            "copper-conservation", "three deposited diamonds return exactly once");
        BlockPos origin = fixture.origin();
        smoke.command("summon minecraft:copper_golem " + (origin.getX() + 2.5) + " " + origin.getY() + " "
            + (origin.getZ() + 2.5) + " {}");
        smoke.check(smoke.await(() -> {
            for (var entity : minecraft().level.entitiesForRendering()) if (W2GameplaySmoke.type(entity).equals("minecraft:copper_golem")) return true;
            return false;
        }), "copper-golem-wire", "received copper golem identity; no sorting-AI claim");
    }

    private void trialVault() throws Exception {
        ScenarioBlockTarget spawner = fixture.set(0, 0, 3, "minecraft:trial_spawner");
        smoke.check(fixture.await(Duration.ofSeconds(30), () -> {
            var state = minecraft().level.getBlockState(new BlockPos(spawner.x(), spawner.y(), spawner.z()));
            return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.TRIAL_SPAWNER_STATE)
                != net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState.INACTIVE;
        }), "trial-player-registration", "received trial state leaves inactive when player approaches");
        ScenarioBlockTarget vault = fixture.set(1, 0, 2, "minecraft:vault");
        require(smoke.client.waitForBlockProperty(vault, "vault_state", "active", Duration.ofSeconds(15)), "vault did not activate for player");
        smoke.give("minecraft:trial_key", 2);
        smoke.client.useItemOn(vault, new ScenarioHeldItem("minecraft:trial_key", 2));
        smoke.check(smoke.client.waitForInventoryCount("minecraft:trial_key", 1, TIMEOUT)
                && smoke.client.waitForBlockProperty(vault, "vault_state", "unlocking", TIMEOUT),
            "vault-key-consumed", "received unlocking state and exact one key debit");
        require(smoke.client.waitForBlockProperty(vault, "vault_state", "inactive", Duration.ofSeconds(40)), "vault reward queue did not complete");
        smoke.client.useItemOn(vault, new ScenarioHeldItem("minecraft:trial_key", 1));
        fixture.ticks(40);
        smoke.check(smoke.client.inventoryCount("minecraft:trial_key") == 1,
            "vault-repeat-no-debit", "same rewarded player cannot consume second key");
    }

    private void selectEmpty() throws Exception {
        smoke.read(() -> {
            for (int slot = 0; slot < 9; slot++) if (minecraft().player.getInventory().getItem(slot).isEmpty()) {
                minecraft().player.getInventory().setSelectedSlot(slot);
                return null;
            }
            throw new IllegalStateException("empty hotbar slot missing");
        });
    }
}
