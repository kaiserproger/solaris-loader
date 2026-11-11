package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderBlockDefinition;
import dev.solaris.loader.LoaderScreenDefinition;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class LoaderMinecraftBlock {
    private LoaderMinecraftBlock() {
    }

    public static Optional<ItemStack> forScreen(
            LoaderScreenDefinition screen, LoaderActivatedContent content) {
        return resolve(screen, content).map(block -> {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(block.carrier())));
            stack.set(DataComponents.ITEM_MODEL, previewModel(block));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(block.name()));
            return stack;
        });
    }

    static Optional<LoaderBlockDefinition> resolve(
            LoaderScreenDefinition screen, LoaderActivatedContent content) {
        return screen.blockId().map(content.blocks()::get).filter(Objects::nonNull);
    }

    static void validate(LoaderActivatedContent content) {
        Set<String> carriers = new HashSet<>();
        for (LoaderBlockDefinition block : content.blocks().values()) {
            Identifier carrier = Identifier.tryParse(block.carrier());
            if (carrier == null || !BuiltInRegistries.BLOCK.containsKey(carrier)
                    || !BuiltInRegistries.ITEM.containsKey(carrier)) {
                throw new IllegalArgumentException("Loader block has unknown carrier " + block.carrier());
            }
            if (!carriers.add(block.carrier())) {
                throw new IllegalArgumentException("duplicate Loader block carrier " + block.carrier());
            }
        }
    }

    static Map<Identifier, byte[]> generatedResources(LoaderActivatedContent content) {
        Map<Identifier, byte[]> resources = new LinkedHashMap<>();
        for (LoaderBlockDefinition block : content.blocks().values()) {
            Identifier carrier = Identifier.parse(block.carrier());
            Identifier path = Identifier.fromNamespaceAndPath(
                    carrier.getNamespace(), "blockstates/" + carrier.getPath() + ".json");
            byte[] state = ("""
                    {"variants":{"facing=north":{"model":"%s","y":180},"facing=east":{"model":"%s","y":270},"facing=south":{"model":"%s"},"facing=west":{"model":"%s","y":90}}}
                    """.formatted(block.model(), block.model(), block.model(), block.model()))
                    .getBytes(StandardCharsets.UTF_8);
            if (resources.putIfAbsent(path, state) != null) {
                throw new IllegalArgumentException("duplicate Loader block carrier " + block.carrier());
            }
            Identifier preview = previewModel(block);
            resources.put(Identifier.fromNamespaceAndPath(preview.getNamespace(),
                    "items/" + preview.getPath() + ".json"),
                    """
                    {"model":{"type":"minecraft:model","model":"%s"}}
                    """.formatted(block.model()).getBytes(StandardCharsets.UTF_8));
        }
        return Map.copyOf(resources);
    }

    private static Identifier previewModel(LoaderBlockDefinition block) {
        Identifier id = Identifier.parse(block.id());
        return Identifier.fromNamespaceAndPath("solaris_loader",
                "block_preview/" + id.getNamespace() + "/" + id.getPath());
    }
}
