package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps the Freezing regions and the generation counter, and runs them at the
 * right points of the generation update. Has no Swing code, so it can be tested
 * on its own.
 *
 * <pre>
 * Universe tick:
 *   beginGeneration()                 generation++
 *   figureNextState(...)              existing B3/S23 (unchanged)
 *   beforeTransition(board)           each region -> its state (Frozen: hold snapshot)
 *   transition()                      existing (unchanged)
 *   afterGeneration()                 each region -> its state (Frozen: countdown, expire)
 * </pre>
 */
final class FreezingManager {
    private final Cell board;
    private final List<FreezingRegion> regions = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private long generation;

    FreezingManager(Cell board) { this.board = board; }

    /** Install a Freezing on the area. The area is clamped to the board.
     *  @throws IllegalArgumentException for an empty area, a bad duration, or overlap. */
    FreezingRegion install(Rectangle selected, int generations) {
        Rectangle area = selected == null ? null
                : selected.intersection(new Rectangle(0, 0, size(), size()));
        if (area == null || area.isEmpty())
            throw new IllegalArgumentException("보드 안의 영역을 선택하세요.");
        for (FreezingRegion r : regions)
            if (r.isFrozen() && r.intersects(area))
                throw new IllegalArgumentException("이미 Freezing된 영역과 겹칩니다.");
        FreezingRegion region = new FreezingRegion(area);
        region.freeze(board, generations, generation);     // Normal -> Frozen
        regions.add(region);
        changed();
        return region;
    }

    void release(FreezingRegion region) {
        if (region == null || !regions.remove(region)) return;
        region.release();                                  // Frozen -> Normal
        changed();
    }

    void releaseAll() {
        if (regions.isEmpty()) return;
        for (FreezingRegion r : regions) r.release();
        regions.clear();
        changed();
    }

    /** Full reset (Grid > Clear / Load): remove every region and reset the generation counter. */
    void reset() {
        for (FreezingRegion r : regions) r.release();
        regions.clear();
        generation = 0;
        changed();
    }

    void beginGeneration() { generation++; }

    /** @return true if any pending next state was overridden. */
    boolean beforeTransition(Cell cells) {
        boolean overridden = false;
        for (FreezingRegion r : regions) overridden |= r.beforeTransition(cells);
        return overridden;
    }

    /** Count down, and remove regions that returned to NormalState. */
    void afterGeneration() {
        for (FreezingRegion r : regions) r.afterGeneration();
        regions.removeIf(r -> !r.isFrozen());
        changed();
    }

    boolean isFrozen(Point cell) {
        for (FreezingRegion r : regions) if (r.isFrozen() && r.contains(cell)) return true;
        return false;
    }

    boolean overlapsFrozen(Rectangle area) {
        for (FreezingRegion r : regions) if (r.isFrozen() && r.intersects(area)) return true;
        return false;
    }

    List<FreezingRegion> regions() { return Collections.unmodifiableList(regions); }
    long generation() { return generation; }
    int size() { return board.widthInCells(); }
    void addListener(Runnable listener) { listeners.add(listener); }
    private void changed() { for (Runnable l : listeners) l.run(); }
}
