package dev.solaris.loader;

import java.util.List;

/** One validated `world_previews` entry of a schema-2 Loader index. */
public record LoaderWorldPreviewDefinition(
        String id,
        String blueprintId,
        String contentHash,
        int rotation,
        int sizeX,
        int sizeY,
        int sizeZ,
        List<Block> blocks) {
    public static final int MAX_AXIS = 64;
    public static final int MAX_ROTATION = 3;
    public static final int MAX_BLOCKS = 65_536;

    public LoaderWorldPreviewDefinition {
        blocks = List.copyOf(blocks);
    }

    /** One local block of a preview, addressed inside the declared box. */
    public record Block(int x, int y, int z, String blockId) {
    }
}
