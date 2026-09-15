package dev.solaris.loader;

/** One of the six declared screen kinds of a schema-2 Loader index. */
public enum LoaderScreenKind {
    SETTLEMENT("settlement"),
    CONSTRUCTION("construction"),
    ECONOMY("economy"),
    GARRISON("garrison"),
    ARMY("army"),
    HUD("hud");

    private final String wireName;

    LoaderScreenKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static LoaderScreenKind fromWireName(String value) {
        for (LoaderScreenKind kind : values()) {
            if (kind.wireName.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown Solaris Loader screen kind " + value);
    }
}
