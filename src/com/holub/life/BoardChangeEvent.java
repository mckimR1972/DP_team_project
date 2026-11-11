package com.holub.life;

import java.util.BitSet;
import java.util.Objects;

/**
 * Immutable current-board snapshot shared by observers.
 *
 * <p>A living cell at (row, column) has bit row * boardSize + column set.
 * The event contains no previous state or references to mutable Cell objects.
 */
public final class BoardChangeEvent {
    public enum Kind {
        INITIAL,
        GENERATION,
        EDIT,
        TEMPLATE,
        TEMPLATE_UNDO,
        CLEAR,
        LOAD
    }

    private final Kind kind;
    private final long generation;
    private final int boardSize;
    private final BitSet aliveCells;

    public BoardChangeEvent(Kind kind, long generation, int boardSize, BitSet aliveCells) {
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(aliveCells, "aliveCells");
        if (generation < 0) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        long cellCount = (long) boardSize * boardSize;
        if (boardSize <= 0 || cellCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("boardSize must fit a positive square board");
        }
        if (aliveCells.length() > cellCount) {
            throw new IllegalArgumentException("aliveCells contains a position outside the board");
        }
        this.generation = generation;
        this.boardSize = boardSize;
        this.aliveCells = (BitSet) aliveCells.clone();
    }

    public Kind kind() {
        return kind;
    }

    public long generation() {
        return generation;
    }

    public int boardSize() {
        return boardSize;
    }

    /** Returns an independent copy; callers cannot change the event's snapshot. */
    public BitSet copyAliveCells() {
        return (BitSet) aliveCells.clone();
    }
}
