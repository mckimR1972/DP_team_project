package com.holub.life;

import java.awt.Dimension;
import java.awt.Insets;
import java.util.Map;
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
            panel.showStatistics(new Statistics.Summary(3_000_000_000L, 120, 2.93, 8, 5, true));
            assertEquals(Map.of("generation", "3000000000", "livingCells", "120",
                    "livingRatio", "2.93%", "births", "8", "deaths", "5"), displayed(panel));
        });
    }

    @ParameterizedTest
    @CsvSource({"0, 0.00%", "100, 100.00%", "12.3456, 12.35%", "0.0244140625, 0.02%"})
    void formatsPercentageToTwoDecimalPlaces(double percentage, String expected) throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(0, 0, percentage, 0, 0, false));
            assertEquals(expected, label(panel, "livingRatio").getText());
        });
    }

    @Test
    void unavailableDeltaReplacesPreviouslyDisplayedCounts() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(1, 3, 0.07, 2, 2, true));
            panel.showStatistics(new Statistics.Summary(1, 4, 0.10, 0, 0, false));
            assertEquals("—", label(panel, "births").getText());
            assertEquals("—", label(panel, "deaths").getText());
            assertEquals("4", label(panel, "livingCells").getText());
        });
    }

    @Test
    void measuredZeroDeltaIsDisplayedAsZero() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(1, 4, 0.10, 0, 0, true));
            assertEquals("0", label(panel, "births").getText());
            assertEquals("0", label(panel, "deaths").getText());
        });
    }

    @Test
    void consecutiveSummariesReplaceAllDisplayedValues() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(10, 120, 2.93, 8, 5, true));
            panel.showStatistics(new Statistics.Summary(11, 0, 0, 0, 120, true));
            assertEquals(Map.of("generation", "11", "livingCells", "0",
                    "livingRatio", "0.00%", "births", "0", "deaths", "120"), displayed(panel));
        });
    }

    @Test
    void rejectsUpdatesOutsideEdt() {
        assertThrows(IllegalStateException.class,
                () -> panel.showStatistics(new Statistics.Summary(0, 0, 0, 0, 0, false)));
    }

    @Test
    void nullSummaryDoesNotChangeTheDisplay() throws Exception {
        LifeTestSupport.onEdt(() -> {
            panel.showStatistics(new Statistics.Summary(1, 3, 0.07, 2, 2, true));
            Map<String, String> before = displayed(panel);
            assertThrows(NullPointerException.class, () -> panel.showStatistics(null));
            assertEquals(before, displayed(panel));
        });
    }
}
