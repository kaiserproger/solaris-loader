package dev.solaris.loader.fabric;

import dev.solaris.loader.ScreenVideo;
import dev.solaris.loader.ScreenProtocol;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The local player's open shared screen: captures the desktop and uploads H.264 pictures
 * only while the server's DEMAND is on, never faster than the demanded fps.
 */
final class ScreenShare implements AutoCloseable {
    private static final long TICK_MILLIS = 50;

    private final ScreenProtocol.Id id;
    private final DesktopCapture capture;
    private final Consumer<byte[]> send;
    private final Consumer<String> failed;
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "solaris-screen-capture");
                thread.setDaemon(true);
                return thread;
            });
    private volatile int maxWidth;
    private volatile int fps;
    private final ScreenVideo.Encoder encoder = new ScreenVideo.Encoder();
    private final AtomicBoolean keyWanted = new AtomicBoolean();
    private long sequence;
    private long nextAt;
    private int failures;
    private volatile boolean confirmed;
    private boolean wasActive;
    final long openedAt = System.nanoTime();

    /** {@code send} and {@code failed} are invoked on the capture thread. */
    ScreenShare(ScreenProtocol.Id id, DesktopCapture capture, Consumer<byte[]> send,
            Consumer<String> failed) {
        this.id = id;
        this.capture = capture;
        this.send = send;
        this.failed = failed;
        worker.scheduleWithFixedDelay(this::step, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    ScreenProtocol.Id id() {
        return id;
    }

    DesktopCapture capture() {
        return capture;
    }

    boolean confirmed() {
        return confirmed;
    }

    void confirm() {
        confirmed = true;
    }

    /** Notes that the server listed the screen as on; false after it was on means the show ended. */
    boolean activeEnded(boolean active) {
        boolean ended = wasActive && !active;
        wasActive |= active;
        return ended;
    }

    /** The server wants this stream's next picture to be a key frame (new viewer, resync, new demand). */
    void wantKey() {
        keyWanted.set(true);
    }

    /** Set from the client thread each tick from the relay's DEMAND state. */
    void demand(ScreenProtocol.Demand demand) {
        if (demand == null) {
            fps = 0;
        } else {
            maxWidth = demand.maxWidth();
            fps = demand.fps();
        }
    }

    private void step() {
        int rate = fps;
        long now = System.nanoTime();
        if (rate <= 0 || now < nextAt) {
            return;
        }
        nextAt = now + 1_000_000_000L / rate;
        try {
            DesktopCapture.Grab grab = capture.grab();
            boolean key = keyWanted.getAndSet(false);
            ScreenVideo.Encoder.Encoded picture =
                    encoder.encode(grab.argb(), grab.width(), grab.height(), maxWidth, key);
            if (picture != null) {
                ScreenVideo.parts(id, ++sequence, picture).forEach(send);
            }
            failures = 0;
        } catch (Exception error) {
            if (++failures >= 3) {
                worker.shutdown();
                failed.accept("Screen capture failed: " + error.getMessage());
            }
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
        capture.close();
    }
}
