package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;

/** Cell-coordinate adapter for pattern editing; does not change the shared Cell contract. */
final class PatternCells {
    private final Cell board;

    PatternCells(Cell board) { this.board = Objects.requireNonNull(board); }
    int size() { return board.widthInCells(); }

    boolean isAlive(Point cell) {
        requireInside(cell);
        return board.isAliveAt(cell.y, cell.x);
    }

    boolean setAlive(Point cell, boolean alive) {
        if (isAlive(cell) == alive) return false;
        // One logical pixel per cell. Existing Composite editing invalidates
        // neighborhood caches and resets only the edited Resident's age.
        board.userClicked(new Point(cell), new Rectangle(0, 0, size(), size()));
        return true;
    }

    private void requireInside(Point cell) {
        if (cell == null || cell.x < 0 || cell.y < 0 || cell.x >= size() || cell.y >= size())
            throw new IllegalArgumentException("Cell is outside the board");
    }
}
