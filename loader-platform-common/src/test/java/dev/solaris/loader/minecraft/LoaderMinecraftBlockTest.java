package dev.solaris.loader.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderBlockDefinition;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class LoaderMinecraftBlockTest {
    @Test
    void referencedBlockResolvesAndOverridesTheCarrierBlockstate() {
        LoaderScreenDefinition screen = new LoaderScreenDefinition(
                new LoaderViewKey("example:catalog"),
                false,
                "Catalog",
                Optional.empty(),
                Optional.of("example:ruby_block"),
                List.of(),
                List.of(),
                List.of());
        LoaderBlockDefinition block = new LoaderBlockDefinition(
                "example:ruby_block",
                "example:block/ruby_block",
                "Ruby Block", "minecraft:pink_glazed_terracotta", "example:ruby_block");
        LoaderBlockDefinition second = new LoaderBlockDefinition(
                "other:sapphire_block",
                "other:block/sapphire_block",
                "Sapphire Block", "minecraft:blue_glazed_terracotta", "other:sapphire_block");
        LoaderActivatedContent content = new LoaderActivatedContent(
                List.of(),
                Map.of(screen.id().value(), screen),
                Map.of(),
                Map.of(block.id(), block),
                Map.of(),
                Map.of(),
                Map.of());

        assertEquals(
                block,
                LoaderMinecraftBlock.resolve(screen, content).orElseThrow());
        Map<Identifier, byte[]> resources =
                LoaderMinecraftBlock.generatedResources(content);
        assertEquals(4, resources.size());
        String ruby = new String(resources.get(Identifier.parse(
                "minecraft:blockstates/pink_glazed_terracotta.json")), StandardCharsets.UTF_8);
        String sapphire = new String(resources.get(Identifier.parse(
                "minecraft:blockstates/blue_glazed_terracotta.json")), StandardCharsets.UTF_8);
        assertTrue(ruby.contains("\"facing=north\":{\"model\":\"example:block/ruby_block\",\"y\":180}"));
        assertTrue(ruby.contains("\"facing=west\":{\"model\":\"example:block/ruby_block\",\"y\":90}"));
        assertTrue(sapphire.contains("\"facing=south\":{\"model\":\"other:block/sapphire_block\"}"));
        assertTrue(new String(resources.get(Identifier.parse(
                "solaris_loader:items/block_preview/example/ruby_block.json")),
                StandardCharsets.UTF_8).contains("example:block/ruby_block"));
    }

    @Test
    void undeclaredBlockDoesNotResolveOrGenerateResources() {
        LoaderScreenDefinition screen = new LoaderScreenDefinition(
                new LoaderViewKey("example:catalog"),
                false,
                "Catalog",
                Optional.empty(),
                Optional.of("example:ruby_block"),
                List.of(),
                List.of(),
                List.of());

        assertTrue(LoaderMinecraftBlock
                .resolve(screen, LoaderActivatedContent.empty())
                .isEmpty());
        assertTrue(LoaderMinecraftBlock
                .generatedResources(LoaderActivatedContent.empty())
                .isEmpty());
    }
}
