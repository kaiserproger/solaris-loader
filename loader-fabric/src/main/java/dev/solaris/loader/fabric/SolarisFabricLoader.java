package dev.solaris.loader.fabric;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderOutgoing;
import dev.solaris.loader.LoaderPermissionController;
import dev.solaris.loader.LoaderPermissionRequest;
import dev.solaris.loader.LoaderPlatform;
import dev.solaris.loader.LoaderRuntimeSettings;
import dev.solaris.loader.LoaderSoundCommand;
import dev.solaris.loader.LoaderViewMessage;
import dev.solaris.loader.minecraft.LoaderMinecraftSound;
import dev.solaris.loader.fabric.mixin.ClientCommonPacketListenerAccessor;
import dev.solaris.loader.fabric.mixin.PackRepositoryAccessor;
import dev.solaris.loader.minecraft.LoaderRuntimeResourcePack;
import dev.solaris.loader.minecraft.LoaderMinecraftView;
import dev.solaris.loader.minecraft.LoaderMinecraftBlock;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.RepositorySource;

public final class SolarisFabricLoader implements ClientModInitializer {
    private static final Map<Object, LoaderPermissionController> CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicReference<LoaderActivatedContent> ACTIVE_CONTENT =
            new AtomicReference<>(LoaderActivatedContent.empty());
    private static final AtomicReference<LoaderFabricMcpRuntime> MCP_RUNTIME =
            new AtomicReference<>();
    private static final LoaderRuntimeResourcePack RESOURCE_PACK =
            new LoaderRuntimeResourcePack();
    private static final AtomicReference<Object> ACTIVE_ORIGIN =
            new AtomicReference<>();

    @Override
    public void onInitializeClient() {
        Minecraft client = Minecraft.getInstance();
        LoaderFabricMcpRuntime mcpRuntime =
                LoaderFabricMcpRuntime.createIfEnabled();
        if (mcpRuntime != null) {
            MCP_RUNTIME.set(mcpRuntime);
            ClientTickEvents.START_CLIENT_TICK.register(
                    ignored -> mcpRuntime.beforeTick());
            ClientTickEvents.END_CLIENT_TICK.register(
                    ignored -> mcpRuntime.afterTick());
        }
        ClientPlayConnectionEvents.JOIN.register((handler, sender, joinedClient) -> {
            if (mcpRuntime != null) {
                mcpRuntime.publishState();
            }
        });
        PackRepositoryAccessor repository =
                (PackRepositoryAccessor) client.getResourcePackRepository();
        repository.solaris$setSources(mutablePackSources(
                repository.solaris$sources(),
                RESOURCE_PACK.repositorySource()));
        HudElementRegistry.addLast(
                Identifier.parse(LoaderMinecraftView.HUD_LAYER), LoaderMinecraftView::renderHud);
        PayloadTypeRegistry.clientboundConfiguration()
                .register(LoaderManifestPayload.TYPE, LoaderManifestPayload.CODEC);
        PayloadTypeRegistry.clientboundConfiguration()
                .register(LoaderArtifactPayload.TYPE, LoaderArtifactPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration()
                .register(LoaderAckPayload.TYPE, LoaderAckPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration()
                .register(LoaderRequestPayload.TYPE, LoaderRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(LoaderViewPayload.TYPE, LoaderViewPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(LoaderSoundPayload.TYPE, LoaderSoundPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(LoaderViewActionPayload.TYPE, LoaderViewActionPayload.CODEC);
        if (!ClientConfigurationNetworking.registerGlobalReceiver(
                LoaderManifestPayload.TYPE, SolarisFabricLoader::handleManifest)) {
            throw new IllegalStateException("Solaris Loader manifest receiver is already registered");
        }
        if (!ClientConfigurationNetworking.registerGlobalReceiver(
                LoaderArtifactPayload.TYPE, SolarisFabricLoader::handleArtifact)) {
            throw new IllegalStateException("Solaris Loader artifact receiver is already registered");
        }
        ClientConfigurationConnectionEvents.DISCONNECT.register(
                (handler, disconnectingClient) -> {
                    unmount(disconnectingClient, handler);
                    if (mcpRuntime != null) {
                        mcpRuntime.publishState();
                    }
                });
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, disconnectingClient) -> {
                    Object origin = ACTIVE_ORIGIN.get();
                    if (origin != null) {
                        unmount(disconnectingClient, origin);
                    }
                    if (mcpRuntime != null) {
                        mcpRuntime.publishState();
                    }
                });
        ClientPlayNetworking.registerGlobalReceiver(
                LoaderViewPayload.TYPE,
                (payload, context) -> {
                    Connection origin =
                            context.packetContext().orElseThrow(PacketContext.CONNECTION);
                    context.client().execute(() -> {
                        var listener = context.client().getConnection();
                        boolean originActive = origin.isConnected()
                                && listener != null
                                && listener.getConnection() == origin;
                        LoaderViewMessage.decode(
                                        payload.bytes(), ACTIVE_CONTENT.get(), originActive)
                                .ifPresent(message -> LoaderMinecraftView.present(
                                        message,
                                        ACTIVE_CONTENT.get(),
                                        actionBytes -> sendViewAction(
                                                context.client(), origin, actionBytes)));
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(
                LoaderSoundPayload.TYPE,
                (payload, context) -> {
                    Connection origin = context.packetContext().orElseThrow(PacketContext.CONNECTION);
                    context.client().execute(() -> {
                        var listener = context.client().getConnection();
                        boolean originActive = origin.isConnected() && listener != null
                                && listener.getConnection() == origin;
                        LoaderSoundCommand.resolve(payload.bytes(), ACTIVE_CONTENT.get(), originActive)
                                .ifPresent(LoaderMinecraftSound::present);
                    });
                });
    }

    private static void handleManifest(
            LoaderManifestPayload manifest,
            ClientConfigurationNetworking.Context context) {
        try {
            ACTIVE_CONTENT.set(LoaderActivatedContent.empty());
            PacketSender sender = context.responseSender();
            Object origin = context.packetListener();
            controller(context.packetListener()).acceptManifest(
                    manifest.bytes(),
                    LoaderPlatform.FABRIC,
                    LoaderRuntimeSettings.LOADER_VERSION,
                    serverIdentity(context.packetListener()),
                    LoaderRuntimeSettings.cacheDirectory(),
                    context.packetListener()::isAcceptingMessages,
                    (request, decision) -> prompt(context.client(), request, decision),
                    outgoing -> send(
                            context.client(),
                            origin,
                            context.packetListener()::isAcceptingMessages,
                            sender,
                            outgoing),
                    reason -> disconnect(context.client(), reason));
        } catch (IllegalArgumentException error) {
            disconnect(context.client(), error.getMessage());
        }
    }

    private static void handleArtifact(
            LoaderArtifactPayload artifact,
            ClientConfigurationNetworking.Context context) {
        try {
            Object origin = context.packetListener();
            controller(context.packetListener())
                    .acceptArtifact(
                            artifact.bytes(),
                            context.packetListener()::isAcceptingMessages)
                    .ifPresent(outgoing -> send(
                            context.client(),
                            origin,
                            context.packetListener()::isAcceptingMessages,
                            context.responseSender(),
                            outgoing));
        } catch (IllegalArgumentException error) {
            disconnect(context.client(), error.getMessage());
        }
    }

    private static void send(
            Minecraft client,
            Object origin,
            BooleanSupplier originActive,
            PacketSender sender,
            LoaderOutgoing outgoing) {
        if (outgoing.kind() == LoaderOutgoing.Kind.REQUEST) {
            sender.sendPacket(payload(outgoing));
            return;
        }
        RESOURCE_PACK.mount(
                        client,
                        origin,
                        originActive,
                        outgoing.activatedContent())
                .whenCompleteAsync((unused, error) -> {
                    if (error != null
                            || !originActive.getAsBoolean()
                            || !RESOURCE_PACK.owns(origin)) {
                        unmount(client, origin);
                        disconnect(
                                client,
                                error == null
                                        ? "connection closed before Loader resources mounted"
                                        : "mounting Loader resources: " + error.getMessage());
                        return;
                    }
                    ACTIVE_ORIGIN.set(origin);
                    sender.sendPacket(payload(outgoing));
                }, client);
    }

    static CustomPacketPayload payload(LoaderOutgoing outgoing) {
        activate(outgoing);
        return switch (outgoing.kind()) {
            case REQUEST -> new LoaderRequestPayload(outgoing.bytes());
            case ACKNOWLEDGEMENT -> new LoaderAckPayload(outgoing.bytes());
        };
    }

    static void activate(LoaderOutgoing outgoing) {
        if (outgoing.kind() == LoaderOutgoing.Kind.ACKNOWLEDGEMENT) {
            LoaderMinecraftView.clear();
            LoaderMinecraftSound.clear();
            ACTIVE_CONTENT.set(outgoing.activatedContent());
        }
    }

    public static LoaderActivatedContent activeContent() {
        return ACTIVE_CONTENT.get();
    }

    static void clearActiveContent() {
        LoaderMinecraftView.clear();
        LoaderMinecraftSound.clear();
        ACTIVE_CONTENT.set(LoaderActivatedContent.empty());
    }

    static Set<RepositorySource> mutablePackSources(
            Set<RepositorySource> existing,
            RepositorySource runtime) {
        Set<RepositorySource> sources = new LinkedHashSet<>(existing);
        sources.add(runtime);
        return sources;
    }

    private static void unmount(
            Minecraft client,
            Object origin) {
        RESOURCE_PACK.unmount(
                client,
                origin,
                () -> {
                    ACTIVE_ORIGIN.compareAndSet(origin, null);
                    clearActiveContent();
                });
    }

    static Optional<LoaderViewMessage> decodeView(
            byte[] payload,
            boolean connectionActive) {
        return LoaderViewMessage.decode(payload, ACTIVE_CONTENT.get(), connectionActive);
    }

    private static void sendViewAction(
            Minecraft client,
            Connection origin,
            byte[] bytes) {
        var listener = client.getConnection();
        boolean originActive = origin.isConnected()
                && listener != null
                && listener.getConnection() == origin;
        if (originActive) {
            ClientPlayNetworking.send(new LoaderViewActionPayload(bytes));
        }
    }

    private static LoaderPermissionController controller(Object connection) {
        synchronized (CONTROLLERS) {
            return CONTROLLERS.computeIfAbsent(
                    connection,
                    ignored -> new LoaderPermissionController(
                            LoaderRuntimeSettings.permissionDecisionsFile(),
                            LoaderMinecraftBlock::carrierStateIds));
        }
    }

    private static void prompt(
            Minecraft client,
            LoaderPermissionRequest request,
            Consumer<Boolean> decision) {
        String permissions = request.permissions().stream()
                .map(permission -> permission.wireName())
                .collect(Collectors.joining(", "));
        client.execute(() -> {
            Screen parent = client.screen;
            client.setScreen(new ConfirmScreen(
                    allowed -> {
                        client.setScreen(parent);
                        publishMcpState();
                        decision.accept(allowed);
                    },
                    Component.literal("Allow Solaris content from " + request.serverIdentity() + "?"),
                    Component.literal("Requested permissions: " + permissions
                            + ". Downloaded content is untrusted."),
                    Component.literal("Allow"),
                    Component.literal("Deny")));
            publishMcpState();
        });
    }

    private static void publishMcpState() {
        LoaderFabricMcpRuntime runtime = MCP_RUNTIME.get();
        if (runtime != null) {
            runtime.publishState();
        }
    }

    private static String serverIdentity(Object packetListener) {
        ServerData server =
                ((ClientCommonPacketListenerAccessor) packetListener)
                        .solaris$serverData();
        if (server == null || server.ip == null || server.ip.isBlank()) {
            throw new IllegalArgumentException(
                    "Solaris Loader cannot identify the current server");
        }
        return server.ip;
    }

    private static void disconnect(Minecraft client, String reason) {
        client.execute(() -> client.disconnect(
                new DisconnectedScreen(
                        new TitleScreen(),
                        CommonComponents.CONNECT_FAILED,
                        Component.literal("Solaris Loader rejected manifest: " + reason)),
                false));
    }
}
