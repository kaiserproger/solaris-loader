package dev.solaris.agent.javaagent;

import com.mojang.brigadier.suggestion.Suggestions;
import dev.solaris.agent.client.ClientScenarioReport;
import dev.solaris.agent.client.ClientTaskExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.solaris.agent.javaagent.ScenarioClientState.requireInPlay;

/** Targeted operator-assisted smoke; every assertion reads the vanilla client's received state. */
final class VanillaUiSmokeScenario {
    static final String COMMANDS = "playable-ui-commands";
    static final String SCOREBOARD = "playable-ui-teams-scoreboard";
    static final String EFFECTS = "playable-ui-effects";
    static final String STATIONS = "playable-ui-enchant-anvil-grindstone";
    static final String ADVERSARIAL = "playable-ui-adversarial";
    static final String W1_VILLAGER_TRADE = "playable-w1-villager-trade";
    static final String W1_REDSTONE = "playable-w1-redstone";
    static final String W1_BREWING = "playable-w1-brewing";
    static final String W1_RANGED = "playable-w1-ranged";
    static final String W1_FISHING = "playable-w1-fishing";
    static final String W1_TNT_FIRE_LIGHT = "playable-w1-tnt-fire-light";
    static final String W1_STATIONS = "playable-w1-stations";
    static final Duration TIMEOUT = Duration.ofSeconds(8);
    private final ClientTaskExecutor executor;
    final ScenarioClient client;
    private final MinecraftClientFacade facade;
    private final Path screenshots;
    private final List<String> observations = new ArrayList<>();
    private int step;
    private long consumeStartGameTime;
    private long consumeStartTimeVersion;
    private long consumeStartNanos;

    VanillaUiSmokeScenario(ClientTaskExecutor executor, ScenarioClient client,
                           MinecraftClientFacade facade, Path screenshots) {
        this.executor = executor;
        this.client = client;
        this.facade = facade;
        this.screenshots = screenshots;
    }

    static boolean supports(String id) {
        if (id == null) {
            return false;
        }
        return COMMANDS.equals(id) || SCOREBOARD.equals(id) || EFFECTS.equals(id)
            || STATIONS.equals(id) || ADVERSARIAL.equals(id)
            || W1_VILLAGER_TRADE.equals(id) || W1_REDSTONE.equals(id) || W1_BREWING.equals(id)
            || W1_RANGED.equals(id) || W1_FISHING.equals(id) || W1_TNT_FIRE_LIGHT.equals(id)
            || W1_STATIONS.equals(id) || W2GameplaySmoke.IDS.contains(id)
            || W3StorageSmoke.IDS.contains(id) || W3CreatureSmoke.IDS.contains(id)
            || W3EnvironmentSmoke.IDS.contains(id);
    }

    ClientScenarioReport run(String id) {
        if (!supports(id)) {
            return new ClientScenarioReport("blocked", id, List.of("unsupported scenario id: " + id));
        }
        try {
            check(await(() -> minecraft().getConnection().getCommands().getRoot().getChild("gamemode") != null),
                "operator-command-tree", "operator fixture must publish gamemode root");
            switch (id) {
                case COMMANDS -> commands();
                case SCOREBOARD -> scoreboard();
                case EFFECTS -> effects();
                case STATIONS -> new VanillaStationSmoke(this).run();
                case ADVERSARIAL -> adversarial();
                case W1_VILLAGER_TRADE -> new W1VillagerTradeSmoke(this).run();
                case W1_REDSTONE -> new W1RedstoneSmoke(this).run();
                case W1_BREWING -> new W1BrewingSmoke(this).run();
                case W1_RANGED -> new W1RangedSmoke(this).run();
                case W1_FISHING -> new W1FishingSmoke(this).run();
                case W1_TNT_FIRE_LIGHT -> new W1TntFireLightSmoke(this).run();
                case W1_STATIONS -> new W1StationsSmoke(this).run();
                default -> {
                    if (W2GameplaySmoke.IDS.contains(id)) new W2GameplaySmoke(this).run(id);
                    else if (W3StorageSmoke.IDS.contains(id)) new W3StorageSmoke(this).run(id);
                    else if (W3CreatureSmoke.IDS.contains(id)) new W3CreatureSmoke(this).run(id);
                    else if (W3EnvironmentSmoke.IDS.contains(id)) new W3EnvironmentSmoke(this).run(id);
                    else throw new IllegalArgumentException(id);
                }
            }
            return new ClientScenarioReport("passed", id, observations);
        } catch (Exception error) {
            observations.add("failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            try {
                capture("failure");
            } catch (Exception screenshotError) {
                observations.add("failure screenshot unavailable: " + screenshotError.getMessage());
            }
            return new ClientScenarioReport("failed", id, observations);
        }
    }

    private void commands() throws Exception {
        int roots = read(() -> minecraft().getConnection().getCommands().getRoot().getChildren().size());
        check(roots > 0, "command-tree", "received_root_count=" + roots);
        ChatScreen chat = read(() -> {
            ChatScreen screen = new ChatScreen("/gam", false);
            minecraft().gui.setScreen(screen);
            CommandSuggestions suggestions = field(screen, "commandSuggestions", CommandSuggestions.class);
            suggestions.setAllowSuggestions(true);
            suggestions.updateCommandInfo();
            return screen;
        });
        CompletableFuture<?> pending = read(() -> field(
            field(chat, "commandSuggestions", CommandSuggestions.class), "pendingSuggestions", CompletableFuture.class));
        Suggestions suggestions = (Suggestions) pending.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        List<String> texts = suggestions.getList().stream().map(s -> s.getText()).toList();
        read(() -> { chat.keyPressed(new KeyEvent(258, 0, 0)); return null; });
        check(texts.contains("gamemode"), "gam-tab-suggestions", "vanilla_pending_suggestions=" + texts);
        String completed = read(() -> field(chat, "input", EditBox.class).getValue());
        for (int tabs = 0; !completed.startsWith("/gamemode") && tabs <= texts.size(); tabs++) {
            read(() -> { chat.keyPressed(new KeyEvent(258, 0, 0)); return null; });
            completed = read(() -> field(chat, "input", EditBox.class).getValue());
        }
        check(completed.startsWith("/gamemode"), "gam-tab-completed", "vanilla_chat_input=" + completed);
        client.closeCurrentScreen(TIMEOUT);
        command("gamemode creative");
        check(await(() -> minecraft().gameMode.getPlayerMode().isCreative()),
            "gamemode-creative", "client_game_mode=" + read(() -> minecraft().gameMode.getPlayerMode().getName()));
        command("clear @s minecraft:diamond");
        require(client.waitForInventoryCount("minecraft:diamond", 0, TIMEOUT), "diamond clear did not converge");
        command("give @s minecraft:diamond 3");
        showInventory();
        check(client.waitForInventoryCount("minecraft:diamond", 3, TIMEOUT),
            "give-three-diamonds", "client_inventory_diamonds=" + client.inventoryCount("minecraft:diamond"));
        client.closeCurrentScreen(TIMEOUT);
        command("gamemode survival");
        require(await(() -> !minecraft().gameMode.getPlayerMode().isCreative()), "survival reset did not converge");
        String name = read(() -> minecraft().player.getScoreboardName());
        command("deop " + name);
        check(await(() -> minecraft().getConnection().getCommands().getRoot().getChild("gamemode") == null),
            "deop-command-tree", "gamemode root must disappear after self-deop");
        clearChat();
        command("gamemode creative");
        boolean refused = client.waitForChatMessage("Unknown or incomplete command", TIMEOUT);
        showChat();
        check(refused && read(() -> !minecraft().gameMode.getPlayerMode().isCreative()),
            "non-op-gamemode-refused", "vanilla_refusal=" + refused + " client_mode="
                + read(() -> minecraft().gameMode.getPlayerMode().getName()));
        client.closeCurrentScreen(TIMEOUT);
        clearChat();
        command("give @s minecraft:diamond 3");
        refused = client.waitForChatMessage("Unknown or incomplete command", TIMEOUT);
        showChat();
        check(refused && client.inventoryCount("minecraft:diamond") == 3,
            "non-op-give-refused", "vanilla_refusal=" + refused
                + " client_inventory_diamonds=" + client.inventoryCount("minecraft:diamond"));
    }

    private void scoreboard() throws Exception {
        command("scoreboard objectives add k dummy");
        check(await(() -> board().getObjective("k") != null), "objective-added", "client objective k exists");
        command("scoreboard objectives setdisplay sidebar k");
        check(await(() -> board().getDisplayObjective(DisplaySlot.SIDEBAR) == board().getObjective("k")),
            "sidebar-selected", "client sidebar objective=k");
        command("scoreboard players set @s k 5");
        check(await(() -> scoreEquals(5)), "sidebar-score-five", "client self score=" + read(this::score));
        command("team add red");
        check(await(() -> board().getPlayerTeam("red") != null), "team-added", "client team red exists");
        command("team modify red color red");
        check(await(() -> board().getPlayerTeam("red").getColor().orElse(null) == TeamColor.RED),
            "team-color-red", "client team color=" + read(() -> board().getPlayerTeam("red").getColor()));
        command("team join red @s");
        check(await(() -> {
            PlayerTeam team = board().getPlayersTeam(minecraft().player.getScoreboardName());
            return team != null && team.getName().equals("red") && team.getColor().orElse(null) == TeamColor.RED;
        }), "team-self-membership", "client self team=" + read(() -> minecraft().player.getTeam()));
    }

    private void effects() throws Exception {
        command("gamemode survival");
        require(await(() -> !minecraft().gameMode.getPlayerMode().isCreative()), "survival mode not received");
        command("effect clear @s");
        require(await(() -> minecraft().player.getActiveEffects().isEmpty()), "initial effects not clear");
        give("minecraft:golden_apple", 1);
        consume("minecraft:golden_apple", () -> minecraft().player.hasEffect(MobEffects.ABSORPTION));
        showInventory();
        check(read(() -> minecraft().player.hasEffect(MobEffects.ABSORPTION)), "golden-apple-effect",
            "client_active_effects=" + read(() -> minecraft().player.getActiveEffects().toString()));
        client.closeCurrentScreen(TIMEOUT);
        give("minecraft:milk_bucket", 1);
        consume("minecraft:milk_bucket", () -> minecraft().player.getActiveEffects().isEmpty());
        showInventory();
        check(read(() -> minecraft().player.getActiveEffects().isEmpty()), "milk-clears-effects",
            "client_active_effects=" + read(() -> minecraft().player.getActiveEffects().toString()));
        client.closeCurrentScreen(TIMEOUT);
        double before = read(() -> minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED));
        command("effect give @s speed 30 1");
        boolean changed = await(() -> minecraft().player.hasEffect(MobEffects.SPEED)
            && movementSpeedMatches(before, minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED), 1));
        showInventory();
        check(changed, "speed-two-attribute", "before=" + before + " after="
            + read(() -> minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED))
            + " effect=" + read(() -> minecraft().player.getEffect(MobEffects.SPEED)));
    }

    private void adversarial() throws Exception {
        command("clear @s minecraft:diamond");
        require(client.waitForInventoryCount("minecraft:diamond", 0, TIMEOUT), "initial inventory did not clear");
        clearChat();
        command("give @s minecraft:diamond 0");
        boolean refused = client.waitForChatMessage("Integer must not be less than 1, found 0", TIMEOUT);
        showChat();
        check(refused && client.inventoryCount("minecraft:diamond") == 0,
            "invalid-give-no-credit", "vanilla_integer_error=" + refused + " client_inventory_diamonds=0");
        client.closeCurrentScreen(TIMEOUT);
        command("scoreboard objectives add k dummy");
        require(await(() -> board().getObjective("k") != null), "initial objective missing");
        Objective original = read(() -> board().getObjective("k"));
        command("scoreboard objectives add k dummy");
        require(client.waitForTicks(20, TIMEOUT), "duplicate objective observation window failed");
        check(read(() -> board().getObjective("k") == original && board().getObjectiveNames().size() == 1),
            "duplicate-objective-preserved", "duplicate objective add does not replace/duplicate client objective");
        command("team add red");
        command("team join red @s");
        require(await(() -> board().getPlayersTeam(minecraft().player.getScoreboardName()) != null), "initial team missing");
        command("team leave @s");
        check(await(() -> board().getPlayersTeam(minecraft().player.getScoreboardName()) == null),
            "team-leave-clears-membership", "client self team=null");
        command("team remove red");
        check(await(() -> board().getPlayerTeam("red") == null), "team-remove-clears-client", "client team red=null");
        command("effect clear @s");
        require(await(() -> minecraft().player.getActiveEffects().isEmpty()), "effect reset failed");
        double before = read(() -> minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED));
        command("effect give @s speed 30 1");
        require(await(() -> movementSpeedMatches(before, minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED), 1)), "speed setup failed");
        command("effect give @s speed 60 0");
        require(client.waitForTicks(20, TIMEOUT), "weaker effect observation window failed");
        check(read(() -> minecraft().player.getEffect(MobEffects.SPEED).getAmplifier() == 1
            && movementSpeedMatches(before, minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED), 1)),
            "weaker-effect-does-not-stack", "stronger speed retained; no duplicated attribute multiplier");
        give("minecraft:milk_bucket", 1);
        consume("minecraft:milk_bucket", () -> minecraft().player.getActiveEffects().isEmpty()
            && Math.abs(minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED) - before) < 0.00001);
        check(true, "milk-restores-speed-baseline", "client movement_speed="
            + read(() -> minecraft().player.getAttributeValue(Attributes.MOVEMENT_SPEED)) + " baseline=" + before);
    }

    static boolean movementSpeedMatches(double baseline, double actual, int amplifier) {
        return baseline > 0 && Math.abs(actual - baseline * (1 + 0.2 * (amplifier + 1))) < 0.00001;
    }

    private boolean scoreEquals(int expected) {
        return score() == expected;
    }

    private int score() {
        Objective objective = board().getObjective("k");
        ReadOnlyScoreInfo score = objective == null ? null : board().getPlayerScoreInfo(minecraft().player, objective);
        return score == null ? Integer.MIN_VALUE : score.value();
    }

    private Scoreboard board() {
        return minecraft().getConnection().scoreboard();
    }

    void command(String value) throws Exception {
        observations.add("command: /" + value);
        client.sendCommand(value);
    }

    ScenarioHeldItem give(String item, int count) throws Exception {
        int before = client.inventoryCount(item);
        command("give @s " + item + " " + count);
        require(client.waitForInventoryCount(item, before + count, TIMEOUT), "give did not converge: " + item);
        ScenarioHeldItem held = client.selectHotbarItem(item, 1, TIMEOUT);
        require(held.matches(item, 1), "given item could not be selected: " + item);
        return held;
    }

    private void consume(String item, Probe finished) throws Exception {
        int before = client.inventoryCount(item);
        long inventoryVersion = ClientStateEvents.containerPacketVersion();
        var nativeUse = read(() -> {
            consumeStartGameTime = ClientStateEvents.serverGameTime();
            consumeStartTimeVersion = ClientStateEvents.serverTimeVersion();
            consumeStartNanos = System.nanoTime();
            minecraft().options.keyUse.setDown(true);
            return minecraft().gameMode.useItem(minecraft().player, InteractionHand.MAIN_HAND);
        });
        try {
            boolean consumed = await(() -> {
                minecraft().options.keyUse.setDown(true);
                return ClientStateEvents.containerPacketVersion() > inventoryVersion
                    && finished.get() && clientInventoryCount(item) == before - 1;
            });
            long finishGameTime = ClientStateEvents.serverGameTime();
            long finishTimeVersion = ClientStateEvents.serverTimeVersion();
            long finishNanos = System.nanoTime();
            observations.add("native-consume-window: item=" + item + " converged=" + consumed
                + " startGameTime=" + consumeStartGameTime + " endGameTime=" + finishGameTime
                + " startTimeVersion=" + consumeStartTimeVersion + " endTimeVersion=" + finishTimeVersion
                + " elapsedMs=" + (finishNanos - consumeStartNanos) / 1_000_000);
            require(consumed, "authoritative consumption/effect did not converge for " + item
                + read(() -> " count=" + clientInventoryCount(item) + " before=" + before
                    + " inventoryVersion=" + ClientStateEvents.containerPacketVersion()
                    + " beforeUse=" + inventoryVersion + " nativeUse=" + nativeUse
                    + " using=" + minecraft().player.isUsingItem()
                    + " usingTicks=" + minecraft().player.getTicksUsingItem()
                    + " hunger=" + minecraft().player.getFoodData().getFoodLevel()
                    + " held=" + minecraft().player.getMainHandItem()
                    + " food=" + minecraft().player.getMainHandItem().get(DataComponents.FOOD)
                    + " consumable=" + minecraft().player.getMainHandItem().get(DataComponents.CONSUMABLE)));
        } finally {
            read(() -> { minecraft().options.keyUse.setDown(false); return null; });
        }
    }

    private int clientInventoryCount(String id) {
        int count = 0;
        for (var stack : minecraft().player.getInventory().getNonEquipmentItems()) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    void showInventory() throws Exception {
        read(() -> { minecraft().gui.setScreen(new InventoryScreen(minecraft().player)); return null; });
    }

    private void showChat() throws Exception {
        read(() -> { minecraft().gui.setScreen(new ChatScreen("", false)); return null; });
    }

    private void clearChat() throws Exception {
        read(() -> { minecraft().gui.hud.getChat().clearMessages(false); return null; });
    }

    void check(boolean passed, String name, String details) throws Exception {
        observations.add(name + ": " + (passed ? "passed" : "failed") + " " + details);
        capture(name);
        require(passed, name + ": " + details);
    }

    void observe(String diagnostic) {
        observations.add(diagnostic);
    }

    private void capture(String name) throws Exception {
        require(client.waitForTicks(2, TIMEOUT), "no client ticks before screenshot " + name);
        Path path = screenshots.resolve(String.format(java.util.Locale.ROOT, "%02d-%s.png", ++step, name));
        facade.screenshot(path);
        observations.add("screenshot: " + path);
    }

    boolean await(Probe probe) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        do {
            long version = ClientStateEvents.tickVersion();
            if (read(probe::get)) {
                return true;
            }
            if (!ScenarioClientState.awaitClientTick(version, deadline)) {
                return read(probe::get);
            }
        } while (true);
    }

    <T> T read(java.util.concurrent.Callable<T> operation) throws Exception {
        return executor.callOnClientThread(operation);
    }

    static Minecraft minecraft() {
        return requireInPlay();
    }

    static void require(boolean passed, String message) {
        if (!passed) {
            throw new IllegalStateException(message);
        }
    }

    static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    @FunctionalInterface
    interface Probe {
        boolean get() throws Exception;
    }
}
