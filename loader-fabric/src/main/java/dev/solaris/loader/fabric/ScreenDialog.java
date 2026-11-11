package dev.solaris.loader.fabric;

import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small centered confirmation dialog: text lines over a row of buttons. Escape runs {@code escape}. */
final class ScreenDialog extends Screen {
    record Choice(String label, Runnable action) {}

    private final List<String> lines;
    private final List<Choice> choices;
    private final Runnable escape;
    private boolean answered;

    ScreenDialog(String title, List<String> lines, List<Choice> choices, Runnable escape) {
        super(Component.literal(title));
        this.lines = lines;
        this.choices = choices;
        this.escape = escape;
    }

    @Override
    protected void init() {
        int top = height / 2 - 12 * (lines.size() + 2) / 2 - 10;
        var heading = new StringWidget(title, font);
        heading.setPosition((width - heading.getWidth()) / 2, top);
        addRenderableWidget(heading);
        for (int i = 0; i < lines.size(); i++) {
            var label = new StringWidget(Component.literal(lines.get(i)), font);
            label.setPosition((width - label.getWidth()) / 2, top + 16 + 12 * i);
            addRenderableWidget(label);
        }
        int buttonWidth = 100;
        int total = choices.size() * buttonWidth + (choices.size() - 1) * 8;
        int y = top + 28 + 12 * lines.size();
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            addRenderableWidget(Button.builder(Component.literal(choice.label()), ignored -> {
                answered = true;
                onClose();
                choice.action().run();
            }).bounds((width - total) / 2 + i * (buttonWidth + 8), y, buttonWidth, 20).build());
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void removed() {
        if (!answered) {
            answered = true;
            escape.run();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
