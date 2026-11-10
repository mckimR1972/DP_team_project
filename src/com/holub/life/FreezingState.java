package com.holub.life;

/**
 * State: the behavior of one {@link FreezingRegion} during a generation.
 *
 * <p>The region does not use if/else on a flag. It forwards each step of the
 * generation update to its current state object, and the state decides what
 * happens:
 * <ul>
 * <li>{@link NormalState}: nothing. The cells follow B3/S23 as computed.
 * <li>{@link FrozenState}: replaces the computed next state with the
 *     install-time snapshot, counts down, and returns the region to
 *     {@link NormalState} when the count reaches zero.
 * </ul>
 */
interface FreezingState {
    /** Called after figureNextState() and before transition().
     *  @return true if any pending next state was overridden. */
    boolean beforeTransition(FreezingRegion region, Cell board);

    /** Called once after the generation is complete. May change the region's state. */
    void afterGeneration(FreezingRegion region);

    boolean isFrozen();

    /** Number of generation updates that are still frozen (0 in NormalState). */
    int remainingGenerations();
}
