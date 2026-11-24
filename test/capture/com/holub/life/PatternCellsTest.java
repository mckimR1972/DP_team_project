package com.holub.life;

import java.awt.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternCellsTest {
    @Test void editsUseColumnRowCoordinatesAndAreIdempotent() {
        Cell board = new Neighborhood(4, new Neighborhood(4, new Resident()));
        PatternCells cells = new PatternCells(board);
        assertTrue(cells.setAlive(new Point(7, 8), true));
        assertTrue(board.isAliveAt(8, 7));
        assertFalse(board.isAliveAt(7, 8));
        assertFalse(cells.setAlive(new Point(7, 8), true));
        assertTrue(cells.setAlive(new Point(7, 8), false));
        assertFalse(board.isAlive());
    }
    @Test void invalidCoordinatesCannotEditBoard() {
        Cell board = new Neighborhood(4, new Resident());
        PatternCells cells = new PatternCells(board);
        assertThrows(IllegalArgumentException.class, () -> cells.setAlive(new Point(-1, 0), true));
        assertThrows(IllegalArgumentException.class, () -> cells.setAlive(new Point(4, 0), true));
        assertThrows(IllegalArgumentException.class, () -> cells.setAlive(null, true));
        assertFalse(board.isAlive());
    }
}
