package dev.solaris.agent.javaagent;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.util.Prediction;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedstoneWireBlock;
import java.util.List;

final class ClientVersionApi {
    private ClientVersionApi() {}

    static boolean isKeyboard(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYBOARD;
    }

    static void swing(LivingEntity player, InteractionHand hand) {
        player.swing(hand, SwingAnimation.DEFAULT, true);
    }

    static ServerboundSignUpdatePacket signUpdate(BlockPos pos, List<String> lines) {
        return new ServerboundSignUpdatePacket(pos, List.copyOf(lines), SignTextSlot.FRONT);
    }

    static String signLine(SignBlockEntity sign, int index) {
        return sign.getText(SignTextSlot.FRONT).getMessages(false).get(index).getString();
    }

    static boolean dropSelected(LocalPlayer player) {
        return player.drop(player.getMainHandItem(), false, Prediction.PREDICTED) != null;
    }

    static int wirePower(BlockState state) {
        return state.getValue(RedstoneWireBlock.POWER);
    }
}
