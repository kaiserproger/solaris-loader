package dev.krasnokamen.loader;

import java.util.Set;
import java.util.List;

public interface LoaderEnvironment {
    LoaderPlatform platform();

    String loaderVersion();

    Set<LoaderPermission> grantedPermissions();

    default List<Integer> carrierBlockStateIds() {
        throw new IllegalStateException(
                "Krasnokamen Loader block carrier states are unavailable");
    }
}
