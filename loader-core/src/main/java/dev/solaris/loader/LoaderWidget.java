package dev.solaris.loader;

import java.util.List;
import java.util.Optional;

/**
 * One declarative schema-2 widget. Every value is already validated and
 * bounded when a screen reaches this model; there is no scripting slot.
 */
public sealed interface LoaderWidget {
    int MAX_WIDGETS_PER_SCREEN = 32;
    int MAX_COLUMNS = 16;
    int MAX_ENTRIES = 16;
    int MIN_WIDTH_BUCKET = 1;
    int MAX_WIDTH_BUCKET = 4;
    int MAX_LABEL_BYTES = 256;
    int MAX_DENY_REASON_BYTES = 128;
    int MIN_INPUT_TEXT_BYTES = 1;
    /** Matches the wire bound of one typed view text field. */
    int MAX_INPUT_TEXT_BYTES = 256;
    double MAX_MARKER_RADIUS = 128.0;

    String id();

    enum Align {
        LEFT("left"),
        CENTER("center"),
        RIGHT("right");

        private final String wireName;

        Align(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Align fromWireName(String value) {
            for (Align align : values()) {
                if (align.wireName.equals(value)) {
                    return align;
                }
            }
            throw new IllegalArgumentException("unknown Solaris Loader column align " + value);
        }
    }

    record Column(String id, String label, Align align, int widthBucket) {
    }

    record Entry(String id, String label) {
    }

    record PagedTable(String id, List<Column> columns) implements LoaderWidget {
        public PagedTable {
            columns = List.copyOf(columns);
        }
    }

    record Tabs(String id, List<Entry> entries) implements LoaderWidget {
        public Tabs {
            entries = List.copyOf(entries);
        }
    }

    record InputNumber(
            String id,
            String label,
            Optional<Double> min,
            Optional<Double> max,
            Optional<Double> step) implements LoaderWidget {
        public InputNumber {
            min = min == null ? Optional.empty() : min;
            max = max == null ? Optional.empty() : max;
            step = step == null ? Optional.empty() : step;
        }
    }

    record InputText(String id, String label, int maxBytes) implements LoaderWidget {
    }

    record SelectEnum(String id, String label, List<Entry> options) implements LoaderWidget {
        public SelectEnum {
            options = List.copyOf(options);
        }
    }

    record ResourcePanel(String id, String label, List<Entry> entries) implements LoaderWidget {
        public ResourcePanel {
            entries = List.copyOf(entries);
        }
    }

    record ActionButton(
            String actionId,
            String label,
            boolean enabled,
            Optional<String> denyReason) implements LoaderWidget {
        public ActionButton {
            denyReason = denyReason == null ? Optional.empty() : denyReason;
        }

        @Override
        public String id() {
            return actionId;
        }
    }

    record WorldMarker(
            String id,
            String label,
            String actionId,
            Optional<String> previewId,
            Optional<LoaderFormation> formation,
            Optional<Double> radius) implements LoaderWidget {
        public WorldMarker {
            previewId = previewId == null ? Optional.empty() : previewId;
            formation = formation == null ? Optional.empty() : formation;
            radius = radius == null ? Optional.empty() : radius;
        }
    }
}
