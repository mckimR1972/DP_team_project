package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Deque;

/** Adapts the composite Cell's Memento protocol to rectangular editing. */
public final class BoardEditor {
    private final Cell board;
    private final Deque<PlacePatternCommand> history = new ArrayDeque<>();

    public BoardEditor(Cell board) { this.board = board; }
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
        PlacePatternCommand command = new PlacePatternCommand(board, pattern, origin);
        if (!command.execute()) return false;
        history.push(command);
        if (history.size() > 100) history.removeLast();
        return true;
    }

    public boolean canUndo() { return !history.isEmpty(); }
    public void undo() { if (canUndo()) history.pop().undo(); }
    public void clearHistory() { history.clear(); }
}
