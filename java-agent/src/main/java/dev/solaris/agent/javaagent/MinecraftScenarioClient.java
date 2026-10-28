package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientTaskExecutor;
import dev.solaris.agent.javaagent.ScenarioEntityWaits.EntityAttackConfirmation;
import dev.solaris.agent.javaagent.ScenarioEntityWaits.EntityAttackStateSnapshot;
import dev.solaris.agent.javaagent.ScenarioEntityWaits.EntityAttackWaitSource;
import dev.solaris.agent.javaagent.ScenarioEntityWaits.EntityMotionSample;
import dev.solaris.agent.javaagent.ScenarioEntityWaits.EntityStateSnapshot;
import dev.solaris.agent.javaagent.ScenarioEntityWaits.EntityWaitSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Prediction;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static dev.solaris.agent.javaagent.ScenarioClientState.awaitClientStateChange;
import static dev.solaris.agent.javaagent.ScenarioClientState.awaitClientTick;
import static dev.solaris.agent.javaagent.ScenarioClientState.requireInPlay;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.HORIZONTAL_DIRECTIONS;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.PLACE_DIRECTIONS;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.blockIdAt;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.blockPropertyMatches;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.breakableBlockTarget;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.clearFoliageObstacleTowardOnClientThread;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.closestAccessibleFace;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.direction;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.findBreakableBlockOnClientThread;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.findHorizontalAttachmentPairOnClientThread;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.findOccupiedPairOnClientThread;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.findPlaceablePairOnClientThread;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.findTillableSoilOnClientThread;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.hitResult;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.isPlayerPassable;
import static dev.solaris.agent.javaagent.ScenarioBlockTargets.pos;

public final class MinecraftScenarioClient implements ScenarioClient {
    private static final double PICKUP_APPROACH_DISTANCE_SQUARED = 4.0;
    private static final long PICKUP_INVENTORY_CONVERGENCE_NANOS = 2_000_000_000L;
    private static final double POSITION_APPROACH_DISTANCE_SQUARED = 2.25;
    private static final double COMBAT_DIAGONAL = Math.sqrt(0.5);
    private static final double[] COMBAT_RETREAT_DIRECTIONS = {
        1.0, 0.0, COMBAT_DIAGONAL, COMBAT_DIAGONAL, 0.0, 1.0, -COMBAT_DIAGONAL, COMBAT_DIAGONAL,
        -1.0, 0.0, -COMBAT_DIAGONAL, -COMBAT_DIAGONAL, 0.0, -1.0, COMBAT_DIAGONAL, -COMBAT_DIAGONAL
    };
    private static final Direction[] HUNGER_DRAIN_DIRECTIONS = {
        Direction.EAST,
        Direction.WEST,
        Direction.SOUTH,
        Direction.NORTH
    };
    private static final List<String> EXCAVATION_BLOCKS = List.of(
        "minecraft:stone", "minecraft:deepslate", "minecraft:granite", "minecraft:diorite",
        "minecraft:andesite", "minecraft:tuff", "minecraft:dirt", "minecraft:grass_block", "minecraft:cobblestone"
    );
    // Two blocks fit a standing player; the third lets it jump onto the next step.
    private static final int EXCAVATION_HEADROOM = CliffStaircase.HEADROOM;
    private static final List<String> EXCAVATION_TOOLS =
        List.of("minecraft:iron_pickaxe", "minecraft:stone_pickaxe", "minecraft:wooden_pickaxe");
    private static final int MAX_CLIFFS_PER_APPROACH = 4;
    private static final Duration CLIFF_APPROACH_TIMEOUT = Duration.ofSeconds(120);
    private int buriedOreSurfaceY = -1;
    private static final AtomicReference<BlockBreakAutomation> ACTIVE_BLOCK_BREAK =
        new AtomicReference<>();
    private static final Field FURNACE_MENU_DATA = furnaceMenuDataField();

    private final ClientTaskExecutor executor;
    private final CombatLedger combatLedger = new CombatLedger();

    MinecraftScenarioClient(ClientTaskExecutor executor) {
        this.executor = executor;
    }

    public static boolean hasActiveBlockBreak() {
        return ACTIVE_BLOCK_BREAK.get() != null;
    }

    public static void runPreTickActions() {
        BlockBreakAutomation action = ACTIVE_BLOCK_BREAK.get();
        if (action == null) {
            return;
        }

        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
                return;
            }
            if (minecraft.gui.screen() != null) {
                return;
            }
            BlockHitResult hit = hitResult(action.target);
            minecraft.hitResult = hit;
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, hit.getLocation());
            minecraft.options.keyAttack.setDown(true);
            if (!action.startSent) {
                action.startSent = true;
                minecraft.gameMode.startDestroyBlock(pos(action.target), action.face);
                minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                action.started.complete(null);
            } else {
                action.continueCalls++;
                if (minecraft.gameMode.continueDestroyBlock(pos(action.target), action.face)) {
                    action.continueAccepted++;
                    minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                }
            }
        } catch (RuntimeException error) {
            ACTIVE_BLOCK_BREAK.compareAndSet(action, null);
            action.started.completeExceptionally(error);
        }
    }

    @Override
    public ScenarioBlockPair findOccupiedPair(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() -> findOccupiedPairOnClientThread(reach));
    }

    @Override
    public ScenarioBlockPair findPlaceablePair(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() -> findPlaceablePairOnClientThread(reach, false));
    }

    @Override
    public ScenarioBlockPair findDryPlaceablePair(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() -> findPlaceablePairOnClientThread(reach, true));
    }

    @Override
    public ScenarioBlockPair findTillableSoil(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() -> findTillableSoilOnClientThread(reach));
    }

    @Override
    public ScenarioBlockPair findOpenDryPlaceablePair(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() -> findPlaceablePairOnClientThread(reach, true, true));
    }

    @Override
    public ScenarioBlockPair findUnobstructedPlaceablePair(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() -> findPlaceablePairOnClientThread(reach, false, true, false, PLACE_DIRECTIONS));
    }

    @Override
    public ScenarioBlockPair findHorizontalPlaceablePair(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() ->
            findPlaceablePairOnClientThread(reach, true, false, HORIZONTAL_DIRECTIONS)
        );
    }

    @Override
    public ScenarioBlockPair findVerticalPlaceablePair(ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() ->
            findPlaceablePairOnClientThread(reach, true, false, new Direction[] { Direction.UP })
        );
    }

    @Override
    public ScenarioBlockPair findHorizontalAttachmentPair(
        ScenarioBlockTarget support,
        ScenarioReach reach
    ) throws Exception {
        return executor.callOnClientThread(() -> findHorizontalAttachmentPairOnClientThread(support, reach));
    }

    @Override
    public ScenarioHeldItem giveAndSelect(String itemId, int count, int hotbarSlot, Duration timeout)
        throws Exception {
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.getConnection().sendCommand("debug give " + itemId + " " + count + " " + hotbarSlot);
            selectHotbarSlotOnClientThread(hotbarSlot);
            return null;
        });

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ScenarioHeldItem latest;
        do {
            long observedVersion = ClientStateEvents.version();
            latest = executor.callOnClientThread(() -> {
                selectHotbarSlotOnClientThread(hotbarSlot);
                return selectedItemOnClientThread();
            });
            if (latest.matches(itemId, count)) {
                return latest;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return latest;
    }

    @Override
    public ScenarioUseResult useItemOn(ScenarioBlockTarget clicked, ScenarioHeldItem heldItem) throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            long ackVersion = ClientStateEvents.blockChangeAckVersion();
            BlockHitResult hit = hitResult(clicked);
            minecraft.hitResult = hit;
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, hit.getLocation());
            InteractionResult result = minecraft.gameMode.useItemOn(
                minecraft.player,
                InteractionHand.MAIN_HAND,
                hit
            );
            minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            return new ScenarioUseResult(result.toString(), ackVersion);
        });
    }

    @Override
    public ScenarioUseResult useItemOnAtHeight(
        ScenarioBlockTarget clicked,
        ScenarioHeldItem heldItem,
        double cursorHeight
    ) throws Exception {
        if (cursorHeight < 0.0 || cursorHeight > 1.0) {
            throw new IllegalArgumentException("cursor height must be between 0 and 1");
        }
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            long ackVersion = ClientStateEvents.blockChangeAckVersion();
            BlockHitResult hit = hitResult(clicked, cursorHeight);
            minecraft.hitResult = hit;
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, hit.getLocation());
            InteractionResult result = minecraft.gameMode.useItemOn(
                minecraft.player,
                InteractionHand.MAIN_HAND,
                hit
            );
            minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            return new ScenarioUseResult(result.toString(), ackVersion);
        });
    }

    @Override
    public boolean waitForUseAcknowledgement(ScenarioUseResult use, Duration timeout)
        throws InterruptedException {
        if (use.blockChangeAckVersionBeforeUse() < 0L) {
            return false;
        }
        return ClientStateEvents.awaitBlockChangeAck(use.blockChangeAckVersionBeforeUse(), timeout);
    }

    @Override
    public boolean waitForBlock(ScenarioBlockTarget target, String blockId, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> Objects.equals(blockIdAt(target), blockId));
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public boolean waitForAnyBlock(ScenarioBlockTarget target, List<String> blockIds, Duration duration)
        throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> blockIds.contains(blockIdAt(target)));
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public boolean waitForBlockProperty(
        ScenarioBlockTarget target,
        String property,
        String value,
        Duration duration
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> blockPropertyMatches(target, property, value));
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public ScenarioLightLevel lightLevel(ScenarioBlockTarget target) throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            BlockPos position = pos(target);
            return new ScenarioLightLevel(
                minecraft.level.getBrightness(LightLayer.SKY, position),
                minecraft.level.getBrightness(LightLayer.BLOCK, position)
            );
        });
    }

    @Override
    public boolean waitForStableBlocks(ScenarioBlockPair pair, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() ->
                Objects.equals(blockIdAt(pair.clicked()), pair.clicked().blockId())
                    && Objects.equals(blockIdAt(pair.target()), pair.target().blockId())
            );
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public boolean waitForNoFluid(ScenarioBlockTarget target, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return minecraft.level.getFluidState(pos(target)).isEmpty();
            });
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public boolean waitForSignEditor(ScenarioBlockTarget target, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return minecraft.gui.screen() instanceof SignEditScreen
                    && minecraft.level.getBlockEntity(pos(target)) instanceof SignBlockEntity;
            });
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public void updateSignText(ScenarioBlockTarget target, List<String> lines) throws Exception {
        requireFourSignLines(lines);
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.getConnection().send(new ServerboundSignUpdatePacket(
                pos(target),
                List.copyOf(lines),
                SignTextSlot.FRONT
            ));
            return null;
        });
    }

    @Override
    public boolean waitForSignText(ScenarioBlockTarget target, List<String> lines, Duration duration)
        throws Exception {
        requireFourSignLines(lines);
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> signTextMatches(target, lines));
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    /** Recipe-book craft: place the recipe, then shift-click the result (vanilla flow). */
    @Override
    public void placeRecipe(int containerId, int recipeDisplayId, boolean useMaxItems) throws Exception {
        ScenarioContainerActions.craftRecipe(
            executor,
            containerId,
            recipeDisplayId,
            useMaxItems,
            Duration.ofSeconds(5)
        );
    }

    /**
     * The recipe book entry that crafts {@code itemId}. Several entries can share a
     * result (a bed from wool and planks, or the shapeless re-dye of any bed), so a
     * shaped recipe wins over a shapeless one and the lowest display id breaks ties.
     */
    @Override
    public int recipeDisplayIdForResult(String itemId) throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            var context = SlotDisplayContext.fromLevel(minecraft.level);
            int best = -1;
            boolean bestShaped = false;
            for (var collection : minecraft.player.getRecipeBook().getCollections()) {
                for (var entry : collection.getRecipes()) {
                    boolean matches = entry.resultItems(context).stream().anyMatch(stack ->
                        !stack.isEmpty()
                            && Objects.equals(
                                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                                itemId
                            )
                    );
                    if (!matches) {
                        continue;
                    }
                    int index = entry.id().index();
                    boolean shaped = entry.display() instanceof ShapedCraftingRecipeDisplay;
                    if (best < 0 || (shaped && !bestShaped) || (shaped == bestShaped && index < best)) {
                        best = index;
                        bestShaped = shaped;
                    }
                }
            }
            return best;
        });
    }

    @Override
    public int inventoryCount(String itemId) throws Exception {
        return executor.callOnClientThread(() -> inventoryCountOnClientThread(itemId));
    }

    @Override
    public boolean waitForInventoryCount(String itemId, int count, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> inventoryCountOnClientThread(itemId) == count);
            if (finalSample || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public int totalExperience() throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            return minecraft.player.totalExperience;
        });
    }

    @Override
    public int experienceLevel() throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            return minecraft.player.experienceLevel;
        });
    }

    @Override
    public boolean waitForExperience(int totalExperience, int level, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        do {
            long observedVersion = ClientStateEvents.version();
            boolean matched = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return minecraft.player.totalExperience == totalExperience
                    && minecraft.player.experienceLevel == level;
            });
            if (matched) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                return false;
            }
        } while (true);
    }

    @Override
    public int waitForTotalExperienceAbove(int totalExperience, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        int latest;
        do {
            long observedVersion = ClientStateEvents.version();
            latest = totalExperience();
            if (latest > totalExperience) {
                return latest;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return latest;
    }

    @Override
    public boolean waitForDayTimeAtOrAfter(long dayTime, Duration duration) throws Exception {
        return waitForDayTime(dayTime, duration, true);
    }

    @Override
    public boolean waitForDayTimeBelow(long dayTime, Duration duration) throws Exception {
        return waitForDayTime(dayTime, duration, false);
    }

    private boolean waitForDayTime(long dayTime, Duration duration, boolean atOrAfter) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.tickVersion();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                long current = Math.floorMod(minecraft.level.getLevelData().getGameTime(), 24_000L);
                return atOrAfter ? current >= dayTime : current < dayTime;
            });
            if (finalSample) {
                return true;
            }
            if (!awaitClientTick(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    public boolean waitForScreenClassName(String className, Duration duration) throws Exception {
        return ScenarioContainerActions.waitForScreenClassName(executor, className, duration);
    }

    @Override
    public boolean closeCurrentScreen(Duration duration) throws Exception {
        return ScenarioContainerActions.closeCurrentScreen(executor, duration);
    }

    @Override
    public int activeContainerId() throws Exception {
        return executor.callOnClientThread(ScenarioContainerActions::activeContainerIdOnClientThread);
    }

    @Override
    public ScenarioFurnaceState furnaceState() throws Exception {
        return executor.callOnClientThread(() -> {
            AbstractContainerMenu menu = requireInPlay().player.containerMenu;
            if (!(menu instanceof AbstractFurnaceMenu furnace)) {
                throw new IllegalStateException("active menu is not a furnace: " + menu.getClass().getName());
            }
            ContainerData data = (ContainerData) FURNACE_MENU_DATA.get(furnace);
            ItemStack fuel = furnace.getSlot(1).getItem();
            String itemId = fuel.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString();
            return furnaceSnapshot(data, itemId, fuel.getCount());
        });
    }

    static ScenarioFurnaceState furnaceSnapshot(ContainerData data, String fuelItemId, int fuelCount) {
        return new ScenarioFurnaceState(data.get(0), data.get(1), data.get(2), data.get(3), fuelItemId, fuelCount);
    }

    private static Field furnaceMenuDataField() {
        try {
            Field field = AbstractFurnaceMenu.class.getDeclaredField("data");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @Override
    public boolean moveSelectedItemToContainerSlot(
        int containerSlot,
        String itemId,
        int count,
        Duration duration
    ) throws Exception {
        return ScenarioContainerActions.moveSelectedItemToContainerSlot(
            executor,
            containerSlot,
            itemId,
            count,
            duration
        );
    }

    @Override
    public boolean waitForContainerSlot(
        int containerSlot,
        String itemId,
        int count,
        Duration duration
    ) throws Exception {
        return ScenarioContainerActions.waitForContainerSlot(executor, containerSlot, itemId, count, duration);
    }

    @Override
    public boolean moveContainerSlotToInventory(
        int containerSlot,
        String itemId,
        int count,
        Duration duration
    ) throws Exception {
        return ScenarioContainerActions.moveContainerSlotToInventory(
            executor,
            containerSlot,
            itemId,
            count,
            duration
        );
    }

    @Override
    public boolean waitForContainerSlotEmpty(int containerSlot, Duration duration) throws Exception {
        return ScenarioContainerActions.waitForContainerSlotEmpty(executor, containerSlot, duration);
    }

    @Override
    public boolean quickMoveContainerSlot(int containerSlot, Duration duration) throws Exception {
        return ScenarioContainerActions.quickMoveContainerSlot(executor, containerSlot, duration);
    }

    public boolean clickContainerSlot(int containerSlot, String button, Duration duration) throws Exception {
        return ScenarioContainerActions.clickContainerSlot(executor, containerSlot, button, duration);
    }

    @Override
    public int findContainerSlot(String itemId, int count) throws Exception {
        return ScenarioContainerActions.findContainerSlot(executor, itemId, count);
    }

    @Override
    public boolean clickContainerButton(int buttonId, Duration duration) throws Exception {
        return ScenarioContainerActions.clickContainerButton(executor, buttonId, duration);
    }

    @Override
    public boolean containerSlotHasEnchantment(int slot, String enchantmentId, int level) throws Exception {
        return ScenarioContainerActions.containerSlotHasEnchantment(executor, slot, enchantmentId, level);
    }

    @Override
    public ScenarioEntityObservation summonEntityNearPlayer(
        String entityTypeId,
        double offsetX,
        double offsetY,
        double offsetZ,
        Duration timeout
    ) throws Exception {
        Vec3 target = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            Vec3 summonTarget = new Vec3(
                minecraft.player.getX() + offsetX,
                minecraft.player.getY() + offsetY,
                minecraft.player.getZ() + offsetZ
            );
            String command = String.format(
                Locale.ROOT,
                "summon %s %.3f %.3f %.3f",
                entityTypeId,
                summonTarget.x,
                summonTarget.y,
                summonTarget.z
            );
            minecraft.getConnection().sendCommand(command);
            return summonTarget;
        });

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ScenarioEntityObservation finalSample = null;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> visibleEntityNearOnClientThread(
                entityTypeId,
                target,
                64.0
            ));
            if (finalSample != null) {
                return finalSample;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public void sendCommand(String command) throws Exception {
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.getConnection().sendCommand(command);
            return null;
        });
    }

    @Override
    public void sendChatMessage(String message) throws Exception {
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.getConnection().sendChat(message);
            return null;
        });
    }

    @Override
    public boolean waitForChatMessage(String expectedText, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> chatHistoryContainsOnClientThread(expectedText));
            if (finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public boolean waitForTicks(long ticks, Duration timeout) throws Exception {
        if (ticks < 1L) {
            throw new IllegalArgumentException("ticks must be positive");
        }
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        int startTick = executor.callOnClientThread(() -> requireInPlay().player.tickCount);
        while (true) {
            long observedVersion = ClientStateEvents.tickVersion();
            int currentTick = executor.callOnClientThread(() -> requireInPlay().player.tickCount);
            if (Integer.toUnsignedLong(currentTick - startTick) >= ticks) {
                return true;
            }
            if (!awaitClientTick(observedVersion, deadlineNanos)) {
                return false;
            }
        }
    }

    @Override
    public long serverGameTime() {
        return ClientStateEvents.serverGameTime();
    }

    @Override
    public long waitForServerTimeAfter(long baseline, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (true) {
            long observedVersion = ClientStateEvents.serverTimeVersion();
            long gameTime = ClientStateEvents.serverGameTime();
            if (gameTime > baseline) {
                return gameTime;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L || !ClientStateEvents.awaitServerTimeChange(
                observedVersion,
                Duration.ofNanos(remainingNanos)
            )) {
                return gameTime;
            }
        }
    }

    @Override
    public ScenarioEntityObservation findVisibleEntity(
        List<String> entityTypeIds,
        ScenarioReach reach,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ScenarioEntityObservation finalSample = null;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> visibleEntityOnClientThread(entityTypeIds, reach, true));
            if (finalSample != null) {
                return finalSample;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public ScenarioEntityObservation findVisibleSheepWithWool(
        String woolItemId,
        ScenarioReach reach,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ScenarioEntityObservation finalSample = null;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> visibleSheepWithWoolOnClientThread(woolItemId, reach));
            if (finalSample != null) {
                return finalSample;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public ScenarioEntityObservation visibleEntity(List<String> entityTypeIds, ScenarioReach reach)
        throws Exception {
        return executor.callOnClientThread(() -> visibleEntityOnClientThread(entityTypeIds, reach, false));
    }

    @Override
    public ScenarioEntityObservation visibleEntityNearPlayerLevel(
        List<String> entityTypeIds,
        double maxVerticalDistance,
        java.util.Set<Integer> excludedEntityIds
    ) throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            Vec3 playerPosition = minecraft.player.position();
            ScenarioEntityObservation nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity.isRemoved() || !entity.isAlive() || entity == minecraft.player || entity instanceof Creeper
                    || excludedEntityIds.contains(entity.getId())) {
                    continue;
                }
                if (!entityTypeIds.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                    || Math.abs(entity.getY() - playerPosition.y) > maxVerticalDistance) {
                    continue;
                }
                double distance = entity.distanceToSqr(playerPosition);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = entityObservation(entity, playerPosition, distance);
                }
            }
            return nearest;
        });
    }

    @Override
    public int[] playerBlockXZ() throws Exception {
        return executor.callOnClientThread(() -> {
            BlockPos pos = requireInPlay().player.blockPosition();
            return new int[] {pos.getX(), pos.getZ()};
        });
    }

    @Override
    public ScenarioEntityInteractionResult interactEntity(ScenarioEntityInteraction interaction)
        throws Exception {
        return EntityInteractionDispatch.queue(
            executor,
            interaction,
            new MinecraftEntityInteractionAccess()
        );
    }

    @Override
    public ScenarioEntityMotionObservation waitForEntityMotion(
        ScenarioEntityObservation entity,
        double minimumHorizontalDistance,
        double minimumVerticalRise,
        Duration timeout
    ) throws Exception {
        if (minimumHorizontalDistance <= 0.0) {
            throw new IllegalArgumentException("minimum horizontal distance must be positive");
        }
        if (minimumVerticalRise < 0.0) {
            throw new IllegalArgumentException("minimum vertical rise must not be negative");
        }
        EntityWaitSource source = entityWaitSource(entity.identity());
        return ScenarioEntityWaits.waitForEntityMotion(
            source,
            entity.entityId(),
            entity.x(),
            entity.y(),
            entity.z(),
            true,
            minimumHorizontalDistance,
            minimumVerticalRise,
            timeout
        );
    }

    public ScenarioEntityMotionObservation waitForEntityMotion(
        int entityId,
        UUID entityUuid,
        String entityTypeId,
        double minimumHorizontalDistance,
        double minimumVerticalRise,
        Duration timeout
    ) throws Exception {
        if (minimumHorizontalDistance <= 0.0) {
            throw new IllegalArgumentException("minimum horizontal distance must be positive");
        }
        if (minimumVerticalRise < 0.0) {
            throw new IllegalArgumentException("minimum vertical rise must not be negative");
        }

        ScenarioEntityIdentity identity = new ScenarioEntityIdentity(entityId, entityUuid, entityTypeId);
        return ScenarioEntityWaits.waitForEntityMotion(
            entityWaitSource(identity),
            entityId,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            false,
            minimumHorizontalDistance,
            minimumVerticalRise,
            timeout
        );
    }


    public boolean waitForEntityRemoved(
        int entityId,
        UUID entityUuid,
        String entityTypeId,
        Duration timeout
    ) throws Exception {
        ScenarioEntityIdentity identity = new ScenarioEntityIdentity(entityId, entityUuid, entityTypeId);
        return ScenarioEntityWaits.waitForEntityRemoved(entityWaitSource(identity), timeout);
    }

    public ScenarioEntityWaits.EntityAttackConfirmation waitForEntityAttackConfirmation(
        ScenarioEntityIdentity identity,
        Object expectedLevel,
        float healthBefore,
        Duration timeout
    ) throws Exception {
        return ScenarioEntityWaits.waitForEntityAttackConfirmation(
            entityAttackWaitSource(identity),
            expectedLevel,
            healthBefore,
            timeout
        );
    }



    private EntityWaitSource entityWaitSource(ScenarioEntityIdentity identity) {
        return new EntityWaitSource() {
            @Override
            public Object captureLevel() throws Exception {
                return executor.callOnClientThread(() -> requireInPlay().level);
            }

            @Override
            public EntityStateSnapshot snapshot() throws Exception {
                return executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    EntityMotionSample motion = entityMotionSampleOnClientThread(identity);
                    return new EntityStateSnapshot(minecraft.level, motion, motion != null);
                });
            }

            @Override
            public long stateVersion() {
                return ClientStateEvents.version();
            }

            @Override
            public boolean awaitStateChange(long observedVersion, long deadlineNanos)
                throws InterruptedException {
                return awaitClientStateChange(observedVersion, deadlineNanos);
            }
        };
    }

    private EntityAttackWaitSource entityAttackWaitSource(ScenarioEntityIdentity identity) {
        return new EntityAttackWaitSource() {
            @Override
            public EntityAttackStateSnapshot snapshot() throws Exception {
                return executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    Entity entity = minecraft.level.getEntity(identity.entityId());
                    if (
                        entity == null
                            || !identity.matches(
                                entity.getId(),
                                entity.getUUID(),
                                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
                            )
                    ) {
                        return new EntityAttackStateSnapshot(
                            minecraft.level,
                            false,
                            Float.NaN
                        );
                    }
                    float health = entity instanceof net.minecraft.world.entity.LivingEntity living
                        ? living.getHealth()
                        : Float.NaN;
                    return new EntityAttackStateSnapshot(minecraft.level, true, health);
                });
            }

            @Override
            public long stateVersion() {
                return ClientStateEvents.version();
            }

            @Override
            public boolean awaitStateChange(long observedVersion, long deadlineNanos)
                throws InterruptedException {
                return awaitClientStateChange(observedVersion, deadlineNanos);
            }
        };
    }


    @Override
    public ScenarioPlayerObservation waitForVisiblePlayer(String playerName, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ScenarioPlayerObservation finalSample = null;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> visiblePlayerOnClientThread(playerName));
            if (finalSample != null) {
                return finalSample;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public boolean waitForNoVisiblePlayer(String playerName, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> visiblePlayerOnClientThread(playerName) == null);
            if (finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return finalSample;
    }

    @Override
    public ScenarioPlayerObservation waitForMovedPlayer(
        String playerName,
        ScenarioPlayerObservation baseline,
        double minHorizontalDelta,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ScenarioPlayerObservation finalSample = null;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> visiblePlayerOnClientThread(playerName));
            if (finalSample != null && horizontalDistance(baseline, finalSample) >= minHorizontalDelta) {
                return finalSample;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return null;
    }

    @Override
    public boolean approachEntity(ScenarioEntityObservation entity, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        CombatAttempt combat = new CombatAttempt(selectedItem().itemId());
        String outcome = "timeout";
        int detourDirection = 0;
        int preferredDetourDirection = 1;
        try {
            do {
                long observedVersion = ClientStateEvents.tickVersion();
                int currentDetourDirection = detourDirection;
                int currentPreferredDirection = preferredDetourDirection;
                EntityApproachSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    if (minecraft.gui.screen() != null) {
                        minecraft.player.closeContainer();
                        minecraft.gui.setScreen(null);
                    }
                    Entity current = entityByIdOnClientThread(entity.entityId());
                    if (current == null) {
                        minecraft.options.keySprint.setDown(false);
                        minecraft.options.keyUp.setDown(false);
                        minecraft.options.keyJump.setDown(false);
                        minecraft.options.keyLeft.setDown(false);
                        minecraft.options.keyRight.setDown(false);
                        return new EntityApproachSample(false, false, 0);
                    }
                    if (current instanceof Enemy) {
                        observeCombatTarget(minecraft, current, clientTick(minecraft), combat);
                        boolean ready = driveCombatOnClientThread(minecraft, current, true, combat);
                        return new EntityApproachSample(true, ready && ScenarioReach.WITHIN_SURVIVAL_REACH.includes(
                            minecraft.player.position().distanceToSqr(current.position())
                        ), 0);
                    }
                    Vec3 target = entityLookTarget(current);
                    minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
                    MovementClearance clearance = MovementDetour.clearance(minecraft, target);
                    boolean stepUp = minecraft.player.horizontalCollision
                        && clearance.raisedForward();
                    int nextDetourDirection = MovementDetour.choose(
                        currentDetourDirection,
                        currentPreferredDirection,
                        minecraft.player.horizontalCollision && !stepUp,
                        clearance.direct(),
                        clearance.left(),
                        clearance.right()
                    );
                    minecraft.options.keySprint.setDown(true);
                    minecraft.options.keyUp.setDown(nextDetourDirection == 0);
                    minecraft.options.keyJump.setDown(stepUp || minecraft.player.isInWater());
                    minecraft.options.keyLeft.setDown(nextDetourDirection < 0);
                    minecraft.options.keyRight.setDown(nextDetourDirection > 0);
                    boolean inReach = ScenarioReach.WITHIN_SURVIVAL_REACH.includes(
                        minecraft.player.position().distanceToSqr(current.position())
                    );
                    return new EntityApproachSample(true, inReach, nextDetourDirection);
                });
                eatOwnedFoodForCombatRecovery(combat, deadlineNanos);
                if (detourDirection == 0 && sample.detourDirection() != 0) {
                    preferredDetourDirection = -sample.detourDirection();
                }
                detourDirection = sample.detourDirection();
                if (combat.gaveUp) {
                    outcome = "gave_up_stuck";
                    break;
                }
                if (sample.visible() && sample.inReach()) {
                    outcome = "in_reach";
                    return true;
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            boolean closeEnough = executor.callOnClientThread(() -> {
                Entity current = entityByIdOnClientThread(entity.entityId());
                return current != null
                    && (!(current instanceof Enemy) || (!combat.recovery.recovering()
                        && !(current instanceof Creeper)))
                    && ScenarioReach.WITHIN_SURVIVAL_REACH.includes(
                        requireInPlay().player.position().distanceToSqr(current.position()))
                    // A close hostile behind a wall or up a tree is not an arrival.
                    && (!(current instanceof Enemy) || requireInPlay().player.hasLineOfSight(current));
            });
            outcome = closeEnough ? "in_reach_after_timeout" : "timeout";
            return closeEnough;
        } finally {
            if (combat.hostile) {
                combatLedger.engagement("approach", entity.entityType(), entity.entityId(), 0,
                    combat.withheldSummary(), outcome);
            }
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyJump.setDown(false);
                    minecraft.options.keyLeft.setDown(false);
                    minecraft.options.keyRight.setDown(false);
                    minecraft.options.keyDown.setDown(false);
                }
                return null;
            });
        }
    }

    @Override
    public ScenarioBreakResult attackEntityUntilDropCollected(
        ScenarioEntityObservation entity,
        String expectedDropItemId,
        int expectedSelectedCount,
        Duration timeout
    ) throws Exception {
        int initialCount = executor.callOnClientThread(() -> inventoryCountOnClientThread(expectedDropItemId));
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        long lastAttackTick = -1L;
        boolean started = false;
        boolean removed = false;
        boolean sawDrop = false;
        boolean pickupRestored = false;
        double lastX = entity.x();
        double lastY = entity.y();
        double lastZ = entity.z();
        ScenarioHeldItem selected = selectedItem();
        CombatAttempt combat = new CombatAttempt(selected.itemId());
        try {
            do {
                long observedVersion = ClientStateEvents.tickVersion();
                BlockPos near = new BlockPos((int) Math.floor(lastX), (int) Math.floor(lastY), (int) Math.floor(lastZ));
                double capturedLastX = lastX;
                double capturedLastY = lastY;
                double capturedLastZ = lastZ;
                long capturedLastAttackTick = lastAttackTick;
                EntityAttackSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    long currentTick = clientTick(minecraft);
                    boolean mayAttack = currentTick != capturedLastAttackTick
                        && AttackCadence.ready(minecraft.player.getAttackStrengthScale(0.0F));
                    Entity current = entityByIdOnClientThread(entity.entityId());
                    Vec3 dropPosition = itemDropPositionOnClientThread(expectedDropItemId, near);
                    if (current != null) {
                        observeCombatTarget(minecraft, current, currentTick, combat);
                        mayAttack = driveCombatOnClientThread(minecraft, current, mayAttack, combat);
                        if (mayAttack) {
                            if (capturedLastAttackTick < 0L) {
                                recordAttackCadence(minecraft.player, current);
                            }
                            recordCombatAttack(minecraft, current, entity.entityType(), currentTick);
                            combat.sent++;
                            minecraft.gameMode.attack(minecraft.player, current);
                            minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                        }
                        return new EntityAttackSample(
                            mayAttack,
                            false,
                            dropPosition != null,
                            selectedItemOnClientThread(),
                            inventoryCountOnClientThread(expectedDropItemId),
                            current.getX(),
                            current.getY(),
                            current.getZ(),
                            currentTick
                        );
                    }
                    combatLedger.targetRemoved(entity.entityId());
                    if (dropPosition != null) {
                        Vec3 playerPosition = minecraft.player.position();
                        var playerBox = minecraft.player.getBoundingBox();
                        boolean inPickupVolume = pickupVolumeIntersects(
                            playerBox.minX, playerBox.minY, playerBox.minZ,
                            playerBox.maxX, playerBox.maxY, playerBox.maxZ,
                            dropPosition.x, dropPosition.y, dropPosition.z
                        );
                        if (combat.hostile) {
                            boolean nearby = hostileNearbyOnClientThread(minecraft, null, null);
                            if (!CombatTactics.dropChaseAllowed(playerPosition.y, dropPosition.y, nearby)) {
                                stopCombatMotion(minecraft);
                                if (nearby) {
                                    retreatFromHostilesOnClientThread(minecraft);
                                }
                            } else if (inPickupVolume) {
                                stopCombatMotion(minecraft);
                            } else {
                                moveCombatSafelyOnClientThread(minecraft, dropPosition, combat);
                            }
                            return new EntityAttackSample(false, true, true, selectedItemOnClientThread(),
                                inventoryCountOnClientThread(expectedDropItemId), dropPosition.x, dropPosition.y,
                                dropPosition.z, currentTick);
                        }
                        Vec3 target = dropPosition.y - playerPosition.y <= -0.75
                            ? new Vec3(
                                pickupApproachCoordinate(playerPosition.y, dropPosition.y, dropPosition.x),
                                dropPosition.y,
                                pickupApproachCoordinate(playerPosition.y, dropPosition.y, dropPosition.z)
                            )
                            : dropPosition;
                        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
                        MovementClearance clearance = MovementDetour.clearance(minecraft, target);
                        minecraft.options.keySprint.setDown(
                            !inPickupVolume && playerPosition.distanceToSqr(target) > 0.64
                        );
                        minecraft.options.keyUp.setDown(!inPickupVolume);
                        minecraft.options.keyJump.setDown(
                            !inPickupVolume && minecraft.player.horizontalCollision && clearance.raisedForward()
                        );
                    } else {
                        stopCombatMotion(minecraft);
                    }
                    return new EntityAttackSample(
                        false,
                        true,
                        dropPosition != null,
                        selectedItemOnClientThread(),
                        inventoryCountOnClientThread(expectedDropItemId),
                        dropPosition == null ? capturedLastX : dropPosition.x,
                        dropPosition == null ? capturedLastY : dropPosition.y,
                        dropPosition == null ? capturedLastZ : dropPosition.z,
                        currentTick
                    );
                });
                started |= sample.attackSent();
                removed |= sample.removed();
                sawDrop |= sample.visibleDrop();
                selected = sample.selectedItem();
                lastX = sample.x();
                lastY = sample.y();
                lastZ = sample.z();
                pickupRestored = sample.inventoryCount() >= initialCount + expectedSelectedCount;
                if (sample.attackSent()) {
                    lastAttackTick = sample.tick();
                }
                eatOwnedFoodForCombatRecovery(combat, deadlineNanos);
                if (removed && pickupRestored) {
                    return new ScenarioBreakResult(started, true, sawDrop, true, selected);
                }
                if (removed && sawDrop && !sample.visibleDrop()) {
                    // Removal and inventory application can arrive on different
                    // client events. Only the inventory delta proves collection.
                    InventoryPickupResult pickup = waitForInventoryPickup(
                        expectedDropItemId,
                        initialCount,
                        expectedSelectedCount,
                        near,
                        System.nanoTime() + PICKUP_INVENTORY_CONVERGENCE_NANOS
                    );
                    return new ScenarioBreakResult(
                        started, true, sawDrop, pickup.confirmed(), selectedItem(), pickup.detail()
                    );
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            return new ScenarioBreakResult(started, removed, sawDrop, pickupRestored, selected);
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    stopCombatMotion(minecraft);
                }
                return null;
            });
        }
    }

    @Override
    public boolean attackEntityUntilRemoved(ScenarioEntityObservation entity, Duration timeout)
        throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        long lastAttackTick = -1L;
        boolean started = false;
        String outcome = "timeout";
        CombatAttempt combat = new CombatAttempt(selectedItem().itemId());
        try {
            while (true) {
                long observedVersion = ClientStateEvents.tickVersion();
                long capturedLastAttackTick = lastAttackTick;
                EntityRemovalAttackSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    long currentTick = clientTick(minecraft);
                    Entity current = entityByIdOnClientThread(entity.entityId());
                    if (current == null) {
                        combatLedger.targetRemoved(entity.entityId());
                        return new EntityRemovalAttackSample(false, true, currentTick);
                    }
                    observeCombatTarget(minecraft, current, currentTick, combat);

                    boolean mayAttack = currentTick != capturedLastAttackTick
                        && AttackCadence.ready(minecraft.player.getAttackStrengthScale(0.0F));
                    mayAttack = driveCombatOnClientThread(minecraft, current, mayAttack, combat);
                    if (mayAttack) {
                        if (capturedLastAttackTick < 0L) {
                            recordAttackCadence(minecraft.player, current);
                        }
                        recordCombatAttack(minecraft, current, entity.entityType(), currentTick);
                        combat.sent++;
                        minecraft.gameMode.attack(minecraft.player, current);
                        minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                    }
                    return new EntityRemovalAttackSample(mayAttack, false, currentTick);
                });
                started |= sample.attackSent();
                if (sample.attackSent()) {
                    lastAttackTick = sample.tick();
                }
                eatOwnedFoodForCombatRecovery(combat, deadlineNanos);
                if (combat.gaveUp) {
                    outcome = "gave_up_stuck";
                    return false;
                }
                if (sample.removed()) {
                    outcome = "removed";
                    return started;
                }
                if (executor.callOnClientThread(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    return minecraft.player != null && minecraft.player.getHealth() <= 0.0F;
                })) {
                    outcome = "player_died";
                    return false;
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    return false;
                }
            }
        } finally {
            combatLedger.engagement("attack", entity.entityType(), entity.entityId(), combat.sent,
                combat.withheldSummary(), outcome);
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    stopCombatMotion(minecraft);
                }
                return null;
            });
        }
    }

    @Override
    public List<ScenarioCombatRecord> drainCombatTelemetry() {
        return combatLedger.drain();
    }

    @Override
    public boolean drainHungerBySprinting(Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        List<int[]> waypoints = executor.callOnClientThread(
            MinecraftScenarioClient::safeHungerDrainWaypointsOnClientThread
        );
        if (waypoints.isEmpty()) {
            return false;
        }
        int nextWaypoint = 0;
        try {
            while (true) {
                boolean foodBelowFull = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    return minecraft.player.getFoodData().getFoodLevel() < 20;
                });
                if (foodBelowFull) {
                    return true;
                }

                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return false;
                }
                long legNanos = Math.min(remainingNanos, Duration.ofSeconds(12).toNanos());
                int[] waypoint = waypoints.get(nextWaypoint);
                boolean reached = approachPosition(
                    waypoint[0],
                    waypoint[1],
                    Duration.ofNanos(legNanos)
                );
                if (reached) {
                    nextWaypoint = (nextWaypoint + 1) % waypoints.size();
                } else {
                    waypoints.remove(nextWaypoint);
                    if (waypoints.isEmpty()) {
                        return false;
                    }
                    nextWaypoint %= waypoints.size();
                }
            }
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyJump.setDown(false);
                }
                return null;
            });
        }
    }

    @Override
    public ScenarioFoodUseResult eatSelectedFood(String itemId, int itemCountBefore, Duration timeout)
        throws Exception {
        FoodUseSample start = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.options.keyUse.setDown(true);
            InteractionResult result = minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
            return new FoodUseSample(
                !"PASS".equals(result.toString()),
                minecraft.player.getFoodData().getFoodLevel(),
                inventoryCountOnClientThread(itemId)
            );
        });
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        FoodUseSample latest = start;
        try {
            do {
                long observedVersion = ClientStateEvents.tickVersion();
                latest = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    minecraft.options.keyUse.setDown(true);
                    return new FoodUseSample(
                        minecraft.player.isUsingItem(),
                        minecraft.player.getFoodData().getFoodLevel(),
                        inventoryCountOnClientThread(itemId)
                    );
                });
                if (latest.foodLevel() > start.foodLevel() && latest.itemCount() < itemCountBefore) {
                    return new ScenarioFoodUseResult(
                        start.started(),
                        start.foodLevel(),
                        latest.foodLevel(),
                        itemCountBefore,
                        latest.itemCount()
                    );
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            return new ScenarioFoodUseResult(
                start.started(),
                start.foodLevel(),
                latest.foodLevel(),
                itemCountBefore,
                latest.itemCount()
            );
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUse.setDown(false);
                }
                return null;
            });
        }
    }

    @Override
    public ScenarioShieldBlockResult blockAttackWithSelectedShield(String itemId, Duration timeout)
        throws Exception {
        ShieldBlockSample initial = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            ItemStack selected = minecraft.player.getInventory().getSelectedItem();
            float health = minecraft.player.getHealth();
            long shieldBlockVersion = ClientStateEvents.shieldBlockVersion();
            if (!ScenarioContainerActions.itemStackMatches(selected, itemId, 1)) {
                return new ShieldBlockSample(false, health, selected.getDamageValue(), shieldBlockVersion);
            }
            minecraft.options.keyUse.setDown(true);
            InteractionResult result = minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
            boolean started = !"PASS".equals(result.toString()) || minecraft.player.isUsingItem();
            return new ShieldBlockSample(started, health, selected.getDamageValue(), shieldBlockVersion);
        });
        if (!initial.useStarted()) {
            return new ScenarioShieldBlockResult(
                false,
                false,
                initial.health(),
                initial.health(),
                initial.shieldDamage(),
                initial.shieldDamage()
            );
        }

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        try {
            do {
                long observedVersion = ClientStateEvents.version();
                ShieldBlockSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    minecraft.options.keyUse.setDown(true);
                    ItemStack selected = minecraft.player.getInventory().getSelectedItem();
                    return new ShieldBlockSample(
                        ScenarioContainerActions.itemStackMatches(selected, itemId, 1) && minecraft.player.isUsingItem(),
                        minecraft.player.getHealth(),
                        selected.getDamageValue(),
                        ClientStateEvents.shieldBlockVersion()
                    );
                });
                boolean blockedAttackObserved = sample.shieldBlockVersion() > initial.shieldBlockVersion();
                if (blockedAttackObserved || sample.health() < initial.health() || !sample.useStarted()) {
                    return new ScenarioShieldBlockResult(
                        true,
                        blockedAttackObserved,
                        initial.health(),
                        sample.health(),
                        initial.shieldDamage(),
                        sample.shieldDamage()
                    );
                }
                if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                    return new ScenarioShieldBlockResult(
                        true,
                        false,
                        initial.health(),
                        sample.health(),
                        initial.shieldDamage(),
                        sample.shieldDamage()
                    );
                }
            } while (true);
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUse.setDown(false);
                }
                return null;
            });
        }
    }

    @Override
    public boolean quickEquipSelectedArmor(String itemId, String armorSlot, Duration duration)
        throws Exception {
        int armorMenuSlot = armorMenuSlot(armorSlot);
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(
                () -> ScenarioContainerActions.containerSlotMatchesOnClientThread(armorMenuSlot, itemId, 1)
            );
            if (finalSample) {
                return true;
            }
            executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                if (minecraft.gui.screen() != null) {
                    minecraft.player.closeContainer();
                    minecraft.gui.setScreen(null);
                }
                AbstractContainerMenu menu = minecraft.player.containerMenu;
                if (
                    armorMenuSlot < menu.slots.size()
                        && ScenarioContainerActions.itemStackMatches(menu.getSlot(armorMenuSlot).getItem(), itemId, 1)
                ) {
                    return null;
                }
                if (!selectedItemOnClientThread().matches(itemId, 1)) {
                    return null;
                }
                int sourceSlot = ScenarioContainerActions.findMenuSlotWithItem(menu, itemId, 1, armorMenuSlot);
                if (sourceSlot < 0) {
                    return null;
                }
                minecraft.gameMode.handleContainerInput(
                    menu.containerId,
                    sourceSlot,
                    0,
                    ContainerInput.QUICK_MOVE,
                    minecraft.player
                );
                return null;
            });
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return executor.callOnClientThread(
            () -> ScenarioContainerActions.containerSlotMatchesOnClientThread(armorMenuSlot, itemId, 1)
        );
    }

    @Override
    public ScenarioHeldItem equippedArmor(String armorSlot) throws Exception {
        int armorMenuSlot = armorMenuSlot(armorSlot);
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            if (armorMenuSlot >= menu.slots.size()) {
                return new ScenarioHeldItem("minecraft:air", 0);
            }
            return heldItemFromStack(menu.getSlot(armorMenuSlot).getItem());
        });
    }

    @Override
    public ScenarioWaterObservation waterObservation() throws Exception {
        return executor.callOnClientThread(MinecraftScenarioClient::waterObservationOnClientThread);
    }

    @Override
    public void setView(float yaw, float pitch) throws Exception {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90.0F || pitch > 90.0F) {
            throw new IllegalArgumentException("view angles must be finite and pitch must be -90..90");
        }
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
            return null;
        });
    }

    @Override
    public void pressInputs(List<String> inputs, int ticks, Duration timeout) throws Exception {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(timeout, "timeout");
        if (inputs.isEmpty() || inputs.size() > 4) {
            throw new IllegalArgumentException("water input probe requires 1..4 inputs");
        }
        if (ticks < 1 || ticks > 255) {
            throw new IllegalArgumentException("water input ticks must be 1..255");
        }
        for (String input : inputs) {
            if (!List.of("forward", "back", "left", "right", "jump", "sneak", "sprint").contains(input)) {
                throw new IllegalArgumentException("unsupported scenario input: " + input);
            }
        }

        executor.callOnClientThread(() -> {
            setScenarioInputs(requireInPlay(), inputs, true);
            return null;
        });
        try {
            if (!waitForTicks(ticks, timeout)) {
                throw new TimeoutException("client did not advance " + ticks + " ticks for water input probe");
            }
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    setScenarioInputs(minecraft, inputs, false);
                }
                return null;
            });
        }
    }

    @Override
    public boolean teleportTo(double x, double y, double z, Duration timeout) throws Exception {
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.getConnection().sendCommand(String.format(
                Locale.ROOT,
                "tp %.3f %.3f %.3f",
                x,
                y,
                z
            ));
            return null;
        });

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                double dx = minecraft.player.getX() - x;
                double dy = minecraft.player.getY() - y;
                double dz = minecraft.player.getZ() - z;
                return dx * dx + dy * dy + dz * dz <= 0.75 * 0.75;
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

    @Override
    public boolean waitForDeathScreen(Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return minecraft.gui.screen() instanceof DeathScreen;
            });
            if (finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return false;
    }

    @Override
    public boolean performRespawn(Duration duration) throws Exception {
        long observedHealthVersion = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            if (minecraft.player.getHealth() > 0.0F && !(minecraft.gui.screen() instanceof DeathScreen)) {
                throw new IllegalStateException("client player is not dead");
            }
            long healthVersion = ClientStateEvents.healthVersion();
            minecraft.player.respawn();
            return healthVersion;
        });

        long deadlineNanos = System.nanoTime() + duration.toNanos();
        return waitForRespawnConfirmation(new RespawnWaitSource() {
            @Override
            public long healthVersion() {
                return ClientStateEvents.healthVersion();
            }

            @Override
            public long tickVersion() {
                return ClientStateEvents.tickVersion();
            }

            @Override
            public long stateVersion() {
                return ClientStateEvents.version();
            }

            @Override
            public boolean activePlayer() throws Exception {
                return executor.callOnClientThread(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    return minecraft.player != null
                        && minecraft.level != null
                        && minecraft.gui.screen() == null
                        && minecraft.player.getHealth() > 0.0F;
                });
            }

            @Override
            public boolean awaitHealthChange(long version, long deadline)
                throws InterruptedException {
                long remainingNanos = deadline - System.nanoTime();
                return remainingNanos > 0L && ClientStateEvents.awaitHealthChange(
                    version,
                    Duration.ofNanos(remainingNanos)
                );
            }

            @Override
            public boolean awaitTickOrStateChange(
                long tickVersion,
                long stateVersion,
                long deadline
            ) throws InterruptedException {
                long remainingNanos = deadline - System.nanoTime();
                return remainingNanos > 0L && ClientStateEvents.awaitTickOrStateChange(
                    tickVersion,
                    stateVersion,
                    Duration.ofNanos(remainingNanos)
                );
            }
        }, observedHealthVersion, deadlineNanos);
    }

    static boolean waitForRespawnConfirmation(
        RespawnWaitSource source,
        long observedHealthVersion,
        long deadlineNanos
    ) throws Exception {
        boolean authoritativeHealthApplied = false;
        while (true) {
            long observedTickVersion = source.tickVersion();
            long observedStateVersion = source.stateVersion();
            long currentHealthVersion = source.healthVersion();
            if (currentHealthVersion != observedHealthVersion) {
                authoritativeHealthApplied = true;
                observedHealthVersion = currentHealthVersion;
            }
            if (authoritativeHealthApplied && source.activePlayer()) {
                return true;
            }
            if (System.nanoTime() >= deadlineNanos) {
                return false;
            }
            boolean changed = authoritativeHealthApplied
                ? source.awaitTickOrStateChange(
                    observedTickVersion,
                    observedStateVersion,
                    deadlineNanos
                )
                : source.awaitHealthChange(observedHealthVersion, deadlineNanos);
            if (!changed) {
                return false;
            }
        }
    }

    private BlockBreakAutomation startBlockBreakAfterReset(
        ScenarioBlockTarget target,
        Direction face,
        long deadlineNanos
    ) throws Exception {
        BlockBreakAutomation action = new BlockBreakAutomation(target, face);
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            BlockBreakAutomation previous = ACTIVE_BLOCK_BREAK.getAndSet(null);
            if (previous != null && !previous.started.isDone()) {
                previous.started.completeExceptionally(
                    new IllegalStateException("block break replaced before it started")
                );
            }
            minecraft.options.keyAttack.setDown(false);
            minecraft.gameMode.stopDestroyBlock();
            ACTIVE_BLOCK_BREAK.set(action);
            return null;
        });
        if (!awaitBlockBreakStarted(action, deadlineNanos)) {
            stopBlockBreak(action);
            return null;
        }
        return action;
    }

    private static boolean awaitBlockBreakStarted(
        BlockBreakAutomation action,
        long deadlineNanos
    ) throws Exception {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return false;
        }
        try {
            action.started.get(remainingNanos, TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException error) {
            return false;
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException("block break failed before start", cause);
        }
    }

    private void stopBlockBreak(BlockBreakAutomation action) throws Exception {
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            ACTIVE_BLOCK_BREAK.compareAndSet(action, null);
            minecraft.options.keyAttack.setDown(false);
            minecraft.gameMode.stopDestroyBlock();
            return null;
        });
    }
    private void finishBlockBreak(BlockBreakAutomation action) throws Exception {
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            ACTIVE_BLOCK_BREAK.compareAndSet(action, null);
            minecraft.options.keyAttack.setDown(false);
            return null;
        });
    }


    @Override
    public ScenarioBreakResult breakBlock(
        ScenarioBlockTarget target,
        String expectedDropItemId,
        int expectedPickupCount,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        Direction face = direction(target.face());
        int initialInventoryCount = executor.callOnClientThread(
            () -> inventoryCountOnClientThread(expectedDropItemId)
        );
        BlockBreakAutomation action = startBlockBreakAfterReset(target, face, deadlineNanos);
        if (action == null) {
            return new ScenarioBreakResult(false, false, false, false, selectedItem());
        }

        boolean sawDrop = false;
        boolean becameAir = false;
        boolean breakStopped = false;
        ScenarioHeldItem selected = selectedItem();
        try {
            do {
                long observedVersion = ClientStateEvents.tickVersion();
                BreakSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    return new BreakSample(
                        minecraft.level.getBlockState(pos(target)).isAir(),
                        itemDropVisibleOnClientThread(expectedDropItemId, pos(target)),
                        selectedItemOnClientThread(),
                        clientTick(minecraft)
                    );
                });
                sawDrop |= sample.sawDrop();
                becameAir |= sample.becameAir();
                selected = sample.selectedItem();
                if (becameAir && !breakStopped) {
                    breakStopped = true;
                    finishBlockBreak(action);
                }
                if (becameAir) {
                    break;
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);

            if (!becameAir) {
                String detail = executor.callOnClientThread(action::diagnostic);
                return new ScenarioBreakResult(true, false, sawDrop, false, selected, detail);
            }

            InventoryPickupResult pickup = waitForInventoryPickup(
                expectedDropItemId,
                initialInventoryCount,
                expectedPickupCount,
                pos(target),
                deadlineNanos
            );
            sawDrop |= pickup.sawDrop();
            selected = selectedItem();
            return new ScenarioBreakResult(
                true,
                true,
                sawDrop,
                pickup.confirmed(),
                selected,
                pickup.detail()
            );
        } finally {
            if (!breakStopped) {
                stopBlockBreak(action);
            }
        }
    }

    private InventoryPickupResult waitForInventoryPickup(
        String itemId,
        int initialCount,
        int expectedPickupCount,
        BlockPos dropPosition,
        long deadlineNanos
    ) throws Exception {
        return waitForInventoryPickup(
            new InventoryPickupWaitSource() {
                @Override
                public long stateVersion() {
                    return ClientStateEvents.version();
                }

                @Override
                public InventoryPickupSample sample() throws Exception {
                    return executor.callOnClientThread(() -> new InventoryPickupSample(
                        inventoryCountOnClientThread(itemId),
                        itemDropVisibleOnClientThread(itemId, dropPosition)
                    ));
                }

                @Override
                public boolean awaitStateChange(long observedVersion, long deadlineNanos)
                    throws InterruptedException {
                    return awaitClientStateChange(observedVersion, deadlineNanos);
                }
            },
            initialCount,
            expectedPickupCount,
            deadlineNanos
        );
    }

    static InventoryPickupResult waitForInventoryPickup(
        InventoryPickupWaitSource source,
        int initialCount,
        int expectedPickupCount,
        long deadlineNanos
    ) throws Exception {
        int observedCount = initialCount;
        boolean sawDrop = false;
        do {
            long observedVersion = source.stateVersion();
            InventoryPickupSample sample = source.sample();
            observedCount = sample.inventoryCount();
            sawDrop |= sample.sawDrop();
            if (pickupCountReached(initialCount, observedCount, expectedPickupCount)) {
                return InventoryPickupResult.of(
                    true,
                    sawDrop,
                    initialCount,
                    observedCount,
                    expectedPickupCount
                );
            }

            if (!source.awaitStateChange(observedVersion, deadlineNanos)) {
                return InventoryPickupResult.of(
                    false,
                    sawDrop,
                    initialCount,
                    observedCount,
                    expectedPickupCount
                );
            }
        } while (true);
    }

    static boolean pickupCountReached(int initialCount, int observedCount, int expectedPickupCount) {
        return (long) observedCount >= (long) initialCount + expectedPickupCount;
    }

    static double pickupApproachCoordinate(double playerFeetY, double dropFeetY, double coordinate) {
        // A drop below the pickup box needs the lower column's footing, not
        // repeated forward/jump oscillation against its raised neighbour.
        return dropFeetY - playerFeetY <= -0.75 ? Math.floor(coordinate) + 0.5 : coordinate;
    }

    static boolean shallowStonePickup(double playerFeetY, double dropFeetY) {
        return Double.isFinite(dropFeetY) && dropFeetY >= playerFeetY - 1.25;
    }

    static boolean pillarPlacementConfirmed(
        String material, String observedBlock, int before, int after, boolean grounded, double feetY, int originY
    ) {
        return material.equals(observedBlock) && before > 0 && after == before - 1
            && grounded && feetY >= originY + 0.9;
    }

    static boolean pickupVolumeIntersects(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        double dropX, double dropY, double dropZ
    ) {
        // Vanilla player box inflated (1, .5, 1), against the .25 item box.
        return dropX + 0.125 > minX - 1.0 && dropX - 0.125 < maxX + 1.0
            && dropY + 0.25 > minY - 0.5 && dropY < maxY + 0.5
            && dropZ + 0.125 > minZ - 1.0 && dropZ - 0.125 < maxZ + 1.0;
    }

    @Override
    public ScenarioBreakResult breakBlockUntilDropVisible(
        ScenarioBlockTarget target,
        String expectedDropItemId,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        Direction face = direction(target.face());
        BlockBreakAutomation action = startBlockBreakAfterReset(target, face, deadlineNanos);
        if (action == null) {
            return new ScenarioBreakResult(false, false, false, false, selectedItem());
        }

        boolean sawDrop = false;
        boolean becameAir = false;
        boolean breakStopped = false;
        ScenarioHeldItem selected = selectedItem();
        try {
            do {
                long observedVersion = ClientStateEvents.tickVersion();
                BreakSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    return new BreakSample(
                        minecraft.level.getBlockState(pos(target)).isAir(),
                        itemDropVisibleOnClientThread(expectedDropItemId, pos(target)),
                        selectedItemOnClientThread(),
                        clientTick(minecraft)
                    );
                });
                sawDrop |= sample.sawDrop();
                becameAir |= sample.becameAir();
                selected = sample.selectedItem();
                if (becameAir && !breakStopped) {
                    breakStopped = true;
                    finishBlockBreak(action);
                    // The drop gets its own window from the removal instead of whatever is left
                    // of the break budget, which a slow first break can use up entirely.
                    deadlineNanos = Math.max(deadlineNanos, System.nanoTime() + timeout.toNanos());
                }
                if (becameAir && sawDrop) {
                    return new ScenarioBreakResult(true, true, true, false, selected);
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            if (!becameAir) {
                String detail = executor.callOnClientThread(action::diagnostic);
                return new ScenarioBreakResult(true, false, sawDrop, false, selected, detail);
            }
            return new ScenarioBreakResult(true, true, sawDrop, false, selected);
        } finally {
            if (!breakStopped) {
                stopBlockBreak(action);
            }
        }
    }

    @Override
    public boolean waitForVisibleItemDrop(String itemId, ScenarioBlockTarget near, Duration timeout)
        throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        boolean finalSample = false;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> itemDropVisibleOnClientThread(itemId, pos(near)));
            if (finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return false;
    }

    @Override
    public List<ScenarioItemDropIdentity> visibleItemDropIdentities(String itemId) throws Exception {
        return executor.callOnClientThread(() -> itemDropIdentitiesOnClientThread(itemId));
    }

    @Override
    public ScenarioItemDropIdentity waitForNewVisibleItemDropIdentity(
        String itemId,
        List<ScenarioItemDropIdentity> excludedIdentities,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        do {
            long observedVersion = ClientStateEvents.version();
            ScenarioItemDropIdentity identity = executor.callOnClientThread(
                () -> newItemDropIdentityOnClientThread(itemId, excludedIdentities)
            );
            if (identity != null) {
                return identity;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                return null;
            }
        } while (true);
    }

    @Override
    public ScenarioBreakResult collectVisibleItemDrop(
        ScenarioBlockTarget near,
        String expectedDropItemId,
        int expectedSelectedCount,
        Duration timeout
    ) throws Exception {
        return collectVisibleItemDrop(
            near,
            expectedDropItemId,
            null,
            executor.callOnClientThread(() -> inventoryCountOnClientThread(expectedDropItemId)),
            expectedSelectedCount,
            timeout
        );
    }

    @Override
    public ScenarioBreakResult collectVisibleItemDropFromInitialCount(
        ScenarioBlockTarget near,
        String expectedDropItemId,
        int initialInventoryCount,
        int expectedSelectedCount,
        Duration timeout
    ) throws Exception {
        return collectVisibleItemDrop(
            near,
            expectedDropItemId,
            null,
            initialInventoryCount,
            expectedSelectedCount,
            timeout
        );
    }

    @Override
    public ScenarioBreakResult collectVisibleItemDropByIdentity(
        ScenarioBlockTarget near,
        String expectedDropItemId,
        ScenarioItemDropIdentity expectedIdentity,
        int expectedSelectedCount,
        Duration timeout
    ) throws Exception {
        return collectVisibleItemDrop(
            near,
            expectedDropItemId,
            expectedIdentity,
            executor.callOnClientThread(() -> inventoryCountOnClientThread(expectedDropItemId)),
            expectedSelectedCount,
            timeout
        );
    }

    private ScenarioBreakResult collectVisibleItemDrop(
        ScenarioBlockTarget near,
        String expectedDropItemId,
        ScenarioItemDropIdentity expectedIdentity,
        int initialCount,
        int expectedSelectedCount,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        long inventoryDeadlineNanos = Long.MIN_VALUE;
        int detourDirection = 0;
        int preferredDetourDirection = 1;
        boolean sawDrop = false;
        boolean dropGone = false;
        boolean pickupRestored = false;
        boolean itemTakeObserved = false;
        ScenarioHeldItem selected = selectedItem();
        PickupSample latestSample = null;
        try {
            do {
                long observedTickVersion = ClientStateEvents.tickVersion();
                long observedStateVersion = ClientStateEvents.version();
                int currentDetourDirection = detourDirection;
                int currentPreferredDirection = preferredDetourDirection;
                PickupSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    Vec3 dropPosition = expectedIdentity == null
                        ? itemDropPositionOnClientThread(expectedDropItemId, pos(near))
                        : itemDropPositionOnClientThread(expectedDropItemId, expectedIdentity);
                    boolean identityTaken = expectedIdentity != null
                        && ClientStateEvents.consumeItemTakenBy(expectedIdentity, minecraft.player.getId());
                    if (expectedIdentity != null && dropPosition == null) {
                        minecraft.options.keyUp.setDown(false);
                        minecraft.options.keySprint.setDown(false);
                        minecraft.options.keyJump.setDown(false);
                        minecraft.options.keyLeft.setDown(false);
                        minecraft.options.keyRight.setDown(false);
                        Vec3 playerPosition = minecraft.player.position();
                        return new PickupSample(
                            false,
                            selectedItemOnClientThread(),
                            inventoryCountOnClientThread(expectedDropItemId),
                            playerPosition.x,
                            playerPosition.y,
                            playerPosition.z,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN,
                            minecraft.player.horizontalCollision,
                            minecraft.player.onGround(),
                            0,
                            clientTick(minecraft),
                            identityTaken,
                            false
                        );
                    }
                    Vec3 playerPosition = minecraft.player.position();
                    var playerBox = minecraft.player.getBoundingBox();
                    boolean inPickupVolume = dropPosition != null && pickupVolumeIntersects(
                        playerBox.minX, playerBox.minY, playerBox.minZ,
                        playerBox.maxX, playerBox.maxY, playerBox.maxZ,
                        dropPosition.x, dropPosition.y, dropPosition.z
                    );
                    boolean avoidDeepDrop = expectedIdentity == null && "minecraft:cobblestone".equals(expectedDropItemId)
                        && dropPosition != null && !inPickupVolume
                        && !shallowStonePickup(playerPosition.y, dropPosition.y);
                    Vec3 target = dropPosition == null
                        ? Vec3.atLowerCornerWithOffset(pos(near), 0.5, 0.5, 0.5)
                        : dropPosition.y - playerPosition.y <= -0.75
                            ? new Vec3(
                                pickupApproachCoordinate(playerPosition.y, dropPosition.y, dropPosition.x),
                                dropPosition.y,
                                pickupApproachCoordinate(playerPosition.y, dropPosition.y, dropPosition.z)
                            )
                            : dropPosition;
                    clearFoliageObstacleTowardOnClientThread(near);
                    minecraft.player.lookAt(
                        EntityAnchorArgument.Anchor.EYES,
                        target
                    );
                    MovementClearance clearance = MovementDetour.clearance(minecraft, target);
                    boolean stepUp = !inPickupVolume && minecraft.player.horizontalCollision
                        && clearance.raisedForward();
                    int nextDetourDirection = inPickupVolume ? 0 : MovementDetour.choose(
                        currentDetourDirection,
                        currentPreferredDirection,
                        minecraft.player.horizontalCollision && !stepUp,
                        clearance.direct(),
                        clearance.left(),
                        clearance.right()
                    );
                    boolean approaching = !avoidDeepDrop && (dropPosition != null
                        ? !inPickupVolume
                        : playerPosition.distanceToSqr(target) > PICKUP_APPROACH_DISTANCE_SQUARED);
                    minecraft.options.keySprint.setDown(approaching);
                    minecraft.options.keyUp.setDown(approaching && nextDetourDirection == 0);
                    minecraft.options.keyJump.setDown(approaching && (stepUp || minecraft.player.isInWater()));
                    minecraft.options.keyLeft.setDown(approaching && nextDetourDirection < 0);
                    minecraft.options.keyRight.setDown(approaching && nextDetourDirection > 0);
                    boolean visible = dropPosition != null;
                    return new PickupSample(
                        visible,
                        selectedItemOnClientThread(),
                        inventoryCountOnClientThread(expectedDropItemId),
                        playerPosition.x,
                        playerPosition.y,
                        playerPosition.z,
                        dropPosition == null ? Double.NaN : dropPosition.x,
                        dropPosition == null ? Double.NaN : dropPosition.y,
                        dropPosition == null ? Double.NaN : dropPosition.z,
                        dropPosition == null ? Double.NaN : playerPosition.distanceToSqr(dropPosition),
                        minecraft.player.horizontalCollision,
                        minecraft.player.onGround(),
                        nextDetourDirection,
                        clientTick(minecraft),
                        identityTaken,
                        avoidDeepDrop
                    );
                });
                latestSample = sample;
                itemTakeObserved |= sample.identityTaken();
                if (detourDirection == 0 && sample.detourDirection() != 0) {
                    preferredDetourDirection = -sample.detourDirection();
                }
                detourDirection = sample.detourDirection();
                sawDrop |= sample.visibleDrop();
                dropGone = !sample.visibleDrop();
                selected = sample.selectedItem();
                pickupRestored = expectedIdentity == null
                    ? sample.inventoryCount() >= initialCount + expectedSelectedCount
                    : itemTakeObserved
                        && sample.inventoryCount() == initialCount + expectedSelectedCount;
                if (dropGone && pickupRestored) {
                    return new ScenarioBreakResult(
                        true,
                        true,
                        sawDrop,
                        true,
                        selected,
                        pickupDetail(sample)
                    );
                }
                if (sample.avoidDeepDrop() && !pickupRestored) {
                    return new ScenarioBreakResult(true, false, true, false, selected,
                        "deep stone drop left for other reachable stone; " + pickupDetail(sample));
                }
                if (expectedIdentity != null && dropGone && !itemTakeObserved) {
                    return new ScenarioBreakResult(
                        true,
                        true,
                        sawDrop,
                        false,
                        selected,
                        pickupDetail(sample)
                    );
                }
                if (dropGone && (sawDrop || itemTakeObserved)) {
                    if (inventoryDeadlineNanos == Long.MIN_VALUE) {
                        inventoryDeadlineNanos = System.nanoTime() + PICKUP_INVENTORY_CONVERGENCE_NANOS;
                    }
                    if (!awaitClientStateChange(observedStateVersion, inventoryDeadlineNanos)) {
                        break;
                    }
                    continue;
                }
                if (!awaitClientTick(observedTickVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            return new ScenarioBreakResult(
                true,
                dropGone,
                sawDrop,
                pickupRestored,
                selected,
                pickupDetail(latestSample)
            );
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyJump.setDown(false);
                    minecraft.options.keyLeft.setDown(false);
                    minecraft.options.keyRight.setDown(false);
                }
                return null;
            });
        }
    }

    @Override
    public boolean waitForNoVisibleItemDrop(String itemId, ScenarioBlockTarget near, Duration timeout)
        throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        boolean finalSample = true;
        do {
            long observedVersion = ClientStateEvents.version();
            finalSample = executor.callOnClientThread(() -> itemDropVisibleOnClientThread(itemId, pos(near)));
            if (!finalSample) {
                return true;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return !finalSample;
    }

    @Override
    public ScenarioBlockTarget findBreakableBlock(List<String> blockIds, ScenarioReach reach) throws Exception {
        return executor.callOnClientThread(() -> {
            if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && blockIds.equals(List.of("minecraft:stone"))) {
                Minecraft minecraft = requireInPlay();
                BlockPos origin = minecraft.player.blockPosition();
                // Prefer walls at feet/head height over floor blocks leading down a natural shaft.
                for (int radius = 1; radius <= 4; radius++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                                continue;
                            }
                            for (int dy = 0; dy <= 3; dy++) {
                                ScenarioBlockTarget block = breakableBlockTarget(origin.offset(dx, dy, dz),
                                    minecraft.player.getEyePosition(), reach, blockIds);
                                if (block != null) {
                                    return block;
                                }
                            }
                        }
                    }
                }
            }
            ScenarioBlockTarget exposed = findBreakableBlockOnClientThread(blockIds, reach);
            if (exposed != null || reach != ScenarioReach.OUTSIDE_SURVIVAL_REACH
                || !blockIds.contains("minecraft:iron_ore")) {
                return exposed;
            }
            return loadedBuriedOreOnClientThread(blockIds);
        });
    }

    private static ScenarioBlockTarget loadedBuriedOreOnClientThread(List<String> blockIds) {
        Minecraft minecraft = requireInPlay();
        BlockPos origin = minecraft.player.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        boolean includeDeepslate = blockIds.contains("minecraft:deepslate_iron_ore");
        for (int dx = -32; dx <= 32; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                for (int dy = -64; dy <= 0; dy++) {
                    block.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!minecraft.level.isLoaded(block)) {
                        continue;
                    }
                    var kind = minecraft.level.getBlockState(block).getBlock();
                    if (kind != Blocks.IRON_ORE && (!includeDeepslate || kind != Blocks.DEEPSLATE_IRON_ORE)) {
                        continue;
                    }
                    double distance = block.distSqr(origin);
                    if (distance < nearestDistance && buriedOreColumnTopOnClientThread(block, origin.getY()) >= 0) {
                        nearestDistance = distance;
                        nearest = block.immutable();
                    }
                }
            }
        }
        return nearest == null ? null : new ScenarioBlockTarget(nearest.getX(), nearest.getY(), nearest.getZ(),
            "up", "loaded-buried-natural-ore", blockIdAt(nearest));
    }

    private static int buriedOreColumnTopOnClientThread(BlockPos ore, int referenceY) {
        Minecraft minecraft = requireInPlay();
        int top = -1;
        for (int y = referenceY + 8; y > ore.getY(); y--) {
            BlockPos block = new BlockPos(ore.getX(), y, ore.getZ());
            if (excavationSupportedOnClientThread(block) && isPlayerPassable(block.above())
                && isPlayerPassable(block.above(2))) {
                top = y + 1;
                break;
            }
        }
        if (top < 0 || !excavationSupportedOnClientThread(ore.below())
            || !minecraft.level.getFluidState(ore).isEmpty()
            || !CliffStaircase.shaftClearable(new CliffStaircase.Step(ore.getX(), ore.getY(), ore.getZ()), top,
                block -> excavatableOnClientThread(new BlockPos(block.x(), block.y(), block.z())))) {
            return -1; // No blind fall through a cave, liquid or unbreakable layer.
        }
        return top;
    }

    private boolean approachBuriedOre(ScenarioBlockTarget ore, Duration timeout) throws Exception {
        if (!approachColumn(ore.x(), ore.z(), timeout)
            || !centerOverColumn(ore.x(), ore.z(), timeout, ore.y() + 0.9)) {
            return false;
        }
        int surfaceY = executor.callOnClientThread(() -> requireInPlay().player.blockPosition().getY());
        if (executor.callOnClientThread(() -> buriedOreColumnTopOnClientThread(pos(ore), surfaceY)) != surfaceY) {
            return false;
        }
        buriedOreSurfaceY = Math.max(buriedOreSurfaceY, surfaceY);
        long deadlineNanos = System.nanoTime() + excavationBudgetNanos(timeout, surfaceY - ore.y());
        String tool = executor.callOnClientThread(() -> EXCAVATION_TOOLS.stream()
            .filter(item -> inventoryCountOnClientThread(item) > 0).findFirst().orElse(null));
        if (tool == null) {
            return false;
        }
        for (int y = surfaceY - 1; y > ore.y(); y--) {
            if (!clearExcavationBlock(new BlockPos(ore.x(), y, ore.z()), tool, deadlineNanos)) {
                return false;
            }
            long settleDeadline = Math.min(deadlineNanos, System.nanoTime() + Duration.ofSeconds(5).toNanos());
            boolean landed = false;
            while (System.nanoTime() < settleDeadline) {
                long version = ClientStateEvents.tickVersion();
                int footingY = y;
                if (executor.callOnClientThread(() -> requireInPlay().player.onGround()
                    && Math.abs(requireInPlay().player.getY() - footingY) < 0.1)) {
                    landed = true;
                    break;
                }
                if (!awaitClientTick(version, settleDeadline)) {
                    break;
                }
            }
            if (!landed) {
                return false;
            }
        }
        return executor.callOnClientThread(() -> breakableBlockTarget(pos(ore), requireInPlay().player.getEyePosition(),
            ScenarioReach.WITHIN_SURVIVAL_REACH, List.of(ore.blockId())) != null);
    }

    @Override
    public boolean approachBlock(ScenarioBlockTarget target, Duration timeout) throws Exception {
        if (!List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore").contains(target.blockId())
            && buriedOreSurfaceY >= 0) {
            if (!excavateStaircaseToSurface(buriedOreSurfaceY, 0, timeout)) {
                return false;
            }
            buriedOreSurfaceY = -1;
        }
        if (executor.callOnClientThread(() -> breakableBlockTarget(pos(target), requireInPlay().player.getEyePosition(),
            ScenarioReach.WITHIN_SURVIVAL_REACH, List.of(target.blockId())) != null)) {
            return true;
        }
        if ("loaded-buried-natural-ore".equals(target.label())) {
            return approachBuriedOre(target, timeout);
        }
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + approachBudgetNanos(timeout, false);
        long cliffDeadlineNanos = startedNanos + approachBudgetNanos(timeout, true);
        int detourDirection = 0;
        int preferredDetourDirection = 1;
        int cliffsCrossed = 0;
        try {
            do {
                if (cliffsCrossed < MAX_CLIFFS_PER_APPROACH) {
                    List<BlockPos> steps = executor.callOnClientThread(
                        () -> cliffStaircaseOnClientThread(Vec3.atCenterOf(pos(target)))
                    );
                    if (!steps.isEmpty()) {
                        deadlineNanos = Math.max(deadlineNanos, cliffDeadlineNanos);
                        if (!crossCliff(steps, deadlineNanos, target)) {
                            return false;
                        }
                        cliffsCrossed++;
                        detourDirection = 0;
                    }
                }
                long observedVersion = ClientStateEvents.tickVersion();
                int currentDetourDirection = detourDirection;
                int currentPreferredDirection = preferredDetourDirection;
                ApproachSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    if (minecraft.gui.screen() != null) {
                        minecraft.player.closeContainer();
                        minecraft.gui.setScreen(null);
                    }
                    Vec3 center = Vec3.atLowerCornerWithOffset(pos(target), 0.5, 0.5, 0.5);
                    minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, center);
                    clearFoliageObstacleTowardOnClientThread(target);
                    MovementClearance clearance = MovementDetour.clearance(minecraft, center);
                    boolean stepUp = minecraft.player.horizontalCollision
                        && clearance.raisedForward();
                    int nextDetourDirection = MovementDetour.choose(
                        currentDetourDirection,
                        currentPreferredDirection,
                        minecraft.player.horizontalCollision && !stepUp,
                        clearance.direct(),
                        clearance.left(),
                        clearance.right()
                    );
                    minecraft.options.keySprint.setDown(true);
                    minecraft.options.keyUp.setDown(nextDetourDirection == 0);
                    minecraft.options.keyJump.setDown(stepUp || minecraft.player.isInWater());
                    minecraft.options.keyLeft.setDown(nextDetourDirection < 0);
                    minecraft.options.keyRight.setDown(nextDetourDirection > 0);
                    boolean targetInReach = breakableBlockTarget(
                        pos(target),
                        minecraft.player.getEyePosition(),
                        ScenarioReach.WITHIN_SURVIVAL_REACH,
                        List.of(target.blockId())
                    ) != null;
                    return new ApproachSample(targetInReach, nextDetourDirection);
                });
                if (detourDirection == 0 && sample.detourDirection() != 0) {
                    preferredDetourDirection = -sample.detourDirection();
                }
                detourDirection = sample.detourDirection();
                if (sample.inReach()) {
                    return true;
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            return executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                boolean inReach = breakableBlockTarget(
                    pos(target),
                    minecraft.player.getEyePosition(),
                    ScenarioReach.WITHIN_SURVIVAL_REACH,
                    List.of(target.blockId())
                ) != null;
                return inReach;
            });
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyJump.setDown(false);
                    minecraft.options.keyLeft.setDown(false);
                    minecraft.options.keyRight.setDown(false);
                }
                return null;
            });
        }
    }

    @Override
    public boolean approachPosition(int x, int z, Duration timeout) throws Exception {
        return approachPosition(x, z, timeout, POSITION_APPROACH_DISTANCE_SQUARED, Double.NEGATIVE_INFINITY, true);
    }

    @Override
    public boolean approachColumn(int x, int z, Duration timeout) throws Exception {
        return approachPosition(x, z, timeout, 0.04, Double.NEGATIVE_INFINITY, true);
    }

    private boolean approachPosition(
        int x, int z, Duration timeout, double distanceSquared, double minimumY, boolean allowExcavation
    ) throws Exception {
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + approachBudgetNanos(timeout, false);
        long cliffDeadlineNanos = startedNanos + approachBudgetNanos(timeout, true);
        Vec3 target = new Vec3(x + 0.5, 0.0, z + 0.5);
        int detourDirection = 0;
        int preferredDetourDirection = 1;
        int cliffsCrossed = 0;
        try {
            do {
                if (allowExcavation && cliffsCrossed < MAX_CLIFFS_PER_APPROACH) {
                    List<BlockPos> steps = executor.callOnClientThread(() -> cliffStaircaseOnClientThread(target));
                    if (!steps.isEmpty()) {
                        deadlineNanos = Math.max(deadlineNanos, cliffDeadlineNanos);
                        if (!crossCliff(steps, deadlineNanos, null)) {
                            return false;
                        }
                        cliffsCrossed++;
                        detourDirection = 0;
                    }
                }
                long observedVersion = ClientStateEvents.tickVersion();
                int currentDetourDirection = detourDirection;
                int currentPreferredDirection = preferredDetourDirection;
                ApproachSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    if (minecraft.gui.screen() != null) {
                        minecraft.player.closeContainer();
                        minecraft.gui.setScreen(null);
                    }
                    Vec3 playerPosition = minecraft.player.position();
                    Vec3 horizontalTarget = new Vec3(target.x, playerPosition.y, target.z);
                    minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, horizontalTarget);
                    MovementClearance clearance = MovementDetour.clearance(minecraft, horizontalTarget);
                    boolean stepUp = minecraft.player.horizontalCollision
                        && clearance.raisedForward();
                    int nextDetourDirection = MovementDetour.choose(
                        currentDetourDirection,
                        currentPreferredDirection,
                        minecraft.player.horizontalCollision && !stepUp,
                        clearance.direct(),
                        clearance.left(),
                        clearance.right()
                    );
                    boolean reachedPosition = positionReached(
                        minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                        target.x, target.z, distanceSquared, minimumY
                    );
                    minecraft.options.keySprint.setDown(!reachedPosition && distanceSquared >= 0.04);
                    minecraft.options.keyUp.setDown(!reachedPosition && nextDetourDirection == 0);
                    // Held jump keeps a swimming player at the surface instead of sinking to drown.
                    minecraft.options.keyJump.setDown(!reachedPosition && (stepUp || minecraft.player.isInWater()));
                    minecraft.options.keyLeft.setDown(!reachedPosition && nextDetourDirection < 0);
                    minecraft.options.keyRight.setDown(!reachedPosition && nextDetourDirection > 0);
                    return new ApproachSample(
                        positionArrivalConfirmed(reachedPosition, minimumY, minecraft.player.onGround()),
                        nextDetourDirection
                    );
                });
                if (detourDirection == 0 && sample.detourDirection() != 0) {
                    preferredDetourDirection = -sample.detourDirection();
                }
                detourDirection = sample.detourDirection();
                if (sample.inReach()) {
                    return true;
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            return executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return positionArrivalConfirmed(positionReached(
                    minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                    target.x, target.z, distanceSquared, minimumY
                ), minimumY, minecraft.player.onGround());
            });
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyJump.setDown(false);
                    minecraft.options.keyLeft.setDown(false);
                    minecraft.options.keyRight.setDown(false);
                }
                return null;
            });
        }
    }
    static boolean positionReached(
        double playerX, double playerY, double playerZ,
        double targetX, double targetZ, double distanceSquared, double minimumY
    ) {
        double deltaX = playerX - targetX;
        double deltaZ = playerZ - targetZ;
        return playerY >= minimumY && deltaX * deltaX + deltaZ * deltaZ <= distanceSquared;
    }

    static boolean positionArrivalConfirmed(boolean reachedPosition, double minimumY, boolean grounded) {
        return reachedPosition && (!Double.isFinite(minimumY) || grounded);
    }

    static long approachBudgetNanos(Duration timeout, boolean excavating) {
        return excavating ? Math.max(timeout.toNanos(), CLIFF_APPROACH_TIMEOUT.toNanos()) : timeout.toNanos();
    }

    private static List<BlockPos> cliffStaircaseOnClientThread(Vec3 target) {
        Minecraft minecraft = requireInPlay();
        if (!minecraft.player.onGround() || !minecraft.player.horizontalCollision
            || MovementDetour.clearance(minecraft, target).raisedForward()) {
            return List.of();
        }
        Direction direction = CliffStaircase.toward(
            target.x - minecraft.player.getX(), target.z - minecraft.player.getZ()
        );
        BlockPos origin = minecraft.player.blockPosition();
        return CliffStaircase.route(
            new CliffStaircase.Step(origin.getX(), origin.getY(), origin.getZ()), direction,
            step -> excavationSupportedOnClientThread(new BlockPos(step.x(), step.y(), step.z())),
            step -> isPlayerPassable(new BlockPos(step.x(), step.y(), step.z())),
            step -> excavatableOnClientThread(new BlockPos(step.x(), step.y(), step.z()))
        ).stream().map(step -> new BlockPos(step.x(), step.y(), step.z())).toList();
    }

    private static boolean excavationSupportedOnClientThread(BlockPos block) {
        Minecraft minecraft = requireInPlay();
        return minecraft.level.isLoaded(block) && minecraft.level.getFluidState(block).isEmpty()
            && !minecraft.level.getBlockState(block).getCollisionShape(minecraft.level, block).isEmpty();
    }

    private static boolean excavatableOnClientThread(BlockPos block) {
        Minecraft minecraft = requireInPlay();
        return minecraft.level.isLoaded(block) && minecraft.level.getFluidState(block).isEmpty()
            && EXCAVATION_BLOCKS.contains(blockIdAt(block));
    }

    private boolean crossCliff(List<BlockPos> steps, long deadlineNanos, ScenarioBlockTarget approachTarget) throws Exception {
        ScenarioHeldItem previous = selectedItem();
        executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keySprint.setDown(false);
            minecraft.options.keyJump.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            return null;
        });
        try {
            return excavateSteps(steps, deadlineNanos, approachTarget);
        } finally {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (previous.count() > 0 && remainingNanos > 0L) {
                selectHotbarItem(previous.itemId(), 1, Duration.ofNanos(remainingNanos));
            }
        }
    }


    @Override
    public boolean excavateStaircaseToSurface(int surfaceY, int minimumCobblestoneCount, Duration timeout) throws Exception {
        int depth = executor.callOnClientThread(() -> Math.max(0, surfaceY - requireInPlay().player.blockPosition().getY()));
        long deadlineNanos = System.nanoTime() + excavationBudgetNanos(timeout, depth);
        ScenarioHeldItem previous = selectedItem();
        try {
            for (int rise = 0; rise < depth; ) {
                if (executor.callOnClientThread(() -> requireInPlay().player.onGround()
                    && requireInPlay().player.getY() >= surfaceY - 0.1)) {
                    return true;
                }
                boolean directPillar = executor.callOnClientThread(() -> {
                    BlockPos origin = requireInPlay().player.blockPosition();
                    return CliffStaircase.pillarFits(
                        new CliffStaircase.Step(origin.getX(), origin.getY(), origin.getZ()), surfaceY,
                        availablePillarBlocks(inventoryCountOnClientThread("minecraft:dirt"),
                            inventoryCountOnClientThread("minecraft:cobblestone"), minimumCobblestoneCount),
                        block -> isPlayerPassable(new BlockPos(block.x(), block.y(), block.z()))
                    );
                });
                if (directPillar) {
                    if (!pillarOneBlock(minimumCobblestoneCount, deadlineNanos)) {
                        return false;
                    }
                    rise++;
                    continue;
                }
                List<BlockPos> steps = executor.callOnClientThread(() -> excavationStaircaseOnClientThread(surfaceY));
                if (!steps.isEmpty()) {
                    if (!crossCliff(steps, deadlineNanos, null)) {
                        return false;
                    }
                    rise += steps.size();
                } else {
                    if (!pillarOneBlock(minimumCobblestoneCount, deadlineNanos)) {
                        return false;
                    }
                    rise++;
                }
            }
            return executor.callOnClientThread(() -> requireInPlay().player.onGround()
                && requireInPlay().player.getY() >= surfaceY - 0.1);
        } finally {
            long remaining = deadlineNanos - System.nanoTime();
            if (previous.count() > 0 && remaining > 0) {
                selectHotbarItem(previous.itemId(), 1, Duration.ofNanos(remaining));
            }
        }
    }

    static long excavationBudgetNanos(Duration timeout, int depth) {
        // Three forward blocks, up to four takeoff ceiling blocks, plus actual arrival per rise.
        return Math.max(timeout.toNanos(), Duration.ofSeconds(50L * Math.max(0, depth)).toNanos());
    }

    static boolean groundedInsideColumn(
        double minX, double minZ, double maxX, double maxZ, double velocityX, double velocityZ,
        int x, int z, boolean grounded
    ) {
        return grounded && minX >= x + 0.02 && maxX <= x + 0.98 && minZ >= z + 0.02 && maxZ <= z + 0.98
            && velocityX * velocityX + velocityZ * velocityZ < 0.0001;
    }

    private boolean centerOverColumn(int x, int z, Duration timeout, double minimumY) throws Exception {
        long deadlineNanos = System.nanoTime() + Math.min(timeout.toNanos(), Duration.ofSeconds(5).toNanos());
        boolean previousSneak = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            boolean previous = minecraft.options.keyShift.isDown();
            minecraft.options.keyShift.setDown(true);
            return previous;
        });
        try {
            while (System.nanoTime() < deadlineNanos) {
                long version = ClientStateEvents.tickVersion();
                if (executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    Vec3 target = new Vec3(x + 0.5, minecraft.player.getEyeY(), z + 0.5);
                    minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
                    boolean reached = positionReached(minecraft.player.getX(), minecraft.player.getY(),
                        minecraft.player.getZ(), x + 0.5, z + 0.5, 0.0064, minimumY);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyUp.setDown(!reached);
                    minecraft.options.keyLeft.setDown(false);
                    minecraft.options.keyRight.setDown(false);
                    minecraft.options.keyJump.setDown(!reached && (minecraft.player.isInWater()
                        || minecraft.player.horizontalCollision && MovementDetour.clearance(minecraft, target).raisedForward()));
                    var box = minecraft.player.getBoundingBox();
                    Vec3 velocity = minecraft.player.getDeltaMovement();
                    return reached && groundedInsideColumn(box.minX, box.minZ, box.maxX, box.maxZ,
                        velocity.x, velocity.z, x, z, minecraft.player.onGround());
                })) {
                    return true;
                }
                if (!awaitClientTick(version, deadlineNanos)) {
                    break;
                }
            }
            return false;
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                minecraft.options.keyUp.setDown(false);
                minecraft.options.keySprint.setDown(false);
                minecraft.options.keyJump.setDown(false);
                minecraft.options.keyLeft.setDown(false);
                minecraft.options.keyRight.setDown(false);
                minecraft.options.keyShift.setDown(previousSneak);
                return null;
            });
        }
    }

    static int availablePillarBlocks(int dirtCount, int cobblestoneCount, int minimumCobblestoneCount) {
        return dirtCount + Math.max(0, cobblestoneCount - minimumCobblestoneCount);
    }

    private boolean pillarOneBlock(int minimumCobblestoneCount, long deadlineNanos) throws Exception {
        BlockPos origin = executor.callOnClientThread(() -> requireInPlay().player.blockPosition());
        if (!centerOverColumn(origin.getX(), origin.getZ(),
            Duration.ofNanos(Math.max(1, deadlineNanos - System.nanoTime())), origin.getY() - 0.1)) {
            System.out.println("Solaris excavation pillar: column_center_failed origin=" + origin);
            return false;
        }
        String tool = executor.callOnClientThread(() -> EXCAVATION_TOOLS.stream()
            .filter(item -> inventoryCountOnClientThread(item) > 0).findFirst().orElse(null));
        if (tool == null) {
            return false;
        }
        for (int height = 2; height <= EXCAVATION_HEADROOM; height++) {
            BlockPos ceiling = origin.above(height);
            if (executor.callOnClientThread(() -> isPlayerPassable(ceiling))) {
                continue;
            }
            String drop = executor.callOnClientThread(() -> excavationDrop(blockIdAt(ceiling)));
            int beforeCeilingPickup = inventoryCount(drop);
            if (!clearExcavationBlock(ceiling, tool, deadlineNanos)
                || !waitForInventoryPickup(drop, beforeCeilingPickup, 1, ceiling, deadlineNanos).confirmed()) {
                return false;
            }
        }
        String material = executor.callOnClientThread(() -> List.of("minecraft:dirt", "minecraft:cobblestone").stream()
            .filter(item -> inventoryCountOnClientThread(item) > ("minecraft:cobblestone".equals(item)
                ? minimumCobblestoneCount : 0)).findFirst().orElse(null));
        if (material == null || deadlineNanos <= System.nanoTime()) {
            return false;
        }
        int before = inventoryCount(material);
        ScenarioHeldItem held = selectHotbarItem(material, 1, Duration.ofNanos(deadlineNanos - System.nanoTime()));
        if (!held.matches(material, 1)) {
            return false;
        }
        boolean placed = false;
        long jumpDeadlineNanos = Math.min(deadlineNanos, System.nanoTime() + Duration.ofSeconds(5).toNanos());
        try {
            while (System.nanoTime() < jumpDeadlineNanos) {
                long version = ClientStateEvents.tickVersion();
                boolean ready = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyLeft.setDown(false);
                    minecraft.options.keyRight.setDown(false);
                    minecraft.options.keyJump.setDown(true);
                    return minecraft.player.getY() >= origin.getY() + 1.01;
                });
                if (ready) {
                    ScenarioBlockTarget clicked = new ScenarioBlockTarget(origin.getX(), origin.getY() - 1,
                        origin.getZ(), "up", "earned-egress-pillar", executor.callOnClientThread(() -> blockIdAt(origin.below())));
                    useItemOn(clicked, held);
                    placed = true;
                    break;
                }
                if (!awaitClientTick(version, jumpDeadlineNanos)) {
                    return false;
                }
            }
        } finally {
            executor.callOnClientThread(() -> {
                requireInPlay().options.keyJump.setDown(false);
                return null;
            });
        }
        if (!placed) {
            return false;
        }
        long settleDeadlineNanos = Math.min(deadlineNanos, System.nanoTime() + Duration.ofSeconds(5).toNanos());
        while (System.nanoTime() < settleDeadlineNanos) {
            long version = ClientStateEvents.tickVersion();
            if (executor.callOnClientThread(() -> pillarPlacementConfirmed(
                material, blockIdAt(origin), before, inventoryCountOnClientThread(material),
                requireInPlay().player.onGround(), requireInPlay().player.getY(), origin.getY()))) {
                return true;
            }
            if (!awaitClientTick(version, settleDeadlineNanos)) {
                return false;
            }
        }
        return false;
    }

    private boolean clearExcavationBlock(BlockPos block, String tool, long deadlineNanos) throws Exception {
        if (executor.callOnClientThread(() -> isPlayerPassable(block))) {
            return true;
        }
        ScenarioBlockTarget target = executor.callOnClientThread(() -> breakableBlockTarget(
            block, requireInPlay().player.getEyePosition(), ScenarioReach.WITHIN_SURVIVAL_REACH, EXCAVATION_BLOCKS
        ));
        long remaining = deadlineNanos - System.nanoTime();
        if (target == null || remaining <= 0
            || !selectHotbarItem(tool, 1, Duration.ofNanos(remaining)).matches(tool, 1)) {
            System.out.println("Solaris excavation clear: target_unavailable block=" + block
                + " target=" + target + " remaining_nanos=" + remaining);
            return false;
        }
        remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            return false;
        }
        String drop = excavationDrop(target.blockId());
        ScenarioBreakResult broke = breakBlockUntilDropVisible(target, drop, Duration.ofNanos(remaining));
        boolean cleared = broke.started() && broke.becameAir() && broke.sawDrop();
        if (!cleared) {
            System.out.println("Solaris excavation clear: break_failed target=" + target + " result=" + broke);
        }
        return cleared;
    }

    static String excavationDrop(String blockId) {
        return switch (blockId) {
            case "minecraft:stone" -> "minecraft:cobblestone";
            case "minecraft:deepslate" -> "minecraft:cobbled_deepslate";
            case "minecraft:grass_block" -> "minecraft:dirt";
            default -> blockId;
        };
    }

    private boolean excavateSteps(List<BlockPos> steps, long deadlineNanos, ScenarioBlockTarget approachTarget) throws Exception {
        String tool = executor.callOnClientThread(() -> EXCAVATION_TOOLS.stream()
            .filter(item -> inventoryCountOnClientThread(item) > 0).findFirst().orElse(null));
        if (tool == null) {
            return false;
        }
        for (BlockPos step : steps) {
            if (approachTarget != null && executor.callOnClientThread(() -> breakableBlockTarget(
                pos(approachTarget), requireInPlay().player.getEyePosition(),
                ScenarioReach.WITHIN_SURVIVAL_REACH, List.of(approachTarget.blockId())
            ) != null)) {
                return true;
            }
            List<BlockPos> takeoff = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                var box = minecraft.player.getBoundingBox();
                return CliffStaircase.takeoffClearance(box.minX, box.minZ, box.maxX, box.maxZ,
                    minecraft.player.getY()).stream()
                    .map(block -> new BlockPos(block.x(), block.y(), block.z())).toList();
            });
            for (BlockPos ceiling : takeoff) {
                if (!clearExcavationBlock(ceiling, tool, deadlineNanos)) {
                    return false;
                }
            }
            for (int headroom = 0; headroom < EXCAVATION_HEADROOM; headroom++) {
                BlockPos block = step.above(headroom);
                if (!clearExcavationBlock(block, tool, deadlineNanos)) {
                    return false;
                }
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L
                || !approachPosition(
                    step.getX(), step.getZ(), Duration.ofNanos(remainingNanos), 0.25, step.getY() - 0.1, false
                )) {
                System.out.println("Solaris excavation staircase: arrival_failed step=" + step
                    + " remaining_nanos=" + remainingNanos);
                return false;
            }
        }
        return true;
    }

    private static List<BlockPos> excavationStaircaseOnClientThread(int surfaceY) {
        Minecraft minecraft = requireInPlay();
        BlockPos origin = minecraft.player.blockPosition();
        List<BlockPos> longest = List.of();
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            List<BlockPos> steps = CliffStaircase.toSurface(
                new CliffStaircase.Step(origin.getX(), origin.getY(), origin.getZ()), surfaceY, direction,
                step -> excavationSupportedOnClientThread(new BlockPos(step.x(), step.y(), step.z())),
                step -> isPlayerPassable(new BlockPos(step.x(), step.y(), step.z())),
                step -> excavatableOnClientThread(new BlockPos(step.x(), step.y(), step.z()))
            ).stream().map(step -> new BlockPos(step.x(), step.y(), step.z())).toList();
            if (steps.size() > longest.size()) {
                longest = steps;
            }
        }
        return longest;
    }

    @Override
    public ScenarioBlockTarget findLoadedBlockInColumn(int x, int z, List<String> blockIds) throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            if (!minecraft.level.hasChunk(x >> 4, z >> 4)) {
                return null;
            }
            Vec3 eye = minecraft.player.getEyePosition();
            for (int y = minecraft.level.getMaxY() - 1; y >= minecraft.level.getMinY(); y--) {
                BlockPos target = new BlockPos(x, y, z);
                if (!minecraft.level.isLoaded(target)) {
                    continue;
                }
                String blockId = blockIdAt(target);
                if (!blockIds.contains(blockId)) {
                    continue;
                }
                Direction face = closestAccessibleFace(target, eye);
                if (face == null) {
                    continue;
                }
                return new ScenarioBlockTarget(x, y, z, face.getName(), "loaded-column", blockId);
            }
            return null;
        });
    }

    @Override
    public boolean standOnBlockUntilDeath(ScenarioBlockTarget target, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        int detourDirection = 0;
        int preferredDetourDirection = 1;
        try {
            do {
                long observedVersion = ClientStateEvents.tickVersion();
                int currentDetourDirection = detourDirection;
                int currentPreferredDirection = preferredDetourDirection;
                HazardStandSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    if (minecraft.gui.screen() instanceof DeathScreen) {
                        return new HazardStandSample(true, 0);
                    }
                    if (minecraft.gui.screen() != null) {
                        minecraft.player.closeContainer();
                        minecraft.gui.setScreen(null);
                    }
                    BlockPos targetPos = pos(target);
                    Vec3 topCenter = Vec3.atLowerCornerWithOffset(targetPos, 0.5, 1.0, 0.5);
                    double dx = minecraft.player.getX() - topCenter.x;
                    double dz = minecraft.player.getZ() - topCenter.z;
                    double distanceSquared = dx * dx + dz * dz;
                    boolean highEnough = minecraft.player.getY() >= targetPos.getY() - 0.05;
                    boolean onTarget = distanceSquared <= 0.08 && highEnough;
                    minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, topCenter);
                    MovementClearance clearance = MovementDetour.clearance(minecraft, topCenter);
                    boolean stepUp = !onTarget
                        && minecraft.player.horizontalCollision
                        && clearance.raisedForward();
                    int nextDetourDirection = MovementDetour.choose(
                        currentDetourDirection,
                        currentPreferredDirection,
                        minecraft.player.horizontalCollision && !stepUp,
                        clearance.direct(),
                        clearance.left(),
                        clearance.right()
                    );
                    minecraft.options.keySprint.setDown(!onTarget);
                    minecraft.options.keyUp.setDown(!onTarget && nextDetourDirection == 0);
                    minecraft.options.keyJump.setDown(stepUp || minecraft.player.isInWater());
                    minecraft.options.keyLeft.setDown(!onTarget && nextDetourDirection < 0);
                    minecraft.options.keyRight.setDown(!onTarget && nextDetourDirection > 0);
                    return new HazardStandSample(false, nextDetourDirection);
                });
                if (detourDirection == 0 && sample.detourDirection() != 0) {
                    preferredDetourDirection = -sample.detourDirection();
                }
                detourDirection = sample.detourDirection();
                if (sample.dead()) {
                    return true;
                }
                if (!awaitClientTick(observedVersion, deadlineNanos)) {
                    break;
                }
            } while (true);
            return executor.callOnClientThread(() -> Minecraft.getInstance().gui.screen() instanceof DeathScreen);
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyJump.setDown(false);
                    minecraft.options.keyLeft.setDown(false);
                    minecraft.options.keyRight.setDown(false);
                }
                return null;
            });
        }
    }

    @Override
    public ScenarioHeldItem selectHotbarItem(String itemId, int count, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ScenarioHeldItem latest;
        do {
            long observedVersion = ClientStateEvents.version();
            HotbarSelectionAttempt attempt = executor.callOnClientThread(
                () -> selectHotbarItemOnClientThread(itemId, count)
            );
            latest = attempt.selectedItem();
            if (attempt.checkpoint() == null) {
                if (latest.matches(itemId, count)) {
                    return latest;
                }
                if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                    break;
                }
                continue;
            }

            HotbarSelectionResponse response = waitForAuthoritativeHotbarSelection(
                attempt,
                itemId,
                count,
                deadlineNanos
            );
            latest = response.selectedItem();
            if (response.confirmed()) {
                return latest;
            }
            if (!response.observed()) {
                break;
            }
        } while (true);
        return latest;
    }

    private HotbarSelectionResponse waitForAuthoritativeHotbarSelection(
        HotbarSelectionAttempt attempt,
        String itemId,
        int count,
        long deadlineNanos
    ) throws Exception {
        HotbarSelectionResponse latest;
        do {
            long observedVersion = ClientStateEvents.version();
            latest = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                AbstractContainerMenu menu = minecraft.player.containerMenu;
                ScenarioHeldItem selectedItem = selectedItemOnClientThread();
                boolean slotMatches = minecraft.player.getInventory().getSelectedSlot()
                        == attempt.targetHotbarSlot()
                    && ScenarioContainerActions.itemStackMatches(
                        minecraft.player.getInventory().getItem(attempt.targetHotbarSlot()),
                        itemId,
                        count
                    );
                boolean confirmed = ScenarioClient.authoritativeContainerUpdateMatches(
                    attempt.checkpoint().containerId(),
                    attempt.checkpoint().stateId(),
                    menu.containerId,
                    menu.getStateId(),
                    slotMatches
                );
                boolean observed = menu.containerId == attempt.checkpoint().containerId()
                    && menu.getStateId() != attempt.checkpoint().stateId();
                return new HotbarSelectionResponse(observed, confirmed, selectedItem);
            });
            if (latest.observed() || !awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return latest;
    }

    @Override
    public ScenarioBlockTarget dropSelectedItem(String itemId, int count, Duration timeout) throws Exception {
        int initialCount = executor.callOnClientThread(() -> inventoryCountOnClientThread(itemId));
        ScenarioBlockTarget target = executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            ScenarioHeldItem selected = selectedItemOnClientThread();
            if (!selected.matches(itemId, count)) {
                throw new IllegalStateException(
                    "selected item does not match drop request expected="
                        + itemId
                        + " x"
                        + count
                        + " selected="
                        + selected.itemId()
                        + " x"
                        + selected.count()
                );
            }
            BlockPos origin = minecraft.player.blockPosition();
            boolean dropped = minecraft.player.drop(
                minecraft.player.getMainHandItem(),
                false,
                Prediction.PREDICTED
            ) != null;
            minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            if (!dropped) {
                throw new IllegalStateException("client refused selected item drop for " + itemId);
            }
            return new ScenarioBlockTarget(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                "up",
                "selected-item-drop",
                itemId
            );
        });

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        int expectedCount = Math.max(0, initialCount - count);
        int latestCount;
        do {
            long observedVersion = ClientStateEvents.version();
            latestCount = executor.callOnClientThread(() -> inventoryCountOnClientThread(itemId));
            if (latestCount <= expectedCount) {
                return target;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return target;
    }

    @Override
    public ScenarioHeldItem selectedItem() throws Exception {
        return executor.callOnClientThread(MinecraftScenarioClient::selectedItemOnClientThread);
    }

    @Override
    public float playerHealth() throws Exception {
        return executor.callOnClientThread(() -> {
            Minecraft minecraft = requireInPlay();
            return minecraft.player.getHealth();
        });
    }

    @Override
    public float waitForPlayerHealthBelow(double health, Duration duration) throws Exception {
        long deadlineNanos = System.nanoTime() + duration.toNanos();
        float latest;
        do {
            long observedVersion = ClientStateEvents.version();
            latest = playerHealth();
            if (healthBelow(latest, health)) {
                return latest;
            }
            if (!awaitClientStateChange(observedVersion, deadlineNanos)) {
                break;
            }
        } while (true);
        return latest;
    }

    static boolean healthBelow(float observed, double threshold) {
        return observed < threshold;
    }

    static void selectHotbarSlotOnClientThread(int slot) {
        Minecraft minecraft = requireInPlay();
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("hotbar slot must be 0..8");
        }
        minecraft.player.getInventory().setSelectedSlot(slot);
        minecraft.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static HotbarSelectionAttempt selectHotbarItemOnClientThread(String itemId, int count) {
        Minecraft minecraft = requireInPlay();
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (ScenarioContainerActions.itemStackMatches(stack, itemId, count)) {
                selectHotbarSlotOnClientThread(slot);
                return new HotbarSelectionAttempt(selectedItemOnClientThread(), null, slot);
            }
        }
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int sourceSlot = ScenarioContainerActions.findMenuSlotWithItem(menu, itemId, count, -1);
        if (sourceSlot >= 0) {
            int targetHotbarSlot = preferredHotbarSwapSlotOnClientThread();
            ContainerUpdateCheckpoint checkpoint = new ContainerUpdateCheckpoint(
                menu.containerId,
                menu.getStateId()
            );
            minecraft.gameMode.handleContainerInput(
                menu.containerId,
                sourceSlot,
                targetHotbarSlot,
                ContainerInput.SWAP,
                minecraft.player
            );
            selectHotbarSlotOnClientThread(targetHotbarSlot);
            return new HotbarSelectionAttempt(
                selectedItemOnClientThread(),
                checkpoint,
                targetHotbarSlot
            );
        }
        return new HotbarSelectionAttempt(selectedItemOnClientThread(), null, -1);
    }

    private static int preferredHotbarSwapSlotOnClientThread() {
        Minecraft minecraft = requireInPlay();
        int selected = minecraft.player.getInventory().getSelectedSlot();
        if (minecraft.player.getInventory().getItem(selected).isEmpty()) {
            return selected;
        }
        for (int slot = 0; slot <= 8; slot++) {
            if (minecraft.player.getInventory().getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return selected;
    }

    static void lookAtBlockOnClientThread(ScenarioBlockTarget target) {
        Minecraft minecraft = requireInPlay();
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, hitResult(target).getLocation());
    }

    static ScenarioUseResult useItemOnClientThread(ScenarioBlockTarget target, String handName) {
        Minecraft minecraft = requireInPlay();
        BlockHitResult hit = hitResult(target);
        InteractionHand hand = interactionHand(handName);
        minecraft.hitResult = hit;
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, hit.getLocation());
        InteractionResult result = minecraft.gameMode.useItemOn(
            minecraft.player,
            hand,
            hit
        );
        minecraft.player.swing(hand, SwingAnimation.DEFAULT, true);
        return new ScenarioUseResult(result.toString());
    }





















    private static List<int[]> safeHungerDrainWaypointsOnClientThread() {
        Minecraft minecraft = requireInPlay();
        BlockPos origin = minecraft.player.blockPosition();
        List<int[]> waypoints = new ArrayList<>();
        if (!safeHungerDrainColumn(minecraft, origin)) {
            return waypoints;
        }
        for (Direction direction : HUNGER_DRAIN_DIRECTIONS) {
            BlockPos endpoint = safeHungerDrainEndpoint(minecraft, origin, direction);
            if (endpoint != null) {
                waypoints.add(new int[] {endpoint.getX(), endpoint.getZ()});
                waypoints.add(new int[] {origin.getX(), origin.getZ()});
            }
        }
        return waypoints;
    }

    private static BlockPos safeHungerDrainEndpoint(
        Minecraft minecraft,
        BlockPos origin,
        Direction direction
    ) {
        BlockPos previous = origin;
        int[] verticalOffsets = {0, 1, -1};
        for (int distance = 1; distance <= 6; distance++) {
            BlockPos horizontal = origin.relative(direction, distance);
            BlockPos next = null;
            for (int verticalOffset : verticalOffsets) {
                BlockPos candidate = new BlockPos(
                    horizontal.getX(),
                    previous.getY() + verticalOffset,
                    horizontal.getZ()
                );
                if (safeHungerDrainColumn(minecraft, candidate)) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            previous = next;
        }
        return previous;
    }

    private static boolean safeHungerDrainColumn(Minecraft minecraft, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos support = feet.below();
        if (!minecraft.level.isLoaded(feet)
            || !minecraft.level.isLoaded(head)
            || !minecraft.level.isLoaded(support)) {
            return false;
        }
        BlockState feetState = minecraft.level.getBlockState(feet);
        BlockState headState = minecraft.level.getBlockState(head);
        BlockState supportState = minecraft.level.getBlockState(support);
        return (feetState.isAir() || feetState.getCollisionShape(minecraft.level, feet).isEmpty())
            && minecraft.level.getFluidState(feet).isEmpty()
            && !isDamagingHungerDrainBlock(feetState)
            && (headState.isAir() || headState.getCollisionShape(minecraft.level, head).isEmpty())
            && minecraft.level.getFluidState(head).isEmpty()
            && !isDamagingHungerDrainBlock(headState)
            && !supportState.isAir()
            && !supportState.getCollisionShape(minecraft.level, support).isEmpty()
            && minecraft.level.getFluidState(support).isEmpty()
            && !isDamagingHungerDrainBlock(supportState);
    }

    private static boolean isDamagingHungerDrainBlock(BlockState state) {
        return state.is(Blocks.CACTUS)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.FIRE)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE);
    }








    private static boolean itemDropVisibleOnClientThread(String itemId, BlockPos near) {
        return itemDropPositionOnClientThread(itemId, near) != null;
    }

    private static List<ScenarioItemDropIdentity> itemDropIdentitiesOnClientThread(String itemId) {
        Minecraft minecraft = requireInPlay();
        List<ScenarioItemDropIdentity> identities = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (
                entity instanceof ItemEntity itemEntity
                    && !entity.isRemoved()
                    && ScenarioContainerActions.itemStackMatches(itemEntity.getItem(), itemId, 1)
            ) {
                identities.add(new ScenarioItemDropIdentity(entity.getId(), entity.getUUID()));
            }
        }
        return List.copyOf(identities);
    }

    private static ScenarioItemDropIdentity newItemDropIdentityOnClientThread(
        String itemId,
        List<ScenarioItemDropIdentity> excludedIdentities
    ) {
        for (ScenarioItemDropIdentity identity : itemDropIdentitiesOnClientThread(itemId)) {
            if (!excludedIdentities.contains(identity)) {
                return identity;
            }
        }
        return null;
    }

    private static Vec3 itemDropPositionOnClientThread(String itemId, BlockPos near) {
        Minecraft minecraft = requireInPlay();
        Vec3 center = Vec3.atLowerCornerWithOffset(near, 0.5, 0.5, 0.5);
        Vec3 nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity itemEntity && !entity.isRemoved()) {
                ItemStack stack = itemEntity.getItem();
                double distance = entity.distanceToSqr(center);
                if ("minecraft:cobblestone".equals(itemId)
                    && !shallowStonePickup(minecraft.player.getY(), entity.getY())) {
                    distance += 16.0; // A reachable drop wins without hiding deep-drop failure evidence.
                }
                if (
                    !stack.isEmpty()
                        && Objects.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), itemId)
                        && entity.distanceToSqr(center) <= 16.0
                ) {
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = entity.position();
                    }
                }
            }
        }
        return nearest;
    }

    private static Vec3 itemDropPositionOnClientThread(String itemId, ScenarioItemDropIdentity identity) {
        Minecraft minecraft = requireInPlay();
        Entity entity = minecraft.level.getEntity(identity.entityId());
        if (
            !(entity instanceof ItemEntity itemEntity)
                || entity.isRemoved()
                || !entity.getUUID().equals(identity.uuid())
        ) {
            return null;
        }
        return ScenarioContainerActions.itemStackMatches(itemEntity.getItem(), itemId, 1)
            ? entity.position()
            : null;
    }

    private static double eyeToBoxDistanceSquared(Minecraft minecraft, Entity target) {
        Vec3 eye = minecraft.player.getEyePosition();
        var box = target.getBoundingBox();
        double dx = eye.x - Math.max(box.minX, Math.min(eye.x, box.maxX));
        double dy = eye.y - Math.max(box.minY, Math.min(eye.y, box.maxY));
        double dz = eye.z - Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean driveCombatOnClientThread(
        Minecraft minecraft, Entity target, boolean attackReady, CombatAttempt combat
    ) {
        stopCombatMotion(minecraft);
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, entityLookTarget(target));
        double distanceSquared = minecraft.player.position().distanceToSqr(target.position());
        if (!(target instanceof Enemy)) {
            minecraft.options.keySprint.setDown(distanceSquared > PICKUP_APPROACH_DISTANCE_SQUARED);
            minecraft.options.keyUp.setDown(distanceSquared > PICKUP_APPROACH_DISTANCE_SQUARED);
            return attackReady;
        }
        combat.hostile = true;
        ItemStack food = combat.recovery.recovering() || minecraft.player.getHealth() <= 10.0F
            ? edibleOwnedFoodOnClientThread(minecraft) : ItemStack.EMPTY;
        combat.recovery.update(minecraft.player.getHealth(), !food.isEmpty());
        combat.requestedFood = null;
        boolean creeper = target instanceof Creeper;
        boolean crowded = hostileNearbyOnClientThread(minecraft, target, target.position());
        double eyeBoxDistance = Math.sqrt(eyeToBoxDistanceSquared(minecraft, target));
        combat.minDistance = Math.min(combat.minDistance, eyeBoxDistance);
        combat.maxDistance = Math.max(combat.maxDistance, eyeBoxDistance);
        boolean sight = minecraft.player.hasLineOfSight(target);
        // Distance alone would stand still against a hostile behind a wall it cannot be hit through.
        boolean inMelee = !creeper && eyeBoxDistance <= 3.0 && sight;
        boolean retreatToEat = combat.recovery.recovering() && !combat.recovery.ateOwnedFood() && !food.isEmpty();
        combat.crowdedSeen += crowded ? 1 : 0;
        if (inMelee) {
            // A hostile inside melee reach is already hitting the player and keeps pace with any
            // retreat (zombie speed ~ sprint speed): stand still and trade blows instead of
            // retreating, eating or backing off for cooldown.
            combat.standAndFight++;
            combat.bestDistanceTick = -1L;
            combat.retreatUntilTick = -1L;
        } else if (creeper || retreatToEat) {
            // Only a creeper (never melee it) or a real chance to eat owned food justifies running;
            // fleeing a crowd without shelter just feeds the mobs and skeleton fire for free.
            retreatFromHostilesOnClientThread(minecraft);
            combat.retreatToEat += retreatToEat ? 1 : 0;
            if (retreatToEat && safeToEatOnClientThread(minecraft, 10.0)) {
                combat.requestedFood = BuiltInRegistries.ITEM.getKey(food.getItem()).toString();
            }
        } else {
            // A target that cannot be closed (shooting skeleton across water or a ledge) must not
            // be stood against: after STUCK ticks without progress, run out of its fire for a while
            // and report the engagement as abandoned so the caller can re-evaluate.
            long now = minecraft.player.tickCount;
            boolean wantsApproach = distanceSquared > 7.29 || !sight;
            if (!wantsApproach) {
                // Close enough that the next swing is only waiting on cooldown/knockback: not stuck.
                combat.bestDistanceTick = -1L;
                combat.retreatUntilTick = -1L;
            } else if (combat.bestDistanceTick < 0L || eyeBoxDistance < combat.bestDistance - 0.5) {
                combat.bestDistance = eyeBoxDistance;
                combat.bestDistanceTick = now;
            }
            if (combat.retreatUntilTick >= 0L
                || (combat.bestDistanceTick >= 0L && now - combat.bestDistanceTick > CombatAttempt.STUCK_TICKS)) {
                if (combat.retreatUntilTick < 0L) {
                    combat.retreatUntilTick = now + CombatAttempt.STUCK_RETREAT_TICKS;
                }
                retreatFromHostilesOnClientThread(minecraft);
                combat.stuckRetreat++;
                combat.gaveUp |= now >= combat.retreatUntilTick;
            } else {
                int movement = sight ? CombatTactics.movement(distanceSquared, attackReady) : 1;
                if (movement < 0) {
                    retreatFromHostilesOnClientThread(minecraft);
                } else if (movement > 0) {
                    if (!moveCombatSafelyOnClientThread(minecraft, target.position(), combat)) {
                        combat.moveBlocked++;
                    }
                }
            }
        }
        if (creeper) {
            combat.withheldCreeper++;
            return false;
        }
        if (!attackReady) {
            combat.withheldNotReady++;
            return false;
        }
        if (eyeToBoxDistanceSquared(minecraft, target) > 9.0) {
            combat.withheldOutOfReach++;
            return false;
        }
        if (!minecraft.player.hasLineOfSight(target)) {
            combat.withheldNoLineOfSight++;
            return false;
        }
        if (!selectedItemOnClientThread().matches(combat.weaponId, 1)) {
            combat.withheldWrongWeapon++;
            combat.reselectWeapon = true;
            return false;
        }
        return true;
    }

    private static boolean hostileNearbyOnClientThread(Minecraft minecraft, Entity target, Vec3 targetPosition) {
        for (Entity other : minecraft.level.entitiesForRendering()) {
            if (!(other instanceof Enemy) || other == target || other.isRemoved() || !other.isAlive()) {
                continue;
            }
            double targetDistance = targetPosition == null ? Double.POSITIVE_INFINITY
                : other.distanceToSqr(targetPosition);
            if (CombatTactics.hostileTooClose(other instanceof Creeper,
                other.distanceToSqr(minecraft.player.position()), targetDistance)) {
                return true;
            }
        }
        return false;
    }

    private static void stopCombatMotion(Minecraft minecraft) {
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keySprint.setDown(false);
        // Stopping in water still holds jump: melee, retreat and eating would otherwise sink and drown.
        // Cleanup after a disconnect runs without a player.
        minecraft.options.keyJump.setDown(minecraft.player != null && minecraft.player.isInWater());
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
    }

    private static boolean moveCombatSafelyOnClientThread(
        Minecraft minecraft, Vec3 target, CombatAttempt combat
    ) {
        stopCombatMotion(minecraft);
        Vec3 offset = target.subtract(minecraft.player.position());
        double distance = Math.hypot(offset.x, offset.z);
        if (distance < 1.0E-6) {
            return false;
        }
        double dx = offset.x / distance;
        double dz = offset.z / distance;
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES,
            new Vec3(target.x, minecraft.player.getEyeY(), target.z));
        MovementClearance clearance = MovementDetour.clearance(minecraft, target);
        if (minecraft.player.isInWater()) {
            // Footing checks reject every fluid cell, which would leave a swimming player sinking.
            minecraft.options.keyUp.setDown(true);
            minecraft.options.keyJump.setDown(true);
            return true;
        }
        boolean stepUp = minecraft.player.horizontalCollision && clearance.raisedForward();
        boolean direct = (clearance.direct() || stepUp) && safeCombatFooting(minecraft, dx, dz);
        boolean left = clearance.left() && safeCombatFooting(minecraft, dz, -dx);
        boolean right = clearance.right() && safeCombatFooting(minecraft, -dz, dx);
        int detour = MovementDetour.choose(combat.detourDirection, combat.preferredDetourDirection,
            !direct, direct, left, right);
        if (combat.detourDirection == 0 && detour != 0) {
            combat.preferredDetourDirection = -detour;
        }
        combat.detourDirection = detour;
        if (!(detour == 0 ? direct : detour < 0 ? left : right)) {
            return false;
        }
        minecraft.options.keyUp.setDown(detour == 0);
        minecraft.options.keyLeft.setDown(detour < 0);
        minecraft.options.keyRight.setDown(detour > 0);
        minecraft.options.keyJump.setDown(detour == 0 && stepUp);
        return true;
    }

    private static boolean safeCombatFooting(Minecraft minecraft, double dx, double dz) {
        var box = minecraft.player.getBoundingBox();
        double feetY = box.minY;
        int highestSupport = (int) Math.floor(feetY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Probe the swept footprint, including walking coast, not just an endpoint
        // that could skip a narrow hole.
        for (int step = 1; step <= 4; step++) {
            double advance = step * 0.4;
            int minX = (int) Math.floor(box.minX + dx * advance + 0.01);
            int maxX = (int) Math.floor(box.maxX + dx * advance - 0.01);
            int minZ = (int) Math.floor(box.minZ + dz * advance + 0.01);
            int maxZ = (int) Math.floor(box.maxZ + dz * advance - 0.01);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean supported = false;
                    for (int y = highestSupport; y >= highestSupport - 4; y--) {
                        pos.set(x, y, z);
                        if (!minecraft.level.hasChunkAt(pos) || !minecraft.level.getFluidState(pos).isEmpty()) {
                            return false;
                        }
                        var shape = minecraft.level.getBlockState(pos).getCollisionShape(minecraft.level, pos);
                        if (!shape.isEmpty()) {
                            double top = y + shape.max(Direction.Axis.Y);
                            supported = CombatTactics.safeSupport(feetY, y)
                                && top >= feetY - 3.0 && top <= feetY + 1.0;
                            break;
                        }
                    }
                    if (!supported) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void retreatFromHostilesOnClientThread(Minecraft minecraft) {
        stopCombatMotion(minecraft);
        Vec3 player = minecraft.player.position();
        double bestScore = nearestHostileDistanceScore(minecraft, player.x, player.y, player.z);
        double bestDx = 0.0;
        double bestDz = 0.0;
        for (int direction = 0; direction < 8; direction++) {
            double dx = COMBAT_RETREAT_DIRECTIONS[direction * 2];
            double dz = COMBAT_RETREAT_DIRECTIONS[direction * 2 + 1];
            double score = nearestHostileDistanceScore(minecraft, player.x + dx * 1.6, player.y, player.z + dz * 1.6);
            if (score <= bestScore || !safeCombatFooting(minecraft, dx, dz)
                || !minecraft.level.noCollision(minecraft.player, minecraft.player.getBoundingBox()
                    .expandTowards(dx * 0.8, 0.0, dz * 0.8).deflate(0.01))) {
                continue;
            }
            bestScore = score;
            bestDx = dx;
            bestDz = dz;
        }
        if (bestDx != 0.0 || bestDz != 0.0) {
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                new Vec3(player.x + bestDx, minecraft.player.getEyeY(), player.z + bestDz));
            minecraft.options.keyUp.setDown(true);
            minecraft.options.keySprint.setDown(true);
        }
    }

    private static double nearestHostileDistanceScore(Minecraft minecraft, double x, double y, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Entity hostile : minecraft.level.entitiesForRendering()) {
            if (!(hostile instanceof Enemy) || hostile.isRemoved() || !hostile.isAlive()) {
                continue;
            }
            double dx = x - hostile.getX();
            double dy = y - hostile.getY();
            double dz = z - hostile.getZ();
            double score = (dx * dx + dy * dy + dz * dz) / (hostile instanceof Creeper ? 49.0 : 36.0);
            nearest = Math.min(nearest, score);
        }
        return nearest;
    }

    private static ItemStack edibleOwnedFoodOnClientThread(Minecraft minecraft) {
        if (minecraft.player.getFoodData().getFoodLevel() >= 20) {
            return ItemStack.EMPTY;
        }
        ItemStack best = ItemStack.EMPTY;
        int nutrition = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            var food = stack.get(DataComponents.FOOD);
            if (!stack.isEmpty() && food != null && stack.get(DataComponents.CONSUMABLE) != null
                && food.nutrition() > nutrition) {
                best = stack;
                nutrition = food.nutrition();
            }
        }
        return best;
    }

    private static boolean safeToEatOnClientThread(Minecraft minecraft, double distance) {
        for (Entity hostile : minecraft.level.entitiesForRendering()) {
            if (!(hostile instanceof Enemy) || hostile.isRemoved() || !hostile.isAlive()) {
                continue;
            }
            double clearance = hostile instanceof Creeper ? distance + 1.0 : distance;
            if (hostile.distanceToSqr(minecraft.player.position()) <= clearance * clearance) {
                return false;
            }
        }
        return true;
    }

    private void eatOwnedFoodForCombatRecovery(CombatAttempt combat, long deadlineNanos) throws Exception {
        if (combat.reselectWeapon && System.nanoTime() < deadlineNanos) {
            combat.reselectWeapon = false;
            selectHotbarItem(combat.weaponId, 1,
                Duration.ofNanos(Math.min(2_000_000_000L, deadlineNanos - System.nanoTime())));
        }
        String food = combat.requestedFood;
        combat.requestedFood = null;
        if (food == null || System.nanoTime() >= deadlineNanos) {
            return;
        }
        try {
            ScenarioHeldItem held = selectHotbarItem(food, 1,
                Duration.ofNanos(Math.min(2_000_000_000L, deadlineNanos - System.nanoTime())));
            if (!held.matches(food, 1)) {
                return;
            }
            FoodUseSample initial = executor.callOnClientThread(() -> {
                Minecraft minecraft = requireInPlay();
                return new FoodUseSample(true, minecraft.player.getFoodData().getFoodLevel(),
                    inventoryCountOnClientThread(food));
            });
            long eatDeadline = Math.min(deadlineNanos, System.nanoTime() + 3_000_000_000L);
            do {
                long version = ClientStateEvents.tickVersion();
                FoodUseSample sample = executor.callOnClientThread(() -> {
                    Minecraft minecraft = requireInPlay();
                    boolean safe = safeToEatOnClientThread(minecraft, 7.0);
                    minecraft.options.keyUse.setDown(safe);
                    retreatFromHostilesOnClientThread(minecraft);
                    if (safe && !minecraft.player.isUsingItem()) {
                        minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
                    }
                    return new FoodUseSample(safe, minecraft.player.getFoodData().getFoodLevel(),
                        inventoryCountOnClientThread(food));
                });
                if (sample.foodLevel() > initial.foodLevel() && sample.itemCount() < initial.itemCount()) {
                    combat.recovery.foodConsumed();
                    break;
                }
                if (!sample.started() || !awaitClientTick(version, eatDeadline)) {
                    break;
                }
            } while (true);
        } finally {
            executor.callOnClientThread(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.options != null) {
                    minecraft.options.keyUse.setDown(false);
                    stopCombatMotion(minecraft);
                }
                if (minecraft.gameMode != null && minecraft.player != null) {
                    minecraft.gameMode.releaseUsingItem(minecraft.player);
                }
                return null;
            });
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining > 0L) {
                selectHotbarItem(combat.weaponId, 1, Duration.ofNanos(Math.min(2_000_000_000L, remaining)));
            }
        }
    }

    private static final class CombatAttempt {
        final CombatTactics.Recovery recovery = new CombatTactics.Recovery();
        final String weaponId;
        boolean hostile;
        String requestedFood;
        int detourDirection;
        int preferredDetourDirection = 1;
        int sent;
        int withheldCreeper;
        int withheldNotReady;
        int withheldOutOfReach;
        int withheldNoLineOfSight;
        int withheldWrongWeapon;
        int retreatToEat;
        int crowdedSeen;
        int standAndFight;
        static final long STUCK_TICKS = 100L;
        static final long STUCK_RETREAT_TICKS = 100L;
        int moveBlocked;
        int stuckRetreat;
        double bestDistance = Double.POSITIVE_INFINITY;
        long bestDistanceTick = -1L;
        long retreatUntilTick = -1L;
        boolean gaveUp;
        float lastPlayerHealth = Float.NaN;
        double minDistance = Double.POSITIVE_INFINITY;
        double maxDistance;
        boolean reselectWeapon;

        CombatAttempt(String weaponId) {
            this.weaponId = weaponId;
        }

        String withheldSummary() {
            return "creeper:" + withheldCreeper + ",not_ready:" + withheldNotReady
                + ",out_of_reach:" + withheldOutOfReach + ",no_line_of_sight:" + withheldNoLineOfSight
                + ",wrong_weapon:" + withheldWrongWeapon + ",retreat_to_eat:" + retreatToEat
                + ",crowded_seen:" + crowdedSeen + ",stand_and_fight:" + standAndFight
                + ",stuck_retreat:" + stuckRetreat
                + ",move_blocked:" + moveBlocked + ",eye_box_dist_min:" + String.format(Locale.ROOT, "%.1f", minDistance)
                + ",eye_box_dist_max:" + String.format(Locale.ROOT, "%.1f", maxDistance);
        }
    }

    private static ScenarioEntityObservation visibleEntityOnClientThread(
        List<String> entityTypeIds,
        ScenarioReach reach,
        boolean skipCrowdedHostiles
    ) {
        Minecraft minecraft = requireInPlay();
        Vec3 playerPosition = minecraft.player.position();
        ScenarioEntityObservation nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.isRemoved() || !entity.isAlive() || entity == minecraft.player) {
                continue;
            }
            String observedTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!entityTypeIds.contains(observedTypeId)) {
                continue;
            }
            if (entity instanceof Enemy
                && (entity instanceof Creeper
                    || (skipCrowdedHostiles && hostileNearbyOnClientThread(minecraft, entity, entity.position())))) {
                continue;
            }
            double distance = entity.distanceToSqr(playerPosition);
            if (!reach.includes(distance)) {
                continue;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entityObservation(entity, playerPosition, distance);
            }
        }
        return nearest;
    }

    private static ScenarioEntityObservation visibleSheepWithWoolOnClientThread(
        String woolItemId,
        ScenarioReach reach
    ) {
        Minecraft minecraft = requireInPlay();
        Vec3 playerPosition = minecraft.player.position();
        ScenarioEntityObservation nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Sheep sheep) || entity.isRemoved() || sheep.isSheared()) {
                continue;
            }
            if (!Objects.equals(sheepWoolItemId(sheep.getColor()), woolItemId)) {
                continue;
            }
            double distance = entity.distanceToSqr(playerPosition);
            if (!reach.includes(distance)) {
                continue;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entityObservation(entity, playerPosition, distance);
            }
        }
        return nearest;
    }

    private static ScenarioPlayerObservation visiblePlayerOnClientThread(String playerName) {
        Minecraft minecraft = requireInPlay();
        Vec3 viewerPosition = minecraft.player.position();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (
                entity.isRemoved()
                    || entity == minecraft.player
                    || !(entity instanceof Player player)
                    || !playerName.equals(player.getPlainTextName())
            ) {
                continue;
            }
            return playerObservation(entity, playerName, viewerPosition);
        }
        return null;
    }

    private static Entity entityByIdOnClientThread(int entityId) {
        Minecraft minecraft = requireInPlay();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!entity.isRemoved() && entity.getId() == entityId) {
                return entity;
            }
        }
        return null;
    }

    static InteractionHand interactionHand(String hand) {
        return switch (hand) {
            case "main_hand" -> InteractionHand.MAIN_HAND;
            case "off_hand" -> InteractionHand.OFF_HAND;
            default -> throw new IllegalArgumentException("interaction hand must be main_hand or off_hand");
        };
    }

    static EntityInteractionDispatch.Outcome interactionOutcome(InteractionResult result) {
        if (result instanceof InteractionResult.Success) {
            return new EntityInteractionDispatch.Outcome("success", true);
        }
        if (result == InteractionResult.FAIL) {
            return new EntityInteractionDispatch.Outcome("fail", false);
        }
        if (result == InteractionResult.PASS) {
            return new EntityInteractionDispatch.Outcome("pass", false);
        }
        if (result == InteractionResult.TRY_WITH_EMPTY_HAND) {
            return new EntityInteractionDispatch.Outcome("try_with_empty_hand", false);
        }
        throw new IllegalStateException("unknown vanilla interaction result: " + result.getClass().getName());
    }

    private static final class MinecraftEntityInteractionAccess
        implements EntityInteractionDispatch.Access<ClientLevel, Entity, EntityHitResult> {
        @Override
        public ClientLevel currentLevel() {
            return Minecraft.getInstance().level;
        }

        @Override
        public Entity entityById(ClientLevel level, int entityId) {
            Entity entity = level.getEntity(entityId);
            return entity == null || entity.isRemoved() ? null : entity;
        }

        @Override
        public ScenarioEntityIdentity identity(Entity entity) {
            return new ScenarioEntityIdentity(
                entity.getId(),
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
            );
        }

        @Override
        public EntityHitResult currentEntityHit() {
            return Minecraft.getInstance().hitResult instanceof EntityHitResult hit ? hit : null;
        }

        @Override
        public Entity hitEntity(EntityHitResult hit) {
            return hit.getEntity();
        }

        @Override
        public boolean isWithinReach(Entity entity) {
            Minecraft minecraft = requireInPlay();
            return minecraft.player.isWithinEntityInteractionRange(entity, 0.0);
        }

        @Override
        public double hitX(EntityHitResult hit) {
            return hit.getLocation().x;
        }

        @Override
        public double hitY(EntityHitResult hit) {
            return hit.getLocation().y;
        }

        @Override
        public double hitZ(EntityHitResult hit) {
            return hit.getLocation().z;
        }

        @Override
        public EntityInteractionDispatch.Outcome interact(
            Entity entity,
            EntityHitResult hit,
            String hand
        ) {
            Minecraft minecraft = requireInPlay();
            return interactionOutcome(minecraft.gameMode.interact(
                minecraft.player,
                entity,
                hit,
                interactionHand(hand)
            ));
        }
    }

    private static EntityMotionSample entityMotionSampleOnClientThread(ScenarioEntityIdentity identity) {
        Entity entity = entityByIdOnClientThread(identity.entityId());
        if (
            entity == null
                || !identity.matches(
                    entity.getId(),
                    entity.getUUID(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
                )
        ) {
            return null;
        }
        return entityMotionSample(entity);
    }

    private static EntityMotionSample entityMotionSample(Entity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
        double yawDelta = Double.NaN;
        if (horizontalSpeed > 0.01) {
            double movementYaw = Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
            yawDelta = wrappedYawDelta(entity.getYRot(), movementYaw);
        }
        return new EntityMotionSample(
            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
            entity.getX(),
            entity.getY(),
            entity.getZ(),
            horizontalSpeed,
            yawDelta
        );
    }

    private static double wrappedYawDelta(double first, double second) {
        double delta = (first - second) % 360.0;
        if (delta > 180.0) {
            delta -= 360.0;
        } else if (delta < -180.0) {
            delta += 360.0;
        }
        return Math.abs(delta);
    }

    private static ScenarioEntityObservation entityObservation(Entity entity, Vec3 playerPosition, double distance) {
        return new ScenarioEntityObservation(
            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
            entity.getId(),
            entity.getUUID(),
            entity.getX(),
            entity.getY(),
            entity.getZ(),
            distance == Double.MAX_VALUE ? entity.distanceToSqr(playerPosition) : distance,
            entity instanceof Sheep sheep && !sheep.isSheared() ? sheepWoolItemId(sheep.getColor()) : null
        );
    }

    static String sheepWoolItemId(DyeColor color) {
        return SheepWoolColor.itemId(color.name());
    }

    private static ScenarioWaterObservation waterObservationOnClientThread() {
        Minecraft minecraft = requireInPlay();
        Player player = minecraft.player;
        BlockPos feetPosition = player.blockPosition();
        BlockPos eyePosition = BlockPos.containing(player.getEyePosition());
        BlockState feetBlock = minecraft.level.getBlockState(feetPosition);
        BlockState eyeBlock = minecraft.level.getBlockState(eyePosition);
        FluidState feetFluid = minecraft.level.getFluidState(feetPosition);
        FluidState eyeFluid = minecraft.level.getFluidState(eyePosition);
        return new ScenarioWaterObservation(
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getEyePosition().y,
            player.getEyeHeight(),
            player.getBoundingBox().getYsize(),
            player.isInWater(),
            player.isUnderWater(),
            player.isSwimming(),
            player.getFluidHeight(FluidTags.WATER),
            BuiltInRegistries.BLOCK.getKey(feetBlock.getBlock()).toString(),
            BuiltInRegistries.FLUID.getKey(feetFluid.getType()).toString(),
            feetFluid.isSource(),
            feetFluid.getHeight(minecraft.level, feetPosition),
            BuiltInRegistries.BLOCK.getKey(eyeBlock.getBlock()).toString(),
            BuiltInRegistries.FLUID.getKey(eyeFluid.getType()).toString(),
            eyeFluid.isSource(),
            eyeFluid.getHeight(minecraft.level, eyePosition),
            player.getAirSupply(),
            player.getMaxAirSupply(),
            player.getHealth(),
            player.getPose().toString().toLowerCase(Locale.ROOT),
            minecraft.getConnection() != null && minecraft.level != null
        );
    }

    private static void setScenarioInputs(Minecraft minecraft, List<String> inputs, boolean down) {
        for (String input : inputs) {
            switch (input) {
                case "forward" -> minecraft.options.keyUp.setDown(down);
                case "back" -> minecraft.options.keyDown.setDown(down);
                case "left" -> minecraft.options.keyLeft.setDown(down);
                case "right" -> minecraft.options.keyRight.setDown(down);
                case "jump" -> minecraft.options.keyJump.setDown(down);
                case "sneak" -> minecraft.options.keyShift.setDown(down);
                case "sprint" -> minecraft.options.keySprint.setDown(down);
                default -> throw new IllegalArgumentException("unsupported scenario input: " + input);
            }
        }
    }

    private static ScenarioPlayerObservation playerObservation(
        Entity entity,
        String playerName,
        Vec3 viewerPosition
    ) {
        return new ScenarioPlayerObservation(
            playerName,
            entity.getId(),
            entity.getX(),
            entity.getY(),
            entity.getZ(),
            entity.distanceToSqr(viewerPosition)
        );
    }

    private static boolean chatHistoryContainsOnClientThread(String expectedText) {
        Minecraft minecraft = requireInPlay();
        for (String text : chatMessageTexts(minecraft.gui.hud.getChat())) {
            if (expectedText.equals(text) || text.contains(expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> chatMessageTexts(ChatComponent chat) {
        try {
            Field allMessagesField = ChatComponent.class.getDeclaredField("allMessages");
            allMessagesField.setAccessible(true);
            Object value = allMessagesField.get(chat);
            if (!(value instanceof List<?> messages)) {
                throw new IllegalStateException("ChatComponent.allMessages is not a List");
            }

            List<String> texts = new ArrayList<>();
            for (Object message : messages) {
                if (message instanceof GuiMessage guiMessage) {
                    texts.add(guiMessage.content().getString());
                }
            }
            return texts;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("could not read client chat history", error);
        }
    }

    private static double horizontalDistance(ScenarioPlayerObservation a, ScenarioPlayerObservation b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3 entityLookTarget(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    private static ScenarioEntityObservation visibleEntityNearOnClientThread(
        String entityTypeId,
        Vec3 target,
        double maxDistanceSquared
    ) {
        Minecraft minecraft = requireInPlay();
        Vec3 playerPosition = minecraft.player.position();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.isRemoved()) {
                continue;
            }
            String observedTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (
                Objects.equals(observedTypeId, entityTypeId)
                    && entity.distanceToSqr(target) <= maxDistanceSquared
            ) {
                return new ScenarioEntityObservation(
                    observedTypeId,
                    entity.getId(),
                    entity.getUUID(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    entity.distanceToSqr(playerPosition),
                    null
                );
            }
        }
        return null;
    }

    private static boolean signTextMatches(ScenarioBlockTarget target, List<String> lines) {
        Minecraft minecraft = requireInPlay();
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos(target));
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return false;
        }
        SignText frontText = sign.getText(SignTextSlot.FRONT);
        for (int index = 0; index < lines.size(); index++) {
            if (!Objects.equals(frontText.getMessages(false).get(index).getString(), lines.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static void requireFourSignLines(List<String> lines) {
        if (lines.size() != 4) {
            throw new IllegalArgumentException("sign text update requires exactly four lines");
        }
    }

    private static ScenarioHeldItem selectedItemOnClientThread() {
        Minecraft minecraft = requireInPlay();
        return heldItemFromStack(minecraft.player.getInventory().getSelectedItem());
    }

    private static ScenarioHeldItem heldItemFromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ScenarioHeldItem("minecraft:air", 0);
        }
        return new ScenarioHeldItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
    }

    private static int inventoryCountOnClientThread(String itemId) {
        Minecraft minecraft = requireInPlay();
        int total = 0;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (
                !stack.isEmpty()
                    && Objects.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), itemId)
            ) {
                total += stack.getCount();
            }
        }
        return total;
    }




    private static int armorMenuSlot(String armorSlot) {
        return switch (armorSlot) {
            case "head" -> 5;
            case "chest" -> 6;
            case "legs" -> 7;
            case "feet" -> 8;
            default -> throw new IllegalArgumentException("unsupported armor slot " + armorSlot);
        };
    }












    private void observeCombatTarget(Minecraft minecraft, Entity target, long tick, CombatAttempt combat) {
        float playerHealth = minecraft.player.getHealth();
        if (!Float.isNaN(combat.lastPlayerHealth) && playerHealth < combat.lastPlayerHealth) {
            StringBuilder nearby = new StringBuilder();
            int count = 0;
            for (Entity other : minecraft.level.entitiesForRendering()) {
                if (!(other instanceof Enemy) || other.isRemoved() || !other.isAlive()) {
                    continue;
                }
                double distance = Math.sqrt(other.distanceToSqr(minecraft.player.position()));
                if (distance > 6.0) {
                    continue;
                }
                count++;
                nearby.append(nearby.length() == 0 ? "" : ",")
                    .append(BuiltInRegistries.ENTITY_TYPE.getKey(other.getType()).getPath())
                    .append('#').append(other.getId())
                    .append(String.format(Locale.ROOT, "@%.1f", distance));
            }
            combatLedger.playerDamaged(tick, combat.lastPlayerHealth, playerHealth, target.getId(), count, nearby.toString());
        }
        combat.lastPlayerHealth = playerHealth;
        if (target instanceof LivingEntity living) {
            combatLedger.observeTarget(target.getId(), tick, living.getHealth());
        }
    }

    private void recordCombatAttack(Minecraft minecraft, Entity target, String entityType, long tick) {
        float targetHealth = target instanceof LivingEntity living ? living.getHealth() : Float.NaN;
        combatLedger.attackSent(tick, entityType, target.getId(),
            Math.sqrt(eyeToBoxDistanceSquared(minecraft, target)), targetHealth, minecraft.player.getHealth());
    }

    private static void recordAttackCadence(Player player, Entity target) {
        System.out.println(
            "Solaris combat attack cadence: entity_id=" + target.getId()
                + " held_item=" + BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem())
                + " attack_speed=" + player.getAttributeValue(Attributes.ATTACK_SPEED)
                + " attack_strength=" + player.getAttackStrengthScale(0.0F)
        );
    }

    private static long clientTick(Minecraft minecraft) {
        return Integer.toUnsignedLong(minecraft.player.tickCount);
    }





    private record ContainerUpdateCheckpoint(int containerId, int stateId) {
    }

    private record HotbarSelectionAttempt(
        ScenarioHeldItem selectedItem,
        ContainerUpdateCheckpoint checkpoint,
        int targetHotbarSlot
    ) {
    }

    private record HotbarSelectionResponse(
        boolean observed,
        boolean confirmed,
        ScenarioHeldItem selectedItem
    ) {
    }

    private static final class BlockBreakAutomation {
        private final ScenarioBlockTarget target;
        private final Direction face;
        private final CompletableFuture<Void> started = new CompletableFuture<>();
        private boolean startSent;
        private int continueCalls;
        private int continueAccepted;

        private BlockBreakAutomation(ScenarioBlockTarget target, Direction face) {
            this.target = target;
            this.face = face;
        }

        private String diagnostic() {
            return "start_sent=" + startSent
                + " continue_calls=" + continueCalls
                + " continue_accepted=" + continueAccepted;
        }
    }

    private record BreakSample(
        boolean becameAir,
        boolean sawDrop,
        ScenarioHeldItem selectedItem,
        long tick
    ) {
    }

    private record PickupSample(
        boolean visibleDrop,
        ScenarioHeldItem selectedItem,
        int inventoryCount,
        double x,
        double y,
        double z,
        double dropX,
        double dropY,
        double dropZ,
        double dropDistanceSquared,
        boolean horizontalCollision,
        boolean onGround,
        int detourDirection,
        long tick,
        boolean identityTaken,
        boolean avoidDeepDrop
    ) {
    }

    private static String pickupDetail(PickupSample sample) {
        if (sample == null) {
            return "";
        }
        String drop = sample.visibleDrop()
            ? String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", sample.dropX(), sample.dropY(), sample.dropZ())
            : "none";
        return String.format(
            Locale.ROOT,
            "player=(%.3f,%.3f,%.3f) drop=%s distance_squared=%.3f horizontal_collision=%s on_ground=%s detour_direction=%d tick=%d",
            sample.x(),
            sample.y(),
            sample.z(),
            drop,
            sample.dropDistanceSquared(),
            sample.horizontalCollision(),
            sample.onGround(),
            sample.detourDirection(),
            sample.tick()
        );
    }

    private record ApproachSample(boolean inReach, int detourDirection) {
    }

    private record HazardStandSample(boolean dead, int detourDirection) {
    }

    private record EntityApproachSample(boolean visible, boolean inReach, int detourDirection) {
    }

    record InventoryPickupResult(boolean confirmed, boolean sawDrop, String detail) {
        private static InventoryPickupResult of(
            boolean confirmed,
            boolean sawDrop,
            int initialCount,
            int observedCount,
            int expectedPickupCount
        ) {
            return new InventoryPickupResult(
                confirmed,
                sawDrop,
                "inventory_count=" + observedCount
                    + " initial_count=" + initialCount
                    + " expected_delta=" + expectedPickupCount
            );
        }
    }

    record InventoryPickupSample(int inventoryCount, boolean sawDrop) {
    }

    interface InventoryPickupWaitSource {
        long stateVersion();

        InventoryPickupSample sample() throws Exception;

        boolean awaitStateChange(long observedVersion, long deadlineNanos)
            throws InterruptedException;
    }


    interface RespawnWaitSource {
        long healthVersion();

        long tickVersion();

        long stateVersion();

        boolean activePlayer() throws Exception;

        boolean awaitHealthChange(long observedVersion, long deadlineNanos)
            throws InterruptedException;

        boolean awaitTickOrStateChange(
            long observedTickVersion,
            long observedStateVersion,
            long deadlineNanos
        ) throws InterruptedException;
    }


    private record EntityAttackSample(
        boolean attackSent,
        boolean removed,
        boolean visibleDrop,
        ScenarioHeldItem selectedItem,
        int inventoryCount,
        double x,
        double y,
        double z,
        long tick
    ) {
    }

    private record EntityRemovalAttackSample(boolean attackSent, boolean removed, long tick) {
    }

    private record FoodUseSample(boolean started, int foodLevel, int itemCount) {
    }

    private record ShieldBlockSample(boolean useStarted, float health, int shieldDamage, long shieldBlockVersion) {
    }

}
