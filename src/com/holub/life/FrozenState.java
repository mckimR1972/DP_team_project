package com.holub.life;

/**
 * State: Freezing is active. Each generation, the computed next state of every
 * cell in the region is replaced with the snapshot taken at install time, so
 * live cells cannot die and dead cells cannot be born. After the given number
 * of generation updates, the region switches back to {@link NormalState}.
 *
 * <p>One instance per region (it holds that region's countdown), not one per cell.
 */
final class FrozenState implements FreezingState {
    private int remaining;

    FrozenState(int generations) {
        if (generations < 1) throw new IllegalArgumentException("지속 세대는 1 이상이어야 합니다.");
        remaining = generations;
    }

    @Override public boolean beforeTransition(FreezingRegion region, Cell board) {
        return region.holdSnapshot(board);
    }

    @Override public void afterGeneration(FreezingRegion region) {
        if (--remaining <= 0) region.changeState(NormalState.INSTANCE);   // Frozen -> Normal
    }

    @Override public boolean isFrozen() { return true; }
    @Override public int remainingGenerations() { return remaining; }
    @Override public String toString() { return "Frozen(" + remaining + ")"; }
}
