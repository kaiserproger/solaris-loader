package dev.solaris.loader;

/** Plain text, bounded scrollback. No ANSI, clickable links, or remote commands. */
public final class ComputerTerminal {
    private static final int MAX_CHARS = 32768;
    private final int session;
    private final StringBuilder text = new StringBuilder();
    private boolean connected;
    private boolean closed;

    public ComputerTerminal(int session) {
        if (session == 0) throw new IllegalArgumentException("Zero session");
        this.session = session;
    }
    public int session() { return session; }
    public boolean connected() { return connected; }
    public String text() { return text.toString(); }
    public boolean accept(ComputerProtocol.Reply reply) {
        if (reply.session() != session || closed) return false;
        if (reply.kind() == 0) connected = true;
        if (reply.kind() == 2) { connected = false; closed = true; }
        append(reply.text());
        if (reply.kind() != 1) append("\n");
        return true;
    }
    public void append(String incoming) {
        incoming.codePoints().filter(cp -> cp == '\n' || cp == '\t' || !Character.isISOControl(cp))
                .forEach(text::appendCodePoint);
        if (text.length() > MAX_CHARS) {
            int start = text.length() - MAX_CHARS;
            if (Character.isLowSurrogate(text.charAt(start))) start++;
            text.delete(0, start);
        }
    }
}
