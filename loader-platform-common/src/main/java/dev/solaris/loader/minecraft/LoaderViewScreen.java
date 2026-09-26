package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewActionRequest;
import dev.solaris.loader.LoaderViewModel;
import dev.solaris.loader.LoaderWidget;
import dev.solaris.loader.LoaderWorldPreviewDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ItemDisplayWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One declarative Loader view instance: the presented model, its declared
 * widgets and the closed wire-3 action sink. Actions carry the exact instance
 * id, revision and action id with a sequence increasing within that revision.
 * A `present_view` replaces the model and restarts its sequence; a disabled
 * action sends nothing.
 *
 * <p>The same renderer serves the modal kind on the client's screen stack and,
 * with {@code hud}, the non-modal kind that extracts only its declared widgets
 * into the native HUD layer.
 */
final class LoaderViewScreen extends Screen {
    static final int MARGIN = 8;
    private static final int SPACING = 4;
    static final int TOP_MARGIN = 24;
    private static final int NAV_HEIGHT = 28;
    private static final int NAV_BUTTON_WIDTH = 110;
    private static final int EDIT_WIDTH = 160;
    private static final int ACTION_WIDTH = 200;
    private static final int MAX_PREVIEW_CELLS = 4096;
    private static final String PREVIEW_SYMBOLS =
            "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MAX_WRAPPED_ROWS = 8;

    private final String viewInstanceId;
    private final LoaderScreenDefinition definition;
    private final List<ItemStack> displayItems;
    private final Map<String, LoaderWorldPreviewDefinition> worldPreviews;
    private final Consumer<byte[]> send;
    private final boolean hud;
    private final Map<String, String> fieldText = new LinkedHashMap<>();
    private final Map<String, Integer> selections = new LinkedHashMap<>();
    private long revision;
    private LoaderViewModel model;
    private long sequence;
    private int contentPage;
    private String selectedTab;

    /**
     * One declared view: {@code hud} selects the non-modal presentation, which
     * extracts exactly the declared widgets and nothing else, so a HUD with no
     * declared widgets renders nothing; the modal presentation keeps the page,
     * reason and undeclared-model chrome the client has always shown.
     */
    LoaderViewScreen(
            String viewInstanceId,
            long revision,
            Component title,
            LoaderScreenDefinition definition,
            LoaderViewModel model,
            List<ItemStack> displayItems,
            Map<String, LoaderWorldPreviewDefinition> worldPreviews,
            Consumer<byte[]> send,
            boolean hud) {
        super(title);
        this.viewInstanceId = viewInstanceId;
        this.revision = revision;
        this.definition = definition;
        this.model = model;
        this.worldPreviews = worldPreviews;
        this.displayItems = List.copyOf(displayItems);
        this.send = send;
        this.hud = hud;
        seedEditors();
    }

    String viewInstanceId() {
        return viewInstanceId;
    }

    /**
     * Re-lay out the declared widgets at a new GUI size; the non-modal HUD
     * container follows the live window instead of the screen stack.
     */
    void relayout(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        rebuildWidgets();
    }

    /** Replace the presented model; prior actions and their sequence are dropped. */
    void present(long revision, LoaderViewModel model) {
        this.revision = revision;
        this.model = model;
        sequence = 0;
        contentPage = 0;
        selectedTab = null;
        fieldText.clear();
        selections.clear();
        seedEditors();
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        LoaderMinecraftView.dismiss(this);
        super.onClose();
    }

    @Override
    protected void init() {
        List<List<AbstractWidget>> pages = pages();
        contentPage = Math.min(Math.max(contentPage, 0), pages.size() - 1);
        int y = TOP_MARGIN;
        for (AbstractWidget widget : pages.get(contentPage)) {
            widget.setX(Math.max(MARGIN, (width - widget.getWidth()) / 2));
            widget.setY(y);
            addRenderableWidget(widget);
            y += widget.getHeight() + SPACING;
        }
        if (pages.size() > 1 && !hud) {
            addPageControls(pages.size());
        }
    }

    private void addPageControls(int pageCount) {
        int y = height - NAV_HEIGHT + (NAV_HEIGHT - Button.DEFAULT_HEIGHT) / 2;
        Button previous = Button.builder(Component.literal("Previous page"), ignored -> {
            contentPage--;
            rebuildWidgets();
        }).width(NAV_BUTTON_WIDTH).build();
        previous.active = contentPage > 0;
        previous.setX(MARGIN);
        previous.setY(y);
        addRenderableWidget(previous);

        StringWidget page = new StringWidget(
                Component.literal((contentPage + 1) + " / " + pageCount), font);
        page.setX(Math.max(MARGIN, (width - page.getWidth()) / 2));
        page.setY(y + (Button.DEFAULT_HEIGHT - page.getHeight()) / 2);
        addRenderableWidget(page);

        Button next = Button.builder(Component.literal("Next page"), ignored -> {
            contentPage++;
            rebuildWidgets();
        }).width(NAV_BUTTON_WIDTH).build();
        next.active = contentPage < pageCount - 1;
        next.setX(width - MARGIN - NAV_BUTTON_WIDTH);
        next.setY(y);
        addRenderableWidget(next);
    }

    /** Section layout: heights, then pagination, then widget creation. */
    private List<List<AbstractWidget>> pages() {
        List<AbstractWidget[]> sections = sections();
        int[] heights = new int[sections.size()];
        for (int index = 0; index < sections.size(); index++) {
            heights[index] = height(sections.get(index));
        }
        int available = height - TOP_MARGIN - NAV_HEIGHT;
        List<List<AbstractWidget>> pages = new ArrayList<>();
        for (LoaderViewLayout.Range range : LoaderViewLayout.pages(heights, available, SPACING)) {
            List<AbstractWidget> widgets = new ArrayList<>();
            for (int index = range.from(); index < range.to(); index++) {
                widgets.addAll(List.of(sections.get(index)));
            }
            pages.add(widgets);
        }
        return pages;
    }

    private static int height(AbstractWidget[] section) {
        int total = 0;
        for (AbstractWidget widget : section) {
            total += widget.getHeight() + SPACING;
        }
        return total;
    }

    private List<AbstractWidget[]> sections() {
        List<AbstractWidget[]> sections = new ArrayList<>();
        if (hud) {
            declared(sections, new LinkedHashSet<>(), new LinkedHashSet<>());
            return sections;
        }
        model.reason().ifPresent(reason ->
                sections.add(new AbstractWidget[] {line("reason: " + reason)}));
        sections.add(new AbstractWidget[] {
                line("Page " + (model.page() + 1) + " of " + model.pageCount())
        });
        if (!displayItems.isEmpty()) {
            List<AbstractWidget> items = new ArrayList<>(displayItems.size());
            for (ItemStack stack : displayItems) {
                items.add(new ItemDisplayWidget(
                        minecraft, 8, 8, 32, 32, stack.getHoverName(), stack, true, true));
            }
            sections.add(items.toArray(new AbstractWidget[0]));
        }
        if (!model.tabs().isEmpty()) {
            sections.add(tabRow());
        }
        Set<String> declaredActions = new LinkedHashSet<>();
        Set<String> declaredFields = new LinkedHashSet<>();
        declared(sections, declaredActions, declaredFields);
        for (LoaderViewModel.Action action : model.actions()) {
            if (!declaredActions.contains(action.actionId())) {
                sections.add(action(action.actionId(), action.label().orElse(action.actionId()),
                        action.enabled(), action.denyReason()));
            }
        }
        for (LoaderViewModel.Field field : model.fields()) {
            if (!declaredFields.contains(field.id())) {
                sections.add(new AbstractWidget[] {line(presented(field))});
            }
        }
        for (LoaderViewModel.Marker marker : model.markers()) {
            if (!canSelect(marker)) {
                continue;
            }
            if (marker.selectionToken().isPresent()) {
                String token = marker.selectionToken().orElseThrow();
                sections.add(new AbstractWidget[] {
                        Button.builder(
                                        Component.literal("Select: "
                                                + marker.actionId().orElse(marker.markerId())),
                                        ignored -> act(
                                                marker.actionId().orElse(marker.markerId()),
                                                Optional.of(token)))
                                .width(ACTION_WIDTH)
                                .build()
                });
                sections.add(new AbstractWidget[] {
                        Button.builder(
                                        Component.literal("Cancel selection"),
                                        ignored -> cancel(token))
                                .width(ACTION_WIDTH)
                                .build()
                });
            }
        }
        return sections;
    }

    /** The presented form of every widget the screen declares. */
    private void declared(
            List<AbstractWidget[]> sections,
            Set<String> declaredActions,
            Set<String> declaredFields) {
        for (LoaderWidget widget : definition.widgets()) {
            switch (widget) {
                case LoaderWidget.PagedTable table -> table(table, sections);
                case LoaderWidget.Tabs ignoredTabs -> {
                    // Rendered from the presented tabs above; a HUD renders them here.
                    if (hud && !model.tabs().isEmpty()) {
                        sections.add(tabRow());
                    }
                }
                case LoaderWidget.InputNumber number -> {
                    declaredFields.add(number.id());
                    sections.add(field(number.id(), number.label()));
                }
                case LoaderWidget.InputText text -> {
                    declaredFields.add(text.id());
                    sections.add(field(text.id(), text.label()));
                }
                case LoaderWidget.SelectEnum select -> {
                    declaredFields.add(select.id());
                    sections.add(field(select.id(), select.label()));
                }
                case LoaderWidget.ResourcePanel panel -> {
                    for (LoaderWidget.Entry entry : panel.entries()) {
                        sections.add(new AbstractWidget[] {
                                line(panel.label() + " " + entry.label() + ": " + resource(entry.id()))
                        });
                    }
                }
                case LoaderWidget.ActionButton button -> {
                    declaredActions.add(button.actionId());
                    sections.add(action(button));
                }
                case LoaderWidget.WorldMarker marker -> {
                    declaredActions.add(marker.actionId());
                    for (LoaderViewModel.Marker active : model.markers()) {
                        if (active.markerId().equals(marker.id())) {
                            preview(active).ifPresent(value -> appendPreview(value, sections));
                            break;
                        }
                    }
                }
            }
        }
    }

    private void table(LoaderWidget.PagedTable table, List<AbstractWidget[]> sections) {
        int[] widths = LoaderViewLayout.columnWidths(width, table.columns());
        List<String> labels = new ArrayList<>(table.columns().size());
        for (LoaderWidget.Column column : table.columns()) {
            labels.add(column.label());
        }
        sections.add(new AbstractWidget[] {line(LoaderViewLayout.row(labels, table.columns(), widths))});
        for (LoaderViewModel.Row row : model.rows()) {
            sections.add(new AbstractWidget[] {
                    line(LoaderViewLayout.row(row.cells(), table.columns(), widths))
            });
        }
    }

    private AbstractWidget[] tabRow() {
        List<AbstractWidget> buttons = new ArrayList<>();
        for (LoaderViewModel.Tab tab : model.tabs()) {
            boolean selected = tab.id().equals(selectedTab);
            Button button = Button.builder(
                            Component.literal(selected ? "[" + tab.label() + "]" : tab.label()),
                            ignored -> {
                                selectedTab = tab.id();
                                rebuildWidgets();
                            })
                    .width(buttonWidth(model.tabs().size()))
                    .build();
            buttons.add(button);
        }
        return buttons.toArray(new AbstractWidget[0]);
    }

    private int buttonWidth(int count) {
        return Math.max(40, (width - 2 * MARGIN - SPACING * Math.max(0, count - 1)) / Math.max(1, count));
    }

    private AbstractWidget[] field(String id, String label) {
        Optional<LoaderViewModel.Field> presented = model.fields().stream()
                .filter(field -> field.id().equals(id))
                .findFirst();
        if (presented.isEmpty()) {
            return new AbstractWidget[] {line(label + ": (not presented)")};
        }
        LoaderViewModel.Field field = presented.orElseThrow();
        if (field.number().isPresent()) {
            EditBox box = new EditBox(
                    font, 0, 0, EDIT_WIDTH, Button.DEFAULT_HEIGHT, Component.literal(label));
            box.setMaxLength(32);
            box.setResponder(value -> fieldText.put(id, value));
            box.setValue(fieldText.getOrDefault(
                    id, LoaderViewLayout.number(field.number().orElseThrow())));
            return new AbstractWidget[] {line(label), box};
        }
        if (field.text().isPresent()) {
            EditBox box = new EditBox(
                    font, 0, 0, EDIT_WIDTH, Button.DEFAULT_HEIGHT, Component.literal(label));
            box.setMaxLength(textLimit(id).orElse(LoaderWidget.MAX_INPUT_TEXT_BYTES));
            box.setResponder(value -> fieldText.put(id, value));
            box.setValue(fieldText.getOrDefault(id, field.text().orElseThrow()));
            return new AbstractWidget[] {line(label), box};
        }
        List<LoaderWidget.Entry> options = options(id);
        if (options.isEmpty()) {
            return new AbstractWidget[] {line(label + ": " + field.selected().orElseThrow())};
        }
        return new AbstractWidget[] {Button.builder(
                        Component.literal(label + ": " + selectedLabel(id, options, field)),
                        ignored -> {
                            selections.put(id, selections.getOrDefault(id, selectedIndex(id, options, field)) + 1);
                            rebuildWidgets();
                        })
                .width(ACTION_WIDTH)
                .build()};
    }

    private AbstractWidget[] action(LoaderWidget.ActionButton declared) {
        Optional<LoaderViewModel.Action> presented = model.actions().stream()
                .filter(action -> action.actionId().equals(declared.actionId()))
                .findFirst();
        return action(
                declared.actionId(),
                presented.flatMap(LoaderViewModel.Action::label).orElse(declared.label()),
                presented.map(LoaderViewModel.Action::enabled).orElse(declared.enabled()),
                presented.flatMap(LoaderViewModel.Action::denyReason)
                        .or(() -> declared.denyReason()));
    }

    private AbstractWidget[] action(
            String actionId,
            String label,
            boolean enabled,
            Optional<String> denyReason) {
        Button button = Button.builder(
                        Component.literal(label),
                        ignored -> act(actionId, Optional.empty()))
                .width(ACTION_WIDTH)
                .build();
        button.active = enabled;
        denyReason.ifPresent(reason -> button.setTooltip(Tooltip.create(Component.literal(reason))));
        if (!enabled && denyReason.isPresent()) {
            return new AbstractWidget[] {button, line("denied: " + denyReason.orElseThrow())};
        }
        return new AbstractWidget[] {button};
    }

    /** Declared field with no input widget: echoed, never editable. */
    private static String presented(LoaderViewModel.Field field) {
        if (field.number().isPresent()) {
            return field.id() + ": " + LoaderViewLayout.number(field.number().orElseThrow());
        }
        if (field.text().isPresent()) {
            return field.id() + ": " + field.text().orElseThrow();
        }
        return field.id() + ": " + field.selected().orElseThrow();
    }

    private boolean canSelect(LoaderViewModel.Marker marker) {
        for (LoaderWidget widget : definition.widgets()) {
            if (widget instanceof LoaderWidget.WorldMarker declared
                    && declared.id().equals(marker.markerId())
                    && declared.actionId().equals(marker.actionId().orElse(""))) {
                return declared.previewId().isEmpty() || preview(marker).isPresent();
            }
        }
        return false;
    }

    private Optional<LoaderWorldPreviewDefinition> preview(LoaderViewModel.Marker marker) {
        for (LoaderWidget widget : definition.widgets()) {
            if (!(widget instanceof LoaderWidget.WorldMarker declared)
                    || !declared.id().equals(marker.markerId())
                    || !declared.actionId().equals(marker.actionId().orElse(""))) {
                continue;
            }
            LoaderWorldPreviewDefinition value = declared.previewId().map(worldPreviews::get).orElse(null);
            if (value == null || value.blocks().isEmpty()
                    || (long) value.sizeX() * value.sizeY() * value.sizeZ() > MAX_PREVIEW_CELLS
                    || value.blocks().size() > MAX_PREVIEW_CELLS) {
                return Optional.empty();
            }
            Set<String> materials = new LinkedHashSet<>();
            for (LoaderWorldPreviewDefinition.Block block : value.blocks()) {
                materials.add(block.blockId());
                if (materials.size() > PREVIEW_SYMBOLS.length()) {
                    return Optional.empty();
                }
            }
            return Optional.of(value);
        }
        return Optional.empty();
    }

    private void appendPreview(
            LoaderWorldPreviewDefinition preview,
            List<AbstractWidget[]> sections) {
        int turn = preview.rotation();
        int width = (turn & 1) == 0 ? preview.sizeX() : preview.sizeZ();
        int depth = (turn & 1) == 0 ? preview.sizeZ() : preview.sizeX();
        char[][][] layers = new char[preview.sizeY()][depth][width];
        Map<String, Character> symbols = new LinkedHashMap<>();
        for (LoaderWorldPreviewDefinition.Block block : preview.blocks()) {
            char symbol = symbols.computeIfAbsent(
                    block.blockId(), ignored -> PREVIEW_SYMBOLS.charAt(symbols.size()));
            int x = switch (turn) {
                case 0 -> block.x();
                case 1 -> preview.sizeZ() - 1 - block.z();
                case 2 -> preview.sizeX() - 1 - block.x();
                case 3 -> block.z();
                default -> throw new IllegalStateException("validated preview turn");
            };
            int z = switch (turn) {
                case 0 -> block.z();
                case 1 -> block.x();
                case 2 -> preview.sizeZ() - 1 - block.z();
                case 3 -> preview.sizeX() - 1 - block.x();
                default -> throw new IllegalStateException("validated preview turn");
            };
            layers[block.y()][z][x] = symbol;
        }
        sections.add(new AbstractWidget[] {line(
                "Blueprint " + preview.blueprintId() + " @ " + (turn * 90)
                        + " degrees, " + width + "x" + preview.sizeY() + "x" + depth
                        + " (" + preview.contentHash().substring(0, 12) + ")")});
        for (int y = preview.sizeY() - 1; y >= 0; y--) {
            sections.add(new AbstractWidget[] {line("Layer Y+" + y)});
            for (int z = 0; z < depth; z++) {
                StringBuilder row = new StringBuilder(width);
                for (int x = 0; x < width; x++) {
                    char cell = layers[y][z][x];
                    row.append(cell == 0 ? '.' : cell);
                }
                sections.add(new AbstractWidget[] {line(row.toString())});
            }
        }
        for (Map.Entry<String, Character> entry : symbols.entrySet()) {
            sections.add(new AbstractWidget[] {line(entry.getValue() + " " + entry.getKey())});
        }
    }

    private String resource(String id) {
        for (LoaderViewModel.ResourceEntry entry : model.resourceEntries()) {
            if (entry.id().equals(id)) {
                return LoaderViewLayout.number(entry.have()) + "/" + LoaderViewLayout.number(entry.need());
            }
        }
        return "-";
    }

    private Optional<Integer> textLimit(String id) {
        return definition.widget(id)
                .filter(LoaderWidget.InputText.class::isInstance)
                .map(widget -> ((LoaderWidget.InputText) widget).maxBytes());
    }

    private List<LoaderWidget.Entry> options(String id) {
        return definition.widget(id)
                .filter(LoaderWidget.SelectEnum.class::isInstance)
                .map(widget -> ((LoaderWidget.SelectEnum) widget).options())
                .orElse(List.of());
    }

    private static int selectedIndex(
            String id,
            List<LoaderWidget.Entry> options,
            LoaderViewModel.Field field) {
        String selected = field.selected().orElseThrow();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id().equals(selected)) {
                return index;
            }
        }
        return 0;
    }

    private String selectedLabel(
            String id,
            List<LoaderWidget.Entry> options,
            LoaderViewModel.Field field) {
        int index = Math.floorMod(
                selections.getOrDefault(id, selectedIndex(id, options, field)), options.size());
        return options.get(index).label();
    }

    private void seedEditors() {
        for (LoaderViewModel.Field field : model.fields()) {
            field.number().ifPresent(value -> fieldText.putIfAbsent(
                    field.id(), LoaderViewLayout.number(value)));
            field.text().ifPresent(value -> fieldText.putIfAbsent(field.id(), value));
        }
    }

    /** Send one enabled action with the current typed field values. */
    private void act(String actionId, Optional<String> selectionToken) {
        Optional<LoaderViewModel.Action> action = model.actions().stream()
                .filter(candidate -> candidate.actionId().equals(actionId))
                .findFirst();
        if (action.isEmpty() || !action.orElseThrow().enabled()) {
            return;
        }
        sequence++;
        LoaderViewActionRequest
                .action(viewInstanceId, revision, actionId, sequence, fields(), selectionToken)
                .ifPresent(send);
    }

    private void cancel(String selectionToken) {
        LoaderViewActionRequest.cancelSelection(selectionToken).ifPresent(send);
    }

    /** The presented typed field schema with the values the user currently edits. */
    private List<LoaderViewModel.Field> fields() {
        List<LoaderViewModel.Field> fields = new ArrayList<>(model.fields().size());
        for (LoaderViewModel.Field field : model.fields()) {
            fields.add(edited(field));
        }
        return fields;
    }

    private LoaderViewModel.Field edited(LoaderViewModel.Field field) {
        if (field.number().isPresent()) {
            String text = fieldText.get(field.id());
            if (text == null) {
                return field;
            }
            try {
                double value = Double.parseDouble(text.trim());
                return Double.isFinite(value)
                        ? LoaderViewModel.Field.ofNumber(field.id(), value)
                        : field;
            } catch (NumberFormatException error) {
                return field;
            }
        }
        if (field.text().isPresent()) {
            String text = fieldText.get(field.id());
            return text == null ? field : LoaderViewModel.Field.ofText(field.id(), text);
        }
        List<LoaderWidget.Entry> options = options(field.id());
        Integer index = selections.get(field.id());
        if (index == null || options.isEmpty()) {
            return field;
        }
        return LoaderViewModel.Field.ofSelected(
                field.id(), options.get(Math.floorMod(index, options.size())).id());
    }

    private MultiLineTextWidget line(String text) {
        return new MultiLineTextWidget(Component.literal(text), font)
                .setMaxWidth(Math.max(20, width - 2 * MARGIN))
                .setMaxRows(MAX_WRAPPED_ROWS);
    }
}
