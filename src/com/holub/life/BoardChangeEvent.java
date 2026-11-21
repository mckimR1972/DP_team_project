package com.holub.life;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 구독자들이 공유하는 게임판 상태와 변경 정보. 생성 후 외부에서 내용을 바꿀 수 없다.
 *
 * <p>생존 배열은 [행][열], Point 좌표는 x=열, y=행 순서로 사용한다.
 * GENERATION 이벤트에만 한 세대 진행으로 발생한 탄생·사망 좌표를 담는다.
 */
public final class BoardChangeEvent {
    public enum Kind {
        INITIAL, GENERATION, EDIT, TEMPLATE, TEMPLATE_UNDO, CLEAR, LOAD, COLOR_POLICY_CHANGED
    }

    private final Kind kind;
    private final long generation;
    private final int boardSize;
    private final boolean[][] aliveCells;
    private final List<Point> births;
    private final List<Point> deaths;
    private final List<Color> aliveColors;

    public BoardChangeEvent(Kind kind, long generation, int boardSize,
                            boolean[][] aliveCells, List<Point> births, List<Point> deaths,
                            List<Color> aliveColors) {
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
        this.aliveColors = List.copyOf(Objects.requireNonNull(aliveColors, "aliveColors"));
        int livingCells = 0;
        for (boolean[] row : this.aliveCells) {
            for (boolean alive : row) if (alive) livingCells++;
        }
        if (this.aliveColors.size() != livingCells) {
            throw new IllegalArgumentException("aliveColors must contain one color per living cell");
        }
    }

    public Kind kind() { return kind; }
    public long generation() { return generation; }
    public int boardSize() { return boardSize; }

    /** 각 행까지 복사한 생존 상태를 반환하여 구독자가 이벤트 원본을 변경하지 못하게 한다. */
    public boolean[][] copyAliveCells() { return copyBoard(aliveCells); }

    /** 탄생 좌표를 반환한다. 목록과 각 Point를 복사하여 원본을 보호한다. */
    public List<Point> copyBirths() { return copyPoints(births); }

    /** 사망 좌표를 반환한다. 목록과 각 Point를 복사하여 원본을 보호한다. */
    public List<Point> copyDeaths() { return copyPoints(deaths); }

    /**
     * 살아 있는 셀의 색상 목록을 수정 불가능한 형태로 반환한다.
     * 색상별 집계에 사용하는 목록이며, 좌표나 나이 정보는 포함하지 않는다.
     */
    public List<Color> aliveColors() { return aliveColors; }

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
