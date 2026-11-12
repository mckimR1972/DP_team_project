package com.holub.life;

import java.awt.Color;

/**
 * Draws every live cell in the same color, whatever its age. With the
 * original live-cell color this keeps the original look of the game.
 * <p>
 * Objects of this class never change, so one object can be shared by
 * every cell on the board.
 */
public final class FixedColorPolicy implements CellColorPolicy {
    private final Color color;

    public FixedColorPolicy(Color color) {
        if (color == null)
            throw new IllegalArgumentException("color must not be null");
        this.color = color;
    }

    @Override
    public Color colorOf(int age) {
        return color;
    }

    @Override
    public boolean changesWithAge() {
        return false;
    }
}
