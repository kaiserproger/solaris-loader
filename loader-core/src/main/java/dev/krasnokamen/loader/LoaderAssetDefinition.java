package dev.krasnokamen.loader;

public record LoaderAssetDefinition(
        String id,
        String archivePath,
        byte[] bytes) {
    public LoaderAssetDefinition {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
