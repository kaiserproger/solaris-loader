package dev.solaris.loader.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solaris.loader.LoaderClientTransport;
import dev.solaris.loader.LoaderEnvironment;
import dev.solaris.loader.LoaderOutgoing;
import dev.solaris.loader.LoaderPermission;
import dev.solaris.loader.LoaderPlatform;
import dev.solaris.loader.LoaderScreenKind;
import dev.solaris.loader.LoaderViewActionRequest;
import dev.solaris.loader.LoaderViewMessage;
import dev.solaris.loader.LoaderWidget;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Method;
import net.minecraftforge.network.ForgePayload;
import net.minecraftforge.network.NetworkRegistry;

final class ForgeLoaderTransportTest {
    private static final byte[] SERVER_MANIFEST_PAYLOAD = Base64.getDecoder().decode(
            "F3NvbGFyaXM6bG9hZGVyL21hbmlmZXN0eyJwcm90b2NvbCI6MSwiYnVuZGxlcyI6W3sib3duZXIiOiJydWJ5LWxpdmUiLCJpZCI6InJpY2gtY29udGVudCIsInZlcnNpb24iOiIxIiwiYXJ0aWZhY3QiOiJjbGllbnQvcmljaC1jb250ZW50LnppcCIsInNoYTI1NiI6IjcwZGQ1MjdhYzBjNTA3NWZhZjFkZmY2NWU4ZTQyNmY2NTc3NDZkNDIyMTVlNGZjNGZkMTgyNDRhYzViOWQ3NjUiLCJzaXplX2J5dGVzIjoxMDA5LCJsb2FkZXJzIjpbImZhYnJpYyIsIm5lb2ZvcmdlIiwiZm9yZ2UiXSwiY29udGVudCI6WyJibG9ja3MiLCJpdGVtcyIsInNjcmVlbnMiLCJhc3NldHMiLCJpbnRlcmFjdGlvbnMiXSwicGVybWlzc2lvbnMiOlsicmVnaXN0ZXJfYmxvY2tzIiwicmVnaXN0ZXJfaXRlbXMiLCJvcGVuX3NjcmVlbnMiLCJsb2FkX2Fzc2V0cyIsInNlbmRfaW50ZXJhY3Rpb25zIl0sImNhY2hlX2tleSI6InJ1YnktbGl2ZTpyaWNoLWNvbnRlbnQvMS83MGRkNTI3YWMwYzUwNzVmYWYxZGZmNjVlOGU0MjZmNjU3NzQ2ZDQyMjE1ZTRmYzRmZDE4MjQ0YWM1YjlkNzY1In0seyJvd25lciI6InNhcHBoaXJlLWxpdmUiLCJpZCI6InJpY2gtY29udGVudCIsInZlcnNpb24iOiIxIiwiYXJ0aWZhY3QiOiJjbGllbnQvcmljaC1jb250ZW50LnppcCIsInNoYTI1NiI6IjZjMTY0MjViMmJmOWM1NDE1MTg0MzQ1YzRjYjZiYzEwZTk4YmY0MWEzZTczZGMyN2IzOTE1YWE3OTYyNDE4YTUiLCJzaXplX2J5dGVzIjoxMDQ4LCJsb2FkZXJzIjpbImZhYnJpYyIsIm5lb2ZvcmdlIiwiZm9yZ2UiXSwiY29udGVudCI6WyJibG9ja3MiLCJpdGVtcyIsInNjcmVlbnMiLCJhc3NldHMiLCJpbnRlcmFjdGlvbnMiXSwicGVybWlzc2lvbnMiOlsicmVnaXN0ZXJfYmxvY2tzIiwicmVnaXN0ZXJfaXRlbXMiLCJvcGVuX3NjcmVlbnMiLCJsb2FkX2Fzc2V0cyIsInNlbmRfaW50ZXJhY3Rpb25zIl0sImNhY2hlX2tleSI6InNhcHBoaXJlLWxpdmU6cmljaC1jb250ZW50LzEvNmMxNjQyNWIyYmY5YzU0MTUxODQzNDVjNGNiNmJjMTBlOThiZjQxYTNlNzNkYzI3YjM5MTVhYTc5NjI0MThhNSJ9XX0="
    );
    private static final byte[] ARCHIVE = archive();
    private static final String HASH = sha256(ARCHIVE);
    private static final String CACHE_KEY = "example:screen/1/" + HASH;

    @TempDir
    Path cacheDirectory;

    @Test
    void configurationServerIdentityFallsBackToRemoteAddress() {
        assertEquals(
                "play.example.test:25565",
                SolarisForgeLoader.serverIdentity(
                        "play.example.test:25565",
                        new InetSocketAddress("127.0.0.1", 25570)));
        assertEquals(
                "127.0.0.1:25570",
                SolarisForgeLoader.serverIdentity(
                        null,
                        new InetSocketAddress("127.0.0.1", 25570)));
    }

    @Test
    void verifiedCacheProducesRawConfigurationAck() throws Exception {
        byte[] manifest = manifest("forge");
        LoaderEnvironment environment = environment();
        Path cached = cacheDirectory.resolve("example/screen/1/" + HASH + ".bundle");
        Files.createDirectories(cached.getParent());
        Files.write(cached, ARCHIVE);

        FriendlyByteBuf inbound = new FriendlyByteBuf(Unpooled.buffer());
        LoaderManifestPayload.CODEC.encode(inbound, new LoaderManifestPayload(manifest));
        LoaderManifestPayload decodedManifest = LoaderManifestPayload.CODEC.decode(inbound);
        LoaderOutgoing outgoing = new LoaderClientTransport()
                .acceptManifest(decodedManifest.bytes(), environment, cacheDirectory);
        assertEquals(LoaderOutgoing.Kind.ACKNOWLEDGEMENT, outgoing.kind());
        byte[] acknowledgement = outgoing.bytes();
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderAckPayload.CODEC.encode(wire, new LoaderAckPayload(acknowledgement));
        LoaderAckPayload decoded = LoaderAckPayload.CODEC.decode(wire);

        assertArrayEquals(acknowledgement, decoded.bytes());
        assertTrue(new String(acknowledgement, StandardCharsets.UTF_8)
                .contains("\"platform\":\"forge\""));
        assertInstanceOf(LoaderAckPayload.class, SolarisForgeLoader.payload(outgoing));
        var screen = SolarisForgeLoader.activeContent().screens().get("example:welcome");
        assertNotNull(screen);
        assertEquals(LoaderScreenKind.SETTLEMENT, screen.kind());
        assertEquals(
                new LoaderWidget.ActionButton(
                        "example:confirm", "Confirm", true, Optional.empty()),
                screen.widget("example:confirm").orElseThrow());

        byte[] actionBytes = LoaderViewActionRequest.action(
                        "solaris:view-1", 1, "example:confirm", 1, List.of(), Optional.empty())
                .orElseThrow();
        RegistryFriendlyByteBuf actionWire =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        LoaderViewActionPayload.CODEC.encode(
                actionWire, new LoaderViewActionPayload(actionBytes));
        assertArrayEquals(
                actionBytes,
                LoaderViewActionPayload.CODEC.decode(actionWire).bytes());
        assertEquals(
                "solaris:loader/view_action",
                LoaderViewActionPayload.TYPE.id().toString());

        byte[] openView = openViewPayload();
        RegistryFriendlyByteBuf openWire =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        LoaderViewPayload.CODEC.encode(
                openWire, new LoaderViewPayload(openView));
        assertEquals(openView.length, openWire.readableBytes());
        assertEquals("solaris:loader/view", LoaderViewPayload.TYPE.id().toString());
        LoaderViewMessage.Open open = assertInstanceOf(
                LoaderViewMessage.Open.class,
                SolarisForgeLoader.decodeView(
                                LoaderViewPayload.CODEC.decode(openWire).bytes(), true)
                        .orElseThrow());
        assertEquals("solaris:view-1", open.viewInstanceId());
        assertEquals(2, open.revision());
        assertEquals("example:welcome", open.viewId());
        assertEquals("Welcome", open.title());
        assertTrue(SolarisForgeLoader.decodeView(openView, false).isEmpty());
        assertEquals("solaris:loader/manifest", LoaderManifestPayload.TYPE.id().toString());
        assertEquals("solaris:loader/ack", LoaderAckPayload.TYPE.id().toString());
        assertEquals("solaris:loader/request", LoaderRequestPayload.TYPE.id().toString());
        assertEquals("solaris:loader/artifact", LoaderArtifactPayload.TYPE.id().toString());

        FriendlyByteBuf requestWire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderRequestPayload.CODEC.encode(
                requestWire, new LoaderRequestPayload("request".getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals(
                "request".getBytes(StandardCharsets.UTF_8),
                LoaderRequestPayload.CODEC.decode(requestWire).bytes());
        FriendlyByteBuf artifactWire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderArtifactPayload.CODEC.encode(
                artifactWire, new LoaderArtifactPayload("artifact".getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals(
                "artifact".getBytes(StandardCharsets.UTF_8),
                LoaderArtifactPayload.CODEC.decode(artifactWire).bytes());
    }

    @Test
    void decodeCapturedManifestThroughConfigCodec() {
        ensureLoaderChannelRegistered();

        assertNotNull(NetworkRegistry.findTarget(LoaderManifestPayload.TYPE.id()));

        FriendlyByteBuf capturedPayload =
                new FriendlyByteBuf(Unpooled.wrappedBuffer(SERVER_MANIFEST_PAYLOAD));
        ClientboundCustomPayloadPacket decoded =
                ClientboundCustomPayloadPacket.CONFIG_STREAM_CODEC.decode(capturedPayload);

        assertFalse(decoded.payload() instanceof DiscardedPayload);
        assertInstanceOf(ForgePayload.class, decoded.payload());

        ForgePayload payload = (ForgePayload) decoded.payload();
        assertEquals(LoaderManifestPayload.TYPE.id(), payload.id());
    }

    private static void ensureLoaderChannelRegistered() {
        if (NetworkRegistry.findTarget(LoaderManifestPayload.TYPE.id()) != null) {
            return;
        }
        try {
            Method buildChannel =
                    SolarisForgeLoader.class.getDeclaredMethod("buildChannel");
            buildChannel.setAccessible(true);
            buildChannel.invoke(null);
        } catch (Exception error) {
            throw new AssertionError("Failed to initialize Solaris Forge channel", error);
        }
    }

    private static LoaderEnvironment environment() {
        return new LoaderEnvironment() {
            @Override
            public LoaderPlatform platform() {
                return LoaderPlatform.FORGE;
            }

            @Override
            public String loaderVersion() {
                return "0.1.0";
            }

            @Override
            public Set<LoaderPermission> grantedPermissions() {
                return Set.of(
                        LoaderPermission.PRESENT_VIEWS,
                        LoaderPermission.SEND_VIEW_ACTIONS);
            }
        };
    }

    private static byte[] manifest(String platform) {
        return """
                {"protocol":3,"bundles":[{
                  "owner":"example","id":"screen","version":"1",
                  "artifact":"client/screen.zip","sha256":"%s","size_bytes":%d,
                  "loaders":["%s"],"content":["views","view_actions"],
                  "permissions":["present_views","send_view_actions"],
                  "cache_key":"%s"
                }]}
                """.formatted(HASH, ARCHIVE.length, platform, CACHE_KEY)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] archive() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry("solaris-client.json"));
                zip.write("""
                        {"schema":2,"screens":[{
                          "id":"example:welcome","kind":"settlement","title":"Welcome",
                          "widgets":[{
                            "type":"action_button","action_id":"example:confirm","label":"Confirm"
                          }]
                        }],"blocks":[],"items":[],"assets":[]}
                        """.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static byte[] openViewPayload() {
        return """
                {"protocol":3,"message":"open_view","view_instance_id":"solaris:view-1",
                 "revision":2,"view_id":"example:welcome","title":"Welcome","model":{
                   "page":0,"page_count":2,
                   "rows":[{"cells":["Hamlet","12"]}],
                   "fields":[{"id":"amount","number":4}],
                   "actions":[{"action_id":"example:confirm","enabled":true,"label":"Confirm"}],
                   "tabs":[{"id":"one","label":"One"}],
                   "resource_entries":[{"id":"example:ruby","have":1,"need":2}],
                   "markers":[{"marker_id":"anchor"}]
                 }}
                """.getBytes(StandardCharsets.UTF_8);
    }
}
