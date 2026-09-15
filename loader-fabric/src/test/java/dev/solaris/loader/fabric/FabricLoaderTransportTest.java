package dev.solaris.loader.fabric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

final class FabricLoaderTransportTest {
    private static final byte[] ARCHIVE = archive();
    private static final String HASH = sha256(ARCHIVE);
    private static final String CACHE_KEY = "example:screen/1/" + HASH;
    private static final byte[] OPEN_VIEW = """
            {"protocol":3,"message":"open_view","view_instance_id":"solaris:view-1",\
            "revision":1,"view_id":"example:welcome","title":"Welcome","model":{\
            "page":0,"page_count":1,"rows":[],"fields":[],"actions":[],"tabs":[],\
            "resource_entries":[],"markers":[]}}
            """.getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path cacheDirectory;

    @Test
    void verifiedCacheProducesRawConfigurationAck() throws Exception {
        byte[] manifest = manifest("fabric");
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
                .contains("\"platform\":\"fabric\""));
        assertInstanceOf(LoaderAckPayload.class, SolarisFabricLoader.payload(outgoing));
        var screens = SolarisFabricLoader.activeContent().screens();
        assertTrue(screens.containsKey("example:welcome"));
        var welcome = screens.get("example:welcome");
        assertEquals(LoaderScreenKind.SETTLEMENT, welcome.kind());
        assertInstanceOf(
                LoaderWidget.ActionButton.class,
                welcome.widget("example:confirm").orElseThrow());

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

        FriendlyByteBuf openWire = new FriendlyByteBuf(Unpooled.buffer());
        LoaderViewPayload.CODEC.encode(openWire, new LoaderViewPayload(OPEN_VIEW));
        byte[] decodedView = LoaderViewPayload.CODEC.decode(openWire).bytes();
        assertArrayEquals(OPEN_VIEW, decodedView);
        assertEquals("solaris:loader/view", LoaderViewPayload.TYPE.id().toString());
        LoaderViewMessage.Open open = assertInstanceOf(
                LoaderViewMessage.Open.class,
                SolarisFabricLoader.decodeView(decodedView, true).orElseThrow());
        assertEquals("solaris:view-1", open.viewInstanceId());
        assertTrue(SolarisFabricLoader.decodeView(decodedView, false).isEmpty());
        SolarisFabricLoader.clearActiveContent();
        assertTrue(SolarisFabricLoader.decodeView(decodedView, true).isEmpty());

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

    private static LoaderEnvironment environment() {
        return new LoaderEnvironment() {
            @Override
            public LoaderPlatform platform() {
                return LoaderPlatform.FABRIC;
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
                            "type":"action_button","action_id":"example:confirm",
                            "label":"Confirm"
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
}
