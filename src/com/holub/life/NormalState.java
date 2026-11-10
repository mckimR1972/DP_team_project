package com.holub.life;

/** State: no Freezing. The existing Conway rules apply unchanged. Stateless, so one shared instance. */
final class NormalState implements FreezingState {
    static final NormalState INSTANCE = new NormalState();

    private NormalState() {}

    @Override public boolean beforeTransition(FreezingRegion region, Cell board) { return false; }
    @Override public void afterGeneration(FreezingRegion region) {}
    @Override public boolean isFrozen() { return false; }
    @Override public int remainingGenerations() { return 0; }
    @Override public String toString() { return "Normal"; }
}
