package dev.solaris.loader;

import com.google.gson.annotations.SerializedName;

public enum LoaderContentKind {
    @SerializedName("blocks")
    BLOCKS,
    @SerializedName("items")
    ITEMS,
    @SerializedName("views")
    VIEWS,
    @SerializedName("view_actions")
    VIEW_ACTIONS,
    @SerializedName("assets")
    ASSETS,
    @SerializedName("world_previews")
    WORLD_PREVIEWS,
    @SerializedName("world_selection")
    WORLD_SELECTION,
    @SerializedName("sounds")
    SOUNDS
}
