package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Keeps the Freezing regions and runs them at the right points of the
 * generation update. Has no Swing code, so it can be tested on its own.
 *
 * <p>The generation number is not counted here. Universe owns the one counter
 * that statistics and sound also receive in BoardChangeEvent, and this class
 * only reads it through the supplier, so the three features can never disagree
 * about which generation it is.
 *
 * <pre>
 * Universe tick:
 *   figureNextState(...)              existing B3/S23 (unchanged)
 *   beforeTransition(board)           each region -> its state (Frozen: hold snapshot)
 *   transition()                      existing (unchanged)
 *   generation++                      Universe's counter
 *   afterGeneration()                 each region -> its state (Frozen: countdown, expire)
 *   BoardChangeEvent(GENERATION)      statistics, sound
 * </pre>
 */
final class FreezingManager {
    private final Cell board;
    private final LongSupplier currentGeneration;
    private final List<FreezingRegion> regions = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** @param currentGeneration the board's generation counter (Universe's in the game) */
    FreezingManager(Cell board, LongSupplier currentGeneration) {
        this.board = Objects.requireNonNull(board, "board");
        this.currentGeneration = Objects.requireNonNull(currentGeneration, "currentGeneration");
    }

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
        region.freeze(board, generations);                   // Normal -> Frozen
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

    /** Full reset (Grid > Clear / Load): remove every region. Universe resets the generation. */
    void reset() {
        for (FreezingRegion r : regions) r.release();
        regions.clear();
        changed();
    }

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
    long generation() { return currentGeneration.getAsLong(); }
    int size() { return board.widthInCells(); }
    void addListener(Runnable listener) { listeners.add(listener); }
    private void changed() { for (Runnable l : listeners) l.run(); }
}
