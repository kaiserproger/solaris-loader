package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderWidget;
import java.util.ArrayList;
import java.util.List;

/**
 * Widget-free view layout: vertical pagination and fixed-width table rows.
 * Every decision is derived from the screen's GUI-unit size, so the same view
 * stays readable at 1280x720 and 1920x1080 across GUI scales by paginating
 * instead of clipping.
 */
final class LoaderViewLayout {
    /** Approximate advance of the default font, in GUI units per character. */
    static final int CHARACTER_WIDTH = 6;
    static final int MIN_COLUMN_CHARACTERS = 3;

    private LoaderViewLayout() {
    }

    /** One half-open range of section indexes that fits on a single page. */
    record Range(int from, int to) {
        int size() {
            return to - from;
        }
    }

    /**
     * Split {@code sectionHeights} into consecutive pages that each fit in
     * {@code available} GUI units. A section taller than one page still gets its
     * own page, so no page ever overflows silently.
     */
    static List<Range> pages(int[] sectionHeights, int available, int spacing) {
        List<Range> pages = new ArrayList<>();
        int from = 0;
        int used = 0;
        for (int index = 0; index < sectionHeights.length; index++) {
            int height = sectionHeights[index];
            int candidate = used == 0 ? height : used + spacing + height;
            if (used > 0 && candidate > available) {
                pages.add(new Range(from, index));
                from = index;
                used = height;
            } else {
                used = candidate;
            }
        }
        if (from < sectionHeights.length || pages.isEmpty()) {
            pages.add(new Range(from, sectionHeights.length));
        }
        return pages;
    }

    /** Character width of every declared column inside {@code availableWidth}. */
    static int[] columnWidths(int availableWidth, List<LoaderWidget.Column> columns) {
        int[] widths = new int[columns.size()];
        int buckets = 0;
        for (LoaderWidget.Column column : columns) {
            buckets += column.widthBucket();
        }
        int usable = Math.max(
                MIN_COLUMN_CHARACTERS * columns.size(),
                (availableWidth - 2 * LoaderViewScreen.MARGIN) / CHARACTER_WIDTH);
        for (int index = 0; index < widths.length; index++) {
            widths[index] = Math.max(
                    MIN_COLUMN_CHARACTERS,
                    usable * columns.get(index).widthBucket() / buckets);
        }
        return widths;
    }

    /** One table line with every cell padded, aligned and truncated to its column. */
    static String row(List<String> cells, List<LoaderWidget.Column> columns, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < widths.length; index++) {
            if (index > 0) {
                line.append(' ');
            }
            String cell = index < cells.size() ? cells.get(index) : "";
            line.append(cell(cell, widths[index], columns.get(index).align()));
        }
        return line.toString();
    }

    static String cell(String text, int characters, LoaderWidget.Align align) {
        String value = text.length() > characters
                ? characters > 1
                        ? text.substring(0, characters - 1) + "\u2026"
                        : text.substring(0, characters)
                : text;
        int padding = characters - value.length();
        return switch (align) {
            case LEFT -> value + " ".repeat(padding);
            case RIGHT -> " ".repeat(padding) + value;
            case CENTER -> " ".repeat(padding / 2) + value + " ".repeat(padding - padding / 2);
        };
    }

    /** Display form of one presented number: integral values lose the fraction. */
    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
