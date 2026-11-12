package com.holub.life;

import java.awt.Color;

/**
 * Moves on to the next color every {@code period} generations and goes
 * back to the first color after the last one. With a period of 10 and the
 * colors red, blue and green, a cell is red at ages 0-9, blue at 10-19,
 * green at 20-29 and red again at 30-39.
 * <p>
 * Objects of this class never change, so one object can be shared by
 * every cell on the board.
 */
public final class CyclingColorPolicy implements CellColorPolicy {
    private final int period;
    private final Color[] colors;

    /**
     * @param period the number of generations that each color lasts;
     *               must be positive.
     * @param colors the colors in the order they are used; at least two,
     *               none of them null. The array is copied, so changing
     *               it afterwards does not change this policy.
     */
    public CyclingColorPolicy(int period, Color... colors) {
        if (period <= 0)
            throw new IllegalArgumentException("period must be positive: " + period);
        if (colors == null)
            throw new IllegalArgumentException("colors must not be null");
        if (colors.length < 2)
            throw new IllegalArgumentException("at least two colors are needed: " + colors.length);
        for (int i = 0; i < colors.length; i++)
            if (colors[i] == null)
                throw new IllegalArgumentException("colors[" + i + "] is null");

        this.period = period;
        this.colors = colors.clone();
    }

    @Override
    public Color colorOf(int age) {
        return colors[(age / period) % colors.length];
    }

    @Override
    public boolean changesWithAge() {
        return true;
    }
}
