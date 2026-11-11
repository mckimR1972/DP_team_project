package com.holub.life;

import java.awt.Color;

/**
 * Decides the color of a live cell from its age (the Strategy in the
 * Strategy pattern). A cell is drawn in the color that the current policy
 * gives for its age, so the coloring can change without changing the cell.
 *
 * @see FixedColorPolicy
 * @see CyclingColorPolicy
 */
public interface CellColorPolicy {
    /**
     * Returns the color of a live cell.
     *
     * @param age the number of generations the cell has been alive:
     *            0 for a cell that was just born. It is never negative.
     */
    Color colorOf(int age);

    /**
     * Returns true if the color of a cell changes as the cell grows older.
     * The board then has to be repainted even on a tick in which no cell
     * is born or dies.
     */
    boolean changesWithAge();
}
