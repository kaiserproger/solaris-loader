package dev.solaris.agent.javaagent;

import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Client-thread-only raw menu images; later local click prediction cannot mutate these snapshots. */
public final class ReceivedContainerQa {
    private record Receipt(long version, int window, List<ItemStack> slots, ItemStack carried) {}
    private static long version;
    private static Receipt latest;

    private ReceivedContainerQa() {}

    public static void content(ClientboundContainerSetContentPacket packet) {
        List<ItemStack> slots = new ArrayList<>(packet.items().size());
        for (ItemStack stack : packet.items()) slots.add(stack.copy());
        latest = new Receipt(++version, packet.containerId(), slots, packet.carriedItem().copy());
    }

    static long version() {
        return version;
    }

    static boolean slotMatches(int window, long after, int slot, ItemStack expected) {
        return latest != null && latest.window() == window && latest.version() > after
            && slot >= 0 && slot < latest.slots().size() && ItemStack.matches(latest.slots().get(slot), expected);
    }

    static boolean cursorMatches(int window, long after, ItemStack expected) {
        return latest != null && latest.window() == window && latest.version() > after
            && ItemStack.matches(latest.carried(), expected);
    }
}
