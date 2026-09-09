package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderInteractionDefinition;
import dev.solaris.loader.LoaderUiPresentation;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.network.chat.Component;

/** Client-thread presentation; the verified UI registry bounds the active HUD set. */
public final class LoaderMinecraftUi {
    private static final Map<String, MultiLineTextWidget> HUD = new LinkedHashMap<>();
    private static int hudWidth = -1;

    private LoaderMinecraftUi() {
    }

    public static void present(
            LoaderUiPresentation presentation,
            LoaderActivatedContent content,
            Consumer<LoaderInteractionDefinition> action) {
        Minecraft client = Minecraft.getInstance();
        var definition = presentation.definition();
        String id = definition.id();
        switch (presentation.mode()) {
            case SCREEN -> {
                HUD.remove(id);
                client.setScreen(new LoaderTextScreen(
                        definition,
                        content.interactions().values().stream()
                                .filter(interaction -> id.equals(interaction.uiId()))
                                .sorted(Comparator.comparing(LoaderInteractionDefinition::id))
                                .toList(),
                        action,
                        LoaderMinecraftDisplay.forScreen(definition, content)));
            }
            case HUD -> {
                closeMatchingScreen(client, id);
                var text = Component.literal(definition.title());
                if (!definition.body().isEmpty()) {
                    if (!definition.title().isEmpty()) {
                        text.append("\n");
                    }
                    text.append(definition.body());
                }
                MultiLineTextWidget widget = HUD.get(id);
                if (widget == null) {
                    widget = new MultiLineTextWidget(text, client.font)
                            .setMaxWidth(hudWidth > 0 ? hudWidth : 256)
                            .setMaxRows(8);
                    HUD.put(id, widget);
                } else {
                    widget.setMessage(text);
                }
            }
            case HIDDEN -> {
                closeMatchingScreen(client, id);
                HUD.remove(id);
            }
        }
    }

    private static void closeMatchingScreen(Minecraft client, String id) {
        if (client.screen instanceof LoaderTextScreen screen && screen.uiId().equals(id)) {
            client.setScreen(null);
        }
    }

    /** Minecraft owns modal teardown; resource activation/unmount drops connection HUD state. */
    public static void clear() {
        HUD.clear();
        hudWidth = -1;
    }

    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker ignoredDelta) {
        if (HUD.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }
        int width = Math.max(1, Math.min(256, graphics.guiWidth() - 16));
        if (width != hudWidth) {
            for (MultiLineTextWidget widget : HUD.values()) {
                widget.setMaxWidth(width);
            }
            hudWidth = width;
        }
        int y = 8;
        for (MultiLineTextWidget widget : HUD.values()) {
            int height = widget.getHeight();
            if (y + height + 4 > graphics.guiHeight() - 8) {
                break;
            }
            graphics.fill(4, y - 4, 12 + widget.getWidth(), y + height + 4, 0xb0000000);
            widget.setX(8);
            widget.setY(y);
            widget.extractRenderState(graphics, -1, -1, 0.0F);
            y += height + 12;
        }
    }
}
