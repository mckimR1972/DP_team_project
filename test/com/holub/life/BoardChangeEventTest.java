package com.holub.life;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class BoardChangeEventTest {
    @Test void retainsMetadataAndRowColumnCoordinates() {
        boolean[][] cells = new boolean[3][3];
        cells[1][2] = true;
        BoardChangeEvent event = new BoardChangeEvent(GENERATION, 42, 3, cells,
                List.of(new Point(2, 1)), List.of(new Point(0, 2)));
        assertEquals(GENERATION, event.kind());
        assertEquals(42, event.generation());
        assertEquals(3, event.boardSize());
        assertArrayEquals(cells, event.copyAliveCells());
        assertEquals(List.of(new Point(2, 1)), event.copyBirths());
        assertEquals(List.of(new Point(0, 2)), event.copyDeaths());
    }

    @Test void acceptsEmptyBoardAtGenerationZero() {
        BoardChangeEvent event = event(INITIAL, 0, 1, new boolean[1][1]);
        assertEquals(0, event.generation());
        assertFalse(event.copyAliveCells()[0][0]);
        assertTrue(event.copyBirths().isEmpty());
        assertTrue(event.copyDeaths().isEmpty());
    }

    @Test void sourceRowsListsAndPointsCannotChangeEvent() {
        boolean[][] cells = new boolean[3][3];
        cells[1][2] = true;
        Point born = new Point(2, 1), died = new Point(0, 2);
        List<Point> births = new ArrayList<>(List.of(born));
        List<Point> deaths = new ArrayList<>(List.of(died));
        BoardChangeEvent event = new BoardChangeEvent(GENERATION, 1, 3, cells, births, deaths);
        cells[1][2] = false;
        cells[0] = null;
        born.move(0, 0); died.move(1, 1);
        births.clear(); deaths.clear();
        assertTrue(event.copyAliveCells()[1][2]);
        assertEquals(3, event.copyAliveCells()[0].length);
        assertEquals(List.of(new Point(2, 1)), event.copyBirths());
        assertEquals(List.of(new Point(0, 2)), event.copyDeaths());
    }

    @Test void returnedRowsListsAndPointsAreIndependentForEveryObserver() {
        boolean[][] cells = new boolean[3][3];
        cells[1][2] = true;
        BoardChangeEvent event = new BoardChangeEvent(GENERATION, 1, 3, cells,
                List.of(new Point(2, 1)), List.of(new Point(0, 2)));
        boolean[][] copy = event.copyAliveCells();
        copy[1][2] = false; copy[0] = null;
        event.copyBirths().get(0).move(0, 0);
        event.copyDeaths().get(0).move(0, 0);
        event.copyBirths().clear(); event.copyDeaths().clear();
        assertArrayEquals(cells, event.copyAliveCells());
        assertEquals(List.of(new Point(2, 1)), event.copyBirths());
        assertEquals(List.of(new Point(0, 2)), event.copyDeaths());
    }

    @Test void rejectsMissingKindBoardListsAndPoints() {
        assertThrows(NullPointerException.class, () -> event(null, 0, 1, new boolean[1][1]));
        assertThrows(NullPointerException.class, () -> event(INITIAL, 0, 1, null));
        assertThrows(NullPointerException.class, () -> new BoardChangeEvent(GENERATION, 1, 1, new boolean[1][1], null, List.of()));
        assertThrows(NullPointerException.class, () -> new BoardChangeEvent(GENERATION, 1, 1, new boolean[1][1], List.of(), null));
        assertThrows(NullPointerException.class, () -> new BoardChangeEvent(GENERATION, 1, 1, new boolean[1][1], Arrays.asList((Point) null), List.of()));
    }

    @ParameterizedTest @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeGeneration(long generation) {
        assertThrows(IllegalArgumentException.class, () -> event(INITIAL, generation, 1, new boolean[1][1]));
    }

    @Test void completedGenerationCannotBeZero() {
        assertThrows(IllegalArgumentException.class, () -> event(GENERATION, 0, 1, new boolean[1][1]));
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 46341, Integer.MAX_VALUE})
    void rejectsInvalidBoardSize(int size) {
        assertThrows(IllegalArgumentException.class, () -> event(INITIAL, 0, size, new boolean[1][1]));
    }

    @Test void rejectsMismatchedRaggedAndNullRows() {
        assertThrows(IllegalArgumentException.class, () -> event(INITIAL, 0, 2, new boolean[3][3]));
        assertThrows(IllegalArgumentException.class, () -> event(INITIAL, 0, 2, new boolean[][] {new boolean[2], new boolean[1]}));
        assertThrows(IllegalArgumentException.class, () -> event(INITIAL, 0, 2, new boolean[][] {new boolean[2], null}));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 3})
    void rejectsCoordinatesOutsideEitherAxis(int outside) {
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(GENERATION, 1, 3,
                new boolean[3][3], List.of(), List.of(new Point(outside, 0))));
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(GENERATION, 1, 3,
                new boolean[3][3], List.of(), List.of(new Point(0, outside))));
    }

    @Test void rejectsDuplicateOrInconsistentChanges() {
        boolean[][] cells = new boolean[2][2]; cells[0][1] = true;
        Point born = new Point(1, 0), dead = new Point(0, 1);
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(GENERATION, 1, 2, cells, List.of(born, born), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(GENERATION, 1, 2, cells, List.of(), List.of(dead, dead)));
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(GENERATION, 1, 2, cells, List.of(dead), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(GENERATION, 1, 2, cells, List.of(), List.of(born)));
    }

    @ParameterizedTest @EnumSource(value = BoardChangeEvent.Kind.class, names = {"GENERATION"}, mode = EnumSource.Mode.EXCLUDE)
    void nonGenerationCannotClaimBirthsOrDeaths(BoardChangeEvent.Kind kind) {
        assertThrows(IllegalArgumentException.class, () -> new BoardChangeEvent(kind, 0, 1,
                new boolean[1][1], List.of(), List.of(new Point(0, 0))));
    }

    private static BoardChangeEvent event(BoardChangeEvent.Kind kind, long generation, int size, boolean[][] cells) {
        return new BoardChangeEvent(kind, generation, size, cells, List.of(), List.of());
    }
}
