package com.holub.life;

import com.holub.ui.MenuSite;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JMenuItem;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/** Real singleton board and clock; only window menu setup is replaced. */
final class LifeTestSupport {
    static Universe universe;
    static Clock clock;
    private static final Map<String, ActionListener> actions = new HashMap<>();

    private LifeTestSupport() { }

    static void initialize() throws Exception {
        if (universe != null) return;
        java.nio.file.Path templates = java.nio.file.Files.createTempDirectory("life-statistics-test-");
        templates.toFile().deleteOnExit();
        onEdt(() -> {
            if (universe != null) return;
            String previousTemplates = System.getProperty("life.templates");
            System.setProperty("life.templates", templates.toString());
            try (MockedStatic<MenuSite> menus = mockStatic(MenuSite.class)) {
                menus.when(() -> MenuSite.addLine(
                        any(), anyString(), anyString(), any(ActionListener.class)))
                        .thenAnswer(call -> {
                            actions.put(call.getArgument(1) + "/" + call.getArgument(2),
                                    call.getArgument(3));
                            return null;
                        });
                clock = Clock.instance();
                universe = Universe.instance();
                universe.setSize(universe.getPreferredSize());
            } finally {
                if (previousTemplates == null) System.clearProperty("life.templates");
                else System.setProperty("life.templates", previousTemplates);
            }
        });
    }

    static void resetBoard() throws Exception {
        onEdt(() -> {
            clock.stop();
            Resident.setColorPolicy(new FixedColorPolicy(Resident.LIVE_COLOR));
            universe.setPlayhead(-1);
            MenuSelectionManager.defaultManager().clearSelectedPath();
            select("Grid", "Clear");
        });
    }

    static void select(String menu, String item) {
        ActionListener action = actions.get(menu + "/" + item);
        assertNotNull(action, menu + "/" + item);
        JMenuItem source = new JMenuItem(item);
        source.setName(item);
        action.actionPerformed(new ActionEvent(source, ActionEvent.ACTION_PERFORMED, item));
    }

    static void click(int row, int column) {
        press(column * 8 + 1, row * 8 + 1, MouseEvent.BUTTON1);
    }

    static void press(int x, int y, int button) {
        universe.dispatchEvent(new MouseEvent(universe, MouseEvent.MOUSE_PRESSED,
                0L, 0, x, y, 1, false, button));
    }

    /** Synthetic colors for existing event fixtures, not a production color rule. */
    static java.util.List<java.awt.Color> redColors(boolean[][] cells) {
        java.util.List<java.awt.Color> colors = new java.util.ArrayList<>();
        if (cells != null) for (boolean[] row : cells) {
            if (row != null) for (boolean alive : row) if (alive) colors.add(java.awt.Color.RED);
        }
        return colors;
    }

    static int countAlive(boolean[][] cells) {
        int count = 0;
        for (boolean[] row : cells) for (boolean alive : row) if (alive) count++;
        return count;
    }

    static void onEdt(Runnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
    }
}
