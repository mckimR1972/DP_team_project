package com.holub.life;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Event and aggregation tests with synthetic data; no actual board or UI. */
class ColorStatisticsTest {
    private final List<Statistics.Summary> summaries = new ArrayList<>();
    private final Statistics statistics = new Statistics(summaries::add);

    @Test void eventCopiesTheSourceColorListAndExposesOnlyAnImmutableList() {
        List<Color> colors = new ArrayList<>(List.of(Color.RED, Color.BLUE));
        BoardChangeEvent event = event(INITIAL, 0, colors);
        colors.clear();
        assertEquals(List.of(Color.RED, Color.BLUE), event.aliveColors());
        assertThrows(UnsupportedOperationException.class, () -> event.aliveColors().clear());
    }

    @Test void rejectsMissingColorListOrMissingColorEntry() {
        assertThrows(NullPointerException.class, () -> new BoardChangeEvent(INITIAL, 0, 1,
                new boolean[][]{{true}}, List.of(), List.of(), null));
        assertThrows(NullPointerException.class, () -> new BoardChangeEvent(INITIAL, 0, 1,
                new boolean[][]{{true}}, List.of(), List.of(), Arrays.asList((Color) null)));
    }

    @ParameterizedTest @ValueSource(ints = {0, 2})
    void rejectsColorCountDifferentFromTheLivingCellCount(int count) {
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(INITIAL, 0, 1,
                new boolean[][]{{true}}, List.of(), List.of(), java.util.Collections.nCopies(count, Color.RED)));
    }

    @Test void calculatesEachColorAgainstLivingCellsInsteadOfBoardArea() {
        statistics.onBoardChanged(event(INITIAL, 0, List.of(Color.RED, Color.RED, Color.BLUE, Color.GREEN)));
        assertEquals(4, current().livingCells());
        assertEquals(25.0, current().livingRatio()); // 4 of 16 board positions
        assertCount(Color.RED, 2, 50.0);
        assertCount(Color.BLUE, 1, 25.0);
        assertCount(Color.GREEN, 1, 25.0);
        assertEquals(4, current().colorCounts().stream().mapToInt(Statistics.ColorCount::cells).sum());
        assertEquals(100.0, current().colorCounts().stream().mapToDouble(Statistics.ColorCount::ratio).sum());
    }

    @Test void groupsEqualColorValuesIntoOneCount() {
        Color blue = new Color(0x0072B2);
        statistics.onBoardChanged(event(EDIT, 0, List.of(blue, new Color(0x0072B2), Color.RED)));
        assertEquals(2, current().colorCounts().size());
        assertCount(blue, 2, 200.0 / 3);
        assertCount(Color.RED, 1, 100.0 / 3);
    }

    @Test void singleColorUsesOneHundredPercentEvenOnASparseBoard() {
        statistics.onBoardChanged(event(INITIAL, 0, List.of(Color.RED)));
        assertEquals(6.25, current().livingRatio());
        assertCount(Color.RED, 1, 100.0);
    }

    @Test void emptyBoardRemovesPreviousColorsWithoutDividingByZero() {
        statistics.onBoardChanged(event(INITIAL, 0, List.of(Color.BLUE)));
        statistics.onBoardChanged(event(CLEAR, 0, List.of()));
        assertEquals(0, current().livingCells());
        assertEquals(0, current().livingRatio());
        assertTrue(current().colorCounts().isEmpty());
    }

    @Test void laterEventsReplaceCountsWithoutMutatingPreviousSummaries() {
        statistics.onBoardChanged(event(INITIAL, 0, List.of(Color.RED, Color.BLUE)));
        Statistics.Summary previous = current();
        statistics.onBoardChanged(event(EDIT, 0, List.of(Color.GREEN)));
        assertEquals(List.of(new Statistics.ColorCount(Color.BLUE, 1, 50),
                new Statistics.ColorCount(Color.RED, 1, 50)), previous.colorCounts());
        assertCount(Color.GREEN, 1, 100.0);
        assertEquals(1, current().colorCounts().size());
    }

    @Test void summaryDefensivelyCopiesItsColorResults() {
        List<Statistics.ColorCount> counts = new ArrayList<>(List.of(new Statistics.ColorCount(Color.RED, 1, 100)));
        Statistics.Summary summary = new Statistics.Summary(0, 1, 6.25, 0, 0, false, counts);
        counts.clear();
        assertEquals(1, summary.colorCounts().size());
        assertThrows(UnsupportedOperationException.class, () -> summary.colorCounts().clear());
    }

    @Test void policyChangePreservesTheLastGenerationDeltaWhileReplacingColors() {
        boolean[][] cells = new boolean[4][4]; cells[0][0] = cells[0][1] = true;
        statistics.onBoardChanged(new BoardChangeEvent(GENERATION, 10, 4, cells,
                List.of(new Point(0, 0)), List.of(new Point(2, 0)), List.of(Color.BLUE, Color.BLUE)));
        statistics.onBoardChanged(event(COLOR_POLICY_CHANGED, 10, List.of(Color.RED, Color.RED)));
        assertEquals(10, current().generation());
        assertTrue(current().hasGenerationDelta());
        assertEquals(1, current().births()); assertEquals(1, current().deaths());
        assertCount(Color.RED, 2, 100.0);
    }

    @Test void policyChangeWithoutAGenerationKeepsDeltaUnavailable() {
        statistics.onBoardChanged(event(COLOR_POLICY_CHANGED, 10, List.of(Color.RED)));
        assertFalse(current().hasGenerationDelta());
        assertEquals(0, current().births()); assertEquals(0, current().deaths());
    }

    @Test void policyChangeAfterEditingDoesNotRestoreAnOldGenerationDelta() {
        statistics.onBoardChanged(event(GENERATION, 10, List.of(Color.BLUE)));
        statistics.onBoardChanged(event(EDIT, 10, List.of(Color.BLUE, Color.RED)));
        statistics.onBoardChanged(event(COLOR_POLICY_CHANGED, 10, List.of(Color.RED, Color.RED)));
        assertFalse(current().hasGenerationDelta());
        assertCount(Color.RED, 2, 100.0);
    }

    private Statistics.Summary current() { return summaries.get(summaries.size() - 1); }
    private void assertCount(Color color, int cells, double ratio) {
        Statistics.ColorCount count = current().colorCounts().stream()
                .filter(item -> item.color().equals(color)).findFirst().orElseThrow();
        assertEquals(cells, count.cells()); assertEquals(ratio, count.ratio(), 0.000001);
    }
    private static BoardChangeEvent event(BoardChangeEvent.Kind kind, long generation, List<Color> colors) {
        boolean[][] cells = new boolean[4][4];
        for (int i = 0; i < colors.size(); i++) cells[i / 4][i % 4] = true;
        return new BoardChangeEvent(kind, generation, 4, cells, List.of(), List.of(), colors);
    }
}
