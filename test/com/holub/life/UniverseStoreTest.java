package com.holub.life;

import com.holub.io.Files;

import java.awt.Point;
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

    @BeforeAll
    static void initializeUniverseWithoutOpeningWindows() throws Exception {
        LifeTestSupport.initialize();
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
