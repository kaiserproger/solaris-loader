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
                "Ruby Block",
                "minecraft:black_glazed_terracotta",
                "example:ruby_block");
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
        assertEquals(1, resources.size());
        String blockstate = new String(
                resources.get(Identifier.parse(
                        "minecraft:blockstates/black_glazed_terracotta.json")),
                StandardCharsets.UTF_8);
        assertTrue(blockstate.contains(
                "\"facing=north\":{\"model\":\"example:block/ruby_block\"}"));
        assertTrue(blockstate.contains(
                "\"facing=east\":{\"model\":\"example:block/ruby_block\",\"y\":90}"));
        assertTrue(blockstate.contains("\"facing=south\":{\"model\":\"example:block/ruby_block\",\"y\":180}"));
        assertTrue(blockstate.contains("\"facing=west\":{\"model\":\"example:block/ruby_block\",\"y\":270}"));
    }

    @Test
    void twoBlocksOnOneCarrierAreRefused() {
        LoaderBlockDefinition first = new LoaderBlockDefinition(
                "example:a", "example:block/a", "A",
                "minecraft:black_glazed_terracotta", "example:a");
        LoaderBlockDefinition second = new LoaderBlockDefinition(
                "example:b", "example:block/b", "B",
                "minecraft:black_glazed_terracotta", "example:b");
        LoaderActivatedContent content = new LoaderActivatedContent(
                List.of(), Map.of(), Map.of(),
                Map.of(first.id(), first, second.id(), second),
                Map.of(), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> LoaderMinecraftBlock.generatedResources(content));
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
