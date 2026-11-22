package com.holub.life;

import java.awt.Dimension;
import java.awt.Insets;
import java.util.Map;
import java.util.List;
import javax.swing.JLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.holub.life.StatisticsPanelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsPanelTest {
    private StatisticsPanel panel;

    @BeforeEach
    void createPanel() throws Exception {
        LifeTestSupport.onEdt(() -> panel = new StatisticsPanel());
    }

    @Test
    void showsPlaceholdersBeforeFirstSummary() throws Exception {
        LifeTestSupport.onEdt(() -> assertEquals(Map.of(
                "generation", "—", "livingCells", "—", "livingRatio", "—",
                "births", "—", "deaths", "—"), displayed(panel)));
    }

    @Test
    void followsTheSharedToolPanelSizeAndHeadingContract() throws Exception {
        LifeTestSupport.onEdt(() -> {
            assertEquals(new Dimension(258, 512), panel.getPreferredSize());
            assertEquals(new Insets(14, 14, 14, 14), panel.getInsets());
            JLabel title = (JLabel) named(panel, "statistics.title");
            assertEquals(16f, title.getFont().getSize2D());
            assertTrue(title.getFont().isBold());
        });
    }

    @Test
    void displaysSummaryWithoutTruncatingLongGeneration() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(3_000_000_000L, 120, 2.93, 8, 5, true, List.of(new Statistics.ColorCount(java.awt.Color.RED, 120, 100.0))));
            assertEquals(Map.of("generation", "3000000000", "livingCells", "120",
                    "livingRatio", "2.93%", "births", "8", "deaths", "5"), displayed(panel));
        });
    }

    @ParameterizedTest
    @CsvSource({"0, 0.00%", "100, 100.00%", "12.3456, 12.35%", "0.0244140625, 0.02%"})
    void formatsPercentageToTwoDecimalPlaces(double percentage, String expected) throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(0, 0, percentage, 0, 0, false, List.of()));
            assertEquals(expected, label(panel, "livingRatio").getText());
        });
    }

    @Test
    void unavailableDeltaReplacesPreviouslyDisplayedCounts() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(1, 3, 0.07, 2, 2, true, List.of(new Statistics.ColorCount(java.awt.Color.RED, 3, 100.0))));
            panel.showStatistics(new Statistics.Summary(1, 4, 0.10, 0, 0, false, List.of(new Statistics.ColorCount(java.awt.Color.RED, 4, 100.0))));
            assertEquals("—", label(panel, "births").getText());
            assertEquals("—", label(panel, "deaths").getText());
            assertEquals("4", label(panel, "livingCells").getText());
        });
    }

    @Test
    void measuredZeroDeltaIsDisplayedAsZero() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(1, 4, 0.10, 0, 0, true, List.of(new Statistics.ColorCount(java.awt.Color.RED, 4, 100.0))));
            assertEquals("0", label(panel, "births").getText());
            assertEquals("0", label(panel, "deaths").getText());
        });
    }

    @Test
    void consecutiveSummariesReplaceAllDisplayedValues() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(10, 120, 2.93, 8, 5, true, List.of(new Statistics.ColorCount(java.awt.Color.RED, 120, 100.0))));
            panel.showStatistics(new Statistics.Summary(11, 0, 0, 0, 120, true, List.of()));
            assertEquals(Map.of("generation", "11", "livingCells", "0",
                    "livingRatio", "0.00%", "births", "0", "deaths", "120"), displayed(panel));
        });
    }

    @Test
    void keepsRedBlueGreenOrderAsColorsAppearAndCountsChange() throws Exception {
        LifeTestSupport.onEdt(() -> {
            java.awt.Color red = java.awt.Color.RED;
            java.awt.Color blue = new java.awt.Color(0x0072B2);
            java.awt.Color green = new java.awt.Color(0x009E73);
            java.awt.Container rows = (java.awt.Container) named(panel, "statistics.colors");
            panel.showStatistics(new Statistics.Summary(0, 4, 0.10, 0, 0, false,
                    List.of(new Statistics.ColorCount(red, 4, 100))));
            assertSame(label(panel, "color.#FF0000").getParent(), rows.getComponent(0));

            // The model's list order is not the view's display order.
            panel.showStatistics(new Statistics.Summary(10, 4, 0.10, 0, 0, true, List.of(
                    new Statistics.ColorCount(blue, 1, 25),
                    new Statistics.ColorCount(red, 3, 75))));
            assertSame(label(panel, "color.#FF0000").getParent(), rows.getComponent(0));
            assertTrue(rows.getComponentZOrder(label(panel, "color.#0072B2").getParent())
                    > rows.getComponentZOrder(label(panel, "color.#FF0000").getParent()));
            assertEquals("3개 · 75.00%", label(panel, "color.#FF0000").getText());
            assertEquals("1개 · 25.00%", label(panel, "color.#0072B2").getText());

            panel.showStatistics(new Statistics.Summary(20, 4, 0.10, 0, 0, true, List.of(
                    new Statistics.ColorCount(green, 2, 50),
                    new Statistics.ColorCount(blue, 1, 25),
                    new Statistics.ColorCount(red, 1, 25))));
            assertSame(label(panel, "color.#FF0000").getParent(), rows.getComponent(0));
            assertTrue(rows.getComponentZOrder(label(panel, "color.#0072B2").getParent())
                    > rows.getComponentZOrder(label(panel, "color.#FF0000").getParent()));
            assertTrue(rows.getComponentZOrder(label(panel, "color.#009E73").getParent())
                    > rows.getComponentZOrder(label(panel, "color.#0072B2").getParent()));
            assertEquals("1개 · 25.00%", label(panel, "color.#FF0000").getText());
            assertEquals("2개 · 50.00%", label(panel, "color.#009E73").getText());
            assertEquals(blue, label(panel, "colorSwatch.#0072B2").getBackground());
            assertEquals("비율 기준: 전체 생존 셀", label(panel, "colorBasis").getText());
        });
    }

    @Test
    void replacingColorsRemovesOldRowsAndEmptyBoardShowsAnExplanation() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(1, 2, 0.05, 0, 0, false, List.of(
                    new Statistics.ColorCount(java.awt.Color.RED, 1, 50),
                    new Statistics.ColorCount(java.awt.Color.BLUE, 1, 50))));
            panel.showStatistics(new Statistics.Summary(1, 1, 0.02, 0, 0, false,
                    List.of(new Statistics.ColorCount(java.awt.Color.RED, 1, 100))));
            assertNull(named(panel, "statistics.color.#0000FF"));
            assertEquals("1개 · 100.00%", label(panel, "color.#FF0000").getText());
            panel.showStatistics(new Statistics.Summary(0, 0, 0, 0, 0, false, List.of()));
            assertNull(named(panel, "statistics.color.#FF0000"));
            assertEquals("살아 있는 셀이 없습니다.", label(panel, "colors.empty").getText());
        });
    }

    @Test
    void rejectsUpdatesOutsideEdt() {
        assertThrows(IllegalStateException.class,
                () -> panel.showStatistics(new Statistics.Summary(0, 0, 0, 0, 0, false, List.of())));
    }

    @Test
    void nullSummaryDoesNotChangeTheDisplay() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(1, 3, 0.07, 2, 2, true, List.of(new Statistics.ColorCount(java.awt.Color.RED, 3, 100.0))));
            Map<String, String> before = displayed(panel);
            assertThrows(NullPointerException.class, () -> panel.showStatistics(null));
            assertEquals(before, displayed(panel));
        });
    }
}
