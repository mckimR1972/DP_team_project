package com.holub.life;

import com.holub.io.Files;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.holub.life.StatisticsPanelTestSupport.recordingPanel;
import static org.mockito.Mockito.mockStatic;

/** Real board, clock, pattern controls and statistics. No MIDI device is opened. */
class MergedFeaturesIntegrationTest {
    private record SoundDelta(List<Point> births, List<Point> deaths, boolean onEdt) { }
    private static final List<SoundDelta> sound = new ArrayList<>();
    private static Universe universe;
    private final List<BoardChangeEvent> events = new ArrayList<>();
    private final BoardChangeListener recorder = events::add;
    private Statistics statistics;
    private final List<Statistics.Summary> summaries = new ArrayList<>();
    private StatisticsPanel panel;
    private JTabbedPane tabs;
    @TempDir Path directory;

    @BeforeAll
    static void initialize() throws Exception {
        LifeTestSupport.initialize();
        universe = LifeTestSupport.universe;
        LifeTestSupport.onEdt(() -> universe.addBoardChangeListener(event -> {
            if (event.kind() == GENERATION
                    && (!event.copyBirths().isEmpty() || !event.copyDeaths().isEmpty())) {
                sound.add(new SoundDelta(copyPoints(event.copyBirths()), copyPoints(event.copyDeaths()),
                        SwingUtilities.isEventDispatchThread()));
            }
        }));
    }

    @BeforeEach
    void connectActualTools() throws Exception {
        LifeTestSupport.resetBoard();
        LifeTestSupport.onEdt(() -> {
            sound.clear();
            panel = recordingPanel(summaries::add);
            tabs = Life.createSidebar(universe, panel);
            statistics = Life.connectStatistics(universe, panel);
            universe.addBoardChangeListener(recorder);
            events.clear();
        });
    }

    @AfterEach
    void detachAndStopEditing() throws Exception {
        LifeTestSupport.onEdt(() -> {
            universe.removeBoardChangeListener(recorder);
            universe.removeBoardChangeListener(statistics);
            universe.finishPatternEditing();
        });
        LifeTestSupport.resetBoard();
    }

    @Test
    void singleTickAdvancesOnceAndBothObserversReportTheSameBlinkerChanges() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(10, 9);
            LifeTestSupport.click(10, 10);
            LifeTestSupport.click(10, 11);
            events.clear();
            LifeTestSupport.select("Go", "Tick (Single Step)");
            assertEquals(1, events.size());
            assertEquals(GENERATION, events.get(0).kind());
            assertEquals(1, summary().generation());
            assertEquals(3, summary().livingCells());
            assertEquals(2, summary().births());
            assertEquals(2, summary().deaths());
            assertEquals(1, sound.size());
            assertTrue(sound.get(0).onEdt());
            assertEquals(new HashSet<>(List.of(new Point(10, 9), new Point(10, 11))),
                    new HashSet<>(sound.get(0).births()));
            assertEquals(new HashSet<>(List.of(new Point(9, 10), new Point(11, 10))),
                    new HashSet<>(sound.get(0).deaths()));
        });
    }

    @Test
    void stableCellsAgeAndChangeColorWhileStatisticsAdvancesWithoutSoundEvents() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(0, 0); LifeTestSupport.click(0, 1);
            LifeTestSupport.click(1, 0); LifeTestSupport.click(1, 1);
            LifeTestSupport.select("Color", "Cycle by Generation");
            events.clear();
            for (int i = 0; i < 10; i++) LifeTestSupport.clock.tick();
            assertEquals(10, events.size());
            assertEquals(10, summary().generation());
            assertEquals(4, summary().livingCells());
            assertTrue(summary().hasGenerationDelta());
            assertEquals(0, summary().births());
            assertEquals(0, summary().deaths());
            assertEquals(new Color(0x0072B2), cellColor(0, 0));
            assertTrue(sound.isEmpty());
        });
    }

    @Test
    void extinctionReachesSoundOnceWhileEmptyGenerationsStillReachStatistics() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 6);
            for (int i = 0; i < 3; i++) LifeTestSupport.clock.tick();
            assertEquals(3, summary().generation());
            assertEquals(0, summary().livingCells());
            assertEquals(1, sound.size());
            assertTrue(sound.get(0).births().isEmpty());
            assertEquals(List.of(new Point(6, 5)), sound.get(0).deaths());
        });
    }

    @Test
    void ordinaryDraggingPublishesOncePerChangedMouseEventAndSupportsErasing() throws Exception {
        LifeTestSupport.onEdt(() -> {
            press(2, 3); drag(6, 3); drag(6, 3); release(6, 3);
            assertEquals(List.of(EDIT, EDIT), kinds());
            assertEquals(5, summary().livingCells());
            assertEquals(5, LifeTestSupport.countAlive(events.get(1).copyAliveCells()));
            press(6, 3); drag(2, 3); release(2, 3);
            assertEquals(4, events.size());
            assertEquals(0, summary().livingCells());
            assertEquals(0, summary().generation());
            assertFalse(summary().hasGenerationDelta());
            assertTrue(sound.isEmpty());
        });
    }

    @Test
    void templateStrokePublishesOnlyAtCompletionAndBlocksGenerationProgress() throws Exception {
        LifeTestSupport.onEdt(() -> {
            chooseBlinker();
            press(20, 20); drag(22, 20);
            assertTrue(universe.liveCells()[20][24]);
            assertTrue(events.isEmpty());
            assertEquals(0, summary().livingCells());
            LifeTestSupport.clock.tick();
            assertEquals(0, summary().generation());
            assertTrue(events.isEmpty());
            release(22, 20);
            assertEquals(List.of(TEMPLATE), kinds());
            assertEquals(5, summary().livingCells());
            assertEquals("5", value("livingCells").getText());
            assertEquals(List.of(new Statistics.ColorCount(Color.RED, 5, 100)), summary().colorCounts());
            assertFalse(summary().hasGenerationDelta());
            assertTrue(sound.isEmpty());
        });
    }

    @Test
    void templateUndoRestoresStatisticsAndNextGenerationUsesTheRestoredBoard() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(10, 9); LifeTestSupport.click(10, 10); LifeTestSupport.click(10, 11);
            events.clear();
            chooseBlinker(); press(20, 20); drag(22, 20); release(22, 20);
            assertEquals(8, summary().livingCells());
            assertEquals(List.of(new Statistics.ColorCount(Color.RED, 8, 100)), summary().colorCounts());
            button("배치 되돌리기").doClick(0);
            assertEquals(List.of(TEMPLATE, TEMPLATE_UNDO), kinds());
            assertEquals(3, summary().livingCells());
            assertEquals(List.of(new Statistics.ColorCount(Color.RED, 3, 100)), summary().colorCounts());
            assertFalse(button("배치 되돌리기").isEnabled());
            button("편집 끝내기 (Esc)").doClick(0);
            LifeTestSupport.clock.tick();
            assertEquals(1, summary().generation());
            assertEquals(2, summary().births());
            assertEquals(2, summary().deaths());
            assertEquals(1, sound.size());
        });
    }

    @Test
    void unchangedOrOutsideTemplatePlacementsDoNotDuplicateNotificationsOrHistory() throws Exception {
        LifeTestSupport.onEdt(() -> {
            chooseBlinker();
            press(63, 63); release(63, 63);
            assertTrue(events.isEmpty());
            press(20, 20); release(20, 20);
            press(20, 20); release(20, 20);
            assertEquals(List.of(TEMPLATE), kinds());
            button("배치 되돌리기").doClick(0);
            assertEquals(List.of(TEMPLATE, TEMPLATE_UNDO), kinds());
            assertEquals(0, summary().livingCells());
            assertFalse(button("배치 되돌리기").isEnabled());
        });
    }

    @Test
    void captureSelectionDoesNotChangeStatisticsOrAdvanceGenerations() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            events.clear();
            button("영역 캡처").doClick(0);
            press(4, 4); drag(8, 8); release(8, 8);
            LifeTestSupport.clock.tick();
            assertTrue(events.isEmpty());
            assertEquals(1, summary().livingCells());
            assertEquals(0, summary().generation());
            assertTrue(sound.isEmpty());
        });
    }

    @Test
    void switchingToStatisticsFinishesAHeldStrokeAndKeepsItsUndoAvailable() throws Exception {
        LifeTestSupport.onEdt(() -> {
            assertEquals(3, tabs.getTabCount());
            assertSame(universe.patternControls(), tabs.getComponentAt(0));
            assertSame(panel, tabs.getComponentAt(1));
            assertSame(universe.freezingControls(), tabs.getComponentAt(2));
            JPanel content = Life.createContent(universe, tabs);
            content.setSize(content.getPreferredSize());
            content.doLayout();
            assertEquals(512, universe.getWidth());
            assertEquals(universe.getWidth(), tabs.getX());
            chooseBlinker(); press(20, 20); drag(22, 20);
            tabs.setSelectedComponent(panel);
            assertEquals(List.of(TEMPLATE), kinds());
            assertEquals(5, summary().livingCells());
            assertTrue(button("배치 되돌리기").isEnabled());
            tabs.setSelectedIndex(0);
            button("배치 되돌리기").doClick(0);
            tabs.setSelectedComponent(panel);
            assertEquals(List.of(TEMPLATE, TEMPLATE_UNDO), kinds());
            assertEquals(0, summary().livingCells());
            LifeTestSupport.clock.tick();
            assertEquals(1, summary().generation());
        });
    }

    @Test
    void switchingTabsOutsideEditingDoesNotStopAutomaticGenerations() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        BoardChangeListener listener = event -> {
            if (event.kind() == GENERATION) completed.countDown();
        };
        LifeTestSupport.onEdt(() -> {
            universe.addBoardChangeListener(listener);
            LifeTestSupport.clock.startTicking(20);
            tabs.setSelectedComponent(panel);
        });
        try {
            assertTrue(completed.await(5, TimeUnit.SECONDS), "Tab changes must not halt the clock");
        } finally {
            LifeTestSupport.onEdt(() -> {
                LifeTestSupport.clock.stop();
                universe.removeBoardChangeListener(listener);
            });
        }
    }

    @Test
    void clearEndsHeldStampingAndLeavesStatisticsEmpty() throws Exception {
        LifeTestSupport.onEdt(() -> {
            chooseBlinker(); press(20, 20); drag(22, 20);
            LifeTestSupport.select("Grid", "Clear");
            assertEquals(List.of(TEMPLATE, CLEAR), kinds());
            assertEquals(0, summary().livingCells());
            assertEquals(0, summary().generation());
            assertFalse(button("배치 되돌리기").isEnabled());
        });
        awaitEdtDelay(180);
        LifeTestSupport.onEdt(() -> {
            assertEquals(List.of(TEMPLATE, CLEAR), kinds());
            assertEquals(0, summary().livingCells());
            assertTrue(sound.isEmpty());
        });
    }

    @Test
    void loadEndsPatternEditingAndNotifiesBothObserversOnTheNextActualGeneration() throws Exception {
        Path source = directory.resolve("blinker.life");
        try (ObjectOutputStream output = new ObjectOutputStream(java.nio.file.Files.newOutputStream(source))) {
            output.writeObject(new ArrayList<>(List.of(new Point(9, 10), new Point(10, 10), new Point(11, 10))));
        }
        LifeTestSupport.onEdt(() -> {
            chooseBlinker(); press(20, 20); release(20, 20);
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Load"))
                        .thenReturn(source.toFile());
                LifeTestSupport.select("Grid", "Load");
            }
            assertEquals(List.of(LOAD), kinds());
            assertEquals(0, summary().generation());
            assertEquals(3, summary().livingCells());
            assertFalse(summary().hasGenerationDelta());
            assertFalse(button("배치 되돌리기").isEnabled());
            assertTrue(sound.isEmpty());
            LifeTestSupport.clock.tick();
            assertEquals(1, summary().generation());
            assertEquals(2, summary().births());
            assertEquals(2, summary().deaths());
            assertEquals(1, sound.size());
        });
    }

    private Statistics.Summary summary() { return summaries.get(summaries.size() - 1); }
    private List<BoardChangeEvent.Kind> kinds() { return events.stream().map(BoardChangeEvent::kind).toList(); }

    private void chooseBlinker() {
        JList<?> list = find(universe.patternControls(), JList.class, null);
        assertNotNull(list);
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (((PatternTemplate) list.getModel().getElementAt(i)).name().equals("Blinker")) {
                list.setSelectedIndex(i);
                button("선택한 템플릿 배치").doClick(0);
                return;
            }
        }
        fail("Built-in Blinker is missing");
    }

    private JButton button(String text) {
        JButton button = find(universe.patternControls(), JButton.class, text);
        assertNotNull(button, text);
        return button;
    }

    private JLabel value(String name) {
        JLabel label = find(panel, JLabel.class, "statistics." + name);
        assertNotNull(label);
        return label;
    }

    private static <T extends Component> T find(Container root, Class<T> type, String key) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && (key == null || key.equals(c.getName())
                    || c instanceof JButton && key.equals(((JButton) c).getText()))) return type.cast(c);
            if (c instanceof Container) {
                T result = find((Container) c, type, key);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void mouse(int id, int column, int row, int button, int modifiers) {
        universe.dispatchEvent(new MouseEvent(universe, id, System.currentTimeMillis(), modifiers,
                column * 8 + 4, row * 8 + 4, 1, false, button));
    }
    private static void press(int x, int y) { mouse(MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1, MouseEvent.BUTTON1_DOWN_MASK); }
    private static void drag(int x, int y) { mouse(MouseEvent.MOUSE_DRAGGED, x, y, MouseEvent.NOBUTTON, MouseEvent.BUTTON1_DOWN_MASK); }
    private static void release(int x, int y) { mouse(MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1, 0); }
    private static List<Point> copyPoints(List<Point> points) { return points.stream().map(Point::new).toList(); }

    private static Color cellColor(int column, int row) {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        universe.paint(graphics);
        graphics.dispose();
        return new Color(image.getRGB(column * 8 + 4, row * 8 + 4));
    }

    private static void awaitEdtDelay(int milliseconds) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        LifeTestSupport.onEdt(() -> {
            Timer delay = new Timer(milliseconds, event -> completed.countDown());
            delay.setRepeats(false);
            delay.start();
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
    }
}
