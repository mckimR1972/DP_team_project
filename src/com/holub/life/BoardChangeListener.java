package com.holub.life;

/**
 * Receives the same completed-board event as all other registered observers.
 * GENERATION includes shared birth/death coordinates; each observer selects
 * the information and event kinds it needs.
 * Universe delivers synchronously on the Swing EDT. Keep callbacks short and
 * do not change the game board from a callback; use the supplied snapshot.
 */
@FunctionalInterface
public interface BoardChangeListener {
    void onBoardChanged(BoardChangeEvent event);
}
