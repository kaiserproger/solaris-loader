package dev.solaris.loader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Client-side state machine of one connection's {@code solaris:screen} traffic.
 * Single-threaded (client thread). Frames never carry input; nothing here decides policy:
 * the server relay is the only authority.
 */
public final class ScreenRelay {
    private static final int MAX_ACTIVE = 128;
    private static final int MAX_DEMAND = 4;
    private static final int MAX_REQUESTS = 32;

    /** A screen another (or this) player has switched on, and its latest picture if any. */
    public static final class Remote {
        private ScreenVideo.Picture picture;
        private Long sequence;
        private boolean dirty = true;
        private ScreenVideo.Decoder decoder;
        private ScreenAudio audio;
        /** A key frame has been decoded since the stream last (re)started. */
        private boolean synced;
        /** A KEY_REQUEST is outstanding; cleared by the next decoded key frame. */
        private boolean keyAsked;

        public ScreenVideo.Picture picture() {
            return picture;
        }

        public ScreenAudio audio() {
            return audio;
        }

        /** True once after the picture changed (new frame or cleared). */
        public boolean takeDirty() {
            boolean was = dirty;
            dirty = false;
            return was;
        }

        void clear() {
            if (audio != null) {
                audio.close();
                audio = null;
            }
            picture = null;
            sequence = null;
            dirty = true;
            decoder = null;
            synced = false;
            keyAsked = false;
        }
    }

    private final Map<ScreenProtocol.Id, Remote> active = new LinkedHashMap<>();
    private final Map<ScreenProtocol.Id, ScreenProtocol.Demand> demand = new TreeMap<>();
    private final Deque<ScreenProtocol.Requested> requests = new ArrayDeque<>();
    private final Set<ScreenProtocol.Id> watching = new HashSet<>();
    private final Set<ScreenProtocol.Id> refused = new HashSet<>();
    /** Screens whose owner (this client) must start its next picture as a key frame. */
    private final Set<ScreenProtocol.Id> keyWanted = new HashSet<>();
    /** Messages the viewer side needs sent (KEY_REQUEST), drained after {@link #receive}. */
    private final List<byte[]> outbox = new ArrayList<>();
    private final Set<ScreenProtocol.Id> acknowledged = new HashSet<>();
    private String notice;

    public Map<ScreenProtocol.Id, Remote> active() {
        return active;
    }

    public Map<ScreenProtocol.Id, ScreenProtocol.Demand> demand() {
        return demand;
    }

    /** Screens this client asked to view and has not left. */
    public Set<ScreenProtocol.Id> watching() {
        return watching;
    }

    /**
     * Screens the server answered an OPEN for (it always replies DEMAND off) since the last call;
     * the owner's proof that the show is registered, since owners are never sent ACTIVE.
     */
    public Set<ScreenProtocol.Id> takeAcknowledged() {
        Set<ScreenProtocol.Id> was = Set.copyOf(acknowledged);
        acknowledged.clear();
        return was;
    }

    /** Whether the server asked this owner for a key frame of the screen since the last call. */
    public boolean takeKeyWanted(ScreenProtocol.Id id) {
        return keyWanted.remove(id);
    }

    /** Messages to send to the server because of what was just received. */
    public List<byte[]> takeOutbox() {
        List<byte[]> was = List.copyOf(outbox);
        outbox.clear();
        return was;
    }

    /** Screens the server refused or stopped serving since the last call (owner side: end the share). */
    public Set<ScreenProtocol.Id> takeRefused() {
        Set<ScreenProtocol.Id> was = Set.copyOf(refused);
        refused.clear();
        return was;
    }

    public ScreenProtocol.Requested pollRequest() {
        return requests.pollFirst();
    }

    public int pendingRequests() {
        return requests.size();
    }

    /** One-shot human-readable status the UI shows, or null. */
    public String takeNotice() {
        String was = notice;
        notice = null;
        return was;
    }

    public void clear() {
        clearPictures();
        active.clear();
        demand.clear();
        requests.clear();
        watching.clear();
        refused.clear();
        acknowledged.clear();
        keyWanted.clear();
        outbox.clear();
        notice = null;
    }

    /** Drop every pictured frame without forgetting which screens are on (F-key leave). */
    public void clearPictures() {
        active.values().forEach(Remote::clear);
    }

    public void receive(byte[] payload) {
        switch (ScreenProtocol.decode(payload)) {
            case ScreenProtocol.Demand message -> {
                acknowledged.add(message.id());
                if (message.on()) {
                    if (demand.size() >= MAX_DEMAND && !demand.containsKey(message.id())) {
                        throw new IllegalArgumentException("screen demand bound");
                    }
                    demand.put(message.id(), message);
                    // A new demand starts a fresh picture stream: its first picture is a key frame.
                    keyWanted.add(message.id());
                } else {
                    demand.remove(message.id());
                    keyWanted.remove(message.id());
                }
            }
            case ScreenProtocol.Frame message -> {
                Remote remote = active.get(message.id());
                if (remote == null) {
                    throw new IllegalArgumentException("frame for inactive screen");
                }
                Long old = remote.sequence;
                if (old != null && (((message.sequence() - old) & 0xFFFFFFFFL) > Integer.MAX_VALUE
                        || message.sequence() == old)) {
                    return;
                }
                remote.sequence = message.sequence();
                if (!message.key() && !remote.synced) {
                    // The server withholds deltas until a key frame; a stray one is not decodable.
                    askKey(message.id(), remote);
                    return;
                }
                try {
                    if (remote.decoder == null) {
                        remote.decoder = new ScreenVideo.Decoder();
                    }
                    ScreenVideo.Picture picture = remote.decoder.decode(
                            message.data(), message.width(), message.height());
                    if (picture != null) {
                        if (message.key()) {
                            remote.synced = true;
                            remote.keyAsked = false;
                        }
                        remote.picture = picture;
                        remote.dirty = true;
                    }
                } catch (IllegalArgumentException error) {
                    remote.decoder = null;
                    remote.synced = false;
                    askKey(message.id(), remote);
                    throw error;
                }
            }
            case ScreenProtocol.KeyRequest message -> {
                // Only an owner with a live demand has a picture stream to restart.
                if (demand.containsKey(message.id())) {
                    keyWanted.add(message.id());
                }
            }
            case ScreenProtocol.Audio message -> {
                Remote remote = active.get(message.id());
                if (remote != null && watching.contains(message.id())) {
                    if (remote.audio == null || remote.audio.closed()) {
                        remote.audio = new ScreenAudio();
                    }
                    remote.audio.receive(message.sequence(), message.opus());
                }
            }
            case ScreenProtocol.Stop message -> {
                Remote remote = active.get(message.id());
                if (remote != null) {
                    remote.clear();
                }
                if (message.reason() != 0) {
                    watching.remove(message.id());
                    notice = "Screen viewing permission ended";
                }
            }
            case ScreenProtocol.Refused message -> {
                demand.remove(message.id());
                keyWanted.remove(message.id());
                watching.remove(message.id());
                Remote remote = active.get(message.id());
                if (remote != null) {
                    remote.clear();
                }
                refused.add(message.id());
                notice = "Screen sharing: " + switch (message.reason()) {
                    case 0 -> "not allowed";
                    case 1 -> "monitor rectangle changed";
                    case 2 -> "out of reach";
                    case 3 -> "screen limit";
                    case 4 -> "another player's screen";
                    case 5 -> "invalid picture";
                    case 6 -> "screen closed";
                    default -> "request rate limited";
                };
            }
            case ScreenProtocol.Requested message -> {
                if (requests.size() >= MAX_REQUESTS) {
                    throw new IllegalArgumentException("screen permission request bound");
                }
                requests.addLast(message);
            }
            case ScreenProtocol.Permission message -> {
                if (!message.allowed()) {
                    watching.remove(message.id());
                    Remote remote = active.get(message.id());
                    if (remote != null) {
                        remote.clear();
                    }
                }
                notice = message.allowed()
                        ? "Viewing allowed - look at the screen; F8 leaves"
                        : "Screen owner denied viewing";
            }
            case ScreenProtocol.Active message -> {
                if (message.on()) {
                    if (active.size() >= MAX_ACTIVE && !active.containsKey(message.id())) {
                        throw new IllegalArgumentException("active screen bound");
                    }
                    active.computeIfAbsent(message.id(), ignored -> new Remote());
                } else {
                    Remote removed = active.remove(message.id());
                    if (removed != null) {
                        removed.clear();
                    }
                    demand.remove(message.id());
                    keyWanted.remove(message.id());
                    watching.remove(message.id());
                }
            }
        }
    }

    private void askKey(ScreenProtocol.Id id, Remote remote) {
        if (!remote.keyAsked) {
            remote.keyAsked = true;
            outbox.add(ScreenProtocol.keyRequest(id));
        }
    }
}
