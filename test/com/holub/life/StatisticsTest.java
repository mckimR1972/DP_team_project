package com.holub.life;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsTest {
    private final List<Statistics.Summary> updates = new ArrayList<>();
    private final Statistics statistics = new Statistics(updates::add);

    @Test
    void hasNoSummaryBeforeFirstEvent() {
        assertTrue(statistics.current().isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test
    void initialSnapshotReportsPopulationAndSuppliedGeneration() {
        statistics.onBoardChanged(event(INITIAL, 12, 4, 0, 5, 10, 15));

        Statistics.Summary summary = current();
        assertEquals(12, summary.generation());
        assertEquals(4, summary.livingCells());
        assertEquals(25.0, summary.livingRatio(), 0.000001);
        assertNoGenerationDelta(summary);
    }

    @Test
    void emptyBoardHasZeroPopulationAndPercentage() {
        statistics.onBoardChanged(event(INITIAL, 0, 4));

        assertEquals(0, current().livingCells());
        assertEquals(0.0, current().livingRatio(), 0.000001);
        assertNoGenerationDelta(current());
    }

    @Test
    void fullBoardHasOneHundredPercentPopulation() {
        statistics.onBoardChanged(event(INITIAL, 0, 2, 0, 1, 2, 3));

        assertEquals(4, current().livingCells());
        assertEquals(100.0, current().livingRatio(), 0.000001);
    }

    @Test
    void generationWithoutBaselineEstablishesOneWithoutInventingBirths() {
        statistics.onBoardChanged(event(GENERATION, 9, 3, 1));
        assertNoGenerationDelta(current());

        statistics.onBoardChanged(event(GENERATION, 10, 3, 2));
        assertGenerationDelta(1, 1);
    }

    @Test
    void blinkerHasTwoBirthsAndTwoDeaths() {
        BoardChangeListener listener = statistics;
        // Horizontal row 2 becomes vertical column 2 on a 5 x 5 board.
        listener.onBoardChanged(event(INITIAL, 40, 5, 11, 12, 13));
        listener.onBoardChanged(event(GENERATION, 41, 5, 7, 12, 17));

        assertEquals(41, current().generation());
        assertEquals(3, current().livingCells());
        assertEquals(12.0, current().livingRatio(), 0.000001);
        assertGenerationDelta(2, 2);
    }

    @Test
    void unchangedGenerationStillReportsMeasuredZeroDelta() {
        statistics.onBoardChanged(event(INITIAL, 5, 4, 0, 1, 4, 5));
        statistics.onBoardChanged(event(GENERATION, 6, 4, 0, 1, 4, 5));

        assertEquals(6, current().generation());
        assertEquals(4, current().livingCells());
        assertGenerationDelta(0, 0);
        assertEquals(2, updates.size());
    }

    @Test
    void countsAllDeathsWhenPopulationBecomesEmpty() {
        statistics.onBoardChanged(event(INITIAL, 0, 3, 0, 1, 2));
        statistics.onBoardChanged(event(GENERATION, 1, 3));

        assertEquals(0, current().livingCells());
        assertGenerationDelta(0, 3);
    }

    @Test
    void countsAllBirthsWhenPreviouslyEmptyBoardBecomesPopulated() {
        statistics.onBoardChanged(event(INITIAL, 0, 3));
        statistics.onBoardChanged(event(GENERATION, 1, 3, 0, 1, 2));

        assertEquals(3, current().livingCells());
        assertGenerationDelta(3, 0);
    }

    @ParameterizedTest
    @EnumSource(value = BoardChangeEvent.Kind.class, names = {"EDIT", "TEMPLATE", "TEMPLATE_UNDO"})
    void nonGenerationChangeReplacesBaselineWithoutCountingBirthsOrDeaths(BoardChangeEvent.Kind kind) {
        statistics.onBoardChanged(event(INITIAL, 4, 3, 0));
        statistics.onBoardChanged(event(GENERATION, 5, 3, 1, 2));
        assertGenerationDelta(2, 1);

        statistics.onBoardChanged(event(kind, 5, 3, 4, 5));
        assertEquals(5, current().generation());
        assertEquals(2, current().livingCells());
        assertNoGenerationDelta(current());

        statistics.onBoardChanged(event(GENERATION, 6, 3, 5, 6));
        assertGenerationDelta(1, 1);
    }

    @Test
    void clearResetsPopulationAndBaselineUsingEventGeneration() {
        statistics.onBoardChanged(event(INITIAL, 8, 3, 0, 1, 2));
        statistics.onBoardChanged(event(CLEAR, 0, 3));

        assertEquals(0, current().generation());
        assertEquals(0, current().livingCells());
        assertNoGenerationDelta(current());

        statistics.onBoardChanged(event(GENERATION, 1, 3, 8));
        assertGenerationDelta(1, 0);
    }

    @Test
    void loadUsesRestoredBoardAsNextGenerationBaseline() {
        statistics.onBoardChanged(event(INITIAL, 8, 3, 0, 3, 5));
        statistics.onBoardChanged(event(LOAD, 0, 3, 1, 4));

        assertEquals(0, current().generation());
        assertEquals(2, current().livingCells());
        assertNoGenerationDelta(current());

        statistics.onBoardChanged(event(GENERATION, 1, 3, 4, 7));
        assertGenerationDelta(1, 1);
    }

    @Test
    void newInitialSnapshotReplacesExistingBaseline() {
        statistics.onBoardChanged(event(INITIAL, 0, 3, 0));
        statistics.onBoardChanged(event(INITIAL, 50, 3, 4));

        assertEquals(50, current().generation());
        assertNoGenerationDelta(current());

        statistics.onBoardChanged(event(GENERATION, 51, 3, 4));
        assertGenerationDelta(0, 0);
    }

    @Test
    void repeatedGenerationsDoNotMutateSnapshotsOrRetainedBaseline() {
        BoardChangeEvent horizontal = event(INITIAL, 0, 5, 11, 12, 13);
        BoardChangeEvent vertical = event(GENERATION, 1, 5, 7, 12, 17);
        BitSet originalHorizontal = horizontal.copyAliveCells();
        BitSet originalVertical = vertical.copyAliveCells();

        statistics.onBoardChanged(horizontal);
        statistics.onBoardChanged(vertical);
        assertGenerationDelta(2, 2);

        statistics.onBoardChanged(event(GENERATION, 2, 5, 11, 12, 13));
        assertGenerationDelta(2, 2);
        assertEquals(originalHorizontal, horizontal.copyAliveCells());
        assertEquals(originalVertical, vertical.copyAliveCells());
    }

    @ParameterizedTest
    @ValueSource(longs = {8, 9, 12})
    void nonConsecutiveGenerationRebasesWithoutReportingSingleGenerationDelta(long generation) {
        statistics.onBoardChanged(event(INITIAL, 9, 3, 0));
        statistics.onBoardChanged(event(GENERATION, generation, 3, 4));

        assertEquals(generation, current().generation());
        assertEquals(1, current().livingCells());
        assertNoGenerationDelta(current());

        statistics.onBoardChanged(event(GENERATION, generation + 1, 3, 5));
        assertGenerationDelta(1, 1);
    }

    @Test
    void changedBoardSizeRebasesBeforeComparingCoordinates() {
        statistics.onBoardChanged(event(INITIAL, 3, 3, 8));
        statistics.onBoardChanged(event(GENERATION, 4, 4, 8));

        assertEquals(6.25, current().livingRatio(), 0.000001);
        assertNoGenerationDelta(current());

        statistics.onBoardChanged(event(GENERATION, 5, 4, 9));
        assertGenerationDelta(1, 1);
    }

    @Test
    void publishesOneImmutableSummaryForEachReceivedEvent() {
        statistics.onBoardChanged(event(INITIAL, 0, 3, 0));
        Statistics.Summary initial = current();
        statistics.onBoardChanged(event(EDIT, 0, 3, 1, 2));
        statistics.onBoardChanged(event(GENERATION, 1, 3, 1, 2));

        assertEquals(3, updates.size());
        assertSame(initial, updates.get(0));
        assertSame(current(), updates.get(2));
        assertEquals(1, initial.livingCells());
        assertEquals(0, initial.generation());
        assertEquals(2, current().livingCells());
        assertGenerationDelta(0, 0);
    }

    @Test
    void rejectsMissingCallback() {
        assertThrows(NullPointerException.class, () -> new Statistics(null));
    }

    @Test
    void rejectsMissingEventWithoutPublishingSummary() {
        assertThrows(NullPointerException.class, () -> statistics.onBoardChanged(null));
        assertTrue(statistics.current().isEmpty());
        assertTrue(updates.isEmpty());
    }

    private Statistics.Summary current() {
        return statistics.current().orElseThrow();
    }

    private void assertGenerationDelta(int births, int deaths) {
        assertTrue(current().hasGenerationDelta());
        assertEquals(births, current().births());
        assertEquals(deaths, current().deaths());
    }

    private static void assertNoGenerationDelta(Statistics.Summary summary) {
        assertFalse(summary.hasGenerationDelta());
        assertEquals(0, summary.births());
        assertEquals(0, summary.deaths());
    }

    private static BoardChangeEvent event(BoardChangeEvent.Kind kind, long generation,
                                         int boardSize, int... indices) {
        BitSet alive = new BitSet();
        for (int index : indices) {
            alive.set(index);
        }
        return new BoardChangeEvent(kind, generation, boardSize, alive);
    }
}
