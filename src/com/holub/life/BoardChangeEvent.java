package com.holub.life;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable completed-board data shared by all board observers.
 * Arrays use [row][column]; Points use x = column, y = row.
 * Only GENERATION carries the births/deaths of one actual transition.
 */
public final class BoardChangeEvent {
    public enum Kind {
        INITIAL, GENERATION, EDIT, TEMPLATE, TEMPLATE_UNDO, CLEAR, LOAD
    }

    private final Kind kind;
    private final long generation;
    private final int boardSize;
    private final boolean[][] aliveCells;
    private final List<Point> births;
    private final List<Point> deaths;

    public BoardChangeEvent(Kind kind, long generation, int boardSize,
                            boolean[][] aliveCells, List<Point> births, List<Point> deaths) {
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(aliveCells, "aliveCells");
        Objects.requireNonNull(births, "births");
        Objects.requireNonNull(deaths, "deaths");
        if (generation < 0 || (kind == Kind.GENERATION && generation == 0)) {
            throw new IllegalArgumentException("generation must be nonnegative, and positive after a transition");
        }
        if (boardSize <= 0 || (long) boardSize * boardSize > Integer.MAX_VALUE
                || aliveCells.length != boardSize) {
            throw new IllegalArgumentException("aliveCells must match a positive square board");
        }
        for (boolean[] row : aliveCells) {
            if (row == null || row.length != boardSize) {
                throw new IllegalArgumentException("aliveCells must have square, non-null rows");
            }
        }
        if (kind != Kind.GENERATION && (!births.isEmpty() || !deaths.isEmpty())) {
            throw new IllegalArgumentException("only GENERATION may contain births or deaths");
        }
        this.generation = generation;
        this.boardSize = boardSize;
        this.aliveCells = copyBoard(aliveCells);
        this.births = copyAndValidatePoints(births, true);
        this.deaths = copyAndValidatePoints(deaths, false);
    }

    public Kind kind() { return kind; }
    public long generation() { return generation; }
    public int boardSize() { return boardSize; }

    /** Returns independent rows; observers cannot alter the shared event. */
    public boolean[][] copyAliveCells() { return copyBoard(aliveCells); }

    /** Both the list and each mutable Point are copied. */
    public List<Point> copyBirths() { return copyPoints(births); }
    public List<Point> copyDeaths() { return copyPoints(deaths); }

    private List<Point> copyAndValidatePoints(List<Point> source, boolean aliveAfter) {
        List<Point> result = new ArrayList<>();
        Set<Point> unique = new HashSet<>();
        for (Point point : source) {
            Point copy = new Point(Objects.requireNonNull(point, "point"));
            if (copy.x < 0 || copy.x >= boardSize || copy.y < 0 || copy.y >= boardSize
                    || !unique.add(copy)) {
                throw new IllegalArgumentException("change coordinates must be unique and inside the board");
            }
            if (aliveCells[copy.y][copy.x] != aliveAfter) {
                throw new IllegalArgumentException("change coordinates must agree with the completed board");
            }
            result.add(copy);
        }
        return result;
    }

    private static List<Point> copyPoints(List<Point> source) {
        List<Point> result = new ArrayList<>(source.size());
        for (Point point : source) result.add(new Point(point));
        return result;
    }

    private static boolean[][] copyBoard(boolean[][] source) {
        boolean[][] result = new boolean[source.length][];
        for (int row = 0; row < source.length; row++) result[row] = source[row].clone();
        return result;
    }
}
