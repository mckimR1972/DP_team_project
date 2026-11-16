package com.holub.life;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Displays completed statistics; it neither reads nor changes the game board. */
public final class StatisticsPanel extends JPanel {
    private static final String UNAVAILABLE = "—";

    private final JLabel generation = valueLabel("generation");
    private final JLabel livingCells = valueLabel("livingCells");
    private final JLabel livingRatio = valueLabel("livingRatio");
    private final JLabel births = valueLabel("births");
    private final JLabel deaths = valueLabel("deaths");

    /** Construct and update this view on the Swing EDT. */
    public StatisticsPanel() {
        requireEventDispatchThread();
        setName("statistics.panel");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setPreferredSize(new Dimension(258, 512));

        JLabel title = new JLabel("게임 통계");
        title.setName("statistics.title");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        addControl(title);
        gap(12);
        addRow("현재 세대", generation);
        gap(6);
        addRow("살아 있는 셀", livingCells);
        gap(6);
        addRow("생존 비율", livingRatio);
        gap(14);
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        addControl(separator);
        gap(14);
        addRow("이번 세대 탄생", births);
        gap(6);
        addRow("이번 세대 사망", deaths);

        JLabel explanation = new JLabel("<html>탄생·사망은 세대 진행에 따른"
                + "<br>변화량입니다.<br>비교할 수 없으면 —로 표시합니다.</html>");
        explanation.setName("statistics.explanation");
        explanation.setFont(explanation.getFont().deriveFont(12f));
        gap(14);
        addControl(explanation);
        add(Box.createVerticalGlue());
    }

    /** Replace every displayed value, including unavailable generation deltas. */
    public void showStatistics(Statistics.Summary summary) {
        requireEventDispatchThread();
        Objects.requireNonNull(summary, "summary");
        generation.setText(Long.toString(summary.generation()));
        livingCells.setText(Integer.toString(summary.livingCells()));
        livingRatio.setText(formatPercentage(summary.livingRatio()));
        births.setText(summary.hasGenerationDelta()
                ? Integer.toString(summary.births()) : UNAVAILABLE);
        deaths.setText(summary.hasGenerationDelta()
                ? Integer.toString(summary.deaths()) : UNAVAILABLE);
    }

    private static String formatPercentage(double value) {
        // Summary already contains a percentage, not a fraction.
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static JLabel valueLabel(String name) {
        JLabel label = new JLabel(UNAVAILABLE, SwingConstants.RIGHT);
        label.setName("statistics." + name);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        return label;
    }

    private void addRow(String caption, JLabel value) {
        JLabel label = new JLabel(caption);
        label.setLabelFor(value);
        value.getAccessibleContext().setAccessibleName(caption);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.add(label, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        row.setPreferredSize(new Dimension(230, 30));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        addControl(row);
    }

    private void addControl(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(component);
    }

    private void gap(int height) { add(Box.createVerticalStrut(height)); }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Statistics view updates must run on the Swing EDT");
        }
    }
}
