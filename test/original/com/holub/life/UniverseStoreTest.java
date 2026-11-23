package com.holub.life;

import com.holub.io.Files;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class UniverseStoreTest {
    @TempDir
    Path directory;
    private static Universe universe;

    @BeforeAll
    static void initializeUniverseWithoutOpeningWindows() throws Exception {
        LifeTestSupport.initialize();
        universe = LifeTestSupport.universe;
    }

    @BeforeEach
    @AfterEach
    void clearBoard() throws Exception {
        LifeTestSupport.resetBoard();
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

    @Test
    void liveCellsSnapshotIsIndexedByRowThenColumn() throws Exception {
        LifeTestSupport.onEdt(() -> mouse(MouseEvent.MOUSE_PRESSED, 5, 2));

        boolean[][] cells = universe.liveCells();

        assertTrue(cells[2][5]);
        assertEquals(false, cells[5][2]);
    }

    @Test
    void playheadIsDrawnOverTheBoardAndCanBeRemoved() throws Exception {
        final int column = 10;
        final int pixel = column * 8;

        LifeTestSupport.onEdt(() -> {
            try {
                universe.setPlayhead(column);
                int shown = renderedPixel(pixel);
                universe.setPlayhead(-1);
                int hidden = renderedPixel(pixel);

                assertEquals(new Color(0, 170, 80).getRGB(), shown);
                assertTrue(shown != hidden);
            } finally {
                universe.setPlayhead(-1);
            }
        });
    }

    private static int renderedPixel(int x) {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setClip(0, 0, 512, 512);
        universe.paint(graphics);
        graphics.dispose();
        return image.getRGB(x, 100) | 0xff000000;
    }

    @Test
    void draggingDrawsACellForEveryPositionPassed() throws Exception {
        LifeTestSupport.onEdt(() -> {
            mouse(MouseEvent.MOUSE_PRESSED, 2, 3);
            mouse(MouseEvent.MOUSE_DRAGGED, 6, 3);   // 세 칸을 건너뜀
            mouse(MouseEvent.MOUSE_DRAGGED, 6, 5);
        });

        assertEquals(
            new HashSet<>(List.of(
                new Point(2, 3), new Point(3, 3), new Point(4, 3), new Point(5, 3),
                new Point(6, 3), new Point(6, 4), new Point(6, 5)
            )),
            new HashSet<>(storeAndRead(List.of()))
        );
    }

    @Test
    void draggingFromALiveCellErases() throws Exception {
        LifeTestSupport.onEdt(() -> {
            mouse(MouseEvent.MOUSE_PRESSED, 1, 1);
            mouse(MouseEvent.MOUSE_DRAGGED, 2, 1);
            mouse(MouseEvent.MOUSE_DRAGGED, 3, 1);
            mouse(MouseEvent.MOUSE_DRAGGED, 4, 1);
            mouse(MouseEvent.MOUSE_RELEASED, 4, 1);
            mouse(MouseEvent.MOUSE_PRESSED, 2, 1);
            mouse(MouseEvent.MOUSE_DRAGGED, 3, 1);
        });

        assertEquals(
            new HashSet<>(List.of(new Point(1, 1), new Point(4, 1))),
            new HashSet<>(storeAndRead(List.of()))
        );
    }

    @Test
    void draggingOffTheBoardStopsAtTheEdge() throws Exception {
        LifeTestSupport.onEdt(() -> {
            mouse(MouseEvent.MOUSE_PRESSED, 62, 0);
            mouse(MouseEvent.MOUSE_DRAGGED, 70, 0);
        });

        assertEquals(
            new HashSet<>(List.of(new Point(62, 0), new Point(63, 0))),
            new HashSet<>(storeAndRead(List.of()))
        );
    }

    private static void mouse(int eventId, int column, int row) {
        universe.dispatchEvent(new MouseEvent(
            universe, eventId, 0L, 0,
            column * 8 + 1, row * 8 + 1, 1, false, MouseEvent.BUTTON1
        ));
    }

    private Collection<?> storeAndRead(List<Point> liveCells) throws Exception {
        Path destination = directory.resolve("board.life");
        LifeTestSupport.onEdt(() -> {
            for (Point cell : liveCells) {
                LifeTestSupport.click(cell.y, cell.x);
            }
            // Real Store callback, clock, cells, memento and output; no file chooser UI.
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Write"))
                       .thenReturn(destination.toFile());
                LifeTestSupport.select("Grid", "Store");
            }
        });
        try (ObjectInputStream input = new ObjectInputStream(
                java.nio.file.Files.newInputStream(destination))) {
            return (Collection<?>) input.readObject();
        }
    }

}
