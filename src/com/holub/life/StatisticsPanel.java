package com.holub.life;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Statistics가 계산한 결과를 화면에 표시한다. 게임판 조회나 상태 변경은 수행하지 않는다. */
public final class StatisticsPanel extends JPanel {
    private static final String UNAVAILABLE = "—";
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 6;
    private static final int TITLE_GAP = 8;
    private static final int SECTION_GAP = 8;
    private static final float TITLE_FONT_SIZE = 16f;
    private static final float LABEL_FONT_SIZE = 13f;
    private static final float VALUE_FONT_SIZE = 14f;
    private static final float NOTE_FONT_SIZE = 12f;

    private final JLabel generation = valueLabel("generation");
    private final JLabel livingCells = valueLabel("livingCells");
    private final JLabel livingRatio = valueLabel("livingRatio");
    private final JLabel births = valueLabel("births");
    private final JLabel deaths = valueLabel("deaths");
    private final JPanel colorCounts = new JPanel();

    /** 패널 생성과 화면 갱신은 Swing 이벤트 처리 스레드(EDT)에서 수행해야 한다. */
    public StatisticsPanel() {
        requireEventDispatchThread();
        setName("statistics.panel");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setPreferredSize(new Dimension(258, 512));

        JLabel title = titleLabel("게임 통계");
        title.setName("statistics.title");
        addControl(title);
        gap(TITLE_GAP);
        addRow("현재 세대", generation);
        gap(ROW_GAP);
        addRow("살아 있는 셀", livingCells);
        gap(ROW_GAP);
        addRow("생존 비율", livingRatio);
        addSectionDivider();
        addRow("이번 세대 탄생", births);
        gap(ROW_GAP);
        addRow("이번 세대 사망", deaths);

        JLabel explanation = new JLabel("<html>세대 진행에 따른 변화량입니다."
                + "<br>비교할 수 없으면 —로 표시합니다.</html>");
        explanation.setName("statistics.explanation");
        explanation.setFont(explanation.getFont().deriveFont(Font.PLAIN, NOTE_FONT_SIZE));
        gap(ROW_GAP);
        addControl(explanation);
        addSectionDivider();
        JLabel colorsTitle = titleLabel("색상별 생존 셀");
        addControl(colorsTitle);
        gap(ROW_GAP);
        JLabel colorBasis = new JLabel("비율 기준: 전체 생존 셀");
        colorBasis.setName("statistics.colorBasis");
        colorBasis.setFont(colorBasis.getFont().deriveFont(Font.PLAIN, NOTE_FONT_SIZE));
        addControl(colorBasis);
        gap(ROW_GAP);
        colorCounts.setName("statistics.colors");
        colorCounts.setLayout(new BoxLayout(colorCounts, BoxLayout.Y_AXIS));
        colorCounts.add(new JLabel(UNAVAILABLE));
        JScrollPane colorsScroll = new JScrollPane(colorCounts,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        colorsScroll.setBorder(BorderFactory.createEmptyBorder());
        int threeRowsHeight = 3 * ROW_HEIGHT + 2 * ROW_GAP;
        colorsScroll.setMinimumSize(new Dimension(0, threeRowsHeight));
        colorsScroll.setPreferredSize(new Dimension(230, threeRowsHeight));
        colorsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, threeRowsHeight));
        colorsScroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT + ROW_GAP);
        addControl(colorsScroll);
        add(Box.createVerticalGlue());
    }

    /**
     * 전달받은 통계 결과로 모든 표시 값을 갱신한다.
     * 세대 변화량이 유효하지 않으면 탄생·사망 표시를 "—"로 바꾼다.
     */
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
        showColors(summary);
    }

    private void showColors(Statistics.Summary summary) {
        colorCounts.removeAll();
        if (summary.colorCounts().isEmpty()) {
            JLabel empty = new JLabel("살아 있는 셀이 없습니다.");
            empty.setName("statistics.colors.empty");
            empty.setFont(empty.getFont().deriveFont(Font.PLAIN, NOTE_FONT_SIZE));
            colorCounts.add(empty);
        }
        // 빨강 → 파랑 → 초록 순서를 유지한다.
        // 그 밖의 색상은 색상값순으로 뒤에 표시한다.
        for (Statistics.ColorCount count : summary.colorCounts().stream()
                .sorted(Comparator.comparingInt((Statistics.ColorCount item) -> colorOrder(item.color()))
                        .thenComparingInt(item -> item.color().getRGB()))
                .toList()) {
            Color color = count.color();
            String hex = String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xffffff);
            JPanel caption = new JPanel(new BorderLayout(ROW_GAP, 0));
            JLabel swatch = new JLabel();
            swatch.setOpaque(true);
            swatch.setBackground(color);
            swatch.setPreferredSize(new Dimension(12, 12));
            swatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            swatch.setName("statistics.colorSwatch." + hex);
            JPanel chip = new JPanel(new java.awt.GridBagLayout());
            chip.add(swatch);
            caption.add(chip, BorderLayout.WEST);
            JLabel name = bodyLabel(colorName(color, hex));
            name.setToolTipText(hex);
            caption.add(name, BorderLayout.CENTER);
            JLabel value = valueLabel("color." + hex);
            value.setText(count.cells() + "개 · " + formatPercentage(count.ratio()));
            value.getAccessibleContext().setAccessibleName(colorName(color, hex) + " 생존 셀 수와 비율");
            if (colorCounts.getComponentCount() > 0) colorCounts.add(Box.createVerticalStrut(ROW_GAP));
            colorCounts.add(statisticRow(caption, value));
        }
        colorCounts.revalidate();
        colorCounts.repaint();
    }

    private static int colorOrder(Color color) {
        return switch (color.getRGB()) {
            case 0xffff0000 -> 0;
            case 0xff0072b2 -> 1;
            case 0xff009e73 -> 2;
            default -> 3;
        };
    }

    private static String colorName(Color color, String hex) {
        return switch (color.getRGB()) {
            case 0xffff0000 -> "빨강";
            case 0xff0072b2 -> "파랑";
            case 0xff009e73 -> "초록";
            default -> hex;
        };
    }

    private static String formatPercentage(double value) {
        // Summary의 비율은 이미 백분율이므로 100을 곱하지 않고 소수 둘째 자리까지 표시한다.
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static JLabel valueLabel(String name) {
        JLabel label = new JLabel(UNAVAILABLE, SwingConstants.RIGHT);
        label.setName("statistics." + name);
        label.setFont(label.getFont().deriveFont(Font.BOLD, VALUE_FONT_SIZE));
        return label;
    }

    private static JLabel titleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, TITLE_FONT_SIZE));
        return label;
    }

    private static JLabel bodyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, LABEL_FONT_SIZE));
        return label;
    }

    private void addRow(String caption, JLabel value) {
        JLabel label = bodyLabel(caption);
        label.setLabelFor(value);
        value.getAccessibleContext().setAccessibleName(caption);
        addControl(statisticRow(label, value));
    }

    private static JPanel statisticRow(JComponent caption, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(ROW_GAP, 0));
        row.add(caption, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        // 최소·선호·최대 높이를 같게 지정하여 모든 통계 행의 높이를 일정하게 유지한다.
        row.setMinimumSize(new Dimension(0, ROW_HEIGHT));
        row.setPreferredSize(new Dimension(230, ROW_HEIGHT));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        return row;
    }

    private void addSectionDivider() {
        gap(SECTION_GAP);
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        addControl(separator);
        gap(SECTION_GAP);
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
