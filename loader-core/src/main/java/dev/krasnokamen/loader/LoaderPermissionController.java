package dev.krasnokamen.loader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class LoaderPermissionController {
    private static final Map<Path, LoaderPermissionStore> STORES = new HashMap<>();

    private final LoaderClientTransport transport = new LoaderClientTransport();
    private final LoaderPermissionStore store;
    private long generation;
    private boolean expectingArtifact;
    private boolean closed;

    public LoaderPermissionController(Path decisionsFile) {
        store = sharedStore(decisionsFile);
    }

    /** Dispose the transfer and invalidate any outstanding permission decision. */
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        generation++;
        expectingArtifact = false;
        transport.abort();
    }

    public void acceptManifest(
            byte[] payload,
            LoaderPlatform platform,
            String loaderVersion,
            String serverIdentity,
            Path cacheDirectory,
            BooleanSupplier connectionActive,
            LoaderPermissionPrompt prompt,
            Consumer<LoaderOutgoing> outgoing,
            Consumer<String> rejected) {
        boolean active = connectionActive.getAsBoolean();
        long currentGeneration;
        LoaderPermissionRequest request = null;
        java.util.Optional<Boolean> stored = java.util.Optional.empty();
        synchronized (this) {
            if (closed) {
                return;
            }
            transport.abort();
            expectingArtifact = false;
            currentGeneration = ++generation;
            if (active) {
                LoaderManifest manifest =
                        LoaderHandshake.inspectManifest(payload, platform, loaderVersion);
                request = new LoaderPermissionRequest(
                        normalizeServerIdentity(serverIdentity),
                        requiredPermissions(manifest));
                stored = store.decision(request);
            }
        }
        if (!isCurrent(currentGeneration)) {
            return;
        }
        if (!active) {
            rejected.accept("connection ended before the permission decision");
            return;
        }
        LoaderPermissionRequest permissionRequest = request;
        if (stored.isPresent()) {
            if (stored.orElseThrow()) {
                startTransfer(currentGeneration, payload, platform, loaderVersion,
                        permissionRequest, cacheDirectory, connectionActive, outgoing, rejected);
            } else {
                rejected.accept("permissions were denied for " + permissionRequest.serverIdentity());
            }
            return;
        }
        try {
            prompt.request(permissionRequest, allowed -> resolvePrompt(
                    currentGeneration, payload, platform, loaderVersion, permissionRequest,
                    cacheDirectory, connectionActive, outgoing, rejected, allowed));
        } catch (RuntimeException error) {
            if (isCurrent(currentGeneration)) {
                rejected.accept("permission prompt failed: " + error.getMessage());
            }
        }
    }

    public java.util.Optional<LoaderOutgoing> acceptArtifact(
            byte[] payload,
            BooleanSupplier connectionActive) {
        boolean active = connectionActive.getAsBoolean();
        synchronized (this) {
            if (closed) {
                throw new IllegalArgumentException(
                        "received a Krasnokamen Loader artifact after its connection ended");
            }
            if (!active) {
                transport.abort();
                expectingArtifact = false;
                generation++;
                throw new IllegalArgumentException(
                        "received a Krasnokamen Loader artifact after its connection ended");
            }
            if (!expectingArtifact) {
                throw new IllegalArgumentException(
                        "received a Krasnokamen Loader artifact without an allowed request");
            }
            var next = transport.acceptArtifact(payload);
            if (next.isPresent()) {
                expectingArtifact = next.orElseThrow().kind() == LoaderOutgoing.Kind.REQUEST;
            }
            return next;
        }
    }

    private void resolvePrompt(
            long currentGeneration,
            byte[] payload,
            LoaderPlatform platform,
            String loaderVersion,
            LoaderPermissionRequest request,
            Path cacheDirectory,
            BooleanSupplier connectionActive,
            Consumer<LoaderOutgoing> outgoing,
            Consumer<String> rejected,
            boolean allowed) {
        boolean active = connectionActive.getAsBoolean();
        String failure = null;
        boolean failed = false;
        synchronized (this) {
            if (currentGeneration != generation || closed) {
                return;
            }
            if (!active) {
                transport.abort();
                expectingArtifact = false;
                generation++;
                return;
            }
            try {
                store.record(request, allowed);
                if (!allowed) {
                    failure = "permissions were denied for " + request.serverIdentity();
                    failed = true;
                }
            } catch (IllegalArgumentException error) {
                failure = error.getMessage();
                failed = true;
            }
        }
        if (!isCurrent(currentGeneration)) {
            return;
        }
        if (failed) {
            rejected.accept(failure);
        } else {
            startTransfer(currentGeneration, payload, platform, loaderVersion,
                    request, cacheDirectory, connectionActive, outgoing, rejected);
        }
    }

    private void startTransfer(
            long currentGeneration,
            byte[] payload,
            LoaderPlatform platform,
            String loaderVersion,
            LoaderPermissionRequest request,
            Path cacheDirectory,
            BooleanSupplier connectionActive,
            Consumer<LoaderOutgoing> outgoing,
            Consumer<String> rejected) {
        if (!connectionActive.getAsBoolean()) {
            return;
        }
        LoaderOutgoing next;
        try {
            synchronized (this) {
                if (currentGeneration != generation || closed) {
                    return;
                }
                next = transport.acceptManifest(
                        payload,
                        new AllowedEnvironment(
                                platform,
                                loaderVersion,
                                Set.copyOf(request.permissions())),
                        cacheDirectory);
                expectingArtifact = next.kind() == LoaderOutgoing.Kind.REQUEST;
            }
        } catch (IllegalArgumentException error) {
            if (isCurrent(currentGeneration)) {
                rejected.accept(error.getMessage());
            }
            return;
        }
        if (!connectionActive.getAsBoolean()) {
            synchronized (this) {
                if (currentGeneration == generation) {
                    transport.abort();
                    expectingArtifact = false;
                    generation++;
                }
            }
            return;
        }
        if (!isCurrent(currentGeneration)) {
            return;
        }
        outgoing.accept(next);
    }

    private synchronized boolean isCurrent(long currentGeneration) {
        return !closed && currentGeneration == generation;
    }

    private static List<LoaderPermission> requiredPermissions(LoaderManifest manifest) {
        LinkedHashSet<LoaderPermission> permissions = new LinkedHashSet<>();
        for (LoaderBundle bundle : manifest.bundles()) {
            permissions.addAll(bundle.permissions());
        }
        return LoaderPermissionStore.orderedPermissions(new ArrayList<>(permissions));
    }

    private static String normalizeServerIdentity(String identity) {
        if (identity == null) {
            throw new IllegalArgumentException("Krasnokamen Loader server identity is missing");
        }
        String normalized = identity.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "Krasnokamen Loader server identity must contain 1..=255 characters");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException(
                        "Krasnokamen Loader server identity contains a control character");
            }
        }
        return normalized;
    }

    private static synchronized LoaderPermissionStore sharedStore(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return STORES.computeIfAbsent(normalized, LoaderPermissionStore::new);
    }

    private record AllowedEnvironment(
            LoaderPlatform platform,
            String loaderVersion,
            Set<LoaderPermission> grantedPermissions) implements LoaderEnvironment {
    }
}
