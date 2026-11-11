package com.holub.life;

import java.util.BitSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.holub.life.BoardChangeEvent.Kind.INITIAL;
import static com.holub.life.BoardChangeEvent.Kind.TEMPLATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardChangeEventTest {
    @Test
    void retainsMetadataAndLastValidCell() {
        BitSet alive = new BitSet();
        alive.set(2 * 3 + 2);
        BoardChangeEvent event = new BoardChangeEvent(TEMPLATE, 42, 3, alive);

        assertEquals(TEMPLATE, event.kind());
        assertEquals(42, event.generation());
        assertEquals(3, event.boardSize());
        assertEquals(alive, event.copyAliveCells());
    }

    @Test
    void acceptsEmptyBoardAtGenerationZero() {
        BoardChangeEvent event = new BoardChangeEvent(INITIAL, 0, 1, new BitSet());

        assertEquals(0, event.generation());
        assertEquals(new BitSet(), event.copyAliveCells());
    }

    @Test
    void changingSourceAfterConstructionDoesNotChangeEvent() {
        BitSet source = new BitSet();
        source.set(1);
        BoardChangeEvent event = new BoardChangeEvent(INITIAL, 0, 3, source);

        source.clear();
        source.set(8);

        BitSet expected = new BitSet();
        expected.set(1);
        assertEquals(expected, event.copyAliveCells());
    }

    @Test
    void changingReturnedCopyDoesNotAffectOtherObservers() {
        BitSet source = new BitSet();
        source.set(4);
        BoardChangeEvent event = new BoardChangeEvent(INITIAL, 0, 3, source);
        BitSet firstObserverCopy = event.copyAliveCells();

        firstObserverCopy.clear();
        firstObserverCopy.set(0);

        assertEquals(source, event.copyAliveCells());
    }

    @Test
    void rejectsMissingKind() {
        assertThrows(NullPointerException.class,
                () -> new BoardChangeEvent(null, 0, 3, new BitSet()));
    }

    @Test
    void rejectsMissingAliveCells() {
        assertThrows(NullPointerException.class,
                () -> new BoardChangeEvent(INITIAL, 0, 3, null));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeGeneration(long generation) {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardChangeEvent(INITIAL, generation, 3, new BitSet()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 46341, Integer.MAX_VALUE})
    void rejectsInvalidBoardSize(int boardSize) {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardChangeEvent(INITIAL, 0, boardSize, new BitSet()));
    }

    @Test
    void rejectsLivingCellOutsideBoard() {
        BitSet alive = new BitSet();
        alive.set(9); // A 3 x 3 board only has indices 0 through 8.

        assertThrows(IllegalArgumentException.class,
                () -> new BoardChangeEvent(INITIAL, 0, 3, alive));
    }
}
