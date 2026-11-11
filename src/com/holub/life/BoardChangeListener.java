package com.holub.life;

/** Receives a snapshot after a complete game-board operation. */
@FunctionalInterface
public interface BoardChangeListener {
    void onBoardChanged(BoardChangeEvent event);
}
