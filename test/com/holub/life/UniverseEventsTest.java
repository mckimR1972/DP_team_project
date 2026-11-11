package com.holub.life;

import com.holub.io.Files;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class UniverseEventsTest {
    private final List<BoardChangeListener> subscriptions = new ArrayList<>();
    private final List<BoardChangeEvent> events = new ArrayList<>();
    private final List<Statistics.Summary> summaries = new ArrayList<>();
    private final Statistics statistics = new Statistics(summaries::add);
    @TempDir Path directory;

    @BeforeAll
    static void initialize() throws Exception { LifeTestSupport.initialize(); }

    @BeforeEach
    void reset() throws Exception { LifeTestSupport.resetBoard(); }

    @AfterEach
    void detach() throws Exception {
        LifeTestSupport.onEdt(() -> subscriptions.forEach(
                LifeTestSupport.universe::removeBoardChangeListener));
        LifeTestSupport.resetBoard();
    }

    @Test
    void registrationDeliversCurrentEmptyBoardOnEdt() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(event -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                events.add(event);
            });
            assertEquals(1, events.size());
            assertEquals(INITIAL, last().kind());
            assertEquals(0, last().generation());
            assertEquals(64, last().boardSize());
            assertTrue(last().copyAliveCells().isEmpty());
        });
    }

    @Test
    void duplicateRegistrationDoesNotRepeatInitialOrBroadcast() throws Exception {
        LifeTestSupport.onEdt(() -> {
            BoardChangeListener first = events::add;
            subscribe(first);
            LifeTestSupport.universe.addBoardChangeListener(first);
            List<BoardChangeEvent> second = new ArrayList<>();
            subscribe(second::add);
            assertEquals(1, events.size(), "A new observer must not reset existing observers");
            assertEquals(1, second.size());
            LifeTestSupport.clock.tick();
            assertEquals(2, events.size());
            assertEquals(2, second.size());
            assertSame(last(), second.get(1));
        });
    }

    @Test
    void removedListenerReceivesNothingUntilRegisteredAgain() throws Exception {
        LifeTestSupport.onEdt(() -> {
            BoardChangeListener listener = events::add;
            subscribe(listener);
            LifeTestSupport.universe.removeBoardChangeListener(listener);
            LifeTestSupport.universe.removeBoardChangeListener(listener);
            LifeTestSupport.clock.tick();
            assertEquals(1, events.size());
            LifeTestSupport.universe.addBoardChangeListener(listener);
            assertEquals(2, events.size());
            assertEquals(INITIAL, last().kind());
            assertEquals(1, last().generation());
        });
    }

    @Test
    void clicksPublishExactRowMajorCoordinatesAndToggleState() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            LifeTestSupport.click(3, 10);
            LifeTestSupport.click(63, 0);
            LifeTestSupport.click(0, 63);
            assertEquals(4, events.size());
            assertEquals(EDIT, last().kind());
            assertEquals(0, last().generation());
            assertEquals(bits(3 * 64 + 10, 63 * 64, 63), last().copyAliveCells());
            LifeTestSupport.click(3, 10);
            assertEquals(bits(63 * 64, 63), last().copyAliveCells());
        });
    }

    @Test
    void ignoredMousePressesDoNotChangeBoardOrPublish() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            LifeTestSupport.press(-1, 1, MouseEvent.BUTTON1);
            LifeTestSupport.press(512, 1, MouseEvent.BUTTON1);
            LifeTestSupport.press(1, 512, MouseEvent.BUTTON1);
            LifeTestSupport.press(1, 1, MouseEvent.BUTTON3);
            assertEquals(1, events.size());
            assertTrue(snapshot().copyAliveCells().isEmpty());
        });
    }

    @Test
    void actualBlinkerAcrossNeighborhoodBoundaryPublishesCompletedGeneration() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            subscribe(statistics);
            LifeTestSupport.click(7, 7);
            LifeTestSupport.click(7, 8);
            LifeTestSupport.click(7, 9);
            events.clear();
            summaries.clear();
            LifeTestSupport.clock.tick();
            assertEquals(1, events.size());
            assertEquals(GENERATION, last().kind());
            assertEquals(1, last().generation());
            assertEquals(bits(6 * 64 + 8, 7 * 64 + 8, 8 * 64 + 8), last().copyAliveCells());
            assertEquals(1, summaries.size());
            assertEquals(3, summary().livingCells());
            assertEquals(3 * 100.0 / 4096, summary().livingRatio(), 0.000001);
            assertDelta(2, 2);
            LifeTestSupport.clock.tick();
            assertEquals(2, last().generation());
            assertEquals(bits(7 * 64 + 7, 7 * 64 + 8, 7 * 64 + 9), last().copyAliveCells());
            assertDelta(2, 2);
        });
    }

    @Test
    void stableBlockStillAdvancesAndReportsMeasuredZeroChange() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            subscribe(statistics);
            seedBlock();
            events.clear();
            for (int generation = 1; generation <= 3; generation++) {
                LifeTestSupport.universe.advanceOneGeneration();
                assertEquals(generation, last().generation());
                assertEquals(4, summary().livingCells());
                assertDelta(0, 0);
            }
            assertEquals(3, events.size());
        });
    }

    @Test
    void emptyBoardStillPublishesEveryGeneration() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            subscribe(statistics);
            LifeTestSupport.clock.tick();
            LifeTestSupport.clock.tick();
            assertEquals(3, events.size());
            assertEquals(2, last().generation());
            assertEquals(0, summary().livingCells());
            assertDelta(0, 0);
        });
    }

    @Test
    void editingAfterGenerationUpdatesBaselineForActualNextGeneration() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(statistics);
            seedBlock();
            LifeTestSupport.clock.tick();
            LifeTestSupport.click(30, 30);
            assertEquals(1, summary().generation());
            assertEquals(5, summary().livingCells());
            assertFalse(summary().hasGenerationDelta());
            LifeTestSupport.clock.tick();
            assertEquals(2, summary().generation());
            assertEquals(4, summary().livingCells());
            assertDelta(0, 1);
        });
    }

    @Test
    void clearPublishesOneEmptyStateAndResetsGeneration() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            subscribe(statistics);
            seedBlock();
            LifeTestSupport.clock.tick();
            events.clear();
            LifeTestSupport.select("Grid", "Clear");
            assertEquals(1, events.size());
            assertEquals(CLEAR, last().kind());
            assertEquals(0, summary().generation());
            assertEquals(0, summary().livingCells());
            assertFalse(summary().hasGenerationDelta());
            LifeTestSupport.clock.tick();
            assertEquals(1, summary().generation());
            assertDelta(0, 0);
        });
    }

    @Test
    void loadPublishesRestoredStateOnceAndResetsComparisonBaseline() throws Exception {
        Path source = directory.resolve("blinker.life");
        try (ObjectOutputStream output = new ObjectOutputStream(java.nio.file.Files.newOutputStream(source))) {
            output.writeObject(new ArrayList<>(List.of(new Point(7, 7), new Point(8, 7), new Point(9, 7))));
        }
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            subscribe(statistics);
            seedBlock();
            LifeTestSupport.clock.tick();
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Load"))
                        .thenReturn(source.toFile());
                LifeTestSupport.select("Grid", "Load");
            }
            assertEquals(1, events.size());
            assertEquals(LOAD, last().kind());
            assertEquals(bits(7 * 64 + 7, 7 * 64 + 8, 7 * 64 + 9), last().copyAliveCells());
            assertEquals(0, summary().generation());
            assertEquals(3, summary().livingCells());
            assertFalse(summary().hasGenerationDelta());
            LifeTestSupport.clock.tick();
            assertDelta(2, 2);
            assertEquals(bits(6 * 64 + 8, 7 * 64 + 8, 8 * 64 + 8), last().copyAliveCells());
        });
    }

    @Test
    void failedReadPreservesBoardGenerationAndStatisticsWithoutPublishing() throws Exception {
        Path source = directory.resolve("invalid.life");
        java.nio.file.Files.writeString(source, "not a serialized board");
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            subscribe(statistics);
            seedBlock();
            LifeTestSupport.clock.tick();
            Statistics.Summary before = summary();
            BitSet cells = last().copyAliveCells();
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class);
                 MockedStatic<JOptionPane> dialogs = mockStatic(JOptionPane.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Load"))
                        .thenReturn(source.toFile());
                LifeTestSupport.select("Grid", "Load");
                dialogs.verify(() -> JOptionPane.showMessageDialog(null, "Read Failed!",
                        "The Game of Life", JOptionPane.ERROR_MESSAGE));
            }
            assertTrue(events.isEmpty());
            assertSame(before, summary());
            BoardChangeEvent current = snapshot();
            assertEquals(1, current.generation());
            assertEquals(cells, current.copyAliveCells());
        });
    }

    @Test
    void cancelledLoadPreservesCurrentStateWithoutPublishingOrShowingError() throws Exception {
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            LifeTestSupport.click(3, 10);
            BitSet cells = last().copyAliveCells();
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class);
                 MockedStatic<JOptionPane> dialogs = mockStatic(JOptionPane.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Load"))
                        .thenThrow(new FileNotFoundException("No file selected by user"));
                LifeTestSupport.select("Grid", "Load");
                dialogs.verifyNoInteractions();
            }
            assertTrue(events.isEmpty());
            assertEquals(cells, snapshot().copyAliveCells());
        });
    }

    @Test
    void storeDoesNotPublishOrResetGeneration() throws Exception {
        Path target = directory.resolve("saved.life");
        LifeTestSupport.onEdt(() -> {
            subscribe(events::add);
            seedBlock();
            LifeTestSupport.clock.tick();
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Write"))
                        .thenReturn(target.toFile());
                LifeTestSupport.select("Grid", "Store");
            }
            assertTrue(events.isEmpty());
            assertEquals(1, snapshot().generation());
            assertEquals(4, snapshot().copyAliveCells().cardinality());
        });
        assertTrue(java.nio.file.Files.size(target) > 0);
    }

    @Test
    void observersShareAnEventButCannotChangeEachOthersSnapshots() throws Exception {
        LifeTestSupport.onEdt(() -> {
            List<BoardChangeEvent> first = new ArrayList<>();
            subscribe(event -> {
                first.add(event);
                event.copyAliveCells().clear();
            });
            subscribe(events::add);
            LifeTestSupport.click(3, 10);
            assertSame(first.get(1), last());
            assertEquals(bits(3 * 64 + 10), last().copyAliveCells());
            LifeTestSupport.click(4, 11);
            assertEquals(bits(3 * 64 + 10), first.get(1).copyAliveCells());
        });
    }

    @Test
    void subscriptionChangesDuringDeliveryApplyToNextBroadcast() throws Exception {
        LifeTestSupport.onEdt(() -> {
            BoardChangeListener second = events::add;
            subscribe(event -> {
                if (event.kind() == EDIT) LifeTestSupport.universe.removeBoardChangeListener(second);
            });
            subscribe(second);
            LifeTestSupport.click(3, 10);
            assertEquals(2, events.size());
            LifeTestSupport.click(4, 11);
            assertEquals(2, events.size());
        });
    }

    @Test
    void boardOperationsRejectCallsOutsideEdt() {
        assertFalse(SwingUtilities.isEventDispatchThread());
        BoardChangeListener listener = event -> { };
        assertThrows(IllegalStateException.class, () -> LifeTestSupport.universe.addBoardChangeListener(listener));
        assertThrows(IllegalStateException.class, () -> LifeTestSupport.universe.removeBoardChangeListener(listener));
        assertThrows(IllegalStateException.class, () -> LifeTestSupport.universe.notifyBoardChanged(EDIT));
        assertThrows(IllegalStateException.class, () -> LifeTestSupport.universe.advanceOneGeneration());
    }

    @Test
    void listenerAndEventKindMustNotBeNull() throws Exception {
        LifeTestSupport.onEdt(() -> {
            assertThrows(NullPointerException.class, () -> LifeTestSupport.universe.addBoardChangeListener(null));
            assertThrows(NullPointerException.class, () -> LifeTestSupport.universe.removeBoardChangeListener(null));
            assertThrows(NullPointerException.class, () -> LifeTestSupport.universe.notifyBoardChanged(null));
        });
    }

    private void subscribe(BoardChangeListener listener) {
        subscriptions.add(listener);
        LifeTestSupport.universe.addBoardChangeListener(listener);
    }

    private BoardChangeEvent snapshot() {
        List<BoardChangeEvent> current = new ArrayList<>();
        BoardChangeListener reader = current::add;
        LifeTestSupport.universe.addBoardChangeListener(reader);
        LifeTestSupport.universe.removeBoardChangeListener(reader);
        return current.get(0);
    }

    private static void seedBlock() {
        LifeTestSupport.click(10, 10);
        LifeTestSupport.click(10, 11);
        LifeTestSupport.click(11, 10);
        LifeTestSupport.click(11, 11);
    }

    private BoardChangeEvent last() { return events.get(events.size() - 1); }
    private Statistics.Summary summary() { return statistics.current().orElseThrow(); }

    private void assertDelta(int births, int deaths) {
        assertTrue(summary().hasGenerationDelta());
        assertEquals(births, summary().births());
        assertEquals(deaths, summary().deaths());
    }

    private static BitSet bits(int... indices) {
        BitSet bits = new BitSet();
        for (int index : indices) bits.set(index);
        return bits;
    }
}
