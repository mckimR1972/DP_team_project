package com.holub.life;

import com.holub.io.Files;
import com.holub.ui.MenuSite;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class UniverseStoreTest {
    private static final Map<String, ActionListener> gridActions = new HashMap<>();
    private static Universe universe;
    private static Clock stoppedClock;

    @TempDir
    Path directory;

    @BeforeAll
    static void initializeUniverseWithoutOpeningWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            stoppedClock = mock(Clock.class);
            // Capture the real menu callbacks without creating a JFrame or timer.
            try (MockedStatic<MenuSite> menus = mockStatic(MenuSite.class);
                 MockedStatic<Clock> clocks = mockStatic(Clock.class)) {
                clocks.when(Clock::instance).thenReturn(stoppedClock);
                menus.when(() -> MenuSite.addLine(
                    any(), eq("Grid"), anyString(), any(ActionListener.class)
                )).thenAnswer(invocation -> {
                    gridActions.put(invocation.getArgument(2), invocation.getArgument(3));
                    return null;
                });
                universe = Universe.instance();
                universe.setSize(universe.getPreferredSize());
            }
            assertNotNull(gridActions.get("Clear"));
            assertNotNull(gridActions.get("Store"));
        });
    }

    @BeforeEach
    @AfterEach
    void clearBoard() throws Exception {
        SwingUtilities.invokeAndWait(() -> selectGridAction("Clear"));
    }

    @ParameterizedTest(name = "store {0} live cell(s) without duplicates")
    @ValueSource(ints = {1, 4})
    void saveWritesEachLiveCellOnce(int liveCellCount) throws Exception {
        List<Point> liveCells = List.of(
            new Point(0, 0), new Point(7, 7), new Point(8, 8), new Point(63, 63)
        ).subList(0, liveCellCount);

        Collection<?> savedCoordinates = storeAndRead(liveCells);

        assertEquals(liveCellCount, savedCoordinates.size(),
                     "Store must write exactly one entry per live cell");
        assertEquals(new HashSet<>(liveCells), new HashSet<>(savedCoordinates),
                     "Stored coordinates must match the actual live cells");
    }

    @Test
    void saveWritesNoCoordinatesForEmptyBoard() throws Exception {
        Collection<?> savedCoordinates = storeAndRead(List.of());

        assertTrue(savedCoordinates.isEmpty());
    }

    private Collection<?> storeAndRead(List<Point> liveCells) throws Exception {
        Path destination = directory.resolve("board.life");
        SwingUtilities.invokeAndWait(() -> {
            for (Point cell : liveCells) {
                universe.dispatchEvent(new MouseEvent(
                    universe, MouseEvent.MOUSE_PRESSED, 0L, 0,
                    cell.x * 8 + 1, cell.y * 8 + 1, 1, false, MouseEvent.BUTTON1
                ));
            }
            // Only replace external boundaries. The actual Store callback, doStore(),
            // cells, memento, serialization and file output all run unchanged.
            // Static mocks are scoped to the EDT because they are thread-local.
            try (MockedStatic<Files> chooser = mockStatic(Files.class);
                 MockedStatic<Clock> clocks = mockStatic(Clock.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Write"))
                       .thenReturn(destination.toFile());
                clocks.when(Clock::instance).thenReturn(stoppedClock);
                selectGridAction("Store");
            }
        });
        try (ObjectInputStream input = new ObjectInputStream(
                java.nio.file.Files.newInputStream(destination))) {
            return (Collection<?>) input.readObject();
        }
    }

    private static void selectGridAction(String name) {
        gridActions.get(name).actionPerformed(
            new ActionEvent(universe, ActionEvent.ACTION_PERFORMED, name)
        );
    }
}
