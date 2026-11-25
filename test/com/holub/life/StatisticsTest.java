package com.holub.life;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsTest {
    private final List<Statistics.Summary> updates = new ArrayList<>();
    private final Statistics statistics = new Statistics(updates::add);

    @Test void hasNoSummaryBeforeFirstEvent() {
        assertTrue(statistics.current().isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test void initialSnapshotReportsPopulationAndSuppliedGeneration() {
        statistics.onBoardChanged(event(INITIAL, 12, 4, 0, 5, 10, 15));
        assertEquals(12, current().generation());
        assertEquals(4, current().livingCells());
        assertEquals(25.0, current().livingRatio(), 0.000001);
        assertNoDelta();
    }

    @Test void emptyBoardHasZeroPopulationAndPercentage() {
        statistics.onBoardChanged(event(INITIAL, 0, 4));
        assertEquals(0, current().livingCells());
        assertEquals(0.0, current().livingRatio());
        assertNoDelta();
    }

    @Test void fullBoardHasOneHundredPercentPopulation() {
        statistics.onBoardChanged(event(INITIAL, 0, 2, 0, 1, 2, 3));
        assertEquals(4, current().livingCells());
        assertEquals(100.0, current().livingRatio());
    }

    @Test void firstGenerationAlreadyContainsItsOwnDelta() {
        statistics.onBoardChanged(generation(9, 3, new int[]{1}, new int[]{0}, 1, 4));
        assertEquals(2, current().livingCells());
        assertDelta(1, 1);
    }

    @Test void blinkerUsesTheSuppliedCoordinates() {
        statistics.onBoardChanged(event(INITIAL, 40, 5, 11, 12, 13));
        statistics.onBoardChanged(generation(41, 5, new int[]{7, 17}, new int[]{11, 13}, 7, 12, 17));
        assertEquals(41, current().generation());
        assertEquals(3, current().livingCells());
        assertEquals(12.0, current().livingRatio());
        assertDelta(2, 2);
    }

    @Test void unchangedGenerationReportsMeasuredZeroRatherThanUnavailable() {
        statistics.onBoardChanged(event(GENERATION, 6, 4, 0, 1, 4, 5));
        assertEquals(4, current().livingCells());
        assertDelta(0, 0);
    }

    @Test void extinctionCountsAllDeaths() {
        statistics.onBoardChanged(generation(1, 3, new int[]{}, new int[]{0, 1, 2}));
        assertEquals(0, current().livingCells());
        assertDelta(0, 3);
    }

    @Test void countsAllBirthsInSuppliedTransition() {
        statistics.onBoardChanged(generation(1, 3, new int[]{0, 1, 2}, new int[]{}, 0, 1, 2));
        assertEquals(3, current().livingCells());
        assertDelta(3, 0);
    }

    @ParameterizedTest
    @CsvSource({"EDIT, 5", "TEMPLATE, 5", "TEMPLATE_UNDO, 5", "CLEAR, 0", "LOAD, 0"})
    void editingOrRestoringUpdatesPopulationAndClearsDelta(BoardChangeEvent.Kind kind, long generation) {
        statistics.onBoardChanged(generation(5, 3, new int[]{1, 2}, new int[]{0}, 1, 2));
        assertDelta(2, 1);

        // 편집·배치·Undo는 세대를 유지하고, Clear·Load는 세대 0으로 시작한다.
        int[] alive = kind == CLEAR ? new int[0] : new int[]{4, 5};
        statistics.onBoardChanged(event(kind, generation, 3, alive));
        assertEquals(generation, current().generation());
        assertEquals(alive.length, current().livingCells());
        assertNoDelta();
    }

    @Test void publishesOneSummaryForEachEvent() {
        statistics.onBoardChanged(event(INITIAL, 0, 3, 0));
        Statistics.Summary initial = current();
        assertEquals(1, updates.size());
        assertSame(initial, updates.get(0));

        statistics.onBoardChanged(event(EDIT, 0, 3, 1, 2));
        assertEquals(2, updates.size());
        assertSame(current(), updates.get(1));
        assertEquals(2, current().livingCells());
        assertNoDelta();

        statistics.onBoardChanged(event(GENERATION, 1, 3, 1, 2));
        assertEquals(3, updates.size());
        assertSame(current(), updates.get(2));
        assertEquals(1, current().generation());
        assertEquals(2, current().livingCells());
        assertDelta(0, 0);
        assertEquals(1, initial.livingCells());
        assertEquals(0, initial.generation());
    }

    @Test void rejectsMissingCallbackAndEvent() {
        assertThrows(NullPointerException.class, () -> new Statistics(null));
        assertThrows(NullPointerException.class, () -> statistics.onBoardChanged(null));
        assertTrue(statistics.current().isEmpty());
        assertTrue(updates.isEmpty());
    }

    private Statistics.Summary current() { return statistics.current().orElseThrow(); }
    private void assertDelta(int births, int deaths) {
        assertTrue(current().hasGenerationDelta());
        assertEquals(births, current().births());
        assertEquals(deaths, current().deaths());
    }
    private void assertNoDelta() {
        assertFalse(current().hasGenerationDelta());
        assertEquals(0, current().births());
        assertEquals(0, current().deaths());
    }
    private static BoardChangeEvent event(BoardChangeEvent.Kind kind, long generation, int size, int... alive) {
        boolean[][] cells = board(size, alive);
        return new BoardChangeEvent(kind, generation, size, cells,
                List.of(), List.of(), LifeTestSupport.redColors(cells));
    }
    private static BoardChangeEvent generation(long generation, int size, int[] births, int[] deaths, int... alive) {
        boolean[][] cells = board(size, alive);
        return new BoardChangeEvent(GENERATION, generation, size, cells,
                points(size, births), points(size, deaths), LifeTestSupport.redColors(cells));
    }
    private static boolean[][] board(int size, int... indices) {
        boolean[][] cells = new boolean[size][size];
        for (int i : indices) cells[i / size][i % size] = true;
        return cells;
    }
    private static List<Point> points(int size, int... indices) {
        List<Point> cells = new ArrayList<>();
        for (int i : indices) cells.add(new Point(i % size, i / size));
        return cells;
    }
}
