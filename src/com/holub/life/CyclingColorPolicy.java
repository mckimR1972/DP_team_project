package com.holub.life;

import java.awt.Color;

/** period 세대마다 다음 색으로 바꾸고, 마지막 색 다음에는 처음 색으로 돌아간다 */
public final class CyclingColorPolicy implements CellColorPolicy {
    private final int period;
    private final Color[] colors;

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
        // int idx = Math.min((age / period), colors.length - 1);
        // return colors[idx]; => 마지막 색이면 순회 X

        // 순회
        return colors[(age / period) % colors.length];
    }

    @Override
    public boolean changesWithAge() {
        return true;
    }
}
