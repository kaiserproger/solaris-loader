package dev.solaris.loader.fabric;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local desktop picture source for the owner of a shared screen. Opened only after the
 * player confirmed sharing; nothing here runs on join or on use of a monitor.
 */
abstract class DesktopCapture implements AutoCloseable {
    /** One captured frame, row-major {@code 0xAARRGGBB}. */
    record Grab(int width, int height, int[] argb) {}

    abstract Grab grab() throws Exception;

    /** Whether {@link #control} can drive this desktop (Windows only; Linux is view-only). */
    abstract boolean controllable();

    /** Pointer/button input on the captured desktop at fractions {@code (0..1, 0..1)} of the picture. */
    void pointer(double fx, double fy) {
        throw new UnsupportedOperationException("view-only capture");
    }

    void button(int awtButtonMask, boolean pressed) {
        throw new UnsupportedOperationException("view-only capture");
    }

    void key(int awtKeyCode, boolean pressed) {
        throw new UnsupportedOperationException("view-only capture");
    }

    @Override
    public void close() {}

    /** The capture this platform supports, or empty with {@code reason} describing why not. */
    static Optional<DesktopCapture> open(StringBuilder reason) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        try {
            if (os.contains("win")) {
                return Optional.of(new RobotCapture());
            }
            if (os.contains("linux")) {
                return Optional.of(new X11Capture());
            }
            reason.append("Screen sharing needs Windows or Linux/X11");
        } catch (Exception | LinkageError error) {
            reason.append(error.getMessage() == null ? error.toString() : error.getMessage());
        }
        return Optional.empty();
    }

    /** Windows (and any non-headless AWT): whole primary screen through {@link Robot}. */
    private static final class RobotCapture extends DesktopCapture {
        private final Robot robot;
        private final Rectangle bounds;

        RobotCapture() throws Exception {
            if (GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("Java is running headless; no desktop to capture");
            }
            robot = new Robot();
            bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }

        @Override
        Grab grab() {
            BufferedImage image = robot.createScreenCapture(bounds);
            return new Grab(image.getWidth(), image.getHeight(),
                    image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
        }

        @Override
        boolean controllable() {
            return true;
        }

        @Override
        void pointer(double fx, double fy) {
            robot.mouseMove(bounds.x + (int) Math.round(fx * (bounds.width - 1)),
                    bounds.y + (int) Math.round(fy * (bounds.height - 1)));
        }

        @Override
        void button(int mask, boolean pressed) {
            if (pressed) {
                robot.mousePress(mask);
            } else {
                robot.mouseRelease(mask);
            }
        }

        @Override
        void key(int code, boolean pressed) {
            if (pressed) {
                robot.keyPress(code);
            } else {
                robot.keyRelease(code);
            }
        }
    }

    /** Linux X11 only: the existing FFmpeg x11grab spike on the player's {@code $DISPLAY}. */
    private static final class X11Capture extends DesktopCapture {
        private static final Pattern DIMENSIONS = Pattern.compile("dimensions:\\s+(\\d+)x(\\d+) pixels");
        private final String ffmpeg;
        private final String display;
        private final int width;
        private final int height;

        X11Capture() throws Exception {
            display = System.getenv("DISPLAY");
            if (display == null || !display.matches(":[0-9]+(?:\\.[0-9]+)?")) {
                throw new IllegalStateException(
                        "Linux screen sharing needs an X11 session ($DISPLAY); Wayland is not supported");
            }
            ffmpeg = which("ffmpeg").orElseThrow(
                    () -> new IllegalStateException("Linux screen sharing needs ffmpeg on PATH"));
            Process process = new ProcessBuilder("xdpyinfo", "-display", display)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String info = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            Matcher matcher = DIMENSIONS.matcher(info);
            if (!matcher.find()) {
                throw new IllegalStateException("Cannot read the X11 screen size (xdpyinfo)");
            }
            width = Math.min(4096, Integer.parseInt(matcher.group(1)));
            height = Math.min(4096, Integer.parseInt(matcher.group(2)));
        }

        private static Optional<String> which(String program) {
            String path = System.getenv("PATH");
            if (path == null) {
                return Optional.empty();
            }
            for (String directory : path.split(java.io.File.pathSeparator)) {
                Path candidate = Path.of(directory, program);
                if (Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toString());
                }
            }
            return Optional.empty();
        }

        @Override
        Grab grab() throws Exception {
            X11ComputerCapture.Frame frame = X11ComputerCapture.capture(ffmpeg, display, width, height);
            ByteBuffer rgba = frame.rgba();
            int[] argb = new int[width * height];
            for (int i = 0; i < argb.length; i++) {
                int r = rgba.get(i * 4) & 0xFF;
                int g = rgba.get(i * 4 + 1) & 0xFF;
                int b = rgba.get(i * 4 + 2) & 0xFF;
                argb[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            return new Grab(width, height, argb);
        }

        @Override
        boolean controllable() {
            return false;
        }
    }
}
