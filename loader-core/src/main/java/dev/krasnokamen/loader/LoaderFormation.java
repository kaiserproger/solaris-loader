package dev.krasnokamen.loader;

/** One closed wire value of the Loader world-selection contract. */
public enum LoaderFormation {
    LINE("line"),
    COLUMN("column"),
    WEDGE("wedge"),
    SQUARE("square");

    private final String wireName;

    LoaderFormation(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static LoaderFormation fromWireName(String value) {
        for (LoaderFormation formation : values()) {
            if (formation.wireName.equals(value)) {
                return formation;
            }
        }
        throw new IllegalArgumentException("unknown Krasnokamen Loader formation " + value);
    }
}
