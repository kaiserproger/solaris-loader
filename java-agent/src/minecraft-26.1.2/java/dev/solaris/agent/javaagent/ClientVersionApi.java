package dev.solaris.agent.javaagent;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.components.ChatComponent;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.ChatFormatting;
import java.util.List;

/** Signatures verified against the local 26.1.2 client jar. */
final class ClientVersionApi {
    private ClientVersionApi() {}

    static Screen screen(Minecraft client) { return client.screen; }
    static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }
    static Overlay overlay(Minecraft client) { return client.getOverlay(); }
    static ChatComponent chat(Minecraft client) { return client.gui.getChat(); }
    static RenderTarget renderTarget(Minecraft client) { return client.getMainRenderTarget(); }
    static net.minecraft.client.renderer.state.GameRenderState renderState(Minecraft client) {
        return client.gameRenderer.getGameRenderState();
    }
    static boolean hudHidden(Minecraft client) { return client.options.hideGui; }
    static void toggleHud(Minecraft client) { client.options.hideGui = !client.options.hideGui; }
    static boolean redTeam(PlayerTeam team) { return team.getColor() == ChatFormatting.RED; }
    static boolean isKeyboard(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYSYM;
    }
    static void swing(LivingEntity player, InteractionHand hand) { player.swing(hand); }
    static ServerboundSignUpdatePacket signUpdate(BlockPos pos, List<String> lines) {
        return new ServerboundSignUpdatePacket(pos, true, lines.get(0), lines.get(1), lines.get(2), lines.get(3));
    }
    static String signLine(SignBlockEntity sign, int index) {
        return sign.getFrontText().getMessages(false)[index].getString();
    }
    static boolean dropSelected(LocalPlayer player) { return player.drop(false); }
    static int wirePower(BlockState state) { return state.getValue(RedStoneWireBlock.POWER); }
}
