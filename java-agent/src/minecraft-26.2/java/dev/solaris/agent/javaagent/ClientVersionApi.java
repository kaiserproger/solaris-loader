package dev.solaris.agent.javaagent;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedStoneWireBlock;
import java.util.List;

final class ClientVersionApi {
    private ClientVersionApi() {}

    static net.minecraft.client.gui.screens.Screen screen(net.minecraft.client.Minecraft client) {
        return client.gui.screen();
    }

    static void setScreen(net.minecraft.client.Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        client.gui.setScreen(screen);
    }

    static net.minecraft.client.gui.screens.Overlay overlay(net.minecraft.client.Minecraft client) {
        return client.gui.overlay();
    }

    static net.minecraft.client.gui.components.ChatComponent chat(net.minecraft.client.Minecraft client) {
        return client.gui.hud.getChat();
    }

    static com.mojang.blaze3d.pipeline.RenderTarget renderTarget(net.minecraft.client.Minecraft client) {
        return client.gameRenderer.mainRenderTarget();
    }

    static net.minecraft.client.renderer.state.GameRenderState renderState(net.minecraft.client.Minecraft client) {
        return client.gameRenderer.gameRenderState();
    }

    static boolean hudHidden(net.minecraft.client.Minecraft client) {
        return client.gui.hud.isHidden();
    }

    static void toggleHud(net.minecraft.client.Minecraft client) {
        client.gui.hud.toggle();
    }

    static boolean redTeam(net.minecraft.world.scores.PlayerTeam team) {
        return team.getColor().orElse(null) == net.minecraft.world.scores.TeamColor.RED;
    }

    static boolean isKeyboard(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYSYM;
    }

    static void swing(LivingEntity player, InteractionHand hand) {
        player.swing(hand);
    }

    static ServerboundSignUpdatePacket signUpdate(BlockPos pos, List<String> lines) {
        return new ServerboundSignUpdatePacket(pos, true, lines.get(0), lines.get(1), lines.get(2), lines.get(3));
    }

    static String signLine(SignBlockEntity sign, int index) {
        return sign.getFrontText().getMessages(false)[index].getString();
    }

    static boolean dropSelected(LocalPlayer player) {
        return player.drop(false);
    }

    static int wirePower(BlockState state) {
        return state.getValue(RedStoneWireBlock.POWER);
    }
}
