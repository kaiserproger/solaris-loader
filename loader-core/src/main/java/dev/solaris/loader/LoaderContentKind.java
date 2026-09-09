package dev.solaris.loader;

import com.google.gson.annotations.SerializedName;

public enum LoaderContentKind {
    @SerializedName("blocks")
    BLOCKS,
    @SerializedName("items")
    ITEMS,
    @SerializedName("ui")
    UI,
    @SerializedName("assets")
    ASSETS,
    @SerializedName("interactions")
    INTERACTIONS,
    @SerializedName("sounds")
    SOUNDS
}
