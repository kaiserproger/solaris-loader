package dev.solaris.loader.fabric;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Explicit X11 spike, not a Wayland fallback. No automatic capture on join/use.
 * A production native helper can supply the same tightly-packed RGBA contract.
 */
final class X11ComputerCapture {
    record Frame(int width, int height, ByteBuffer rgba) {
        Frame {
            int expected = byteCount(width, height);
            if (!rgba.isDirect() || rgba.remaining() != expected) {
                throw new IllegalArgumentException("Capture must be exact direct RGBA8");
            }
        }
    }
    private X11ComputerCapture() {}
    static int byteCount(int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException("Capture dimensions must be 1..4096");
        }
        return Math.multiplyExact(Math.multiplyExact(width, height), 4);
    }
    /** Caller must obtain local consent for this display/region before invoking. */
    static Frame capture(String ffmpeg, String display, int width, int height) throws Exception {
        int expected = byteCount(width, height);
        if (!display.matches(":[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("An explicit local X11 display is required");
        }
        Process process = new ProcessBuilder(ffmpeg, "-nostdin", "-loglevel", "error",
                "-f", "x11grab", "-draw_mouse", "0", "-video_size", width + "x" + height,
                "-i", display + "+0,0", "-frames:v", "1", "-threads", "1",
                "-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var bytes = executor.submit(() -> process.getInputStream().readNBytes(expected + 1));
            try {
                byte[] rgba = bytes.get(10, TimeUnit.SECONDS);
                if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0 || rgba.length != expected) {
                    throw new IOException("Capture helper failed or produced a malformed frame");
                }
                ByteBuffer direct = ByteBuffer.allocateDirect(expected);
                direct.put(rgba).flip();
                return new Frame(width, height, direct);
            } finally {
                process.destroyForcibly();
                process.getInputStream().close();
                bytes.cancel(true);
            }
        } finally {
            process.destroyForcibly();
        }
    }
}
