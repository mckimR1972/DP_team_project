package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Captures rectangular shapes and manages placement commands. */
public final class BoardEditor {
    public enum EditKind { TEMPLATE, TEMPLATE_UNDO }
    private final Consumer<EditKind> onEdit;
    private Predicate<Rectangle> placementBlocked = area -> false;
    private final Cell board;
    private final Deque<PlacePatternCommand> history = new ArrayDeque<>();

    private boolean grouping;
    private PlacePatternCommand stroke;

    public BoardEditor(Cell board) { this(board, kind -> { }); }
    public BoardEditor(Cell board, Consumer<EditKind> onEdit) {
        this.board = Objects.requireNonNull(board);
        this.onEdit = Objects.requireNonNull(onEdit);
    }

    public void setPlacementGuard(Predicate<Rectangle> guard) {
        placementBlocked = Objects.requireNonNull(guard);
    }

    public boolean canPlace(PatternTemplate pattern, Point origin) {
        return fits(pattern, origin) && !placementBlocked.test(
                new Rectangle(origin.x, origin.y, pattern.width(), pattern.height()));
    }
    public int size() { return board.widthInCells(); }

    public PatternTemplate capture(String name, Rectangle area) {
        if (area == null || area.width < 1 || area.height < 1
                || !new Rectangle(0, 0, size(), size()).contains(area)) {
            throw new IllegalArgumentException("보드 안의 영역을 선택하세요.");
        }
        Cell.Memento state = (Cell.Memento) board.createMemento();
        boolean[][] cells = new boolean[area.height][area.width];
        for (int y = 0; y < area.height; y++)
            for (int x = 0; x < area.width; x++)
                cells[y][x] = state.isAlive(new Point(area.x + x, area.y + y));
        PatternTemplate pattern = new PatternTemplate(name, cells);
        if (pattern.liveCount() == 0) throw new IllegalArgumentException("살아 있는 셀이 없는 영역입니다.");
        return pattern;
    }

    public boolean fits(PatternTemplate pattern, Point origin) {
        return origin != null && origin.x >= 0 && origin.y >= 0
                && origin.x <= size() - pattern.width() && origin.y <= size() - pattern.height();
    }

    public boolean place(PatternTemplate pattern, Point origin) {
        if (!fits(pattern, origin)) throw new IllegalArgumentException("패턴 전체가 보드 안에 들어오도록 배치하세요.");
        if (!canPlace(pattern, origin)) return false;
        PlacePatternCommand command = new PlacePatternCommand(board, pattern, origin);
        if (!command.execute()) return false;
        if (grouping && stroke != null) {
            stroke.include(command);
        } else {
            history.push(command);
            if (grouping) stroke = command;
            if (history.size() > 100) history.removeLast();
        }
        if (!grouping) onEdit.accept(EditKind.TEMPLATE);
        return true;
    }

    public void beginStroke() {
        endStroke();
        grouping = true;
    }

    /** Finish a gesture; true only when it introduced cells. */
    public boolean endStroke() {
        boolean changed = grouping && stroke != null;
        grouping = false;
        stroke = null;
        if (changed) onEdit.accept(EditKind.TEMPLATE);
        return changed;
    }

    public boolean canUndo() { return !history.isEmpty(); }
    public void undo() {
        endStroke();
        if (!canUndo()) return;
        history.pop().undo();
        onEdit.accept(EditKind.TEMPLATE_UNDO);
    }
    public void clearHistory() { endStroke(); history.clear(); }
}
