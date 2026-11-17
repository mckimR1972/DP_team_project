package com.holub.life;

import java.awt.Point;
import java.util.LinkedHashSet;
import java.util.Set;

/** Command: adds live cells and remembers only cells introduced by this placement. */
final class PlacePatternCommand {
    private final PatternCells cells;
    private final PatternTemplate pattern;
    private final Point origin;
    private final Set<Point> added = new LinkedHashSet<>();

    PlacePatternCommand(Cell board, PatternTemplate pattern, Point origin) {
        this.cells = new PatternCells(board);
        this.pattern = pattern;
        this.origin = new Point(origin);
    }

    boolean execute() {
        int size = cells.size();
        if (origin.x < 0 || origin.y < 0 || origin.x > size - pattern.width()
                || origin.y > size - pattern.height()) {
            throw new IllegalArgumentException("패턴 전체가 보드 안에 들어오도록 배치하세요.");
        }
        for (int y = 0; y < pattern.height(); y++) {
            for (int x = 0; x < pattern.width(); x++) {
                Point target = new Point(origin.x + x, origin.y + y);
                if (pattern.isAlive(x, y) && cells.setAlive(target, true)) added.add(target);
            }
        }
        return !added.isEmpty();
    }

    void undo() {
        for (Point cell : added) cells.setAlive(cell, false);
    }
}
