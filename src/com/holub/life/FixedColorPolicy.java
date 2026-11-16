package com.holub.life;

import java.awt.Color;

/** 나이와 상관없이 한 가지 색으로 칠한다 */
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
