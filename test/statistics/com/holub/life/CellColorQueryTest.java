package com.holub.life;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellColorQueryTest {
    @AfterEach void restorePolicy() { Resident.setColorPolicy(new FixedColorPolicy(Color.RED)); }

    @Test void deadResidentAndDummyHaveNoLivingColor() {
        assertNull(new Resident().colorAt(0, 0));
        assertNull(Cell.DUMMY.colorAt(0, 0));
    }

    @Test void repeatedQueriesDoNotAdvanceAgeOrChangeCellState() {
        Resident.setColorPolicy(new CyclingColorPolicy(1, Color.RED, Color.BLUE));
        Resident cell = new Resident();
        cell.userClicked(new Point(4, 4), new Rectangle(0, 0, 8, 8));
        for (int i = 0; i < 30; i++) assertEquals(Color.RED, cell.colorAt(0, 0));
        assertTrue(cell.isAlive());
    }

    @Test void nestedQueryUsesRowAndColumnAcrossNeighborhoodBoundaries() {
        Resident.setColorPolicy(new FixedColorPolicy(Color.MAGENTA));
        Cell board = new Neighborhood(8, new Neighborhood(8, new Resident()));
        board.userClicked(new Point(20 * 8 + 4, 10 * 8 + 4), new Rectangle(0, 0, 512, 512));
        assertEquals(Color.MAGENTA, board.colorAt(10, 20));
        assertNull(board.colorAt(20, 10));
        assertNull(board.colorAt(10, 19));
        board.clear();
        assertNull(board.colorAt(10, 20));
    }
}
