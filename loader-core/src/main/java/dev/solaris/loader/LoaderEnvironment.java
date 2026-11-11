package dev.solaris.loader;

import java.util.Set;

public interface LoaderEnvironment {
    LoaderPlatform platform();

    String loaderVersion();

    Set<LoaderPermission> grantedPermissions();
}
