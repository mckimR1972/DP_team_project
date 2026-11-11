package com.holub.life;

/**
 * Receives a snapshot after a complete game-board operation.
 * Universe delivers synchronously on the Swing EDT. Keep callbacks short and
 * do not change the game board from a callback; use the supplied snapshot.
 */
@FunctionalInterface
public interface BoardChangeListener {
    void onBoardChanged(BoardChangeEvent event);
}
