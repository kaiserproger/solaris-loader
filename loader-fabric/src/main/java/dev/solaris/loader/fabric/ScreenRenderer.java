package dev.solaris.loader.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.solaris.loader.ScreenGeometry;
import dev.solaris.loader.ScreenVideo;
import dev.solaris.loader.ScreenProtocol;
import dev.solaris.loader.ScreenRelay;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every active shared screen as an unlit quad over its monitor rectangle: the
 * generic "on" panel until a frame arrives, then the picture letterboxed on a dark panel.
 */
final class ScreenRenderer {
    /** Distance beyond which a screen is not drawn. */
    private static final double RANGE = 96;
    private static final Identifier ON_ID = Identifier.parse("solaris:screen/on");
    private static final int ON_W = 32;
    private static final int ON_H = 16;
    /** Solid dark texel of the "on" panel (its top-left corner). */
    private static final float DARK_U = 2F / ON_W;
    private static final float DARK_V = 2F / ON_H;
    private static final double PICTURE_LIFT = 0.002;

    private final Map<ScreenProtocol.Id, ScreenTexture> pictures = new HashMap<>();
    private ScreenTexture on;
    private int counter;

    /** {@code own} is the local player's open screen: the server never lists it as active to its owner. */
    void render(LevelRenderContext context, ScreenRelay relay, int dimension, ScreenProtocol.Id own) {
        pruneTextures(relay);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (Map.Entry<ScreenProtocol.Id, ScreenRelay.Remote> entry : relay.active().entrySet()) {
            ScreenProtocol.Id id = entry.getKey();
            if (id.dimension() != dimension || distance(id, camera) > RANGE) {
                continue;
            }
            ScreenRelay.Remote remote = entry.getValue();
            ScreenVideo.Picture picture = remote.picture();
            ScreenTexture texture = null;
            if (picture != null) {
                texture = pictures.computeIfAbsent(id, ignored -> new ScreenTexture(
                        Identifier.parse("solaris:screen/s" + counter++)));
                if (remote.takeDirty() || texture.width() == 0) {
                    texture.set(picture);
                }
            }
            draw(context, id, camera, texture);
        }
        if (own != null && own.dimension() == dimension && !relay.active().containsKey(own)
                && distance(own, camera) <= RANGE) {
            draw(context, own, camera, null);
        }
    }

    private void draw(LevelRenderContext context, ScreenProtocol.Id id, Vec3 camera,
            ScreenTexture picture) {
        ScreenTexture panel = onPanel();
        ScreenGeometry.Basis basis = ScreenGeometry.basis(id);
        double[] normal = normal(id.side());
        int light = LightCoordsUtil.FULL_BRIGHT;
        PoseStack stack = context.poseStack();
        RenderType panelType = RenderTypes.entityCutout(panel.id());
        context.submitNodeCollector().submitCustomGeometry(stack, panelType,
                (pose, consumer) -> {
                    if (picture == null) {
                        double[] size = ScreenGeometry.fit(ON_W, ON_H, id.width(), id.height());
                        quad(pose, consumer, basis, camera, normal, light, id.width(), id.height(),
                                0, 0, id.width(), id.height(), DARK_U, DARK_V, DARK_U, DARK_V, 0);
                        quad(pose, consumer, basis, camera, normal, light, id.width(), id.height(),
                                (id.width() - size[0]) / 2, (id.height() - size[1]) / 2,
                                size[0], size[1], 0, 0, 1, 1, PICTURE_LIFT);
                    } else {
                        quad(pose, consumer, basis, camera, normal, light, id.width(), id.height(),
                                0, 0, id.width(), id.height(), DARK_U, DARK_V, DARK_U, DARK_V, 0);
                    }
                });
        if (picture != null) {
            double[] size = ScreenGeometry.fit(picture.width(), picture.height(), id.width(), id.height());
            context.submitNodeCollector().submitCustomGeometry(stack,
                    RenderTypes.entityCutout(picture.id()), (pose, consumer) -> quad(
                            pose, consumer, basis, camera, normal, light, id.width(), id.height(),
                            (id.width() - size[0]) / 2, (id.height() - size[1]) / 2,
                            size[0], size[1], 0, 0, 1, 1, PICTURE_LIFT));
        }
    }

    /**
     * Quad of {@code w x h} blocks at image offset ({@code du}, {@code dv}) from the face
     * origin, lifted {@code lift} blocks off the face; camera-relative coordinates.
     */
    private static void quad(PoseStack.Pose pose, VertexConsumer consumer,
            ScreenGeometry.Basis basis, Vec3 camera, double[] normal, int light,
            int faceW, int faceH, double du, double dv, double w, double h,
            float u0, float v0, float u1, float v1, double lift) {
        double[][] corner = {{du, dv, u0, v0}, {du + w, dv, u1, v0},
                {du + w, dv + h, u1, v1}, {du, dv + h, u0, v1}};
        for (double[] c : corner) {
            double x = basis.origin()[0] + basis.right()[0] * c[0] + basis.down()[0] * c[1]
                    + normal[0] * lift - camera.x;
            double y = basis.origin()[1] + basis.right()[1] * c[0] + basis.down()[1] * c[1]
                    + normal[1] * lift - camera.y;
            double z = basis.origin()[2] + basis.right()[2] * c[0] + basis.down()[2] * c[1]
                    + normal[2] * lift - camera.z;
            consumer.addVertex(pose, (float) x, (float) y, (float) z)
                    .setColor(-1)
                    .setUv((float) c[2], (float) c[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, (float) normal[0], (float) normal[1], (float) normal[2]);
        }
    }

    private static double[] normal(int side) {
        return switch (side) {
            case 2 -> new double[] {0, 0, -1};
            case 3 -> new double[] {0, 0, 1};
            case 4 -> new double[] {-1, 0, 0};
            default -> new double[] {1, 0, 0};
        };
    }

    private static double distance(ScreenProtocol.Id id, Vec3 camera) {
        double cx = id.x() + (id.axis() == 0 ? id.width() : 1) / 2.0;
        double cy = id.y() + id.height() / 2.0;
        double cz = id.z() + (id.axis() == 2 ? id.width() : 1) / 2.0;
        return Math.sqrt((cx - camera.x) * (cx - camera.x) + (cy - camera.y) * (cy - camera.y)
                + (cz - camera.z) * (cz - camera.z));
    }

    private ScreenTexture onPanel() {
        if (on == null) {
            on = new ScreenTexture(ON_ID);
            int[] argb = new int[ON_W * ON_H];
            for (int y = 0; y < ON_H; y++) {
                for (int x = 0; x < ON_W; x++) {
                    boolean mark = x >= 14 && x < 18 && y >= 5 && y < 11;
                    argb[y * ON_W + x] = mark ? 0xFF1EE65A : 0xFF0C161C;
                }
            }
            on.set(new ScreenVideo.Picture(ON_W, ON_H, argb));
        }
        return on;
    }

    private void pruneTextures(ScreenRelay relay) {
        Iterator<Map.Entry<ScreenProtocol.Id, ScreenTexture>> it = pictures.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ScreenProtocol.Id, ScreenTexture> entry = it.next();
            ScreenRelay.Remote remote = relay.active().get(entry.getKey());
            if (remote == null || remote.picture() == null) {
                entry.getValue().close();
                it.remove();
            }
        }
    }

    /** Release every GL texture (disconnect). */
    void close() {
        pictures.values().forEach(ScreenTexture::close);
        pictures.clear();
        if (on != null) {
            on.close();
            on = null;
        }
    }
}
