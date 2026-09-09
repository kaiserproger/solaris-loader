package dev.solaris.loader;

/** The matching verified asset is assets/<owner>/sounds/<path>.ogg. */
public record LoaderSoundDefinition(String id) {
    public String archivePath() {
        int separator = id.indexOf(':');
        return "assets/" + id.substring(0, separator) + "/sounds/" + id.substring(separator + 1) + ".ogg";
    }
}
