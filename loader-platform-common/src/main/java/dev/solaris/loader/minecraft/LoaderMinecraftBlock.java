package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderBlockDefinition;
import dev.solaris.loader.LoaderScreenDefinition;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Carrier-backed Loader blocks (ADR 0018 "Monitor block"). Core stores and sends
 * only the vanilla carrier block's {@code facing} states; the Loader block is the
 * client's model for them. The runtime resource pack therefore overrides the
 * carrier's blockstate with the bundle's model (rotated per {@code facing}); no
 * block is registered and no state id is negotiated.
 */
public final class LoaderMinecraftBlock {
    static final int MAX_BLOCKS = 8;
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    private LoaderMinecraftBlock() {
    }

    public static Optional<ItemStack> forScreen(
            LoaderScreenDefinition screen,
            LoaderActivatedContent content) {
        return resolve(screen, content)
                .map(LoaderBlockDefinition::dropItem)
                .map(content.items()::get)
                .flatMap(LoaderMinecraftItem::create);
    }

    static Optional<LoaderBlockDefinition> resolve(
            LoaderScreenDefinition screen,
            LoaderActivatedContent content) {
        return screen.blockId()
                .map(content.blocks()::get)
                .filter(Objects::nonNull);
    }

    static void validate(LoaderActivatedContent content) {
        if (content.blocks().size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Solaris Loader block capacity exceeded");
        }
        for (LoaderBlockDefinition definition : content.blocks().values()) {
            Block carrier = carrierBlock(definition);
            for (BlockState state : carrier.getStateDefinition().getPossibleStates()) {
                if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    throw new IllegalArgumentException(
                            "Loader block carrier has no horizontal facing: "
                                    + definition.carrier());
                }
            }
            if (!content.items().containsKey(definition.dropItem())) {
                throw new IllegalArgumentException(
                        "Loader block drop item is not an installed item: "
                                + definition.dropItem());
            }
        }
    }

    /** Whether {@code state} is the carrier of any activated Loader block. */
    public static boolean isCarrier(
            LoaderActivatedContent content,
            BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return content.blocks().values().stream()
                .anyMatch(definition -> definition.carrier().equals(id.toString()));
    }

    static Map<Identifier, byte[]> generatedResources(
            LoaderActivatedContent content) {
        Map<Identifier, byte[]> resources = new LinkedHashMap<>();
        for (LoaderBlockDefinition definition : content.blocks().values()) {
            Identifier carrier = Identifier.parse(definition.carrier());
            Identifier blockstate = Identifier.fromNamespaceAndPath(
                    carrier.getNamespace(), "blockstates/" + carrier.getPath() + ".json");
            if (resources.putIfAbsent(blockstate, blockstates(definition.model())) != null) {
                throw new IllegalArgumentException(
                        "two Loader blocks share carrier " + definition.carrier());
            }
        }
        return Map.copyOf(resources);
    }

    private static byte[] blockstates(String model) {
        StringBuilder json = new StringBuilder("{\"variants\":{");
        for (int index = 0; index < FACINGS.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("\"facing=").append(FACINGS[index]).append("\":{\"model\":\"")
                    .append(model).append('"');
            if (index > 0) {
                json.append(",\"y\":").append(index * 90);
            }
            json.append('}');
        }
        return json.append("}}\n").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Block carrierBlock(LoaderBlockDefinition definition) {
        Identifier id = Identifier.tryParse(definition.carrier());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Loader block has unknown carrier " + definition.carrier());
        }
        return BuiltInRegistries.BLOCK.getValue(id);
    }
}
