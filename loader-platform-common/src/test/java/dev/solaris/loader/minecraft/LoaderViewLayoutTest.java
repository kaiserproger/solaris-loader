package dev.solaris.loader.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solaris.loader.LoaderWidget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * View screens stay readable at 1280x720 and 1920x1080 across GUI scales:
 * content is paginated instead of clipped and table rows fit their width.
 */
final class LoaderViewLayoutTest {
    private static final int WINDOW_1280 = 1280;
    private static final int WINDOW_1920 = 1920;
    private static final int LINE = 9;
    private static final int SPACING = 4;

    @Test
    void everyPageFitsAtEveryWindowAndGuiScale() {
        for (int windowWidth : new int[] {WINDOW_1280, WINDOW_1920}) {
            for (int scale : new int[] {1, 2, 3, 4}) {
                int guiWidth = windowWidth / scale;
                int guiHeight = (windowWidth == WINDOW_1280 ? 720 : 1080) / scale;
                int[] sections = sections();
                int available = guiHeight - 24 - 28;

                List<LoaderViewLayout.Range> pages =
                        LoaderViewLayout.pages(sections, available, SPACING);
                assertTrue(pages.size() >= 1);
                int covered = 0;
                for (LoaderViewLayout.Range page : pages) {
                    int height = 0;
                    for (int index = page.from(); index < page.to(); index++) {
                        height += sections[index] + (index == page.from() ? 0 : SPACING);
                    }
                    assertTrue(
                            height <= available,
                            "page of " + page.size() + " sections overflowed "
                                    + available + " GUI units at " + guiWidth + "x" + guiHeight);
                    assertTrue(page.size() > 0);
                    covered += page.size();
                }
                assertEquals(sections.length, covered);
                assertTrue(
                        pages.size() > 1,
                        "a full page of content must paginate at " + guiWidth + "x" + guiHeight);

                int[] widths = LoaderViewLayout.columnWidths(guiWidth, columns());
                for (int width : widths) {
                    assertTrue(width >= LoaderViewLayout.MIN_COLUMN_CHARACTERS);
                }
                String row = LoaderViewLayout.row(
                        List.of("Ruby Loader Fixture", "128"),
                        columns(),
                        widths);
                assertTrue(
                        row.length() * LoaderViewLayout.CHARACTER_WIDTH <= guiWidth,
                        "table row exceeded the screen width at " + guiWidth + "x" + guiHeight);
            }
        }
    }

    @Test
    void rowsAreAlignedTruncatedAndNumbered() {
        int[] widths = LoaderViewLayout.columnWidths(WINDOW_1920, columns());
        String row = LoaderViewLayout.row(List.of("Hamlet", "12"), columns(), widths);
        assertEquals(widths[0] + 1 + widths[1], row.length());
        assertTrue(row.startsWith("Hamlet"));
        assertTrue(row.endsWith("12"));

        String truncated = LoaderViewLayout.row(
                List.of("x".repeat(400), "1"), columns(), widths);
        assertEquals(widths[0] + 1 + widths[1], truncated.length());
        assertTrue(truncated.contains("\u2026"));

        String centered = LoaderViewLayout.cell(
                "ab", 6, LoaderWidget.Align.CENTER);
        assertEquals(6, centered.length());
        assertTrue(centered.contains("ab"));

        assertEquals("3", LoaderViewLayout.number(3.0));
        assertEquals("2.5", LoaderViewLayout.number(2.5));
        assertEquals("0", LoaderViewLayout.number(0.0));
        assertEquals("-7", LoaderViewLayout.number(-7.0));
    }

    @Test
    void anEmptyViewIsOneEmptyPage() {
        List<LoaderViewLayout.Range> pages = LoaderViewLayout.pages(new int[0], 100, SPACING);
        assertEquals(1, pages.size());
        assertEquals(0, pages.get(0).size());
    }

    @Test
    void aSectionTallerThanOnePageStillGetsItsOwnPage() {
        List<LoaderViewLayout.Range> pages =
                LoaderViewLayout.pages(new int[] {10, 400, 10}, 100, SPACING);
        assertEquals(3, pages.size());
        assertEquals(1, pages.get(1).size());
        assertEquals(1, pages.get(0).size());
        assertEquals(1, pages.get(2).size());
    }

    private static int[] sections() {
        List<Integer> heights = new ArrayList<>();
        heights.add(LINE);
        heights.add(LINE);
        for (int row = 0; row < 64; row++) {
            heights.add(LINE);
        }
        for (int resource = 0; resource < 16; resource++) {
            heights.add(LINE);
        }
        for (int action = 0; action < 8; action++) {
            heights.add(20);
        }
        int[] sections = new int[heights.size()];
        for (int index = 0; index < sections.length; index++) {
            sections[index] = heights.get(index);
        }
        return sections;
    }

    private static List<LoaderWidget.Column> columns() {
        return List.of(
                new LoaderWidget.Column("name", "Name", LoaderWidget.Align.LEFT, 3),
                new LoaderWidget.Column("count", "Count", LoaderWidget.Align.RIGHT, 1));
    }
}
