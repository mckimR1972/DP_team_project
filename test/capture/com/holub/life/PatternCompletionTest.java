package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternCompletionTest {
    @Test void observersSeeCompletedStateAndNoEventsForNoOps() {
        Cell cells = new Neighborhood(4, new Resident());
        List<BoardEditor.EditKind> kinds = new ArrayList<>();
        List<Boolean> states = new ArrayList<>();
        BoardEditor editor = new BoardEditor(cells, kind -> { kinds.add(kind); states.add(cells.isAlive()); });
        PatternTemplate dot = PatternTemplate.fromRows("Dot", "O");
        editor.beginStroke();
        editor.place(dot, new Point(1, 1));
        editor.place(dot, new Point(2, 1));
        assertTrue(kinds.isEmpty());
        editor.endStroke();
        editor.place(dot, new Point(1, 1));
        editor.undo();
        editor.undo();
        assertEquals(List.of(BoardEditor.EditKind.TEMPLATE, BoardEditor.EditKind.TEMPLATE_UNDO), kinds);
        assertEquals(List.of(true, false), states);
    }
    @Test void modelRejectsGuardedRectanglesIncludingTransparentMargins() {
        Cell cells = new Neighborhood(4, new Resident());
        List<BoardEditor.EditKind> kinds = new ArrayList<>();
        BoardEditor editor = new BoardEditor(cells, kinds::add);
        editor.setPlacementGuard(area -> area.intersects(new Rectangle(2, 0, 1, 4)));
        assertFalse(editor.place(PatternTemplate.fromRows("Margin", "O."), new Point(1, 1)));
        assertFalse(cells.isAlive());
        assertFalse(editor.canUndo());
        assertTrue(kinds.isEmpty());
    }
}
