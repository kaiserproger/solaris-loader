package dev.solaris.loader.fabric;

import dev.solaris.loader.ComputerProtocol;
import dev.solaris.loader.ComputerTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ComputerScreen extends Screen {
    private final ComputerTerminal terminal;
    private final Consumer<byte[]> send;
    private EditBox input;
    private int page;
    private boolean closeSent;
    private boolean waiting = true;
    private final long openDeadline = System.nanoTime() + 10_000_000_000L;

    ComputerScreen(int session, Consumer<byte[]> send) {
        super(Component.literal("Solaris Computer"));
        this.terminal = new ComputerTerminal(session);
        this.send = send;
        terminal.append("Connecting to operator terminal…\n");
    }
    void receive(ComputerProtocol.Reply reply) {
        if (terminal.accept(reply)) {
            if (reply.kind() != 1) waiting = false;
            page = 0;
            rebuildWidgets();
        }
    }
    @Override public void tick() {
        if (waiting && System.nanoTime() >= openDeadline) {
            waiting = false;
            send.accept(ComputerProtocol.close(terminal.session()));
            receive(new ComputerProtocol.Reply(terminal.session(), 2,
                    "Server did not open the computer. Check [computer].enabled and operator access."));
        }
        super.tick();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }
    @Override protected void init() {
        String draft = input == null ? "" : input.getValue();
        int columns = Math.max(16, (width - 32) / 6);
        int rows = Math.max(1, (height - 92) / 12);
        List<String> lines = new ArrayList<>();
        for (String line : terminal.text().split("\n", -1)) {
            if (line.isEmpty()) lines.add("");
            for (int start = 0; start < line.length(); start += columns) {
                lines.add(line.substring(start, Math.min(start + columns, line.length())));
            }
        }
        int pages = Math.max(1, (lines.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int end = Math.max(0, lines.size() - page * rows);
        int start = Math.max(0, end - rows);
        for (int i = start; i < end; i++) {
            var label = new StringWidget(Component.literal(lines.get(i)), font);
            label.setX(12); label.setY(24 + (i - start) * 12);
            addRenderableWidget(label);
        }
        input = new EditBox(font, 12, height - 52, Math.max(40, width - 104), 20, Component.literal("Line to agent"));
        input.setMaxLength(ComputerProtocol.MAX_INPUT);
        input.setValue(draft);
        input.active = terminal.connected();
        addRenderableWidget(input);
        var submit = Button.builder(Component.literal("Send"), ignored -> submit())
                .bounds(width - 84, height - 52, 72, 20).build();
        submit.active = terminal.connected();
        addRenderableWidget(submit);
        var older = Button.builder(Component.literal("Older"), ignored -> { page++; rebuildWidgets(); })
                .bounds(12, height - 26, 72, 20).build();
        older.active = page < pages - 1;
        addRenderableWidget(older);
        var newer = Button.builder(Component.literal("Newer"), ignored -> { page--; rebuildWidgets(); })
                .bounds(88, height - 26, 72, 20).build();
        newer.active = page > 0;
        addRenderableWidget(newer);
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(width - 84, height - 26, 72, 20).build());
        setInitialFocus(input);
    }
    private void submit() {
        if (!terminal.connected()) return;
        String text = input.getValue();
        try {
            byte[] payload = ComputerProtocol.line(terminal.session(), text);
            send.accept(payload);
            terminal.append("> " + text + "\n");
            input.setValue("");
        } catch (IllegalArgumentException error) {
            terminal.append(error.getMessage() + "\n");
        }
        page = 0;
        rebuildWidgets();
    }
    @Override public void removed() {
        if (!closeSent) {
            closeSent = true;
            send.accept(ComputerProtocol.close(terminal.session()));
        }
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
