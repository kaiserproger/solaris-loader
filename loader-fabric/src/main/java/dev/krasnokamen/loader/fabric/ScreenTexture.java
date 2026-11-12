package dev.krasnokamen.loader.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.krasnokamen.loader.ScreenVideo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** One registered dynamic texture holding a shared-screen picture. Render thread only. */
final class ScreenTexture implements AutoCloseable {
    private static final class Linear extends DynamicTexture {
        Linear(int width, int height) {
            super(() -> "krasnokamen screen", width, height, false);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private final Identifier id;
    private DynamicTexture texture;
    private int width;
    private int height;

    ScreenTexture(Identifier id) {
        this.id = id;
    }

    Identifier id() {
        return id;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void set(ScreenVideo.Picture picture) {
        if (texture == null || width != picture.width() || height != picture.height()) {
            release();
            texture = new Linear(picture.width(), picture.height());
            width = picture.width();
            height = picture.height();
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        NativeImage pixels = texture.getPixels();
        int[] argb = picture.argb();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = argb[y * width + x];
                pixels.setPixelABGR(x, y,
                        (value & 0xFF00FF00) | ((value >> 16) & 0xFF) | ((value & 0xFF) << 16));
            }
        }
        texture.upload();
    }

    private void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }

    @Override
    public void close() {
        release();
    }
}
