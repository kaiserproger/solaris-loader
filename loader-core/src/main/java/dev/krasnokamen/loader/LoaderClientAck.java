package dev.krasnokamen.loader;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record LoaderClientAck(
        int protocol,
        LoaderPlatform platform,
        @SerializedName("loader_version") String loaderVersion,
        @SerializedName("accepted_permissions") List<LoaderPermission> acceptedPermissions,
        @SerializedName("cached_bundles") List<String> cachedBundles) {
}
