package dev.solaris.loader;

import com.google.gson.annotations.SerializedName;

public enum LoaderPermission {
    @SerializedName("register_blocks")
    REGISTER_BLOCKS,
    @SerializedName("register_items")
    REGISTER_ITEMS,
    @SerializedName("present_ui")
    PRESENT_UI,
    @SerializedName("load_assets")
    LOAD_ASSETS,
    @SerializedName("send_interactions")
    SEND_INTERACTIONS,
    @SerializedName("play_sounds")
    PLAY_SOUNDS;

    public String wireName() {
        return switch (this) {
            case REGISTER_BLOCKS -> "register_blocks";
            case REGISTER_ITEMS -> "register_items";
            case PRESENT_UI -> "present_ui";
            case LOAD_ASSETS -> "load_assets";
            case SEND_INTERACTIONS -> "send_interactions";
            case PLAY_SOUNDS -> "play_sounds";
        };
    }

    public static LoaderPermission fromWireName(String value) {
        return switch (value) {
            case "register_blocks" -> REGISTER_BLOCKS;
            case "register_items" -> REGISTER_ITEMS;
            case "present_ui" -> PRESENT_UI;
            case "load_assets" -> LOAD_ASSETS;
            case "send_interactions" -> SEND_INTERACTIONS;
            case "play_sounds" -> PLAY_SOUNDS;
            default -> throw new IllegalArgumentException("unknown Solaris Loader permission " + value);
        };
    }
}
