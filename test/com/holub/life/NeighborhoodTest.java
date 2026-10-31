package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborhoodTest {
    @Test
    void isAliveReturnsFalseForEmptyNeighborhood() {
        Neighborhood board = new Neighborhood(3, new Resident());

        assertFalse(board.isAlive());
    }

    @Test
    void isAliveReturnsFalseForEmptyNestedNeighborhood() {
        Neighborhood board = nestedBoard();

        assertFalse(board.isAlive());
    }

    @ParameterizedTest(name = "live child at ({0}, {1})")
    @CsvSource({"0, 0", "1, 1", "2, 2"})
    void isAliveReturnsTrueWithLivingChild(int x, int y) {
        Neighborhood board = new Neighborhood(3, new Resident());
        click(board, x, y);

        assertTrue(board.isAlive());
    }

    @ParameterizedTest(name = "nested live child at ({0}, {1})")
    @CsvSource({"0, 0", "4, 4", "8, 8"})
    void isAliveReturnsTrueWithLivingNestedChild(int x, int y) {
        Neighborhood board = nestedBoard();
        click(board, x, y);

        assertTrue(board.isAlive());
    }

    @Test
    void isAliveReturnsFalseAfterLastLivingChildDies() {
        Neighborhood board = nestedBoard();
        click(board, 4, 4);
        click(board, 4, 4);

        assertFalse(board.isAlive());
    }

    @Test
    void isAliveReturnsTrueWhileAnotherChildRemainsAlive() {
        Neighborhood board = nestedBoard();
        click(board, 0, 0);
        click(board, 8, 8);
        click(board, 0, 0);

        assertTrue(board.isAlive());
    }

    @Test
    void isAliveReturnsFalseAfterClear() {
        Neighborhood board = nestedBoard();
        click(board, 0, 0);
        click(board, 8, 8);
        board.clear();

        assertFalse(board.isAlive());
    }

    private Neighborhood nestedBoard() {
        return new Neighborhood(3, new Neighborhood(3, new Resident()));
    }

    private void click(Cell board, int x, int y) {
        int width = board.widthInCells() * 8;
        board.userClicked(new Point(x * 8 + 1, y * 8 + 1),
                          new Rectangle(0, 0, width, width));
    }
}
