package dev.krasnokamen.loader.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.krasnokamen.loader.LoaderActivatedContent;
import dev.krasnokamen.loader.ScreenGeometry;
import dev.krasnokamen.loader.ScreenProtocol;
import dev.krasnokamen.loader.ScreenRelay;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client end of the {@code krasnokamen:screen} relay (core ADR 0018): shows shared monitors,
 * asks owners for viewing permission, answers requests for the local player's own screen
 * and uploads that screen's frames on demand. Active only while the monitor bundle is
 * admitted on the current connection; nothing is captured without an explicit dialog.
 */
final class ScreenClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("krasnokamen-loader/screen");
    private static final int KEY_F8 = 297;
    private static final long OPEN_TIMEOUT_NANOS = 10_000_000_000L;

    private static final ScreenRelay RELAY = new ScreenRelay();
    private static final ScreenRenderer RENDERER = new ScreenRenderer();
    private static final Map<ScreenProtocol.Id, ScreenAudioSound> AUDIO = new HashMap<>();
    private static ScreenShare share;
    private static boolean leaveDown;

    private ScreenClient() {}

    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(ScreenPayload.TYPE, ScreenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScreenPayload.TYPE, ScreenPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ScreenPayload.TYPE, (payload, context) -> {
            Connection origin = context.packetContext().orElseThrow(PacketContext.CONNECTION);
            byte[] bytes = payload.bytes();
            context.client().execute(() -> {
                var listener = context.client().getConnection();
                if (listener != null && listener.getConnection() == origin && origin.isConnected()
                        && admitted()) {
                    receive(bytes);
                }
            });
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ScreenClient::reset));
        ClientTickEvents.END_CLIENT_TICK.register(ScreenClient::tick);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Minecraft client = Minecraft.getInstance();
            if (!level.isClientSide() || player != client.player || hand != InteractionHand.MAIN_HAND
                    || player.isShiftKeyDown() || !admitted()) {
                return InteractionResult.PASS;
            }
            return screenAt(level, hit.getBlockPos(), hit.getDirection())
                    .map(ScreenClient::use).orElse(InteractionResult.PASS);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && admitted()) {
                RENDERER.render(context, RELAY, dimension(client.level),
                        share == null ? null : share.id());
            }
        });
    }

    /** True only when the computer monitor bundle is active. */
    private static boolean admitted() {
        return KrasnokamenFabricLoader.activeContent().blocks().containsKey("computer:monitor");
    }

    static boolean isMonitorCarrier(LoaderActivatedContent content, String carrier) {
        var monitor = content.blocks().get("computer:monitor");
        return monitor != null && monitor.carrier().equals(carrier);
    }

    private static void reset() {
        closeShare();
        RELAY.clear();
        AUDIO.values().forEach(Minecraft.getInstance().getSoundManager()::stop);
        AUDIO.clear();
        RENDERER.close();
        leaveDown = false;
    }

    private static void closeShare() {
        if (share != null) {
            share.close();
            share = null;
        }
    }

    private static int dimension(Level level) {
        return switch (level.dimension().identifier().toString()) {
            case "minecraft:overworld" -> 0;
            case "minecraft:the_nether" -> 1;
            case "minecraft:the_end" -> 2;
            default -> -1;
        };
    }

    // ---- network ---------------------------------------------------------------

    private static void receive(byte[] bytes) {
        LOGGER.debug("krasnokamen:screen received kind {} ({} bytes)", bytes.length > 1 ? bytes[1] : -1, bytes.length);
        try {
            RELAY.receive(bytes);
        } catch (IllegalArgumentException error) {
            LOGGER.warn("Ignoring invalid krasnokamen:screen message: {}", error.getMessage());
        }
        RELAY.takeOutbox().forEach(ScreenClient::send);
    }

    private static void send(byte[] bytes) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            LOGGER.debug("krasnokamen:screen sent kind {} ({} bytes)", bytes.length > 1 ? bytes[1] : -1, bytes.length);
            ClientPlayNetworking.send(new ScreenPayload(bytes));
        }
    }

    // ---- interaction -----------------------------------------------------------

    private static Optional<ScreenProtocol.Id> screenAt(Level level, BlockPos pos, Direction face) {
        if (face.getAxis().isVertical() || dimension(level) < 0) {
            return Optional.empty();
        }
        LoaderActivatedContent content = KrasnokamenFabricLoader.activeContent();
        if (!admitted()) return Optional.empty();
        ScreenGeometry.Members members = (x, y, z) -> {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            return isMonitorCarrier(content, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                    && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == face;
        };
        return ScreenGeometry.rectangle(pos.getX(), pos.getY(), pos.getZ(), face.get3DDataValue(),
                dimension(level), members);
    }

    private static InteractionResult use(ScreenProtocol.Id id) {
        Minecraft client = Minecraft.getInstance();
        if (share != null && share.id().equals(id)) {
            client.execute(() -> client.setScreen(ownerMenu()));
            return InteractionResult.SUCCESS;
        }
        if (RELAY.active().containsKey(id)) {
            if (!RELAY.watching().contains(id)) {
                send(ScreenProtocol.request(id));
                RELAY.watching().add(id);
                notice("Viewing requested. Waiting for the owner; F8 cancels/leaves");
            }
            return InteractionResult.SUCCESS;
        }
        if (share != null) {
            notice("You already share a screen; stop it first");
            return InteractionResult.SUCCESS;
        }
        StringBuilder reason = new StringBuilder();
        Optional<DesktopCapture> probe = DesktopCapture.open(reason);
        if (probe.isEmpty()) {
            notice(reason.toString());
            return InteractionResult.SUCCESS;
        }
        // Opened only to prove this machine can capture; the dialog decides whether it is used.
        DesktopCapture capture = probe.get();
        client.execute(() -> client.setScreen(new ScreenDialog(
                "Share your screen on this monitor?",
                List.of("Your entire primary screen can be shown to players you allow.",
                        "Nothing is shared until a player asks and you allow it."),
                List.of(new ScreenDialog.Choice("Share", () -> startShare(id, capture)),
                        new ScreenDialog.Choice("Cancel", capture::close)),
                capture::close)));
        return InteractionResult.SUCCESS;
    }

    private static void startShare(ScreenProtocol.Id id, DesktopCapture capture) {
        Minecraft client = Minecraft.getInstance();
        Connection origin = client.getConnection() == null ? null : client.getConnection().getConnection();
        if (origin == null) {
            capture.close();
            return;
        }
        closeShare();
        share = new ScreenShare(id, capture, bytes -> client.execute(() -> {
            var listener = client.getConnection();
            if (listener != null && listener.getConnection() == origin && origin.isConnected()) {
                send(bytes);
            }
        }), message -> client.execute(() -> {
            closeShare();
            notice(message);
        }));
        send(ScreenProtocol.open(id));
        notice("Sharing requested; players ask to view by right-clicking the monitor");
    }

    private static ScreenDialog ownerMenu() {
        ScreenShare current = share;
        return new ScreenDialog("Your screen is shared on this monitor",
                List.of("Viewers need your permission and must look at the monitor.",
                        current != null && current.capture().controllable()
                                ? "Desktop control is available while this window is open."
                                : "View-only: this platform cannot be controlled from the game."),
                List.of(new ScreenDialog.Choice("Stop sharing", ScreenClient::stopShare),
                        new ScreenDialog.Choice("Close", () -> { })),
                () -> { });
    }

    private static void stopShare() {
        if (share != null) {
            ScreenProtocol.Id id = share.id();
            closeShare();
            send(ScreenProtocol.close(id));
            notice("Screen sharing stopped");
        }
    }

    private static void notice(String text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.setOverlayMessage(Component.literal(text), false));
    }

    // ---- per tick --------------------------------------------------------------

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null || !admitted()) {
            return;
        }
        updateAudio(client);
        String message = RELAY.takeNotice();
        if (message != null) {
            notice(message);
        }
        boolean f8 = InputConstants.isKeyDown(client.getWindow(), KEY_F8);
        if (f8 && !leaveDown && client.screen == null) {
            leave();
        }
        leaveDown = f8;
        if (client.screen == null && RELAY.pendingRequests() > 0) {
            ScreenProtocol.Requested request = RELAY.pollRequest();
            client.setScreen(new ScreenDialog("Screen viewing request",
                    List.of(request.name() + " asks to view your shared screen."),
                    List.of(new ScreenDialog.Choice("Allow", () -> send(ScreenProtocol.answer(request.request(), true))),
                            new ScreenDialog.Choice("Deny", () -> send(ScreenProtocol.answer(request.request(), false)))),
                    () -> send(ScreenProtocol.answer(request.request(), false))));
        }
        if (share != null) {
            ScreenProtocol.Id id = share.id();
            boolean refused = RELAY.takeRefused().contains(id);
            boolean active = RELAY.active().containsKey(id);
            if (RELAY.takeKeyWanted(id)) {
                share.wantKey();
            }
            share.demand(RELAY.demand().get(id));
            if (active || RELAY.takeAcknowledged().contains(id)) {
                share.confirm();
            }
            if (refused || share.activeEnded(active)
                    || !share.confirmed() && System.nanoTime() - share.openedAt > OPEN_TIMEOUT_NANOS) {
                closeShare();
                notice("Screen sharing ended");
            }
        }
    }

    private static void updateAudio(Minecraft client) {
        var manager = client.getSoundManager();
        AUDIO.entrySet().removeIf(entry -> {
            ScreenRelay.Remote remote = RELAY.active().get(entry.getKey());
            if (remote == null || remote.audio() == null || remote.audio().closed()
                    || !entry.getValue().owns(remote.audio())) {
                manager.stop(entry.getValue());
                return true;
            }
            return false;
        });
        RELAY.active().forEach((id, remote) -> {
            if (remote.audio() != null && !remote.audio().closed() && !AUDIO.containsKey(id)) {
                ScreenAudioSound sound = new ScreenAudioSound(id, remote.audio());
                AUDIO.put(id, sound);
                manager.play(sound);
            }
        });
    }

    /** F8: stop viewing every screen this player asked to see. */
    private static void leave() {
        for (ScreenProtocol.Id id : List.copyOf(RELAY.watching())) {
            send(ScreenProtocol.leave(id));
        }
        RELAY.watching().clear();
        RELAY.clearPictures();
        notice("Viewing ended");
    }
}
