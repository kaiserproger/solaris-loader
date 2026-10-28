package dev.solaris.agent.javaagent;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-thread-only packet receipts: predicted mobInteract state is never the oracle. */
public final class ReceivedEntityQa {
    private static final int LIMIT = 128;
    private static final int AGE_LOCK_ID = ageLockId();
    private static final Map<Integer, HandReceipt> HANDS = new LinkedHashMap<>();
    private static final Map<Integer, AgeReceipt> AGES = new LinkedHashMap<>();
    private static long version;

    private ReceivedEntityQa() {}

    private record HandReceipt(long version, String item, int count) {}
    private record AgeReceipt(long version, boolean locked) {}

    public static long version() {
        return version;
    }

    public static void equipment(ClientboundSetEquipmentPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(packet.getEntity()) instanceof Allay)) return;
        for (var slot : packet.getSlots()) if (slot.getFirst() == EquipmentSlot.MAINHAND) {
            var stack = slot.getSecond();
            HANDS.put(packet.getEntity(), new HandReceipt(++version,
                W1SmokeFixture.itemId(stack), stack.getCount()));
            trim(HANDS);
        }
    }

    public static void metadata(ClientboundSetEntityDataPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(packet.id()) instanceof AgeableMob)) return;
        for (var data : packet.packedItems()) if (data.id() == AGE_LOCK_ID && data.value() instanceof Boolean locked) {
            AGES.put(packet.id(), new AgeReceipt(++version, locked));
            trim(AGES);
        }
    }

    static boolean handMatches(int entityId, long after, String item, int count) {
        HandReceipt receipt = HANDS.get(entityId);
        return receipt != null && receipt.version() > after && receipt.item().equals(item) && receipt.count() == count;
    }

    static boolean ageMatches(int entityId, long after, boolean locked) {
        AgeReceipt receipt = AGES.get(entityId);
        return receipt != null && receipt.version() > after && receipt.locked() == locked;
    }

    private static <T> void trim(Map<Integer, T> receipts) {
        if (receipts.size() > LIMIT) receipts.remove(receipts.keySet().iterator().next());
    }

    private static int ageLockId() {
        try {
            var field = AgeableMob.class.getDeclaredField("AGE_LOCKED");
            field.setAccessible(true);
            return ((EntityDataAccessor<?>) field.get(null)).id();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("26.3 age-lock metadata accessor missing", error);
        }
    }
}
