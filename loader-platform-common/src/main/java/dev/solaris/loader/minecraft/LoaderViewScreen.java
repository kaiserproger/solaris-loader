package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderFormation;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewActionRequest;
import dev.solaris.loader.LoaderViewMessage;
import dev.solaris.loader.LoaderViewModel;
import dev.solaris.loader.LoaderWidget;
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
 * id, revision and action id with a per-instance increasing sequence; a
 * `present_view` replaces the model and resets the sequence, and a disabled
 * action sends nothing.
 */
final class LoaderViewScreen extends Screen {
    static final int MARGIN = 8;
    private static final int SPACING = 4;
    private static final int TOP_MARGIN = 24;
    private static final int NAV_HEIGHT = 28;
    private static final int NAV_BUTTON_WIDTH = 110;
    private static final int EDIT_WIDTH = 160;
    private static final int ACTION_WIDTH = 200;
    private static final int MAX_WRAPPED_ROWS = 8;

    private final String viewInstanceId;
    private final LoaderScreenDefinition definition;
    private final List<ItemStack> displayItems;
    private final Consumer<byte[]> send;
    private final Map<String, String> fieldText = new LinkedHashMap<>();
    private final Map<String, Integer> selections = new LinkedHashMap<>();
    private long revision;
    private LoaderViewModel model;
    private long sequence;
    private int contentPage;
    private String selectedTab;

    LoaderViewScreen(
            LoaderViewMessage.Open open,
            LoaderScreenDefinition definition,
            List<ItemStack> displayItems,
            Consumer<byte[]> send) {
        super(Component.literal(open.title()));
        viewInstanceId = open.viewInstanceId();
        revision = open.revision();
        model = open.model();
        this.definition = definition;
        this.displayItems = List.copyOf(displayItems);
        this.send = send;
        seedEditors();
    }

    String viewInstanceId() {
        return viewInstanceId;
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
        if (pages.size() > 1) {
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
        for (LoaderWidget widget : definition.widgets()) {
            switch (widget) {
                case LoaderWidget.PagedTable table -> table(table, sections);
                case LoaderWidget.Tabs ignoredTabs -> {
                    // Rendered from the presented tabs above.
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
                case LoaderWidget.WorldMarker marker -> sections.add(marker(marker));
            }
        }
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
            sections.add(new AbstractWidget[] {line(markerLine(marker))});
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

    private AbstractWidget[] marker(LoaderWidget.WorldMarker declared) {
        String preview = declared.previewId().orElse("none");
        String formation = declared.formation().map(LoaderFormation::wireName).orElse("none");
        String radius = declared.radius().map(LoaderViewLayout::number).orElse("none");
        return new AbstractWidget[] {line(declared.label()
                + " (action " + declared.actionId() + ", preview " + preview
                + ", formation " + formation + ", radius " + radius + ")")};
    }

    private String markerLine(LoaderViewModel.Marker marker) {
        String formation = marker.formation().map(LoaderFormation::wireName).orElse("none");
        String radius = marker.radius().map(LoaderViewLayout::number).orElse("none");
        return "marker " + marker.markerId()
                + (marker.selectionToken().isPresent() ? " [selection armed]" : "")
                + " formation " + formation + " radius " + radius;
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
