package dev.solaris.agent.javaagent;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientVersionApiTest {
    @Test
    void recognizesKeyboardButNotMouse() {
        assertTrue(ClientVersionApi.isKeyboard(InputConstants.getKey("key.keyboard.a")));
        assertFalse(ClientVersionApi.isKeyboard(InputConstants.getKey("key.mouse.left")));
    }
}
