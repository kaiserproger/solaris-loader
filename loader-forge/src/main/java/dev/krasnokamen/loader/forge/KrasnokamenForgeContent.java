package dev.krasnokamen.loader.forge;

import dev.krasnokamen.loader.minecraft.LoaderBlockCarrier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryObject;

final class KrasnokamenForgeContent {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, KrasnokamenForgeLoader.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, KrasnokamenForgeLoader.MOD_ID);
    private static final List<RegistryObject<Block>> LOADER_BLOCKS =
            IntStream.range(0, LoaderBlockCarrier.MAX_CARRIERS)
                    .mapToObj(index -> BLOCKS.register(
                            LoaderBlockCarrier.path(index),
                            () -> LoaderBlockCarrier.createBlock(index)))
                    .toList();
    @SuppressWarnings("unused")
    private static final List<RegistryObject<BlockItem>> LOADER_BLOCK_ITEMS =
            IntStream.range(0, LoaderBlockCarrier.MAX_CARRIERS)
                    .mapToObj(index -> ITEMS.register(
                            LoaderBlockCarrier.path(index),
                            () -> LoaderBlockCarrier.createItem(
                                    index,
                                    LOADER_BLOCKS.get(index).get())))
                    .toList();

    private KrasnokamenForgeContent() {
    }

    static void register(FMLJavaModLoadingContext context) {
        BLOCKS.register(context.getModBusGroup());
        ITEMS.register(context.getModBusGroup());
    }

    static List<Integer> carrierStateIds() {
        var stateIds = GameData.BlockCallbacks.getBlockStateIDMap();
        List<Integer> result = new ArrayList<>(LoaderBlockCarrier.MAX_CARRIERS);
        for (RegistryObject<Block> carrier : LOADER_BLOCKS) {
            BlockState state = carrier.get().defaultBlockState();
            int stateId = stateIds.getId(state);
            if (stateId < 0 || stateIds.byId(stateId) != state) {
                stateIds.add(state);
                stateId = stateIds.getId(state);
            }
            if (stateId < 0 || stateIds.byId(stateId) != state) {
                throw new IllegalStateException(
                        "Krasnokamen Loader Forge carrier could not bind an exact runtime state id");
            }
            result.add(stateId);
        }
        return List.copyOf(result);
    }
}
