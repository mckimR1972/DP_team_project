package com.holub.life;

import com.holub.io.Files;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.holub.life.StatisticsPanelTestSupport.recordingPanel;
import static org.mockito.Mockito.mockStatic;

/**
 * Freezing together with every merged feature on the real singleton board:
 * clock and single step, board events (statistics and a sound stand-in),
 * generation colors, the drag brush, pattern templates, and the sidebar.
 * Coordinates: mouse helpers take (column, row); LifeTestSupport.click takes (row, column).
 */
class FreezingIntegrationTest {
    private static final Color RED = Resident.LIVE_COLOR;
    private static final Color BLUE = new Color(0x0072B2);
    private static final Color GREEN = new Color(0x009E73);
    private static final int SIZE = 64;

    private record SoundDelta(Set<Point> births, Set<Point> deaths) { }

    private static Universe universe;
    private static Cell cells;
    private final List<BoardChangeEvent> events = new ArrayList<>();
    private final List<SoundDelta> sound = new ArrayList<>();
    private final BoardChangeListener recorder = events::add;
    /** Like SoundEffects: reacts only to generations that changed something. */
    private final BoardChangeListener soundStandIn = event -> {
        if (event.kind() == GENERATION && (!event.copyBirths().isEmpty() || !event.copyDeaths().isEmpty()))
            sound.add(new SoundDelta(new HashSet<>(event.copyBirths()), new HashSet<>(event.copyDeaths())));
    };
    private Statistics statistics;
    private final List<Statistics.Summary> summaries = new ArrayList<>();
    private StatisticsPanel panel;
    private JTabbedPane tabs;
    @TempDir Path directory;

    @BeforeAll
    static void initialize() throws Exception {
        LifeTestSupport.initialize();
        universe = LifeTestSupport.universe;
        Field field = Universe.class.getDeclaredField("outermostCell");
        field.setAccessible(true);
        cells = (Cell) field.get(universe);
    }

    @BeforeEach
    void connectActualTools() throws Exception {
        LifeTestSupport.resetBoard();
        LifeTestSupport.onEdt(() -> {
            panel = recordingPanel(summaries::add);
            tabs = Life.createSidebar(universe, panel);
            statistics = Life.connectStatistics(universe, panel);
            universe.addBoardChangeListener(soundStandIn);
            universe.addBoardChangeListener(recorder);
            events.clear();
            sound.clear();
            spinner().setValue(5);
        });
    }

    @AfterEach
    void detachAndStopEditing() throws Exception {
        LifeTestSupport.onEdt(() -> {
            universe.removeBoardChangeListener(recorder);
            universe.removeBoardChangeListener(soundStandIn);
            universe.removeBoardChangeListener(statistics);
            universe.finishPatternEditing();
            universe.finishFreezingEditing();
        });
        LifeTestSupport.resetBoard();
    }

    // ------------------------------------------------ clock and generation

    @Test
    void freezingStatisticsAndEventsShareOneGenerationCounter() throws Exception {
        LifeTestSupport.onEdt(() -> {
            for (int g = 1; g <= 10; g++) LifeTestSupport.clock.tick();
            assertEquals(10, LifeTestSupport.freezing().generation());
            assertEquals(10, summary().generation());
            assertEquals("현재 세대: 10", generationLabel().getText());

            LifeTestSupport.click(10, 10);
            tabs.setSelectedIndex(2);
            installByDragging(10, 10, 10, 10);
            FreezingRegion region = LifeTestSupport.freezing().regions().get(0);
            assertEquals(5, region.remainingGenerations(), "installed at generation 10 for 5");
            events.clear();

            for (int g = 11; g <= 15; g++) {
                LifeTestSupport.clock.tick();
                BoardChangeEvent event = events.get(events.size() - 1);
                assertEquals(g, event.generation());
                assertEquals(g, LifeTestSupport.freezing().generation());
                assertEquals(g, summary().generation());
                assertEquals("현재 세대: " + g, generationLabel().getText());
                assertEquals(Long.toString(g), value("generation").getText());
                assertTrue(alive(10, 10), "frozen during generation " + g);
                assertEquals(15 - g, region.remainingGenerations());
            }
            assertTrue(LifeTestSupport.freezing().regions().isEmpty(), "released by the 15th update");
            assertEquals(0, list().getModel().getSize());

            LifeTestSupport.clock.tick();
            BoardChangeEvent sixteenth = events.get(events.size() - 1);
            assertEquals(16, sixteenth.generation());
            assertFalse(alive(10, 10), "normal rules from generation 16");
            assertEquals(List.of(new Point(10, 10)), sixteenth.copyDeaths());
        });
    }

    @Test
    void singleStepMenuAdvancesExactlyOneGenerationAndOneCountdown() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            FreezingRegion region = LifeTestSupport.freezing().install(new Rectangle(5, 5, 1, 1), 2);
            events.clear();
            LifeTestSupport.select("Go", "Tick (Single Step)");
            assertEquals(List.of(GENERATION), kinds());
            assertEquals(1, region.remainingGenerations());
            assertTrue(alive(5, 5));
            LifeTestSupport.select("Go", "Tick (Single Step)");
            assertFalse(region.isFrozen());
            assertTrue(alive(5, 5), "still held on its last frozen generation");
            LifeTestSupport.select("Go", "Tick (Single Step)");
            assertFalse(alive(5, 5));
            assertEquals(3, summary().generation());
        });
    }

    @Test
    void theRunningClockCountsDownAndReleasesOnTime() throws Exception {
        CountDownLatch sixGenerations = new CountDownLatch(6);
        BoardChangeListener counter = event -> {
            if (event.kind() == GENERATION) sixGenerations.countDown();
        };
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(30, 30);
            LifeTestSupport.freezing().install(new Rectangle(30, 30, 1, 1), 3);
            events.clear();
            universe.addBoardChangeListener(counter);
            LifeTestSupport.select("Go", "Fast");
        });
        try {
            assertTrue(sixGenerations.await(10, TimeUnit.SECONDS), "the clock must keep running with a Freezing");
        } finally {
            LifeTestSupport.onEdt(() -> {
                LifeTestSupport.select("Go", "Halt");
                universe.removeBoardChangeListener(counter);
            });
        }
        LifeTestSupport.onEdt(() -> {
            assertTrue(events.size() >= 6);
            for (int i = 0; i < events.size(); i++) {
                BoardChangeEvent event = events.get(i);
                assertEquals(GENERATION, event.kind());
                assertEquals(i + 1, event.generation(), "no generation may be skipped or repeated");
                assertEquals(event.generation() <= 3, event.copyAliveCells()[30][30],
                        "generation " + event.generation());
            }
            assertTrue(LifeTestSupport.freezing().regions().isEmpty());
        });
    }

    @Test
    void areaSelectionStopsTheClockAndBlocksGenerationsUntilItEnds() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            events.clear();
            tabs.setSelectedIndex(2);
            LifeTestSupport.select("Go", "Fast");
            button("영역 선택").doClick(0);
            assertEquals(Cursor.CROSSHAIR_CURSOR, universe.getCursor().getType());
            LifeTestSupport.clock.tick();
            LifeTestSupport.select("Go", "Tick (Single Step)");
            assertTrue(events.isEmpty(), "no generation while an area is being selected");
            assertEquals(0, summary().generation());
            assertTrue(alive(5, 5));

            universe.getActionMap().get("cancel-editing").actionPerformed(null);        // Esc
            assertEquals(Cursor.DEFAULT_CURSOR, universe.getCursor().getType());
            LifeTestSupport.clock.tick();
            assertEquals(List.of(GENERATION), kinds());
        });
        // "영역 선택" stopped the Fast clock: nothing may tick on its own afterwards.
        awaitMillis(150);
        LifeTestSupport.onEdt(() -> assertEquals(1, summary().generation()));
    }

    @Test
    void rightClickEndsAreaSelection() throws Exception {
        LifeTestSupport.onEdt(() -> {
            tabs.setSelectedIndex(2);
            button("영역 선택").doClick(0);
            press(4, 4); drag(8, 8);
            mouse(MouseEvent.MOUSE_PRESSED, 6, 6, MouseEvent.BUTTON3, MouseEvent.BUTTON3_DOWN_MASK);
            assertEquals(Cursor.DEFAULT_CURSOR, universe.getCursor().getType());
            assertFalse(button("Freezing 설치").isEnabled());
            assertTrue(LifeTestSupport.freezing().regions().isEmpty());
            assertEquals(0, LifeTestSupport.countAlive(universe.liveCells()), "selection must not draw cells");
        });
    }

    // ------------------------------------- board events: statistics, sound

    @Test
    void frozenCellsNeverAppearInBirthsOrDeathsAndCountAsLiving() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);                                   // lone cell: would die
            LifeTestSupport.click(10, 9); LifeTestSupport.click(10, 10); LifeTestSupport.click(10, 11);
            LifeTestSupport.freezing().install(new Rectangle(5, 5, 1, 1), 4);
            events.clear();
            for (int g = 1; g <= 4; g++) {
                LifeTestSupport.clock.tick();
                BoardChangeEvent event = events.get(g - 1);
                assertEquals(GENERATION, event.kind());
                assertEquals(2, event.copyBirths().size());
                assertEquals(2, event.copyDeaths().size());
                assertFalse(event.copyDeaths().contains(new Point(5, 5)));
                assertTrue(event.copyAliveCells()[5][5]);
                assertEquals(4, summary().livingCells(), "3 blinker cells + 1 frozen cell");
                assertEquals(2, summary().births());
                assertEquals(2, summary().deaths());
                assertEquals("4", value("livingCells").getText());
                assertEquals(new HashSet<>(event.copyBirths()), sound.get(g - 1).births());
                assertEquals(new HashSet<>(event.copyDeaths()), sound.get(g - 1).deaths());
            }
            LifeTestSupport.clock.tick();
            assertTrue(events.get(4).copyDeaths().contains(new Point(5, 5)), "dies once released");
            assertEquals(3, summary().deaths());
            assertEquals(3, summary().livingCells());
        });
    }

    @Test
    void aFullyFrozenBoardStillPublishesGenerationsButNoSoundDeltas() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(10, 9); LifeTestSupport.click(10, 10); LifeTestSupport.click(10, 11);
            LifeTestSupport.freezing().install(new Rectangle(0, 0, SIZE, SIZE), 3);
            events.clear();
            for (int g = 1; g <= 3; g++) LifeTestSupport.clock.tick();
            assertEquals(List.of(GENERATION, GENERATION, GENERATION), kinds());
            assertEquals(3, summary().generation());
            assertTrue(summary().hasGenerationDelta());
            assertEquals(0, summary().births());
            assertEquals(0, summary().deaths());
            assertTrue(sound.isEmpty(), "nothing was born or died");
            LifeTestSupport.clock.tick();
            assertEquals(1, sound.size(), "the blinker turns on the first free generation");
        });
    }

    @Test
    void installAndReleaseDoNotPublishBoardEventsBecauseNoCellChanges() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            boolean[][] before = universe.liveCells();
            events.clear();
            tabs.setSelectedIndex(2);
            installByDragging(4, 4, 8, 8);
            installByDragging(20, 20, 22, 22);
            assertEquals(2, LifeTestSupport.freezing().regions().size());
            list().setSelectedIndex(0);
            button("선택한 Freezing 해제").doClick(0);
            assertEquals(1, LifeTestSupport.freezing().regions().size());
            assertEquals(new Rectangle(20, 20, 3, 3), LifeTestSupport.freezing().regions().get(0).area());
            button("모든 Freezing 해제").doClick(0);
            assertTrue(LifeTestSupport.freezing().regions().isEmpty());
            assertFalse(button("모든 Freezing 해제").isEnabled());
            assertFalse(button("선택한 Freezing 해제").isEnabled());
            assertTrue(events.isEmpty());
            assertArrayEquals(before, universe.liveCells());
            assertEquals(0, summary().generation());
        });
    }

    // ------------------------------------------------- generation colors

    @Test
    void frozenCellsKeepTheirColorWhileOthersCycleAndResumeAfterRelease() throws Exception {
        LifeTestSupport.onEdt(() -> {
            block(0, 0);
            block(20, 20);
            LifeTestSupport.select("Color", "Cycle by Generation");
            LifeTestSupport.freezing().install(new Rectangle(0, 0, 2, 2), 10);
            for (int g = 1; g <= 10; g++) LifeTestSupport.clock.tick();
            assertEquals(RED, cells.colorAt(0, 0), "frozen block did not age");
            assertEquals(RED, cells.colorAt(1, 1));
            assertEquals(BLUE, cells.colorAt(20, 20), "free block is 10 generations old");
            assertEquals(Map.of(RED, 4, BLUE, 4), colorCounts());
            assertTrue(LifeTestSupport.freezing().regions().isEmpty());

            for (int g = 11; g <= 19; g++) LifeTestSupport.clock.tick();
            assertEquals(RED, cells.colorAt(0, 0), "9 free generations: still the first color");
            LifeTestSupport.clock.tick();
            assertEquals(BLUE, cells.colorAt(0, 0), "aging resumed from the age held at install");
            assertEquals(GREEN, cells.colorAt(20, 20));
            assertEquals(Map.of(BLUE, 4, GREEN, 4), colorCounts());

            LifeTestSupport.select("Color", "Single Color");
            assertEquals(Map.of(RED, 8), colorCounts());
        });
    }

    @Test
    void switchingTheColorRuleDuringAFreezingKeepsTheRegionAndItsCountdown() throws Exception {
        LifeTestSupport.onEdt(() -> {
            block(0, 0);
            FreezingRegion region = LifeTestSupport.freezing().install(new Rectangle(0, 0, 2, 2), 6);
            LifeTestSupport.clock.tick();
            LifeTestSupport.select("Color", "Cycle by Generation");
            LifeTestSupport.clock.tick();
            LifeTestSupport.select("Color", "Single Color");
            LifeTestSupport.clock.tick();
            assertTrue(region.isFrozen());
            assertEquals(3, region.remainingGenerations());
            assertEquals(4, summary().livingCells());
        });
    }

    // ------------------------------------------------------- drag brush

    @Test
    void clickingAFrozenCellChangesNothingAndPublishesNothing() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            LifeTestSupport.freezing().install(new Rectangle(5, 5, 2, 2), 50);
            events.clear();
            LifeTestSupport.click(5, 5);                                   // try to erase a frozen live cell
            assertTrue(alive(5, 5));
            LifeTestSupport.click(6, 6);                                   // try to draw on a frozen dead cell
            assertFalse(alive(6, 6));
            assertTrue(events.isEmpty());
            assertTrue(statusLabel().getText().contains("바꿀 수 없습니다"));
            LifeTestSupport.click(7, 7);                                   // just outside
            assertTrue(alive(7, 7));
            assertEquals(List.of(EDIT), kinds());
        });
    }

    @Test
    void draggingThroughAFrozenAreaSkipsItsCellsBothWhenDrawingAndErasing() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(3, 11);                                  // live cell that will be frozen
            LifeTestSupport.freezing().install(new Rectangle(10, 3, 3, 1), 50);    // columns 10-12 of row 3
            events.clear();

            press(8, 3); drag(14, 3); release(14, 3);                      // draw from column 8 to 14
            assertEquals(Set.of(8, 9, 11, 13, 14), liveColumnsOfRow(3));
            assertEquals(List.of(EDIT, EDIT), kinds());
            assertEquals(5, summary().livingCells());

            press(14, 3); drag(8, 3); release(8, 3);                       // erase back
            assertEquals(Set.of(11), liveColumnsOfRow(3), "the frozen live cell survives the eraser");
            assertEquals(1, summary().livingCells());
            assertEquals(0, summary().generation());
        });
    }

    @Test
    void aStrokeThatStartsOnAFrozenCellIsIgnoredEntirely() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.freezing().install(new Rectangle(10, 3, 3, 1), 50);
            events.clear();
            press(11, 3); drag(20, 3); drag(20, 9); release(20, 9);
            assertEquals(0, LifeTestSupport.countAlive(universe.liveCells()));
            assertTrue(events.isEmpty());
        });
    }

    @Test
    void cellsBecomeEditableAgainRightAfterRelease() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.freezing().install(new Rectangle(5, 5, 1, 1), 1);
            LifeTestSupport.click(5, 5);
            assertFalse(alive(5, 5));
            LifeTestSupport.clock.tick();                                  // expires
            events.clear();
            LifeTestSupport.click(5, 5);
            assertTrue(alive(5, 5));
            assertEquals(List.of(EDIT), kinds());
        });
    }

    // -------------------------------------------------- pattern templates

    @Test
    void aTemplateIsNotPlacedOnAFrozenAreaButIsPlacedNextToIt() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.freezing().install(new Rectangle(21, 20, 1, 1), 50);
            chooseBlinker();
            events.clear();
            press(20, 20); release(20, 20);                                // would cover columns 20-22
            press(19, 20); release(19, 20);                                // 19-21
            press(21, 20); release(21, 20);                                // 21-23
            assertEquals(0, LifeTestSupport.countAlive(universe.liveCells()));
            assertTrue(events.isEmpty());
            assertFalse(patternButton("배치 되돌리기").isEnabled());

            press(22, 20); release(22, 20);                                // 22-24: touches, does not overlap
            press(18, 20); release(18, 20);                                // 18-20
            assertEquals(Set.of(18, 19, 20, 22, 23, 24), liveColumnsOfRow(20));
            assertEquals(List.of(TEMPLATE, TEMPLATE), kinds());
        });
    }

    @Test
    void aHeldTemplateStrokeSkipsEveryStampThatWouldTouchTheFrozenArea() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.freezing().install(new Rectangle(21, 18, 1, 5), 50);   // column 21, rows 18-22
            chooseBlinker();
            events.clear();
            press(14, 20); drag(17, 20); drag(20, 20); drag(24, 20); drag(27, 20);
            assertTrue(events.isEmpty(), "a stroke publishes only when it ends");
            release(27, 20);
            Set<Integer> live = liveColumnsOfRow(20);
            assertFalse(live.contains(21), "frozen column stayed empty");
            assertTrue(live.containsAll(Set.of(14, 15, 16, 17, 18, 19, 20)), "left of the area: " + live);
            assertTrue(live.containsAll(Set.of(22, 23, 24, 25, 26, 27, 28, 29)), "right of the area: " + live);
            for (int row = 18; row <= 22; row++) assertFalse(alive(row, 21));
            assertEquals(List.of(TEMPLATE), kinds());

            patternButton("배치 되돌리기").doClick(0);
            assertEquals(0, LifeTestSupport.countAlive(universe.liveCells()));
            assertEquals(1, LifeTestSupport.freezing().regions().size(), "undo does not touch the Freezing");
        });
    }

    @Test
    void startingAnAreaSelectionEndsPatternEditingAndClearsItsUndoHistory() throws Exception {
        LifeTestSupport.onEdt(() -> {
            chooseBlinker();
            press(30, 30); release(30, 30);
            assertTrue(patternButton("배치 되돌리기").isEnabled());
            tabs.setSelectedIndex(2);
            installByDragging(29, 29, 33, 31);                              // freeze the placed blinker
            assertFalse(patternButton("배치 되돌리기").isEnabled(),
                    "an undo must not be able to erase frozen cells");
            tabs.setSelectedIndex(0);
            patternButton("배치 되돌리기").doClick(0);
            universe.getActionMap().get("undo-pattern").actionPerformed(null);          // Ctrl+Z
            assertEquals(Set.of(30, 31, 32), liveColumnsOfRow(30));
            for (int g = 1; g <= 5; g++) LifeTestSupport.clock.tick();
            assertEquals(Set.of(30, 31, 32), liveColumnsOfRow(30), "the frozen blinker does not turn");
        });
    }

    @Test
    void aTemplateWhoseEmptyMarginAloneOverlapsIsRejectedWhole() throws Exception {
        // capture/test-plan.md IT-01. Glider rows: ".O." "..O" "OOO": its upper-left cell is empty.
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.freezing().install(new Rectangle(20, 20, 1, 1), 50);
            chooseTemplate("Glider");
            events.clear();
            press(20, 20); release(20, 20);                                // only the empty corner overlaps
            assertEquals(0, LifeTestSupport.countAlive(universe.liveCells()));
            assertTrue(events.isEmpty());
            assertFalse(patternButton("배치 되돌리기").isEnabled());
            press(21, 20); release(21, 20);                                // one column right: no overlap
            assertEquals(5, LifeTestSupport.countAlive(universe.liveCells()));
            assertEquals(List.of(TEMPLATE), kinds());
        });
    }


    @Test
    void startingAnAreaSelectionDuringAHeldStrokeEndsTheStroke() throws Exception {
        // capture/test-plan.md IT-03.
        LifeTestSupport.onEdt(() -> {
            chooseBlinker();
            events.clear();
            press(20, 20); drag(22, 20);                                   // button still held
            assertTrue(events.isEmpty());
            tabs.setSelectedIndex(2);                                      // the hidden stroke is finished
            assertEquals(List.of(TEMPLATE), kinds());
            button("영역 선택").doClick(0);
            assertFalse(patternButton("배치 되돌리기").isEnabled(), "history is invalidated");
            drag(30, 20); release(30, 20);                                 // the old stroke must not continue
            assertEquals(Set.of(20, 21, 22, 23, 24), liveColumnsOfRow(20));
            press(40, 40); drag(42, 42); release(42, 42);                  // the mouse now selects an area
            assertTrue(button("Freezing 설치").isEnabled());
            assertEquals(5, LifeTestSupport.countAlive(universe.liveCells()));
            assertEquals(List.of(TEMPLATE), kinds());
        });
    }


    @Test
    void capturingAFrozenAreaOnlyReadsIt() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            FreezingRegion region = LifeTestSupport.freezing().install(new Rectangle(4, 4, 3, 3), 9);
            events.clear();
            patternButton("영역 캡처").doClick(0);
            press(3, 3); drag(8, 8); release(8, 8);
            assertTrue(patternButton("선택 영역 저장").isEnabled());
            assertTrue(region.isFrozen());
            assertEquals(9, region.remainingGenerations());
            assertTrue(alive(5, 5));
            assertTrue(events.isEmpty());
        });
    }

    // ------------------------------------------------------------ sidebar

    @Test
    void theSidebarHasPatternStatisticsAndFreezingTabsAtBoardHeight() throws Exception {
        LifeTestSupport.onEdt(() -> {
            assertEquals(3, tabs.getTabCount());
            assertEquals("패턴 캡처", tabs.getTitleAt(0));
            assertEquals("통계", tabs.getTitleAt(1));
            assertEquals("Freezing", tabs.getTitleAt(2));
            assertSame(universe.freezingControls(), tabs.getComponentAt(2));
            JPanel content = Life.createContent(universe, tabs);
            content.setSize(content.getPreferredSize());
            content.doLayout();
            assertEquals(512, universe.getWidth());
            assertEquals(512, tabs.getHeight());
            assertEquals(universe.getWidth(), tabs.getX());
        });
    }

    @Test
    void leavingTheFreezingTabEndsSelectionButKeepsInstalledRegions() throws Exception {
        LifeTestSupport.onEdt(() -> {
            tabs.setSelectedIndex(2);
            installByDragging(4, 4, 8, 8);
            button("영역 선택").doClick(0);
            press(20, 20); drag(25, 25);
            for (int other : new int[] {1, 0}) {
                tabs.setSelectedIndex(2);
                button("영역 선택").doClick(0);
                tabs.setSelectedIndex(other);
                assertEquals(Cursor.DEFAULT_CURSOR, universe.getCursor().getType());
                assertFalse(button("Freezing 설치").isEnabled());
            }
            assertEquals(1, LifeTestSupport.freezing().regions().size());
            events.clear();
            LifeTestSupport.clock.tick();
            assertEquals(List.of(GENERATION), kinds(), "the board runs again after the tab change");
            assertEquals(4, LifeTestSupport.freezing().regions().get(0).remainingGenerations());
        });
    }

    @Test
    void openingTheFreezingTabEndsTemplatePlacementSoTheBrushWorksAgain() throws Exception {
        LifeTestSupport.onEdt(() -> {
            chooseBlinker();
            tabs.setSelectedIndex(2);
            events.clear();
            LifeTestSupport.click(40, 40);
            assertEquals(1, LifeTestSupport.countAlive(universe.liveCells()), "one brush cell, not a template");
            assertEquals(List.of(EDIT), kinds());
        });
    }

    @Test
    void switchingTabsWithARegionInstalledDoesNotStopTheClock() throws Exception {
        CountDownLatch generation = new CountDownLatch(2);
        BoardChangeListener counter = event -> {
            if (event.kind() == GENERATION) generation.countDown();
        };
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.freezing().install(new Rectangle(1, 1, 3, 3), 500);
            universe.addBoardChangeListener(counter);
            LifeTestSupport.clock.startTicking(20);
            tabs.setSelectedIndex(2);
            tabs.setSelectedIndex(1);
            tabs.setSelectedIndex(0);
        });
        try {
            assertTrue(generation.await(5, TimeUnit.SECONDS));
        } finally {
            LifeTestSupport.onEdt(() -> {
                LifeTestSupport.clock.stop();
                universe.removeBoardChangeListener(counter);
            });
        }
    }

    // ------------------------------------------------- selection details

    @Test
    void reverseDragOverlapAndOffBoardSelectionsBehaveAsDocumented() throws Exception {
        LifeTestSupport.onEdt(() -> {
            tabs.setSelectedIndex(2);
            button("영역 선택").doClick(0);
            assertFalse(button("Freezing 설치").isEnabled(), "nothing selected yet");
            press(12, 12); drag(8, 8); release(8, 8);                       // reverse drag
            assertTrue(button("Freezing 설치").isEnabled());
            button("Freezing 설치").doClick(0);
            assertEquals(new Rectangle(8, 8, 5, 5), LifeTestSupport.freezing().regions().get(0).area());
            assertEquals(Cursor.DEFAULT_CURSOR, universe.getCursor().getType());

            button("영역 선택").doClick(0);
            press(9, 9); drag(15, 15); release(15, 15);                     // overlaps the first
            assertFalse(button("Freezing 설치").isEnabled());
            assertTrue(statusLabel().getText().contains("겹칩니다"));
            button("Freezing 설치").doClick(0);
            assertEquals(1, LifeTestSupport.freezing().regions().size());

            press(20, 20);
            mouseAt(MouseEvent.MOUSE_DRAGGED, 900, 700, MouseEvent.NOBUTTON, MouseEvent.BUTTON1_DOWN_MASK);
            mouseAt(MouseEvent.MOUSE_RELEASED, 900, 700, MouseEvent.BUTTON1, 0);
            assertTrue(button("Freezing 설치").isEnabled());
            button("Freezing 설치").doClick(0);
            assertEquals(new Rectangle(20, 20, 44, 44), LifeTestSupport.freezing().regions().get(1).area(),
                    "a drag past the edge is clamped to the board");
            assertEquals(2, list().getModel().getSize());
            assertEquals(0, LifeTestSupport.countAlive(universe.liveCells()));
        });
    }

    @Test
    void theDurationSpinnerSetsHowLongTheRegionLasts() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            tabs.setSelectedIndex(2);
            spinner().setValue(2);
            installByDragging(5, 5, 5, 5);
            assertEquals(2, LifeTestSupport.freezing().regions().get(0).remainingGenerations());
            assertTrue(statusLabel().getText().contains("1 ~ 2세대 고정"), statusLabel().getText());
            LifeTestSupport.clock.tick(); LifeTestSupport.clock.tick();
            assertTrue(alive(5, 5));
            LifeTestSupport.clock.tick();
            assertFalse(alive(5, 5));
        });
    }

    // ------------------------------------------------------ Clear and Load

    @Test
    void clearRemovesEveryRegionResetsTheGenerationAndPublishesOneClear() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            LifeTestSupport.freezing().install(new Rectangle(5, 5, 1, 1), 50);
            LifeTestSupport.freezing().install(new Rectangle(30, 30, 4, 4), 50);
            for (int g = 1; g <= 3; g++) LifeTestSupport.clock.tick();
            tabs.setSelectedIndex(2);
            button("영역 선택").doClick(0);
            events.clear();
            LifeTestSupport.select("Grid", "Clear");
            assertEquals(List.of(CLEAR), kinds());
            assertTrue(LifeTestSupport.freezing().regions().isEmpty());
            assertEquals(0, LifeTestSupport.freezing().generation());
            assertEquals("현재 세대: 0", generationLabel().getText());
            assertEquals(0, list().getModel().getSize());
            assertEquals(Cursor.DEFAULT_CURSOR, universe.getCursor().getType());
            assertEquals(0, summary().livingCells());
            LifeTestSupport.click(5, 5);
            assertTrue(alive(5, 5), "the old frozen cell is an ordinary cell again");
            LifeTestSupport.clock.tick();
            assertFalse(alive(5, 5));
        });
    }

    @Test
    void loadRemovesEveryRegionAndTheLoadedBoardEvolvesNormally() throws Exception {
        Path source = directory.resolve("blinker.life");
        try (ObjectOutputStream output = new ObjectOutputStream(java.nio.file.Files.newOutputStream(source))) {
            output.writeObject(new ArrayList<>(List.of(new Point(9, 10), new Point(10, 10), new Point(11, 10))));
        }
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.freezing().install(new Rectangle(8, 9, 5, 3), 50);     // exactly where the blinker loads
            LifeTestSupport.clock.tick();
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Load"))
                        .thenReturn(source.toFile());
                LifeTestSupport.select("Grid", "Load");
            }
            assertEquals(List.of(LOAD), kinds());
            assertTrue(LifeTestSupport.freezing().regions().isEmpty());
            assertEquals(0, LifeTestSupport.freezing().generation());
            assertEquals(Set.of(9, 10, 11), liveColumnsOfRow(10));
            LifeTestSupport.clock.tick();
            assertEquals(Set.of(10), liveColumnsOfRow(10), "the loaded blinker turns: it is not frozen");
            assertEquals(2, summary().births());
        });
    }

    @Test
    void aCancelledLoadKeepsTheRegions() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            LifeTestSupport.freezing().install(new Rectangle(5, 5, 1, 1), 50);
            LifeTestSupport.clock.tick();
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Load"))
                        .thenThrow(new FileNotFoundException("cancelled"));
                LifeTestSupport.select("Grid", "Load");
            }
            assertTrue(events.isEmpty());
            assertEquals(1, LifeTestSupport.freezing().regions().size());
            assertEquals(1, LifeTestSupport.freezing().generation());
            assertTrue(alive(5, 5));
        });
    }

    @Test
    void storeWritesTheCellsAndLeavesTheRegionsAlone() throws Exception {
        Path target = directory.resolve("saved.life");
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);
            FreezingRegion region = LifeTestSupport.freezing().install(new Rectangle(5, 5, 1, 1), 50);
            LifeTestSupport.clock.tick();
            events.clear();
            try (MockedStatic<Files> chooser = mockStatic(Files.class)) {
                chooser.when(() -> Files.userSelected(".", ".life", "Life File", "Write"))
                        .thenReturn(target.toFile());
                LifeTestSupport.select("Grid", "Store");
            }
            assertTrue(region.isFrozen());
            assertEquals(49, region.remainingGenerations());
            assertTrue(alive(5, 5));
            assertTrue(events.isEmpty());
        });
        try (java.io.ObjectInputStream input = new java.io.ObjectInputStream(
                java.nio.file.Files.newInputStream(target))) {
            assertEquals(List.of(new Point(5, 5)), new ArrayList<>((java.util.Collection<?>) input.readObject()));
        }
    }


    // ------------------------------------------------------------ painting

    @Test
    void theRegionIsPaintedOverTheBoardAndDisappearsWhenReleased() throws Exception {
        LifeTestSupport.onEdt(() -> {
            Color plain = pixel(40, 40);
            assertEquals(plain, pixel(50, 50));
            LifeTestSupport.freezing().install(new Rectangle(38, 38, 5, 5), 7);
            Color tinted = pixel(40, 40);
            assertNotEquals(plain, tinted, "a frozen dead cell is tinted");
            assertTrue(tinted.getBlue() > tinted.getRed(), "the tint is blue: " + tinted);
            assertEquals(plain, pixel(50, 50), "cells outside are not tinted");

            // Everything that draws over the board at once: sound playhead, template preview, region.
            universe.setPlayhead(39);
            chooseBlinker();
            mouse(MouseEvent.MOUSE_MOVED, 39, 40, MouseEvent.NOBUTTON, 0);
            assertDoesNotThrow(() -> pixel(40, 40));
            universe.finishPatternEditing();
            universe.setPlayhead(-1);

            LifeTestSupport.freezing().releaseAll();
            assertEquals(plain, pixel(40, 40));
        });
    }

    @Test
    void freezingRepaintsOnlyItsOwnAreaAndNothingWhenThereIsNoRegion() throws Exception {
        LifeTestSupport.onEdt(() -> {
            block(0, 0);                                                   // still life, single color
            assertTrue(dirtyRegionsDuringTicks(5).isEmpty(),
                    "no region: an unchanged generation requests no repaint at all");

            LifeTestSupport.freezing().install(new Rectangle(30, 30, 4, 4), 3);
            List<Rectangle> dirty = dirtyRegionsDuringTicks(5);
            assertEquals(3, dirty.size(), "one request per frozen generation, none after release: " + dirty);
            Rectangle regionPixels = new Rectangle(30 * 8, 30 * 8, 4 * 8, 4 * 8);
            for (Rectangle request : dirty) {
                assertTrue(request.contains(regionPixels), "covers the region: " + request);
                assertTrue(request.width < 512 && request.height < 512, "never the whole board: " + request);
                assertFalse(request.contains(4, 4), "does not reach the unrelated still life");
            }
        });
    }

    // ------------------------------------------- everything together, random

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {7, 19, 42, 77, 1205})
    void randomRunsWithFreezingBrushAndColorsMatchAnIndependentReference(long seed) throws Exception {
        LifeTestSupport.onEdt(() -> {
            Random random = new Random(seed);
            boolean[][] alive = new boolean[SIZE][SIZE];
            int[][] age = new int[SIZE][SIZE];
            int density = 12 + random.nextInt(25);
            for (int row = 0; row < SIZE; row++) for (int column = 0; column < SIZE; column++)
                if (random.nextInt(100) < density) {
                    alive[row][column] = true;
                    LifeTestSupport.click(row, column);
                }
            LifeTestSupport.select("Color", "Cycle by Generation");
            Color[] cycle = {RED, BLUE, GREEN};
            List<Rectangle> areas = new ArrayList<>();
            List<int[]> remaining = new ArrayList<>();
            events.clear();
            sound.clear();
            int soundEvents = 0;

            for (int g = 1; g <= 120; g++) {
                if (random.nextInt(4) == 0) {                               // install
                    Rectangle area = new Rectangle(random.nextInt(SIZE), random.nextInt(SIZE),
                            1 + random.nextInt(12), 1 + random.nextInt(12))
                            .intersection(new Rectangle(0, 0, SIZE, SIZE));
                    int duration = 1 + random.nextInt(15);
                    if (areas.stream().anyMatch(area::intersects)) {
                        assertThrows(IllegalArgumentException.class,
                                () -> LifeTestSupport.freezing().install(area, duration));
                    } else {
                        LifeTestSupport.freezing().install(area, duration);
                        areas.add(area);
                        remaining.add(new int[] {duration});
                    }
                }
                if (!areas.isEmpty() && random.nextInt(10) == 0) {          // forced release
                    int index = random.nextInt(areas.size());
                    Rectangle target = areas.remove(index);
                    remaining.remove(index);
                    LifeTestSupport.freezing().release(LifeTestSupport.freezing().regions().stream()
                            .filter(r -> r.area().equals(target)).findFirst().orElseThrow());
                }
                for (int i = random.nextInt(4); i > 0; i--) {               // brush clicks
                    int row = random.nextInt(SIZE), column = random.nextInt(SIZE);
                    LifeTestSupport.click(row, column);
                    Point cell = new Point(column, row);
                    if (areas.stream().noneMatch(area -> area.contains(cell))) {
                        alive[row][column] = !alive[row][column];
                        age[row][column] = 0;
                    }
                }
                events.clear();

                boolean[][] next = FreezingModelTest.conway(alive);
                boolean[][] held = new boolean[SIZE][SIZE];
                for (Rectangle area : areas)
                    for (int row = area.y; row < area.y + area.height; row++)
                        for (int column = area.x; column < area.x + area.width; column++) {
                            next[row][column] = alive[row][column];
                            held[row][column] = true;
                        }
                Set<Point> births = new HashSet<>(), deaths = new HashSet<>();
                List<Color> colors = new ArrayList<>();
                Map<Color, Integer> counts = new HashMap<>();
                for (int row = 0; row < SIZE; row++) for (int column = 0; column < SIZE; column++) {
                    if (!held[row][column])
                        age[row][column] = alive[row][column] && next[row][column] ? age[row][column] + 1 : 0;
                    if (next[row][column] && !alive[row][column]) births.add(new Point(column, row));
                    if (!next[row][column] && alive[row][column]) deaths.add(new Point(column, row));
                    if (next[row][column]) {
                        Color color = cycle[(age[row][column] / 10) % cycle.length];
                        colors.add(color);
                        counts.merge(color, 1, Integer::sum);
                    }
                }
                alive = next;
                for (int i = areas.size() - 1; i >= 0; i--)
                    if (--remaining.get(i)[0] == 0) { areas.remove(i); remaining.remove(i); }
                if (!births.isEmpty() || !deaths.isEmpty()) soundEvents++;

                LifeTestSupport.clock.tick();

                String where = "seed " + seed + " generation " + g;
                assertEquals(1, events.size(), where);
                BoardChangeEvent event = events.get(0);
                assertEquals(GENERATION, event.kind(), where);
                assertEquals(g, event.generation(), where);
                assertArrayEquals(alive, event.copyAliveCells(), where);
                assertArrayEquals(alive, universe.liveCells(), where);
                assertEquals(births, new HashSet<>(event.copyBirths()), where);
                assertEquals(deaths, new HashSet<>(event.copyDeaths()), where);
                assertEquals(colors, event.aliveColors(), where);
                assertEquals(colors.size(), summary().livingCells(), where);
                assertEquals(births.size(), summary().births(), where);
                assertEquals(deaths.size(), summary().deaths(), where);
                assertEquals(counts, colorCounts(), where);
                assertEquals(areas.size(), LifeTestSupport.freezing().regions().size(), where);
                assertEquals(g, LifeTestSupport.freezing().generation(), where);
                assertEquals(soundEvents, sound.size(), where);
                if (!births.isEmpty() || !deaths.isEmpty()) {
                    assertEquals(births, sound.get(sound.size() - 1).births(), where);
                    assertEquals(deaths, sound.get(sound.size() - 1).deaths(), where);
                }
            }
        });
    }

    // --------------------------------------------------------------- helpers

    private Statistics.Summary summary() { return summaries.get(summaries.size() - 1); }
    private List<BoardChangeEvent.Kind> kinds() { return events.stream().map(BoardChangeEvent::kind).toList(); }
    private static boolean alive(int row, int column) { return cells.isAliveAt(row, column); }

    private Map<Color, Integer> colorCounts() {
        Map<Color, Integer> counts = new HashMap<>();
        for (Statistics.ColorCount count : summary().colorCounts()) counts.put(count.color(), count.cells());
        return counts;
    }

    private static Set<Integer> liveColumnsOfRow(int row) {
        Set<Integer> columns = new HashSet<>();
        boolean[][] board = universe.liveCells();
        for (int column = 0; column < SIZE; column++) if (board[row][column]) columns.add(column);
        return columns;
    }

    /** 2 x 2 block whose upper-left cell is (row, column). */
    private static void block(int row, int column) {
        LifeTestSupport.click(row, column); LifeTestSupport.click(row, column + 1);
        LifeTestSupport.click(row + 1, column); LifeTestSupport.click(row + 1, column + 1);
    }

    private void installByDragging(int fromColumn, int fromRow, int toColumn, int toRow) {
        button("영역 선택").doClick(0);
        press(fromColumn, fromRow); drag(toColumn, toRow); release(toColumn, toRow);
        assertTrue(button("Freezing 설치").isEnabled());
        button("Freezing 설치").doClick(0);
    }

    private void chooseBlinker() { chooseTemplate("Blinker"); }

    private void chooseTemplate(String name) {
        tabs.setSelectedIndex(0);
        JList<?> templates = find(universe.patternControls(), JList.class, null);
        for (int i = 0; i < templates.getModel().getSize(); i++) {
            if (((PatternTemplate) templates.getModel().getElementAt(i)).name().equals(name)) {
                templates.setSelectedIndex(i);
                patternButton("선택한 템플릿 배치").doClick(0);
                return;
            }
        }
        fail("Built-in " + name + " is missing");
    }

    /** Every repaint request made for the board while the clock ticks. */
    private static List<Rectangle> dirtyRegionsDuringTicks(int ticks) {
        List<Rectangle> requests = new ArrayList<>();
        javax.swing.RepaintManager original = javax.swing.RepaintManager.currentManager(universe);
        javax.swing.RepaintManager.setCurrentManager(new javax.swing.RepaintManager() {
            @Override public void addDirtyRegion(javax.swing.JComponent c, int x, int y, int w, int h) {
                if (c == universe) requests.add(new Rectangle(x, y, w, h));
            }
        });
        try {
            for (int i = 0; i < ticks; i++) LifeTestSupport.clock.tick();
        } finally {
            javax.swing.RepaintManager.setCurrentManager(original);
        }
        return requests;
    }

    private static JButton button(String text) {
        JButton button = find(universe.freezingControls(), JButton.class, text);
        assertNotNull(button, text);
        return button;
    }
    private static JButton patternButton(String text) {
        JButton button = find(universe.patternControls(), JButton.class, text);
        assertNotNull(button, text);
        return button;
    }
    private static JSpinner spinner() { return find(universe.freezingControls(), JSpinner.class, null); }
    private static JList<?> list() { return find(universe.freezingControls(), JList.class, null); }
    private static JLabel generationLabel() { return label("현재 세대"); }
    /** The status line is the last label of the Freezing panel. */
    private static JLabel statusLabel() {
        JLabel last = null;
        for (Component c : universe.freezingControls().getComponents()) if (c instanceof JLabel) last = (JLabel) c;
        return last;
    }
    private static JLabel label(String prefix) {
        for (Component c : universe.freezingControls().getComponents())
            if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().startsWith(prefix))
                return (JLabel) c;
        return fail("no label starting with " + prefix);
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
        mouseAt(id, column * 8 + 4, row * 8 + 4, button, modifiers);
    }
    private static void mouseAt(int id, int x, int y, int button, int modifiers) {
        universe.dispatchEvent(new MouseEvent(universe, id, System.currentTimeMillis(), modifiers,
                x, y, 1, false, button));
    }
    private static void press(int column, int row) {
        mouse(MouseEvent.MOUSE_PRESSED, column, row, MouseEvent.BUTTON1, MouseEvent.BUTTON1_DOWN_MASK);
    }
    private static void drag(int column, int row) {
        mouse(MouseEvent.MOUSE_DRAGGED, column, row, MouseEvent.NOBUTTON, MouseEvent.BUTTON1_DOWN_MASK);
    }
    private static void release(int column, int row) {
        mouse(MouseEvent.MOUSE_RELEASED, column, row, MouseEvent.BUTTON1, 0);
    }

    /** Color at the center of the cell after painting the whole board. */
    private static Color pixel(int column, int row) {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        universe.paint(graphics);
        graphics.dispose();
        return new Color(image.getRGB(column * 8 + 4, row * 8 + 4));
    }

    private static void awaitMillis(int milliseconds) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        LifeTestSupport.onEdt(() -> {
            javax.swing.Timer delay = new javax.swing.Timer(milliseconds, event -> done.countDown());
            delay.setRepeats(false);
            delay.start();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }
}
