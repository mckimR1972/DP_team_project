package com.holub.life;

import java.awt.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternHistoryTest {
    private final Cell board = new Neighborhood(4, new Neighborhood(4, new Resident()));
    private final BoardEditor editor = new BoardEditor(board);
    private final PatternTemplate dot = PatternTemplate.fromRows("Dot", "O");

    @Test void historyKeepsOnlyTheLastHundredChangedPlacements() {
        for (int i = 0; i < 101; i++) editor.place(dot, new Point(i % 16, i / 16));
        // Repeated stamping must not evict a real command.
        for (int i = 0; i < 150; i++) assertFalse(editor.place(dot, new Point(4, 6)));
        for (int i = 0; i < 100; i++) { assertTrue(editor.canUndo()); editor.undo(); }
        assertFalse(editor.canUndo());
        assertTrue(board.isAliveAt(0, 0));
        for (int i = 1; i < 101; i++) assertFalse(board.isAliveAt(i / 16, i % 16));
    }
    @Test void overlappingCommandsUndoOnlyTheirOwnAdditions() {
        editor.place(dot, new Point(7, 7));
        editor.place(PatternTemplate.fromRows("Block", "OO", "OO"), new Point(7, 7));
        editor.undo();
        assertTrue(board.isAliveAt(7, 7));
        assertFalse(board.isAliveAt(7, 8));
        assertFalse(board.isAliveAt(8, 7));
        assertFalse(board.isAliveAt(8, 8));
        editor.undo();
        assertFalse(board.isAlive());
    }
}
