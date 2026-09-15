package dev.solaris.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One bounded, server-authoritative page of one declarative screen, as
 * presented by a wire-3 `open_view` or `present_view` message.
 */
public record LoaderViewModel(
        int page,
        int pageCount,
        List<Row> rows,
        List<Field> fields,
        List<Action> actions,
        List<Tab> tabs,
        List<ResourceEntry> resourceEntries,
        List<Marker> markers,
        Optional<String> reason) {
    public static final int MAX_ROWS = 64;
    public static final int MAX_FIELDS = 16;
    public static final int MAX_ACTIONS = 16;
    public static final int MAX_TABS = 16;
    public static final int MAX_RESOURCES = 16;
    public static final int MAX_MARKERS = 16;
    public static final int MAX_CELLS_PER_ROW = 16;
    public static final int MAX_CELL_BYTES = 256;
    public static final int MAX_TEXT_BYTES = 256;
    public static final int MAX_LABEL_BYTES = 128;
    public static final int MAX_DENY_REASON_BYTES = 128;
    public static final int MAX_REASON_BYTES = 256;
    public static final double MAX_MARKER_RADIUS = 128.0;

    private static final Set<String> MODEL_FIELDS = Set.of(
            "page",
            "page_count",
            "rows",
            "fields",
            "actions",
            "tabs",
            "resource_entries",
            "markers",
            "reason");
    private static final Set<String> ROW_FIELDS = Set.of("cells");
    private static final Set<String> FIELD_FIELDS = Set.of("id", "number", "text", "selected");
    private static final Set<String> ACTION_FIELDS =
            Set.of("action_id", "enabled", "label", "deny_reason");
    private static final Set<String> TAB_FIELDS = Set.of("id", "label");
    private static final Set<String> RESOURCE_FIELDS = Set.of("id", "have", "need");
    private static final Set<String> MARKER_FIELDS = Set.of(
            "marker_id",
            "selection_token",
            "action_id",
            "formation",
            "radius");

    public LoaderViewModel {
        rows = List.copyOf(rows);
        fields = List.copyOf(fields);
        actions = List.copyOf(actions);
        tabs = List.copyOf(tabs);
        resourceEntries = List.copyOf(resourceEntries);
        markers = List.copyOf(markers);
        reason = reason == null ? Optional.empty() : reason;
    }

    /** One table row; every cell is a bounded display string, never authority. */
    public record Row(List<String> cells) {
        public Row {
            cells = List.copyOf(cells);
        }
    }

    /** One typed form field carrying exactly one of number, text or selected. */
    public record Field(
            String id,
            Optional<Double> number,
            Optional<String> text,
            Optional<String> selected) {
        public Field {
            number = number == null ? Optional.empty() : number;
            text = text == null ? Optional.empty() : text;
            selected = selected == null ? Optional.empty() : selected;
        }

        public static Field ofNumber(String id, double number) {
            return new Field(id, Optional.of(number), Optional.empty(), Optional.empty());
        }

        public static Field ofText(String id, String text) {
            return new Field(id, Optional.empty(), Optional.of(text), Optional.empty());
        }

        public static Field ofSelected(String id, String selected) {
            return new Field(id, Optional.empty(), Optional.empty(), Optional.of(selected));
        }

        public int typedValues() {
            return (number.isPresent() ? 1 : 0) + (text.isPresent() ? 1 : 0) + (selected.isPresent() ? 1 : 0);
        }
    }

    public record Action(
            String actionId,
            boolean enabled,
            Optional<String> label,
            Optional<String> denyReason) {
        public Action {
            label = label == null ? Optional.empty() : label;
            denyReason = denyReason == null ? Optional.empty() : denyReason;
        }
    }

    public record Tab(String id, String label) {
    }

    public record ResourceEntry(String id, double have, double need) {
    }

    /**
     * One world-selection affordance. `selectionToken` is the opaque
     * server-issued context id the client may only echo back.
     */
    public record Marker(
            String markerId,
            Optional<String> selectionToken,
            Optional<String> actionId,
            Optional<LoaderFormation> formation,
            Optional<Double> radius) {
        public Marker {
            selectionToken = selectionToken == null ? Optional.empty() : selectionToken;
            actionId = actionId == null ? Optional.empty() : actionId;
            formation = formation == null ? Optional.empty() : formation;
            radius = radius == null ? Optional.empty() : radius;
        }
    }

    /** Parse one closed model object; every deviation fails closed. */
    static LoaderViewModel parse(JsonObject model) {
        LoaderJson.rejectUnknown(model, MODEL_FIELDS, "loader view model");
        int page = (int) LoaderJson.integer(model, "page", 0, Integer.MAX_VALUE, "loader view page");
        int pageCount = (int) LoaderJson.integer(
                model, "page_count", 1, Integer.MAX_VALUE, "loader view page count");
        if (page >= pageCount) {
            throw new IllegalArgumentException("loader view page must be below its page count");
        }
        List<Row> rows = rows(model);
        List<Field> fields = fields(model);
        List<Action> actions = actions(model);
        List<Tab> tabs = tabs(model);
        List<ResourceEntry> resources = resources(model);
        List<Marker> markers = markers(model);
        Optional<String> reason = LoaderJson.optionalText(
                model, "reason", MAX_REASON_BYTES, "loader view reason");
        return new LoaderViewModel(
                page, pageCount, rows, fields, actions, tabs, resources, markers, reason);
    }

    private static List<Row> rows(JsonObject model) {
        List<JsonElement> entries = LoaderJson.array(model, "rows", MAX_ROWS, "loader view rows");
        List<Row> rows = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            JsonObject row = LoaderJson.object(entry, "loader view row");
            LoaderJson.rejectUnknown(row, ROW_FIELDS, "loader view row");
            List<JsonElement> cells = LoaderJson.array(
                    row, "cells", MAX_CELLS_PER_ROW, "loader view row cells");
            List<String> values = new ArrayList<>(cells.size());
            for (JsonElement cell : cells) {
                values.add(LoaderJson.bounded(
                        LoaderJson.string(cell, "loader view cell"),
                        MAX_CELL_BYTES,
                        "loader view cell"));
            }
            rows.add(new Row(values));
        }
        return rows;
    }

    private static List<Field> fields(JsonObject model) {
        List<JsonElement> entries = LoaderJson.array(model, "fields", MAX_FIELDS, "loader view fields");
        List<Field> fields = new ArrayList<>(entries.size());
        Set<String> ids = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject field = LoaderJson.object(entry, "loader view field");
            LoaderJson.rejectUnknown(field, FIELD_FIELDS, "loader view field");
            String id = LoaderJson.identifier(field, "id", "loader view field id");
            int typed = (field.has("number") ? 1 : 0)
                    + (field.has("text") ? 1 : 0)
                    + (field.has("selected") ? 1 : 0);
            if (typed != 1) {
                throw new IllegalArgumentException(
                        "loader view field must carry exactly one typed value");
            }
            Field parsed = field.has("number")
                    ? Field.ofNumber(id, LoaderJson.number(field, "number", "loader view field number"))
                    : field.has("text")
                            ? Field.ofText(
                                    id,
                                    LoaderJson.text(
                                            field, "text", MAX_TEXT_BYTES, "loader view field text"))
                            : Field.ofSelected(
                                    id,
                                    LoaderJson.identifier(
                                            field, "selected", "loader view field selection"));
            if (!ids.add(id)) {
                throw new IllegalArgumentException("loader view model repeats field " + id);
            }
            fields.add(parsed);
        }
        return fields;
    }

    private static List<Action> actions(JsonObject model) {
        List<JsonElement> entries = LoaderJson.array(
                model, "actions", MAX_ACTIONS, "loader view actions");
        List<Action> actions = new ArrayList<>(entries.size());
        Set<String> ids = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject action = LoaderJson.object(entry, "loader view action");
            LoaderJson.rejectUnknown(action, ACTION_FIELDS, "loader view action");
            String id = LoaderJson.identifier(action, "action_id", "loader view action id");
            boolean enabled = LoaderJson.flag(action, "enabled", "loader view action enabled");
            Optional<String> label = LoaderJson.optionalText(
                    action, "label", MAX_LABEL_BYTES, "loader view action label");
            Optional<String> denyReason = LoaderJson.optionalText(
                    action, "deny_reason", MAX_DENY_REASON_BYTES, "loader view action deny reason");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("loader view model repeats action " + id);
            }
            actions.add(new Action(id, enabled, label, denyReason));
        }
        return actions;
    }

    private static List<Tab> tabs(JsonObject model) {
        List<JsonElement> entries = LoaderJson.array(model, "tabs", MAX_TABS, "loader view tabs");
        List<Tab> tabs = new ArrayList<>(entries.size());
        Set<String> ids = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject tab = LoaderJson.object(entry, "loader view tab");
            LoaderJson.rejectUnknown(tab, TAB_FIELDS, "loader view tab");
            String id = LoaderJson.identifier(tab, "id", "loader view tab id");
            String label = LoaderJson.nonEmpty(tab, "label", MAX_CELL_BYTES, "loader view tab label");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("loader view model repeats tab " + id);
            }
            tabs.add(new Tab(id, label));
        }
        return tabs;
    }

    private static List<ResourceEntry> resources(JsonObject model) {
        List<JsonElement> entries = LoaderJson.array(
                model, "resource_entries", MAX_RESOURCES, "loader view resource entries");
        List<ResourceEntry> resources = new ArrayList<>(entries.size());
        Set<String> ids = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject resource = LoaderJson.object(entry, "loader view resource entry");
            LoaderJson.rejectUnknown(resource, RESOURCE_FIELDS, "loader view resource entry");
            String id = LoaderJson.identifier(resource, "id", "loader view resource id");
            double have = LoaderJson.number(
                    resource, "have", 0.0, Double.MAX_VALUE, "loader view resource have");
            double need = LoaderJson.number(
                    resource, "need", 0.0, Double.MAX_VALUE, "loader view resource need");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("loader view model repeats resource " + id);
            }
            resources.add(new ResourceEntry(id, have, need));
        }
        return resources;
    }

    private static List<Marker> markers(JsonObject model) {
        List<JsonElement> entries = LoaderJson.array(
                model, "markers", MAX_MARKERS, "loader view markers");
        List<Marker> markers = new ArrayList<>(entries.size());
        Set<String> ids = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject marker = LoaderJson.object(entry, "loader view marker");
            LoaderJson.rejectUnknown(marker, MARKER_FIELDS, "loader view marker");
            String id = LoaderJson.identifier(marker, "marker_id", "loader view marker id");
            Optional<String> token = LoaderJson.optionalIdentifier(
                    marker, "selection_token", "loader view selection token");
            Optional<String> actionId = LoaderJson.optionalIdentifier(
                    marker, "action_id", "loader view marker action");
            if (token.isPresent() && actionId.isEmpty()) {
                throw new IllegalArgumentException(
                        "loader view marker with a selection token requires an action");
            }
            Optional<LoaderFormation> formation = LoaderJson.optionalFormation(
                    marker, "formation", "loader view marker formation");
            Optional<Double> radius = LoaderJson.optionalNumber(
                    marker,
                    "radius",
                    0.0,
                    MAX_MARKER_RADIUS,
                    "loader view marker radius");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("loader view model repeats marker " + id);
            }
            if (token.isPresent() && !tokens.add(token.orElseThrow())) {
                throw new IllegalArgumentException(
                        "loader view model repeats a selection token");
            }
            markers.add(new Marker(id, token, actionId, formation, radius));
        }
        return markers;
    }
}
