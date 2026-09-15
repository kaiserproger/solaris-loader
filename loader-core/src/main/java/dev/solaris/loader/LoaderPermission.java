package dev.solaris.loader;

import com.google.gson.annotations.SerializedName;

public enum LoaderPermission {
    @SerializedName("register_blocks")
    REGISTER_BLOCKS,
    @SerializedName("register_items")
    REGISTER_ITEMS,
    @SerializedName("present_views")
    PRESENT_VIEWS,
    @SerializedName("send_view_actions")
    SEND_VIEW_ACTIONS,
    @SerializedName("load_assets")
    LOAD_ASSETS,
    @SerializedName("present_world_previews")
    PRESENT_WORLD_PREVIEWS,
    @SerializedName("send_world_selection")
    SEND_WORLD_SELECTION,
    @SerializedName("play_sounds")
    PLAY_SOUNDS;

    public String wireName() {
        return switch (this) {
            case REGISTER_BLOCKS -> "register_blocks";
            case REGISTER_ITEMS -> "register_items";
            case PRESENT_VIEWS -> "present_views";
            case SEND_VIEW_ACTIONS -> "send_view_actions";
            case LOAD_ASSETS -> "load_assets";
            case PRESENT_WORLD_PREVIEWS -> "present_world_previews";
            case SEND_WORLD_SELECTION -> "send_world_selection";
            case PLAY_SOUNDS -> "play_sounds";
        };
    }

    public static LoaderPermission fromWireName(String value) {
        return switch (value) {
            case "register_blocks" -> REGISTER_BLOCKS;
            case "register_items" -> REGISTER_ITEMS;
            case "present_views" -> PRESENT_VIEWS;
            case "send_view_actions" -> SEND_VIEW_ACTIONS;
            case "load_assets" -> LOAD_ASSETS;
            case "present_world_previews" -> PRESENT_WORLD_PREVIEWS;
            case "send_world_selection" -> SEND_WORLD_SELECTION;
            case "play_sounds" -> PLAY_SOUNDS;
            default -> throw new IllegalArgumentException("unknown Solaris Loader permission " + value);
        };
    }
}
