package com.holub.life;

import java.awt.Point;

/** Command: one placement, with the existing Cell Memento as its undo state. */
final class PlacePatternCommand {
    private final Cell board;
    private final PatternTemplate pattern;
    private final Point origin;
    private Storable before;

    PlacePatternCommand(Cell board, PatternTemplate pattern, Point origin) {
        this.board = board;
        this.pattern = pattern;
        this.origin = new Point(origin);
    }

    boolean execute() {
        int size = board.widthInCells();
        if (origin.x < 0 || origin.y < 0 || origin.x > size - pattern.width()
                || origin.y > size - pattern.height()) {
            throw new IllegalArgumentException("패턴 전체가 보드 안에 들어오도록 배치하세요.");
        }
        before = board.createMemento();
        Cell.Memento after = (Cell.Memento) board.createMemento();
        boolean changed = false;
        for (int y = 0; y < pattern.height(); y++) {
            for (int x = 0; x < pattern.width(); x++) {
                Point target = new Point(origin.x + x, origin.y + y);
                if (pattern.isAlive(x, y) && !after.isAlive(target)) {
                    after.markAsAlive(target);
                    changed = true;
                }
            }
        }
        if (changed) restore(board, after);
        return changed;
    }

    void undo() { restore(board, before); }

    private static void restore(Cell board, Storable state) {
        board.clear();
        board.transfer(state, new Point(0, 0), Cell.LOAD);
    }
}
