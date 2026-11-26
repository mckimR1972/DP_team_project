package com.holub.life;

import com.holub.io.Files;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static com.holub.life.StatisticsPanelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class StatisticsPanelIntegrationTest {
    private StatisticsPanel panel;
    private Statistics statistics;
    private final List<Statistics.Summary> summaries = new ArrayList<>();
    @TempDir Path directory;

    @BeforeAll
    static void initialize() throws Exception { LifeTestSupport.initialize(); }

    @BeforeEach
    void reset() throws Exception {
        LifeTestSupport.resetBoard();
        LifeTestSupport.onEdt(() -> panel = recordingPanel(summaries::add));
    }

    @AfterEach
    void detach() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.clock.stop();
            if (statistics != null) LifeTestSupport.universe.removeBoardChangeListener(statistics);
            Container parent = LifeTestSupport.universe.getParent();
            if (parent != null) parent.remove(LifeTestSupport.universe);
        });
        LifeTestSupport.resetBoard();
    }

    @Test
    void startupConnectionDisplaysInitialStateSynchronously() throws Exception {
        LifeTestSupport.onEdt(() -> {
            connect();
            assertDisplay("0", "0", "0.00%", "—", "—");
        });
    }

    @Test
    void connectingToAnExistingGamePreservesItsStateAndGeneration() throws Exception {
        LifeTestSupport.onEdt(() -> {
            seedBlock();
            LifeTestSupport.clock.tick();
            LifeTestSupport.clock.tick();
            connect();
            assertDisplay("2", "4", "0.10%", "—", "—");
            LifeTestSupport.clock.tick();
            assertDisplay("3", "4", "0.10%", "0", "0");
        });
    }

    @Test
    void pausedEditingUpdatesCountsAndClearsPreviousGenerationDelta() throws Exception {
        LifeTestSupport.onEdt(() -> {
            connect();
            LifeTestSupport.click(30, 30);
            assertDisplay("0", "1", "0.02%", "—", "—");
            LifeTestSupport.clock.tick();
            assertDisplay("1", "0", "0.00%", "0", "1");
            LifeTestSupport.click(30, 30);
            assertDisplay("1", "1", "0.02%", "—", "—");
            LifeTestSupport.click(30, 30);
            assertDisplay("1", "0", "0.00%", "—", "—");
        });
    }

    @Test
    void singleStepDisplaysActualBlinkerBirthsAndDeaths() throws Exception {
        LifeTestSupport.onEdt(() -> {
            connect();
            LifeTestSupport.click(7, 7);
            LifeTestSupport.click(7, 8);
            LifeTestSupport.click(7, 9);
            LifeTestSupport.select("Go", "Tick (Single Step)");
            assertDisplay("1", "3", "0.07%", "2", "2");
        });
    }

    @Test
    void stableBoardDisplaysIncreasingGenerationsAndMeasuredZeroChanges() throws Exception {
        LifeTestSupport.onEdt(() -> {
            connect();
            seedBlock();
            for (int generation = 1; generation <= 3; generation++) {
                LifeTestSupport.clock.tick();
                assertDisplay(Integer.toString(generation), "4", "0.10%", "0", "0");
            }
        });
    }

    @Test
    void clearResetsEveryDisplayedValue() throws Exception {
        LifeTestSupport.onEdt(() -> {
            connect();
            seedBlock();
            LifeTestSupport.clock.tick();
            LifeTestSupport.select("Grid", "Clear");
            assertDisplay("0", "0", "0.00%", "—", "—");
        });
    }

    @Test
    void loadDisplaysRestoredBoardAndNextGenerationDelta() throws Exception {
        Path source = directory.resolve("blinker.life");
        try (ObjectOutputStream output = new ObjectOutputStream(java.nio.file.Files.newOutputStream(source))) {
            output.writeObject(new ArrayList<>(List.of(new Point(7, 7), new Point(8, 7), new Point(9, 7))));
        }
        LifeTestSupport.onEdt(() -> {
            connect();
            seedBlock();
            LifeTestSupport.clock.tick();
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Load"))
                        .thenReturn(source.toFile());
                LifeTestSupport.select("Grid", "Load");
            }
            assertDisplay("0", "3", "0.07%", "—", "—");
            assertEquals("3개 · 100.00%", label(panel, "color.#FF0000").getText());
            LifeTestSupport.clock.tick();
            assertDisplay("1", "3", "0.07%", "2", "2");
        });
    }

    @Test
    void removingTheObserverStopsDisplayUpdates() throws Exception {
        LifeTestSupport.onEdt(() -> {
            connect();
            Map<String, String> before = displayed(panel);
            LifeTestSupport.universe.removeBoardChangeListener(statistics);
            LifeTestSupport.click(30, 30);
            LifeTestSupport.clock.tick();
            assertEquals(before, displayed(panel));
        });
    }

    @Test
    void automaticTimerReachesTheDisplayOnEdt() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        LifeTestSupport.onEdt(() -> {
            connect();
            seedBlock();
            label(panel, "generation").addPropertyChangeListener("text", event -> {
                if ("3".equals(event.getNewValue())) {
                    LifeTestSupport.clock.stop();
                    completed.countDown();
                }
            });
            LifeTestSupport.clock.startTicking(15);
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS), "Timer must reach the statistics view");
        LifeTestSupport.onEdt(() -> assertDisplay("3", "4", "0.10%", "0", "0"));
    }

    @Test
    void standaloneStatisticsTabFitsBesideTheBoardWithoutAGap() throws Exception {
        LifeTestSupport.onEdt(() -> {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("통계", panel);
            JPanel content = Life.createContent(LifeTestSupport.universe, tabs);
            connect();
            layoutAtPreferredSize(content);
            assertEquals(1, tabs.getTabCount());
            assertEquals("통계", tabs.getTitleAt(0));
            assertSame(panel, tabs.getComponentAt(0));
            assertBoardAndSidebarBounds(content, tabs);
            assertLabelsFit(panel);
        });
    }

    @Test
    void sharedTabsKeepTheirPagesSelectionAndListenersWhenLaidOut() throws Exception {
        LifeTestSupport.onEdt(() -> {
            JTabbedPane tabs = sharedTabs();
            Component capture = tabs.getComponentAt(0);
            Component freezing = tabs.getComponentAt(1);
            tabs.setSelectedIndex(1);
            int[] changes = {0};
            tabs.addChangeListener(event -> changes[0]++);
            // Simulate a common pane whose preferred width was fixed before adding statistics.
            tabs.setPreferredSize(new Dimension(80, 512));
            tabs.addTab("통계", panel);
            JPanel content = Life.createContent(LifeTestSupport.universe, tabs);
            layoutAtPreferredSize(content);

            assertSame(tabs, ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.EAST));
            assertEquals(3, tabs.getTabCount());
            assertEquals(List.of("패턴 캡처", "Freezing", "통계"),
                    List.of(tabs.getTitleAt(0), tabs.getTitleAt(1), tabs.getTitleAt(2)));
            assertSame(capture, tabs.getComponentAt(0));
            assertSame(freezing, tabs.getComponentAt(1));
            assertSame(panel, tabs.getComponentAt(2));
            assertEquals(1, tabs.getSelectedIndex());
            assertEquals(0, changes[0]);
            assertTrue(tabs.getWidth() >= panel.getPreferredSize().width);
            assertBoardAndSidebarBounds(content, tabs);

            tabs.setSelectedIndex(2);
            layoutTree(content);
            assertEquals(1, changes[0], "Existing tab-change listener must remain installed");
            assertLabelsFit(panel);
        });
    }

    @Test
    void hiddenStatisticsKeepsUpdatingAndTabChangesPreserveTheSummary() throws Exception {
        LifeTestSupport.onEdt(() -> {
            JTabbedPane tabs = sharedTabs();
            tabs.addTab("통계", panel);
            JPanel content = Life.createContent(LifeTestSupport.universe, tabs);
            layoutAtPreferredSize(content);
            connect();
            assertFalse(panel.isVisible());

            LifeTestSupport.click(7, 7);
            LifeTestSupport.click(7, 8);
            LifeTestSupport.click(7, 9);
            LifeTestSupport.clock.tick();
            tabs.setSelectedIndex(1);
            LifeTestSupport.clock.tick();
            assertDisplay("2", "3", "0.07%", "2", "2");
            Statistics.Summary beforeSelection = summary();

            tabs.setSelectedIndex(2);
            assertTrue(panel.isVisible());
            assertSame(beforeSelection, summary());
            assertDisplay("2", "3", "0.07%", "2", "2");
            tabs.setSelectedIndex(0);
            LifeTestSupport.clock.tick();
            tabs.setSelectedIndex(2);
            assertDisplay("3", "3", "0.07%", "2", "2");
        });
    }

    @Test
    void threeColorRowsAndLargeValuesFitInsideTheExistingSharedTab() throws Exception {
        LifeTestSupport.onEdt(() -> {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("통계", panel);
            JPanel content = Life.createContent(LifeTestSupport.universe, tabs);
            panel.showStatistics(new Statistics.Summary(3_000_000_000L, 4096, 100, 4096, 4096, true,
                    List.of(new Statistics.ColorCount(java.awt.Color.RED, 2048, 50),
                            new Statistics.ColorCount(new java.awt.Color(0x0072B2), 1024, 25),
                            new Statistics.ColorCount(new java.awt.Color(0x009E73), 1024, 25))));
            layoutAtPreferredSize(content);
            assertLabelsFit(panel);
            assertBoardAndSidebarBounds(content, tabs);
            JLabel mainValue = label(panel, "livingCells");
            JLabel colorValue = label(panel, "color.#FF0000");
            assertEquals(mainValue.getFont(), colorValue.getFont(), "All statistics values share one font");
            assertEquals(mainValue.getParent().getHeight(), colorValue.getParent().getHeight(),
                    "All statistics rows share one height");
            int mainStep = mainValue.getParent().getY() - label(panel, "generation").getParent().getY();
            int colorStep = label(panel, "color.#0072B2").getParent().getY() - colorValue.getParent().getY();
            assertEquals(mainStep, colorStep, "Row spacing is consistent across sections");
        });
    }

    /** Layout placeholders only: these are not the actual capture or Freezing features. */
    private static JTabbedPane sharedTabs() {
        JTabbedPane tabs = new JTabbedPane();
        for (String title : List.of("패턴 캡처", "Freezing")) {
            JPanel placeholder = new JPanel();
            placeholder.setPreferredSize(new Dimension(258, 512));
            tabs.addTab(title, placeholder);
        }
        return tabs;
    }

    private static void layoutAtPreferredSize(JPanel content) {
        content.setSize(content.getPreferredSize());
        layoutTree(content);
    }

    private static void assertBoardAndSidebarBounds(JPanel content, JTabbedPane tabs) {
        Rectangle board = SwingUtilities.convertRectangle(LifeTestSupport.universe.getParent(),
                LifeTestSupport.universe.getBounds(), content);
        Rectangle sidebar = SwingUtilities.convertRectangle(tabs.getParent(), tabs.getBounds(), content);
        assertEquals(new Rectangle(0, 0, 512, 512), board);
        assertEquals(board.x + board.width, sidebar.x, "Board and tabs should touch");
        assertEquals(0, sidebar.y);
        assertEquals(board.height, sidebar.height, "The tab header is included in the board height");
        assertTrue(new Rectangle(content.getSize()).contains(sidebar));
    }

    private void assertLabelsFit(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) {
                assertTrue(label.getWidth() >= label.getPreferredSize().width,
                        "Label width should not clip: " + label.getText());
                assertTrue(label.getHeight() >= label.getPreferredSize().height,
                        "Label height should not clip: " + label.getText());
                Rectangle bounds = SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), panel);
                assertTrue(new Rectangle(panel.getSize()).contains(bounds), label.getText());
            } else if (child instanceof Container nested) {
                assertLabelsFit(nested);
            }
        }
    }

    private void connect() { statistics = Life.connectStatistics(LifeTestSupport.universe, panel); }

    private Statistics.Summary summary() { return summaries.get(summaries.size() - 1); }

    private void assertDisplay(String generation, String livingCells, String ratio, String births, String deaths) {
        assertEquals(Map.of("generation", generation, "livingCells", livingCells,
                "livingRatio", ratio, "births", births, "deaths", deaths), displayed(panel));
    }

    private static void seedBlock() {
        LifeTestSupport.click(10, 10);
        LifeTestSupport.click(10, 11);
        LifeTestSupport.click(11, 10);
        LifeTestSupport.click(11, 11);
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }
}
