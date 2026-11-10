package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * State pattern context: one rectangular Freezing area.
 *
 * <p>It holds the area, the install-time snapshot, the start generation, the
 * duration, and its current {@link FreezingState}. The generation update calls
 * only {@link #beforeTransition} and {@link #afterGeneration}. What actually
 * happens is decided by the current state object.
 * State changes: {@code Normal -> Frozen (freeze) -> Normal (expired or release)}.
 */
final class FreezingRegion {
    private final Rectangle area;           // x = column, y = row, in cells
    private boolean[][] snapshot;           // [row][column] relative to the area
    private FreezingState state = NormalState.INSTANCE;
    private long startGeneration;
    private int duration;

    FreezingRegion(Rectangle area) {
        if (area == null || area.width < 1 || area.height < 1)
            throw new IllegalArgumentException("Freezing 영역을 선택하세요.");
        this.area = new Rectangle(area);
    }

    /** Normal -> Frozen. Saves every cell state (alive and dead) at this moment. */
    void freeze(Cell board, int generations, long currentGeneration) {
        FrozenState frozen = new FrozenState(generations);     // validates first
        Cell.Memento memento = (Cell.Memento) board.createMemento();
        snapshot = new boolean[area.height][area.width];
        for (int y = 0; y < area.height; y++)
            for (int x = 0; x < area.width; x++)
                snapshot[y][x] = memento.isAlive(new Point(area.x + x, area.y + y));
        startGeneration = currentGeneration;
        duration = generations;
        changeState(frozen);
    }

    /** Forced release: Frozen -> Normal. */
    void release() { changeState(NormalState.INSTANCE); }

    boolean beforeTransition(Cell board) { return state.beforeTransition(this, board); }
    void afterGeneration() { state.afterGeneration(this); }

    void changeState(FreezingState next) { state = next; }

    /** Used by FrozenState: replace the pending next state of every cell with the snapshot. */
    boolean holdSnapshot(Cell board) {
        boolean overridden = false;
        Point location = new Point();
        for (int y = 0; y < area.height; y++)
            for (int x = 0; x < area.width; x++) {
                location.setLocation(area.x + x, area.y + y);
                overridden |= board.holdNextState(location, snapshot[y][x]);
            }
        return overridden;
    }

    boolean isFrozen() { return state.isFrozen(); }
    int remainingGenerations() { return state.remainingGenerations(); }
    FreezingState state() { return state; }
    Rectangle area() { return new Rectangle(area); }
    long startGeneration() { return startGeneration; }
    int duration() { return duration; }
    boolean contains(Point cell) { return cell != null && area.contains(cell); }
    boolean intersects(Rectangle other) { return area.intersects(other); }

    /** Install-time state of a cell (board coordinates). */
    boolean wasAlive(int column, int row) {
        return snapshot != null && area.contains(column, row)
                && snapshot[row - area.y][column - area.x];
    }

    @Override public String toString() {
        return "(" + area.x + "," + area.y + ") " + area.width + "x" + area.height
                + " · 남은 " + remainingGenerations() + "세대";
    }
}
