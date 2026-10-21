package dev.solaris.loader.neoforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solaris.loader.LoaderClientTransport;
import dev.solaris.loader.LoaderEnvironment;
import dev.solaris.loader.LoaderOutgoing;
import dev.solaris.loader.LoaderPermission;
import dev.solaris.loader.LoaderPlatform;
import dev.solaris.loader.LoaderViewKey;
import dev.solaris.loader.LoaderViewActionRequest;
import dev.solaris.loader.LoaderViewMessage;
import dev.solaris.loader.LoaderWidget;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NeoForgeLoaderTransportTest {
    private static final byte[] ARCHIVE = archive();
    private static final String HASH = sha256(ARCHIVE);
    private static final String CACHE_KEY = "example:screen/1/" + HASH;

    @TempDir
    Path cacheDirectory;

    @Test
    void probeCodecRoundTripAndHandlerSendsExactlyOneHello() {
        assertEquals(
                "solaris:loader/probe",
                LoaderProbePayload.TYPE.id().toString());
        assertEquals(
                "solaris:loader/hello",
                LoaderHelloPayload.TYPE.id().toString());

        FriendlyByteBuf probeWire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderProbePayload.CODEC.encode(probeWire, new LoaderProbePayload());
        assertEquals(0, probeWire.readableBytes());
        LoaderProbePayload.CODEC.decode(probeWire);

        FriendlyByteBuf helloWire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderHelloPayload.CODEC.encode(helloWire, new LoaderHelloPayload());
        assertEquals(0, helloWire.readableBytes());
        LoaderHelloPayload.CODEC.decode(helloWire);

        FriendlyByteBuf trailing = new FriendlyByteBuf(Unpooled.buffer());
        trailing.writeByte(1);
        try {
            LoaderProbePayload.CODEC.decode(trailing);
            throw new AssertionError("probe with trailing bytes must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        FriendlyByteBuf helloTrailing = new FriendlyByteBuf(Unpooled.buffer());
        helloTrailing.writeByte(1);
        try {
            LoaderHelloPayload.CODEC.decode(helloTrailing);
            throw new AssertionError("hello with trailing bytes must be rejected");
        } catch (IllegalArgumentException expected) {
        }

        java.util.List<String> replied = new java.util.ArrayList<>();
        Object listener = java.lang.reflect.Proxy.newProxyInstance(
                NeoForgeLoaderTransportTest.class.getClassLoader(),
                new Class<?>[] {
                    net.neoforged.neoforge.common.extensions.ICommonPacketListener.class
                },
                (proxy, method, args) -> {
                    if (method.getName().equals("send")
                            && args != null
                            && args.length == 1
                            && args[0]
                                    instanceof
                                    net.minecraft.network.protocol.common.custom.CustomPacketPayload
                                    payload) {
                        replied.add(payload.type().id().toString());
                        return null;
                    }
                    throw new UnsupportedOperationException(
                            "probe hello needs no listener " + method.getName());
                });
        net.neoforged.neoforge.network.handling.IPayloadContext context =
                (net.neoforged.neoforge.network.handling.IPayloadContext)
                        java.lang.reflect.Proxy.newProxyInstance(
                                NeoForgeLoaderTransportTest.class.getClassLoader(),
                                new Class<?>[] {
                                    net.neoforged.neoforge.network.handling.IPayloadContext.class
                                },
                                (proxy, method, args) -> {
                                    if (method.getName().equals("listener")) {
                                        return listener;
                                    }
                                    if (method.getName().equals("reply")
                                            && args != null
                                            && args.length == 1
                                            && args[0]
                                                    instanceof
                                                    net.minecraft.network.protocol.common.custom.CustomPacketPayload
                                                    payload) {
                                        replied.add(payload.type().id().toString());
                                        return null;
                                    }
                                    throw new UnsupportedOperationException(
                                            "probe hello needs no context " + method.getName());
                                });
        SolarisNeoForgeLoader.handleProbe(new LoaderProbePayload(), context);
        assertEquals(List.of("solaris:loader/hello"), List.copyOf(replied));
    }

    @Test
    void configurationServerIdentityFallsBackToRemoteAddress() {
        assertEquals(
                "play.example.test:25565",
                SolarisNeoForgeLoader.serverIdentity(
                        "play.example.test:25565",
                        new InetSocketAddress("127.0.0.1", 25570)));
        assertEquals(
                "127.0.0.1:25570",
                SolarisNeoForgeLoader.serverIdentity(
                        null,
                        new InetSocketAddress("127.0.0.1", 25570)));
    }

    @Test
    void verifiedCacheProducesRawConfigurationAck() throws Exception {
        byte[] manifest = manifest("neoforge");
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
                .contains("\"platform\":\"neoforge\""));
        assertInstanceOf(LoaderAckPayload.class, SolarisNeoForgeLoader.payload(outgoing));
        var screen = SolarisNeoForgeLoader.activeContent()
                .screens()
                .get("example:welcome");
        var confirm = assertInstanceOf(
                LoaderWidget.ActionButton.class,
                screen.widget("example:confirm").orElseThrow());
        assertEquals("Confirm", confirm.label());
        byte[] actionBytes = LoaderViewActionRequest
                .action("solaris:view-1", 1, "example:confirm", 1, List.of(), Optional.empty())
                .orElseThrow();
        FriendlyByteBuf actionWire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderViewActionPayload.CODEC.encode(
                actionWire,
                new LoaderViewActionPayload(actionBytes));
        assertArrayEquals(
                actionBytes,
                LoaderViewActionPayload.CODEC.decode(actionWire).bytes());
        assertEquals(
                "solaris:loader/view_action",
                LoaderViewActionPayload.TYPE.id().toString());
        byte[] viewBytes = viewPayload();
        FriendlyByteBuf viewWire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderViewPayload.CODEC.encode(
                viewWire, new LoaderViewPayload(viewBytes));
        assertEquals(viewBytes.length, viewWire.readableBytes());
        byte[] decodedView = LoaderViewPayload.CODEC.decode(viewWire).bytes();
        var open = assertInstanceOf(
                LoaderViewMessage.Open.class,
                SolarisNeoForgeLoader.decodeView(decodedView, true).orElseThrow());
        assertEquals("solaris:view-1", open.viewInstanceId());
        assertEquals(new LoaderViewKey("example:welcome"), open.viewId());
        assertEquals("solaris:loader/view", LoaderViewPayload.TYPE.id().toString());
        assertTrue(SolarisNeoForgeLoader.decodeView(decodedView, false).isEmpty());
        SolarisNeoForgeLoader.clearActiveContent();
        assertTrue(SolarisNeoForgeLoader.decodeView(viewBytes, true).isEmpty());
        assertEquals("solaris:loader/manifest", LoaderManifestPayload.TYPE.id().toString());
        assertEquals("solaris:loader/ack", LoaderAckPayload.TYPE.id().toString());
        assertEquals("solaris:loader/request", LoaderRequestPayload.TYPE.id().toString());
        assertEquals("solaris:loader/artifact", LoaderArtifactPayload.TYPE.id().toString());
        assertEquals("solaris:loader/probe", LoaderProbePayload.TYPE.id().toString());
        assertEquals("solaris:loader/hello", LoaderHelloPayload.TYPE.id().toString());

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

    private static LoaderEnvironment environment() {
        return new LoaderEnvironment() {
            @Override
            public LoaderPlatform platform() {
                return LoaderPlatform.NEOFORGE;
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
                          "id":"example:welcome","title":"Welcome",
                          "widgets":[{"type":"action_button","action_id":"example:confirm",
                                      "label":"Confirm"}]
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

    private static byte[] viewPayload() {
        return """
                {"protocol":3,"message":"open_view","view_instance_id":"solaris:view-1","revision":1,
                 "view_id":"example:welcome","title":"Welcome","model":{
                 "page":0,"page_count":1,"rows":[{"cells":["Hamlet","12"]}],
                 "fields":[{"id":"note","text":"hi"}],
                 "actions":[{"action_id":"example:confirm","enabled":true,"label":"Confirm"}],
                 "tabs":[],"resource_entries":[],"markers":[]}}
                """.getBytes(StandardCharsets.UTF_8);
    }
}
