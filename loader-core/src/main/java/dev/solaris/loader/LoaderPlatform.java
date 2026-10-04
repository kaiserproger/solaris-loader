package dev.solaris.loader;

import com.google.gson.annotations.SerializedName;

public enum LoaderPlatform {
    @SerializedName("fabric")
    FABRIC,
    @SerializedName("neoforge")
    NEOFORGE,
    @SerializedName("forge")
    FORGE,
    /** Supported bundle metadata only; never a Java adapter's handshake identity. */
    @SerializedName("native")
    NATIVE
}
