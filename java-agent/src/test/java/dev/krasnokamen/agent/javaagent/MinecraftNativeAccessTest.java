package dev.krasnokamen.agent.javaagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.scores.PlayerTeam;
import org.junit.jupiter.api.Test;

final class MinecraftNativeAccessTest {
    @Test
    void bindsActualRuntimeApis() {
        assertDoesNotThrow(() -> MinecraftActionAccess.resolve(LocalPlayer.class, InteractionHand.class,
            SwingAnimation.class, ItemStack.class));
        assertDoesNotThrow(() -> MinecraftSignAccess.resolve(ServerboundSignUpdatePacket.class,
            BlockPos.class, SignBlockEntity.class, SignText.class));
        assertDoesNotThrow(() -> MinecraftHudAccess.resolve(Minecraft.class));
        assertDoesNotThrow(() -> MinecraftHudAccess.resolveColor(PlayerTeam.class));
    }

    @Test
    void legacyActionsInvokeTheNativePlayerOverrideAndDrop() throws Throwable {
        var access = MinecraftActionAccess.resolve(LegacyPlayer.class, Hand.class, Animation.class, Stack.class);
        var player = new LegacyPlayer();
        var stack = new Stack();
        access.swing().invokeExact((Object) player, (Object) Hand.OFF);
        assertEquals(Hand.OFF, player.swung);
        assertSame(stack, (Object) access.drop().invokeExact((Object) player, (Object) stack, false));
        assertFalse(player.randomly);
    }

    @Test
    void modernActionsPreserveDefaultAnimationPredictionAndNativeDropResult() throws Throwable {
        var access = MinecraftActionAccess.resolve(ModernPlayer.class, Hand.class, Animation.class, Stack.class);
        var player = new ModernPlayer();
        access.swing().invokeExact((Object) player, (Object) Hand.MAIN);
        assertEquals(Hand.MAIN, player.swung);
        assertEquals(Animation.DEFAULT, player.animation);
        assertTrue(player.force);
        assertNull((Object) access.drop().invokeExact((Object) player, (Object) new Stack(), true));
        assertTrue(player.randomly);
        assertEquals(Prediction.PREDICTED, player.prediction);
    }

    @Test
    void legacySignsRetainFrontSideLineOrderAndUnfilteredMessages() throws Throwable {
        var access = MinecraftSignAccess.resolve(LegacyPacket.class, Position.class, LegacySign.class, LegacyText.class);
        var position = new Position();
        List<String> lines = List.of("one", "two", "three", "four");
        var packet = (LegacyPacket) access.frontUpdate(position, lines);
        assertSame(position, packet.position);
        assertTrue(packet.front);
        assertEquals(lines, packet.lines);
        var sign = new LegacySign();
        Object text = (Object) access.frontText().invokeExact((Object) sign);
        assertTrue(sign.front);
        assertSame(sign.text, text);
        assertEquals("four", access.message(text, 3));
        assertFalse(sign.text.filtered);
        assertThrows(IllegalArgumentException.class, () -> access.frontUpdate(position, List.of("short")));
    }

    @Test
    void modernSignsRetainNativeFrontSlotAndListMessages() throws Throwable {
        var access = MinecraftSignAccess.resolve(ModernPacket.class, Position.class, ModernSign.class, ModernText.class);
        List<String> lines = List.of("one", "two", "three", "four");
        var position = new Position();
        var packet = (ModernPacket) access.frontUpdate(position, lines);
        assertSame(position, packet.position());
        assertSame(lines, packet.lines());
        assertEquals(Slot.FRONT, packet.slot());
        var sign = new ModernSign();
        Object text = (Object) access.frontText().invokeExact((Object) sign);
        assertEquals(Slot.FRONT, sign.slot);
        assertSame(sign.text, text);
        assertEquals("two", access.message(text, 1));
        assertFalse(sign.text.filtered);
    }

    @Test
    void legacyHudWritesNativeOptionAndModernHudTogglesOnlyWhenNecessary() throws Throwable {
        var legacy = new LegacyMinecraft();
        var oldAccess = MinecraftHudAccess.resolve(LegacyMinecraft.class);
        oldAccess.setHidden(legacy, true);
        assertTrue(legacy.options.hideGui);
        oldAccess.setHidden(legacy, false);
        assertFalse(legacy.options.hideGui);
        var modern = new ModernMinecraft();
        var newAccess = MinecraftHudAccess.resolve(ModernMinecraft.class);
        newAccess.setHidden(modern, true);
        newAccess.setHidden(modern, true);
        assertTrue(modern.gui.hud.hidden);
        assertEquals(1, modern.gui.hud.toggles);
        newAccess.setHidden(modern, false);
        assertFalse(modern.gui.hud.hidden);
        assertEquals(2, modern.gui.hud.toggles);
    }

    @Test
    void readsActualLegacyEnumAndModernOptionalTeamColors() throws Throwable {
        Object legacy = (Object) MinecraftHudAccess.resolveColor(LegacyTeam.class)
            .invokeExact((Object) new LegacyTeam());
        Object modern = (Object) MinecraftHudAccess.resolveColor(ModernTeam.class)
            .invokeExact((Object) new ModernTeam());
        assertEquals("RED", MinecraftHudAccess.colorName(legacy));
        assertEquals("RED", MinecraftHudAccess.colorName(modern));
        assertNull(MinecraftHudAccess.colorName(Optional.empty()));
    }

    public enum Hand { MAIN, OFF }
    public enum Animation { DEFAULT }
    public enum Prediction { PREDICTED }
    public enum Slot { FRONT, BACK }
    public enum Color { RED }
    public static final class Stack { }
    public static final class Position { }
    public static final class LegacyPlayer {
        Hand swung;
        boolean randomly;
        public void swing(Hand hand) { swung = hand; }
        public Stack drop(Stack stack, boolean randomly) { this.randomly = randomly; return stack; }
    }
    public static final class ModernPlayer {
        Hand swung;
        Animation animation;
        boolean force;
        boolean randomly;
        Prediction prediction;
        public boolean swing(Hand hand, Animation animation, boolean force) {
            this.swung = hand; this.animation = animation; this.force = force; return false;
        }
        public Stack drop(Stack stack, boolean randomly, Prediction prediction) {
            this.randomly = randomly; this.prediction = prediction; return null;
        }
    }
    public static final class LegacyPacket {
        final Position position;
        final boolean front;
        final List<String> lines;
        public LegacyPacket(Position position, boolean front, String a, String b, String c, String d) {
            this.position = position; this.front = front; this.lines = List.of(a, b, c, d);
        }
    }
    public record ModernPacket(Position position, List<String> lines, Slot slot) { }
    public static final class LegacyText {
        boolean filtered;
        public String[] getMessages(boolean filtered) {
            this.filtered = filtered; return new String[]{"one", "two", "three", "four"};
        }
    }
    public static final class ModernText {
        boolean filtered;
        public List<String> getMessages(boolean filtered) {
            this.filtered = filtered; return List.of("one", "two", "three", "four");
        }
    }
    public static final class LegacySign {
        boolean front;
        final LegacyText text = new LegacyText();
        public LegacyText getText(boolean front) { this.front = front; return text; }
    }
    public static final class ModernSign {
        Slot slot;
        final ModernText text = new ModernText();
        public ModernText getText(Slot slot) { this.slot = slot; return text; }
    }
    public static final class LegacyOptions { public boolean hideGui; }
    public static final class ModernOptions { }
    public static final class LegacyMinecraft { public LegacyOptions options = new LegacyOptions(); }
    public static final class ModernMinecraft {
        public ModernOptions options = new ModernOptions();
        public ModernGui gui = new ModernGui();
    }
    public static final class ModernGui { public ModernHud hud = new ModernHud(); }
    public static final class ModernHud {
        boolean hidden;
        int toggles;
        public boolean isHidden() { return hidden; }
        public void toggle() { hidden = !hidden; toggles++; }
    }
    public static final class LegacyTeam { public Color getColor() { return Color.RED; } }
    public static final class ModernTeam { public Optional<Color> getColor() { return Optional.of(Color.RED); } }
}
