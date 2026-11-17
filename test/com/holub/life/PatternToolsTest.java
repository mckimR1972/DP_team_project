package com.holub.life;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternToolsTest {
    @TempDir Path directory;
    private String oldDirectory;
    private MockedStatic<Clock> clocks;
    private Clock clock;
    private Cell cells;
    private JPanel board;
    private PatternTools tools;

    @BeforeEach void setup() throws Exception {
        oldDirectory = System.getProperty("life.templates");
        System.setProperty("life.templates", directory.toString());
        SwingUtilities.invokeAndWait(() -> {
            clock = mock(Clock.class);
            clocks = mockStatic(Clock.class);
            clocks.when(Clock::instance).thenReturn(clock);
            cells = new Neighborhood(4, new Neighborhood(4, new Resident()));
            board = new JPanel();
            board.setSize(128, 128);
            tools = new PatternTools(board, cells);
        });
    }
    @AfterEach void cleanup() throws Exception {
        SwingUtilities.invokeAndWait(() -> { tools.cancel(); clocks.close(); });
        if (oldDirectory == null) System.clearProperty("life.templates");
        else System.setProperty("life.templates", oldDirectory);
    }
    private static <T> T find(Container root, Class<T> type, String text) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && (text == null || c instanceof AbstractButton && text.equals(((AbstractButton)c).getText()))) return type.cast(c);
            if (c instanceof Container) { T found = find((Container)c, type, text); if (found != null) return found; }
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    private void choose(PatternTemplate pattern) {
        JList<PatternTemplate> list = find(tools.controls(), JList.class, null);
        ((DefaultListModel<PatternTemplate>)list.getModel()).addElement(pattern);
        list.setSelectedValue(pattern, true);
        button("선택한 템플릿 배치").doClick();
    }
    private JButton button(String text) { return find(tools.controls(), JButton.class, text); }
    private void mouse(int id, int x, int y, int button, int modifiers) {
        board.dispatchEvent(new MouseEvent(board, id, System.currentTimeMillis(), modifiers, x * 8 + 4, y * 8 + 4, 1, false, button));
    }
    private void press(int x, int y) { mouse(MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1, MouseEvent.BUTTON1_DOWN_MASK); }
    private void drag(int x, int y) { mouse(MouseEvent.MOUSE_DRAGGED, x, y, MouseEvent.NOBUTTON, MouseEvent.BUTTON1_DOWN_MASK); }
    private void release(int x, int y) { mouse(MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1, 0); }

    @Test void dotAndLargerTemplatesStampWithoutErasing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            choose(PatternTemplate.fromRows("Dot", "O"));
            press(1, 1); drag(2, 1); release(2, 1);
            assertTrue(cells.isAliveAt(1, 1));
            assertTrue(cells.isAliveAt(1, 2));
            choose(PatternTemplate.fromRows("Corners", "O.", ".O"));
            press(1, 1); drag(2, 1); release(2, 1);
            assertTrue(cells.isAliveAt(1, 1));
            assertTrue(cells.isAliveAt(1, 2));
            assertTrue(cells.isAliveAt(2, 2));
            assertTrue(cells.isAliveAt(2, 3));
            mouse(MouseEvent.MOUSE_MOVED, 10, 10, MouseEvent.NOBUTTON, 0);
            assertFalse(cells.isAliveAt(10, 10));
        });
    }
    @Test void holdingStillDoesNotToggleOrCreateExtraUndoEntries() throws Exception {
        SwingUtilities.invokeAndWait(() -> { choose(PatternTemplate.fromRows("Dot", "O")); press(3, 4); });
        // Let actual Swing timer events run while the pointer is stationary.
        Thread.sleep(260);
        SwingUtilities.invokeAndWait(() -> {
            release(3, 4);
            assertTrue(cells.isAliveAt(4, 3));
            button("배치 되돌리기").doClick();
            assertFalse(cells.isAlive());
            assertFalse(button("배치 되돌리기").isEnabled());
        });
    }
}
