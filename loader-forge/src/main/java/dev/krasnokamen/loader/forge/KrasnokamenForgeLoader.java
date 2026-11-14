package dev.krasnokamen.loader.forge;

import dev.krasnokamen.loader.LoaderActivatedContent;
import dev.krasnokamen.loader.LoaderHandshake;
import dev.krasnokamen.loader.LoaderOutgoing;
import dev.krasnokamen.loader.LoaderPermissionController;
import dev.krasnokamen.loader.LoaderPermissionRequest;
import dev.krasnokamen.loader.LoaderPlatform;
import dev.krasnokamen.loader.LoaderRuntimeSettings;
import dev.krasnokamen.loader.LoaderViewMessage;
import dev.krasnokamen.loader.LoaderViewActionRequest;
import dev.krasnokamen.loader.LoaderViewKey;
import dev.krasnokamen.loader.LoaderSoundCommand;
import dev.krasnokamen.loader.minecraft.LoaderMinecraftInput;
import dev.krasnokamen.loader.minecraft.LoaderMinecraftSound;
import dev.krasnokamen.loader.minecraft.LoaderMinecraftView;
import dev.krasnokamen.loader.minecraft.LoaderRuntimeResourcePack;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadConnection;
import net.minecraftforge.network.payload.PayloadFlow;

@Mod(KrasnokamenForgeLoader.MOD_ID)
public final class KrasnokamenForgeLoader {
    public static final String MOD_ID = "krasnokamen_loader";
    private static final Map<Connection, LoaderPermissionController> CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicReference<LoaderActivatedContent> ACTIVE_CONTENT =
            new AtomicReference<>(LoaderActivatedContent.empty());
    private static final LoaderRuntimeResourcePack RESOURCE_PACK =
            new LoaderRuntimeResourcePack();
    private static final AtomicBoolean RESOURCE_PACK_REGISTERED =
            new AtomicBoolean();
    private static final LoaderForgeMcpRuntime MCP_RUNTIME =
            LoaderForgeMcpRuntime.createIfEnabled();
    private static volatile boolean clientSetupComplete;
    private static Channel<CustomPacketPayload> channel;

    public KrasnokamenForgeLoader(FMLJavaModLoadingContext context) {
        FMLClientSetupEvent.getBus(context.getModBusGroup())
                .addListener(KrasnokamenForgeLoader::onClientSetup);
        ClientTickEvent.Pre.BUS.addListener(KrasnokamenForgeLoader::onClientTickPre);
        ClientTickEvent.Post.BUS.addListener(KrasnokamenForgeLoader::onClientTickPost);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(
                KrasnokamenForgeLoader::onLoggingIn);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(
                KrasnokamenForgeLoader::onLoggingOut);
        AddGuiOverlayLayersEvent.BUS.addListener(KrasnokamenForgeLoader::registerGuiLayers);
        channel = buildChannel();
    }

    private static void registerGuiLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                Identifier.parse(LoaderMinecraftView.HUD_LAYER), LoaderMinecraftView::renderHud);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ConfigTracker.loadDefaultServerConfigs();
        clientSetupComplete = true;
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        if (MCP_RUNTIME != null && clientSetupComplete) {
            MCP_RUNTIME.beforeTick();
        }
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        if (MCP_RUNTIME != null && clientSetupComplete) {
            MCP_RUNTIME.afterTick();
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        publishMcpState();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        publishMcpState();
        Connection connection = event.getConnection();
        if (connection != null) {
            unmount(Minecraft.getInstance(), connection);
        }
    }

    private static Channel<CustomPacketPayload> buildChannel() {
        PayloadConnection<CustomPacketPayload> connection =
                ChannelBuilder.named("krasnokamen:loader")
                        .networkProtocolVersion(LoaderHandshake.PROTOCOL_VERSION)
                        .optional()
                        .payloadChannel();
        PayloadFlow<FriendlyByteBuf, CustomPacketPayload> flow =
                connection.configuration().clientbound();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                connection.play().clientbound();
        play.add(
                LoaderViewPayload.TYPE,
                LoaderViewPayload.CODEC,
                (payload, context) -> {
                    Minecraft client = Minecraft.getInstance();
                    Connection origin = context.getConnection();
                    client.execute(() -> {
                        var listener = client.getConnection();
                        boolean originActive = origin.isConnected()
                                && listener != null
                                && listener.getConnection() == origin;
                        LoaderViewMessage.decode(
                                        payload.bytes(), ACTIVE_CONTENT.get(), originActive)
                                .ifPresent(message -> LoaderMinecraftView.present(
                                        message,
                                        ACTIVE_CONTENT.get(),
                                        bytes -> sendViewAction(client, origin, bytes)));
                    });
                    context.setPacketHandled(true);
                });
        play.add(
                LoaderSoundPayload.TYPE,
                LoaderSoundPayload.CODEC,
                (payload, context) -> {
                    Connection origin = context.getConnection();
                    Minecraft.getInstance().execute(() -> {
                        var listener = Minecraft.getInstance().getConnection();
                        boolean originActive = origin.isConnected() && listener != null
                                && listener.getConnection() == origin;
                        LoaderSoundCommand.resolve(payload.bytes(), ACTIVE_CONTENT.get(), originActive)
                                .ifPresent(LoaderMinecraftSound::present);
                    });
                    context.setPacketHandled(true);
                });
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> playServerbound =
                play.serverbound();
        playServerbound.add(
                LoaderViewActionPayload.TYPE,
                LoaderViewActionPayload.CODEC,
                (payload, context) -> context.setPacketHandled(true));
        flow.add(
                LoaderManifestPayload.TYPE,
                LoaderManifestPayload.CODEC,
                (manifest, context) -> {
                    try {
                        ACTIVE_CONTENT.set(LoaderActivatedContent.empty());
                        Connection networkConnection = context.getConnection();
                        Minecraft client = Minecraft.getInstance();
                        ensureResourcePackRegistered(client);
                        LoaderPermissionController permissionController = controller(networkConnection);
                        watchConnection(client, networkConnection, permissionController);
                        permissionController.acceptManifest(
                                        manifest.bytes(),
                                        LoaderPlatform.FORGE,
                                        LoaderRuntimeSettings.LOADER_VERSION,
                                        serverIdentity(networkConnection),
                                        LoaderRuntimeSettings.cacheDirectory(),
                                        networkConnection::isConnected,
                                        KrasnokamenForgeLoader::prompt,
                                        outgoing -> send(
                                                Minecraft.getInstance(),
                                                networkConnection,
                                                outgoing),
                                        reason -> disconnect(networkConnection, reason));
                    } catch (IllegalArgumentException error) {
                        disconnect(context.getConnection(), error.getMessage());
                    }
                    context.setPacketHandled(true);
                });
        flow.add(
                LoaderArtifactPayload.TYPE,
                LoaderArtifactPayload.CODEC,
                (artifact, context) -> {
                    try {
                        controller(context.getConnection())
                                .acceptArtifact(
                                        artifact.bytes(),
                                        context.getConnection()::isConnected)
                                .ifPresent(outgoing -> send(
                                        Minecraft.getInstance(),
                                        context.getConnection(),
                                        outgoing));
                    } catch (IllegalArgumentException error) {
                        disconnect(context.getConnection(), error.getMessage());
                    }
                    context.setPacketHandled(true);
                });
        flow.add(
                LoaderProbePayload.TYPE,
                LoaderProbePayload.CODEC,
                KrasnokamenForgeLoader::handleProbe);
        PayloadFlow<FriendlyByteBuf, CustomPacketPayload> serverbound = flow.serverbound();
        serverbound.add(
                LoaderRequestPayload.TYPE,
                LoaderRequestPayload.CODEC,
                (payload, context) -> {
                    context.getConnection()
                            .disconnect(Component.literal(
                                    "Krasnokamen Loader request is client-only"));
                    context.setPacketHandled(true);
                });
        serverbound.add(
                LoaderAckPayload.TYPE,
                LoaderAckPayload.CODEC,
                (payload, context) -> {
                    context.getConnection()
                            .disconnect(Component.literal(
                                    "Krasnokamen Loader acknowledgement is client-only"));
                    context.setPacketHandled(true);
                });
        serverbound.add(
                LoaderHelloPayload.TYPE,
                LoaderHelloPayload.CODEC,
                (payload, context) -> {
                    context.getConnection()
                            .disconnect(Component.literal(
                                    "Krasnokamen Loader hello is client-only"));
                    context.setPacketHandled(true);
                });
        return flow.build();
    }

    static void handleProbe(
            LoaderProbePayload probe,
            net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        sendHello(context.getConnection());
        context.setPacketHandled(true);
    }

    static void sendHello(Connection connection) {
        channel.send(new LoaderHelloPayload(), connection);
    }

    static CustomPacketPayload payload(LoaderOutgoing outgoing) {
        activate(outgoing);
        return switch (outgoing.kind()) {
            case REQUEST -> new LoaderRequestPayload(outgoing.bytes());
            case ACKNOWLEDGEMENT -> new LoaderAckPayload(outgoing.bytes());
        };
    }

    private static void send(
            Minecraft client,
            Connection connection,
            LoaderOutgoing outgoing) {
        if (outgoing.kind() == LoaderOutgoing.Kind.REQUEST) {
            channel.send(payload(outgoing), connection);
            return;
        }
        RESOURCE_PACK.mount(
                        client,
                        connection,
                        connection::isConnected,
                        outgoing.activatedContent())
                .whenCompleteAsync((unused, error) -> {
                    if (error != null
                            || !connection.isConnected()
                            || !RESOURCE_PACK.owns(connection)) {
                        unmount(client, connection);
                        disconnect(
                                connection,
                                error == null
                                        ? "connection closed before Loader resources mounted"
                                        : "mounting Loader resources: " + error.getMessage());
                        return;
                    }
                    channel.send(payload(outgoing), connection);
                }, client);
    }

    static void activate(LoaderOutgoing outgoing) {
        if (outgoing.kind() == LoaderOutgoing.Kind.ACKNOWLEDGEMENT) {
            LoaderMinecraftView.clear();
            LoaderMinecraftSound.clear();
            ACTIVE_CONTENT.set(outgoing.activatedContent());
            LoaderMinecraftInput.armRequestKeys(
                    outgoing.activatedContent(),
                    view -> tryRequestView(view.value()));
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

    private static void watchConnection(
            Minecraft client,
            Connection connection,
            LoaderPermissionController permissionController) {
        connection.channel().closeFuture().addListener(ignored -> {
            permissionController.close();
            unmount(client, connection);
        });
    }

    private static void ensureResourcePackRegistered(Minecraft client) {
        if (RESOURCE_PACK_REGISTERED.compareAndSet(false, true)) {
            client.getResourcePackRepository()
                    .addPackFinder(RESOURCE_PACK.repositorySource());
        }
    }

    private static void unmount(
            Minecraft client,
            Connection connection) {
        RESOURCE_PACK.unmount(
                client,
                connection,
                KrasnokamenForgeLoader::clearActiveContent);
    }

    static Optional<LoaderViewMessage> decodeView(
            byte[] payload,
            boolean connectionActive) {
        return LoaderViewMessage.decode(payload, ACTIVE_CONTENT.get(), connectionActive);
    }

    static void requestView(String viewKey) {
        if (!tryRequestView(viewKey)) {
            throw new IllegalStateException(
                    "Loader view request requires an active declared view");
        }
    }

    /** Send one declared view request on the active Loader connection; false when none applies. */
    static boolean tryRequestView(String viewKey) {
        Minecraft client = Minecraft.getInstance();
        var listener = client.getConnection();
        Connection origin = listener == null ? null : listener.getConnection();
        boolean active = origin != null && origin.isConnected() && RESOURCE_PACK.owns(origin);
        Optional<byte[]> bytes = LoaderViewActionRequest.request(
                new LoaderViewKey(viewKey), ACTIVE_CONTENT.get(), active);
        bytes.ifPresent(request -> sendViewAction(client, origin, request));
        return bytes.isPresent();
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
            channel.send(new LoaderViewActionPayload(bytes), origin);
        }
    }

    private static LoaderPermissionController controller(Connection connection) {
        synchronized (CONTROLLERS) {
            return CONTROLLERS.computeIfAbsent(
                    connection,
                    ignored -> new LoaderPermissionController(
                            LoaderRuntimeSettings.permissionDecisionsFile()));
        }
    }

    private static void prompt(
            LoaderPermissionRequest request,
            Consumer<Boolean> decision) {
        Minecraft client = Minecraft.getInstance();
        String permissions = request.permissions().stream()
                .map(permission -> permission.wireName())
                .collect(Collectors.joining(", "));
        client.execute(() -> {
            Screen parent = client.gui.screen();
            client.gui.setScreen(new ConfirmScreen(
                    allowed -> {
                        client.gui.setScreen(parent);
                        publishMcpState();
                        decision.accept(allowed);
                    },
                    Component.literal("Allow Krasnokamen content from " + request.serverIdentity() + "?"),
                    Component.literal("Requested permissions: " + permissions
                            + ". Downloaded content is untrusted."),
                    Component.literal("Allow"),
                    Component.literal("Deny")));
            publishMcpState();
        });
    }

    private static void publishMcpState() {
        if (MCP_RUNTIME != null) {
            MCP_RUNTIME.publishState();
        }
    }

    private static String serverIdentity(Connection connection) {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return serverIdentity(
                server == null ? null : server.ip,
                connection.getRemoteAddress());
    }

    static String serverIdentity(
            String configuredAddress,
            SocketAddress remoteAddress) {
        if (configuredAddress != null && !configuredAddress.isBlank()) {
            return configuredAddress;
        }
        if (remoteAddress instanceof InetSocketAddress address) {
            return address.getHostString() + ":" + address.getPort();
        }
        throw new IllegalArgumentException(
                "Krasnokamen Loader cannot identify the current server");
    }

    private static void disconnect(Connection connection, String reason) {
        connection.disconnect(Component.literal(
                "Krasnokamen Loader rejected manifest: " + reason));
    }
}
