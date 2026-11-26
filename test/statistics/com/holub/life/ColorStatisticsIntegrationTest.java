package com.holub.life;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static com.holub.life.StatisticsPanelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Clock, Universe, Statistics and panel. No synthetic board events. */
class ColorStatisticsIntegrationTest {
    private static final Color BLUE = new Color(0x0072B2);
    private static final Color GREEN = new Color(0x009E73);
    private Statistics statistics;
    private final List<Statistics.Summary> summaries = new ArrayList<>();
    private StatisticsPanel panel;
    private final List<BoardChangeEvent> events = new ArrayList<>();
    private final BoardChangeListener recorder = events::add;

    @BeforeAll static void initialize() throws Exception { LifeTestSupport.initialize(); }
    @BeforeEach void connect() throws Exception {
        LifeTestSupport.resetBoard();
        LifeTestSupport.onEdt(() -> {
            panel = recordingPanel(summaries::add);
            statistics = Life.connectStatistics(LifeTestSupport.universe, panel);
            LifeTestSupport.universe.addBoardChangeListener(recorder);
        });
    }
    @AfterEach void detach() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.universe.removeBoardChangeListener(statistics);
            LifeTestSupport.universe.removeBoardChangeListener(recorder);
        });
        LifeTestSupport.resetBoard();
    }

    @Test void stablePopulationStillUpdatesColorsAtEachTenGenerationBoundary() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Color", "Cycle by Generation");
            block(10, 20);
            for (int generation = 0; generation <= 30; generation++) {
                Color expected = generation < 10 || generation == 30 ? Color.RED : generation < 20 ? BLUE : GREEN;
                assertEquals(generation, summary().generation());
                assertEquals(List.of(new Statistics.ColorCount(expected, 4, 100)), summary().colorCounts());
                assertEquals(List.of(expected, expected, expected, expected), last().aliveColors());
                if (generation < 30) LifeTestSupport.clock.tick();
            }
            assertEquals("4개 · 100.00%", label(panel, "color.#FF0000").getText());
        });
    }

    @Test void cellsWithDifferentAgesProduceThreeIndependentCounts() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Color", "Cycle by Generation");
            block(2, 3); tick(10);
            block(20, 21); tick(10);
            block(40, 41);
            assertEquals(20, summary().generation());
            assertEquals(12, summary().livingCells());
            for (Color color : List.of(Color.RED, BLUE, GREEN)) {
                Statistics.ColorCount count = summary().colorCounts().stream()
                        .filter(item -> item.color().equals(color)).findFirst().orElseThrow();
                assertEquals(4, count.cells()); assertEquals(100.0 / 3, count.ratio(), 0.000001);
            }
            assertEquals("4개 · 33.33%", label(panel, "color.#FF0000").getText());
            assertEquals("4개 · 33.33%", label(panel, "color.#0072B2").getText());
            assertEquals("4개 · 33.33%", label(panel, "color.#009E73").getText());
        });
    }

    @Test void switchingPolicyWhilePausedPublishesOnceWithoutAdvancingOrLosingDelta() throws Exception {
        LifeTestSupport.onEdt(() -> {
            block(10, 20); tick(10);
            events.clear();
            LifeTestSupport.select("Color", "Cycle by Generation");
            assertEquals(1, events.size()); assertEquals(COLOR_POLICY_CHANGED, last().kind());
            assertTrue(last().copyBirths().isEmpty()); assertTrue(last().copyDeaths().isEmpty());
            assertEquals(10, summary().generation());
            assertEquals(List.of(new Statistics.ColorCount(BLUE, 4, 100)), summary().colorCounts());
            assertEquals("0", label(panel, "births").getText());
            LifeTestSupport.select("Color", "Single Color");
            assertEquals(2, events.size()); assertEquals(10, summary().generation());
            assertEquals(List.of(new Statistics.ColorCount(Color.RED, 4, 100)), summary().colorCounts());
            assertNull(named(panel, "statistics.color.#0072B2"));
            LifeTestSupport.select("Color", "Cycle by Generation");
            assertEquals(List.of(new Statistics.ColorCount(BLUE, 4, 100)), summary().colorCounts());
            assertEquals(10, summary().generation()); // querying and switching never resets age
        });
    }

    @Test void newbornsAndSurvivorsAreCountedAfterTransitionAndClickRemovesOldColor() throws Exception {
        LifeTestSupport.onEdt(() -> {
            Resident.setColorPolicy(new CyclingColorPolicy(1, Color.RED, BLUE));
            LifeTestSupport.click(10, 9); LifeTestSupport.click(10, 10); LifeTestSupport.click(10, 11);
            tick(1);
            assertEquals(2, summary().births()); assertEquals(2, summary().deaths());
            assertEquals("2개 · 66.67%", label(panel, "color.#FF0000").getText());
            assertEquals("1개 · 33.33%", label(panel, "color.#0072B2").getText());
            LifeTestSupport.click(10, 10);
            assertEquals(List.of(new Statistics.ColorCount(Color.RED, 2, 100)), summary().colorCounts());
            assertEquals("—", label(panel, "births").getText());
            assertNull(named(panel, "statistics.color.#0072B2"));
            LifeTestSupport.select("Grid", "Clear");
            assertTrue(summary().colorCounts().isEmpty());
            assertNotNull(named(panel, "statistics.colors.empty"));
        });
    }

    @Test void subscribingToAnExistingBlueBoardReceivesColorsInInitialEvent() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Color", "Cycle by Generation");
            block(10, 20); tick(10);
            List<BoardChangeEvent> initial = new ArrayList<>();
            BoardChangeListener observer = initial::add;
            LifeTestSupport.universe.addBoardChangeListener(observer);
            LifeTestSupport.universe.removeBoardChangeListener(observer);
            assertEquals(1, initial.size()); assertEquals(INITIAL, initial.get(0).kind());
            assertEquals(10, initial.get(0).generation());
            assertEquals(List.of(BLUE, BLUE, BLUE, BLUE), initial.get(0).aliveColors());
        });
    }

    private static void block(int row, int col) {
        LifeTestSupport.click(row, col); LifeTestSupport.click(row, col + 1);
        LifeTestSupport.click(row + 1, col); LifeTestSupport.click(row + 1, col + 1);
    }
    private static void tick(int count) { for (int i = 0; i < count; i++) LifeTestSupport.clock.tick(); }
    private BoardChangeEvent last() { return events.get(events.size() - 1); }
    private Statistics.Summary summary() { return summaries.get(summaries.size() - 1); }
}
