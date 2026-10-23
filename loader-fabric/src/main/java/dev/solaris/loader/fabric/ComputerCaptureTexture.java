package dev.solaris.loader.fabric;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Spike texture owner. Construct/upload/close only on the current GL render thread. */
final class ComputerCaptureTexture implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private int texture = GL11.glGenTextures();
    private int width;
    private int height;

    int id() { return texture; }
    void upload(X11ComputerCapture.Frame frame) {
        requireOwner();
        if (texture == 0) throw new IllegalStateException("Texture is closed");
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            if (width != frame.width() || height != frame.height()) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, frame.width(), frame.height(),
                        0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frame.rgba());
                width = frame.width(); height = frame.height();
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frame.rgba());
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }
    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Capture texture belongs to render thread");
    }
    @Override public void close() {
        requireOwner();
        if (texture != 0) { GL11.glDeleteTextures(texture); texture = 0; }
    }
}
