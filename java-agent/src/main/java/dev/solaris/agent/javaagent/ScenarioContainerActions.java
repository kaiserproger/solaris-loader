package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientTaskExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.time.Duration;
import java.util.Objects;

import static dev.solaris.agent.javaagent.ScenarioClientState.awaitClientStateChange;
import static dev.solaris.agent.javaagent.ScenarioClientState.requireInPlay;

/**
 * Container/menu item moves with server/state acknowledgement.
 *
 * <p>Owns slot matching, click checkpoints, and authoritative ACK waits. Actions in this module
 * never report success from a sent input alone: every mutation waits for the container
 * state/packet advance plus the expected slot state.
 */
final class ScenarioContainerActions {
    private ScenarioContainerActions() {
    }

    /**
     * A recipe-book craft as a vanilla player does it: the recipe click puts the ingredients
     * into the grid, then a shift-click on the result slot (menu slot 0 in both the inventory
     * and the crafting table) crafts every set the grid holds into the inventory.
     */
    static boolean craftRecipe(
        ClientTaskExecutor executor,
        int containerId,
        int recipeDisplayId,
        boolean useMaxItems,
        Duration duration
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        ContainerUpdateCheckpoint placed = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            ContainerUpdateCheckpoint before = new ContainerUpdateCheckpoint(
                menu.containerId,
                menu.getStateId()
            );
            minecraft.getConnection().send(new ServerboundPlaceRecipePacket(
                containerId,
                new RecipeDisplayId(recipeDisplayId),
                useMaxItems
            ));
            return before;
        });
        if (!waitForContainerStateAdvance(executor, placed, deadlineNanos)) {
            return false;
        }
        ContainerUpdateCheckpoint taken = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            if (menu.slots.isEmpty() || menu.slots.get(0).getItem().isEmpty()) {
                return null;
            }
            ContainerUpdateCheckpoint before = new ContainerUpdateCheckpoint(
                menu.containerId,
                menu.getStateId()
            );
            minecraft.gameMode.handleContainerInput(
                menu.containerId,
                0,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
            );
            return before;
        });
        return taken != null && waitForContainerStateAdvance(executor, taken, deadlineNanos);
    }

    static boolean closeCurrentScreenOnClientThread(Minecraft minecraft) {
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen != null) {
            minecraft.player.closeContainer();
            minecraft.setScreen(null);
        }
        return minecraft.screen == null;
    }

    static int activeContainerIdOnClientThread() {
        return requireInPlay().player.containerMenu.containerId;
    }

    static boolean waitForScreenClassName(
        ClientTaskExecutor executor,
        String className,
        Duration duration
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return minecraft.screen != null
                    && Objects.equals(minecraft.screen.getClass().getName(), className);
            });
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    static boolean closeCurrentScreen(ClientTaskExecutor executor, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return closeCurrentScreenOnClientThread(minecraft);
            });
            if (finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    static boolean moveSelectedItemToContainerSlot(
        ClientTaskExecutor executor,
        int containerSlot,
        String itemId,
        int count,
        Duration duration
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        do {
            long observedVersion = ClientStateEvents.version();
            ContainerClickAttempt attempt = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                AbstractContainerMenu menu = minecraft.player.containerMenu;
                if (containerSlotMatchesOnClientThread(containerSlot, itemId, count)) {
                    return new ContainerClickAttempt(true, null);
                }
                int sourceSlot = findMenuSlotWithItem(menu, itemId, count, containerSlot);
                if (sourceSlot < 0) {
                    return new ContainerClickAttempt(false, null);
                }
                ContainerUpdateCheckpoint checkpoint = new ContainerUpdateCheckpoint(
                    menu.containerId,
                    menu.getStateId()
                );
                minecraft.gameMode.handleContainerInput(
                    menu.containerId,
                    sourceSlot,
                    0,
                    ContainerInput.QUICK_MOVE,
                    minecraft.player
                );
                return new ContainerClickAttempt(false, checkpoint);
            });
            if (attempt.alreadyMatched()) {
                return true;
            }
            if (attempt.checkpoint() != null) {
                return waitForAuthoritativeContainerUpdate(
                    executor,
                    attempt.checkpoint(),
                    containerSlot,
                    itemId,
                    count,
                    false,
                    deadlineNanos
                );
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return false;
    }

    static boolean waitForContainerSlot(
        ClientTaskExecutor executor,
        int containerSlot,
        String itemId,
        int count,
        Duration duration
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(
                () -> containerSlotMatchesOnClientThread(containerSlot, itemId, count)
            );
            if (finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    static boolean moveContainerSlotToInventory(
        ClientTaskExecutor executor,
        int containerSlot,
        String itemId,
        int count,
        Duration duration
    ) throws Exception {
        ContainerClickAttempt attempt = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            if (containerSlotEmptyOnClientThread(containerSlot)) {
                return new ContainerClickAttempt(true, null);
            }
            if (
                containerSlot < 0
                    || containerSlot >= menu.slots.size()
                    || !itemStackMatches(menu.getSlot(containerSlot).getItem(), itemId, count)
            ) {
                return new ContainerClickAttempt(false, null);
            }
            ContainerUpdateCheckpoint checkpoint = new ContainerUpdateCheckpoint(
                menu.containerId,
                menu.getStateId()
            );
            minecraft.gameMode.handleContainerInput(
                menu.containerId,
                containerSlot,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
            );
            return new ContainerClickAttempt(false, checkpoint);
        });
        if (attempt.alreadyMatched()) {
            return true;
        }
        if (attempt.checkpoint() == null) {
            return false;
        }
        return waitForAuthoritativeContainerUpdate(
            executor,
            attempt.checkpoint(),
            containerSlot,
            itemId,
            count,
            true,
            System.nanoTime() + duration.toNanos()
        );
    }

    static boolean waitForContainerSlotEmpty(
        ClientTaskExecutor executor,
        int containerSlot,
        Duration duration
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(
                () -> containerSlotEmptyOnClientThread(containerSlot)
            );
            if (finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    static boolean quickMoveContainerSlot(
        ClientTaskExecutor executor,
        int containerSlot,
        Duration duration
    ) throws Exception {
        if (containerSlot < 0 || containerSlot > Short.MAX_VALUE) {
            throw new IllegalArgumentException("container slot must be between 0 and " + Short.MAX_VALUE);
        }
        ContainerUpdateCheckpoint checkpoint = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            if (containerSlot >= menu.slots.size()) {
                throw new IllegalArgumentException(
                    "container slot " + containerSlot + " is outside menu size " + menu.slots.size()
                );
            }
            ContainerUpdateCheckpoint before = new ContainerUpdateCheckpoint(
                menu.containerId,
                menu.getStateId()
            );
            minecraft.gameMode.handleContainerInput(
                menu.containerId,
                containerSlot,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
            );
            return before;
        });
        return waitForContainerStateAdvance(executor, checkpoint, System.nanoTime() + duration.toNanos());
    }

    static boolean clickContainerButton(
        ClientTaskExecutor executor,
        int buttonId,
        Duration duration
    ) throws Exception {
        if (buttonId < 0) {
            throw new IllegalArgumentException("container button id must be non-negative");
        }
        ContainerUpdateCheckpoint checkpoint = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            ContainerUpdateCheckpoint before = new ContainerUpdateCheckpoint(
                menu.containerId,
                menu.getStateId()
            );
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            return before;
        });
        return waitForContainerStateAdvance(executor, checkpoint, System.nanoTime() + duration.toNanos());
    }

    private static boolean waitForAuthoritativeContainerUpdate(
        ClientTaskExecutor executor,
        ContainerUpdateCheckpoint checkpoint,
        int containerSlot,
        String itemId,
        int count,
        boolean expectEmpty,
        long deadlineNanos
    ) throws Exception {
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                AbstractContainerMenu menu = minecraft.player.containerMenu;
                boolean slotMatches = expectEmpty
                    ? containerSlotEmptyOnClientThread(containerSlot)
                    : containerSlotMatchesOnClientThread(containerSlot, itemId, count);
                return ScenarioClient.authoritativeContainerUpdateMatches(
                    checkpoint.containerId(),
                    checkpoint.stateId(),
                    menu.containerId,
                    menu.getStateId(),
                    slotMatches
                );
            });
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    private static boolean waitForContainerStateAdvance(
        ClientTaskExecutor executor,
        ContainerUpdateCheckpoint checkpoint,
        long deadlineNanos
    ) throws Exception {
        do {
            long observedVersion = ClientStateEvents.version();
            boolean advanced = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                AbstractContainerMenu menu = minecraft.player.containerMenu;
                return menu.containerId == checkpoint.containerId()
                    && menu.getStateId() != checkpoint.stateId();
            });
            if (advanced) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                return false;
            }
        } while (true);
    }

    private static boolean waitForContainerUpdate(
        ClientTaskExecutor executor,
        ContainerClickCheckpoint checkpoint,
        long deadlineNanos
    ) throws Exception {
        do {
            long currentPacketVersion = ClientStateEvents.containerPacketVersion();
            boolean updated = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                AbstractContainerMenu menu = minecraft.player.containerMenu;
                return menu.containerId != checkpoint.containerId()
                    || menu.getStateId() != checkpoint.stateId();
            });
            if (currentPacketVersion != checkpoint.containerPacketVersion() && updated) {
                return true;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L || !ClientStateEvents.awaitContainerPacket(
                currentPacketVersion,
                Duration.ofNanos(remainingNanos)
            )) {
                return false;
            }
        } while (true);
    }

    static int findMenuSlotWithItem(
        AbstractContainerMenu menu,
        String itemId,
        int count,
        int excludedSlot
    ) {
        for (int slot = menu.slots.size() - 1; slot >= 0; slot--) {
            if (slot == excludedSlot) {
                continue;
            }
            ItemStack stack = menu.getSlot(slot).getItem();
            if (itemStackMatches(stack, itemId, count)) {
                return slot;
            }
        }
        return -1;
    }

    static boolean containerSlotMatchesOnClientThread(
        int containerSlot,
        String itemId,
        int count
    ) {
        Minecraft minecraft = requireInPlay();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (containerSlot < 0 || containerSlot >= menu.slots.size()) {
            return false;
        }
        return itemStackMatches(menu.getSlot(containerSlot).getItem(), itemId, count);
    }

    static boolean containerSlotEmptyOnClientThread(int containerSlot) {
        Minecraft minecraft = requireInPlay();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (containerSlot < 0 || containerSlot >= menu.slots.size()) {
            return false;
        }
        return menu.getSlot(containerSlot).getItem().isEmpty();
    }

    static boolean itemStackMatches(ItemStack stack, String itemId, int count) {
        return !stack.isEmpty()
            && stack.getCount() >= count
            && Objects.equals(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                itemId
            );
    }

    static boolean clickContainerSlot(
        ClientTaskExecutor executor,
        int containerSlot,
        String button,
        Duration duration
    ) throws Exception {
        if (containerSlot < 0 || containerSlot > Short.MAX_VALUE) {
            throw new IllegalArgumentException("container slot must be between 0 and " + Short.MAX_VALUE);
        }
        int mouseButton = switch (button) {
            case "primary" -> 0;
            case "secondary" -> 1;
            default -> throw new IllegalArgumentException("button must be primary or secondary");
        };
        ContainerClickCheckpoint checkpoint = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            if (!(minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)
                || screen.getMenu() != menu) {
                throw new IllegalStateException("no visible container menu is open");
            }
            if (containerSlot >= menu.slots.size()) {
                throw new IllegalArgumentException(
                    "container slot " + containerSlot + " is outside menu size " + menu.slots.size()
                );
            }
            ContainerClickCheckpoint before = new ContainerClickCheckpoint(
                menu.containerId,
                menu.getStateId(),
                ClientStateEvents.containerPacketVersion()
            );
            minecraft.gameMode.handleContainerInput(
                menu.containerId,
                containerSlot,
                mouseButton,
                ContainerInput.PICKUP,
                minecraft.player
            );
            return before;
        });
        return waitForContainerUpdate(executor, checkpoint, System.nanoTime() + duration.toNanos());
    }

    static int findContainerSlot(ClientTaskExecutor executor, String itemId, int count) throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            int chestSlotLimit = Math.min(27, menu.slots.size());
            for (int slot = 0; slot < chestSlotLimit; slot++) {
                if (itemStackMatches(menu.getSlot(slot).getItem(), itemId, count)) {
                    return slot;
                }
            }
            return -1;
        });
    }

    static boolean containerSlotHasEnchantment(
        ClientTaskExecutor executor,
        int slot,
        String enchantmentId,
        int level
    ) throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            if (slot < 0 || slot >= menu.slots.size()) {
                return false;
            }
            ItemStack stack = menu.getSlot(slot).getItem();
            return stack.getEnchantments().entrySet().stream().anyMatch(entry ->
                entry.getIntValue() == level
                    && Objects.equals(entry.getKey().getRegisteredName(), enchantmentId)
            );
        });
    }

    private record ContainerUpdateCheckpoint(int containerId, int stateId) {
    }

    private record ContainerClickCheckpoint(
        int containerId,
        int stateId,
        long containerPacketVersion
    ) {
    }

    private record ContainerClickAttempt(
        boolean alreadyMatched,
        ContainerUpdateCheckpoint checkpoint
    ) {
    }
}
