package com.holub.life;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Swing input and display for Freezing. Same layout and handling as PatternTools; the rules live in FreezingManager. */
final class FreezingTools {
    private static final Color FILL = new Color(70, 170, 255, 70);   // translucent sky blue
    private static final Color EDGE = new Color(20, 110, 210);       // blue border + badge
    private static final Color ICE = new Color(190, 235, 255);
    private static final Stroke DASH = new BasicStroke(2f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10f, new float[] { 6f, 4f }, 0f);

    private final JPanel board;
    private final FreezingManager manager;
    private final Runnable beforeSelect;
    private final JPanel controls = new JPanel();
    private final JLabel generation = new JLabel();
    private final JSpinner duration = new JSpinner(new SpinnerNumberModel(5, 1, 999, 1));
    private final JButton install = new JButton("Freezing 설치");
    private final JButton release = new JButton("선택한 Freezing 해제");
    private final JButton releaseAll = new JButton("모든 Freezing 해제");
    private final DefaultListModel<FreezingRegion> entries = new DefaultListModel<>();
    private final JList<FreezingRegion> list = new JList<>(entries);
    private final JLabel status = new JLabel();
    private boolean selecting;
    private Point anchor;
    private Rectangle selection;

    /** @param beforeSelect run before area selection starts (ends the pattern editing mode) */
    FreezingTools(JPanel board, FreezingManager manager, Runnable beforeSelect) {
        this.board = board;
        this.manager = manager;
        this.beforeSelect = beforeSelect;
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(new EmptyBorder(14, 14, 14, 14));
        controls.setPreferredSize(new Dimension(258, 512));
        JLabel title = new JLabel("Freezing");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title);
        gap(4);
        add(generation);
        gap(10);
        JPanel durationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        durationRow.add(new JLabel("지속 세대  "));
        ((JSpinner.DefaultEditor) duration.getEditor()).getTextField().setColumns(4);
        durationRow.add(duration);
        durationRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        add(durationRow);
        gap(8);
        JButton select = new JButton("영역 선택");
        select.addActionListener(e -> beginSelection());
        add(select);
        gap(6);
        install.addActionListener(e -> installSelection());
        install.setEnabled(false);
        add(install);
        gap(14);
        add(new JLabel("활성 Freezing"));
        gap(4);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);
        list.addListSelectionListener(e -> release.setEnabled(list.getSelectedValue() != null));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(230, 120));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        add(scroll);
        gap(6);
        release.addActionListener(e -> {
            FreezingRegion region = list.getSelectedValue();
            if (region == null) return;
            manager.release(region);
            message("Freezing을 해제했습니다. 다음 세대부터 정상 규칙을 적용합니다.");
        });
        release.setEnabled(false);
        add(release);
        gap(6);
        releaseAll.addActionListener(e -> {
            manager.releaseAll();
            message("모든 Freezing을 해제했습니다.");
        });
        add(releaseAll);
        gap(6);
        JButton cancel = new JButton("선택 끝내기 (Esc)");
        cancel.addActionListener(e -> cancel());
        add(cancel);
        gap(12);
        status.setVerticalAlignment(SwingConstants.TOP);
        status.setPreferredSize(new Dimension(230, 64));
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        add(status);
        message("‘영역 선택’ 후 보드를 드래그하고 ‘Freezing 설치’를 누르세요.");

        manager.addListener(this::refresh);
        refresh();

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!selecting) return;
                if (SwingUtilities.isRightMouseButton(e)) { cancel(); return; }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                anchor = cellAt(e.getPoint(), false);
                if (anchor != null) selectThrough(anchor);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (selecting && anchor != null) selectThrough(cellAt(e.getPoint(), true));
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (selecting && anchor != null && SwingUtilities.isLeftMouseButton(e)) {
                    selectThrough(cellAt(e.getPoint(), true));
                    anchor = null;
                }
            }
        };
        board.addMouseListener(mouse);
        board.addMouseMotionListener(mouse);
    }

    JPanel controls() { return controls; }
    boolean isEditing() { return selecting; }
    FreezingManager manager() { return manager; }

    void beginSelection() {
        beforeSelect.run();
        Clock.instance().stop();
        selecting = true;
        selection = null;
        anchor = null;
        install.setEnabled(false);
        board.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        message("보드에서 드래그해 Freezing할 영역을 선택하세요.");
        board.repaint();
    }

    void cancel() {
        if (!selecting) return;
        selecting = false;
        selection = null;
        anchor = null;
        install.setEnabled(false);
        board.setCursor(Cursor.getDefaultCursor());
        message("선택을 끝냈습니다. Go 메뉴에서 시뮬레이션을 시작하세요.");
        board.repaint();
    }

    /** Grid > Clear / Load: end selection and remove every region. */
    void reset() {
        cancel();
        manager.reset();
        message("초기화: Freezing 정보와 세대 수를 비웠습니다.");
    }

    void blockedEdit() { message("Freezing 영역 안의 셀은 해제 전까지 바꿀 수 없습니다."); }

    private void installSelection() {
        if (selection == null) return;
        int generations = (Integer) duration.getValue();
        try {
            FreezingRegion region = manager.install(selection, generations);
            selecting = false;
            selection = null;
            install.setEnabled(false);
            board.setCursor(Cursor.getDefaultCursor());
            list.setSelectedValue(region, true);
            message(region.area().width + " x " + region.area().height + "칸을 " + generations
                    + "세대 동안 Freezing합니다. (" + (manager.generation() + 1) + " ~ "
                    + (manager.generation() + generations) + "세대 고정)");
        } catch (IllegalArgumentException e) {
            message(e.getMessage());
        }
        board.repaint();
    }

    private void refresh() {
        FreezingRegion selected = list.getSelectedValue();
        entries.clear();
        for (FreezingRegion r : manager.regions()) entries.addElement(r);
        if (selected != null && entries.contains(selected)) list.setSelectedValue(selected, false);
        release.setEnabled(list.getSelectedValue() != null);
        releaseAll.setEnabled(!entries.isEmpty());
        generation.setText("현재 세대: " + manager.generation());
        board.repaint();
    }

    private void add(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (component instanceof JButton) component.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        controls.add(component);
    }
    private void gap(int size) { controls.add(Box.createVerticalStrut(size)); }
    private void message(String text) {
        status.setText("<html>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
    }
    private Point cellAt(Point point, boolean clamp) {
        int unit = board.getWidth() / manager.size();
        if (unit <= 0) return null;
        int extent = unit * manager.size();
        if (!clamp && (point.x < 0 || point.y < 0 || point.x >= extent || point.y >= extent)) return null;
        return new Point(Math.max(0, Math.min(manager.size() - 1, point.x / unit)),
                Math.max(0, Math.min(manager.size() - 1, point.y / unit)));
    }
    private void selectThrough(Point end) {
        if (end == null || anchor == null) return;
        selection = new Rectangle(Math.min(anchor.x, end.x), Math.min(anchor.y, end.y),
                Math.abs(end.x - anchor.x) + 1, Math.abs(end.y - anchor.y) + 1);
        boolean overlaps = manager.overlapsFrozen(selection);
        install.setEnabled(!overlaps);
        message(overlaps ? "이미 Freezing된 영역과 겹칩니다. 다른 곳을 선택하세요."
                : selection.width + " x " + selection.height + "칸 선택. ‘Freezing 설치’를 누르세요.");
        board.repaint();
    }

    /** Active regions: translucent sky blue + dashed blue border + ice marks on frozen live cells + remaining generations. */
    void paintOverlay(Graphics2D original) {
        int unit = board.getWidth() / manager.size();
        if (unit <= 0) return;
        Graphics2D g = (Graphics2D) original.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (FreezingRegion region : manager.regions()) {
            if (!region.isFrozen()) continue;
            Rectangle a = region.area();
            g.setColor(FILL);
            g.fillRect(a.x * unit, a.y * unit, a.width * unit, a.height * unit);
            g.setColor(ICE);
            for (int y = a.y; y < a.y + a.height; y++)
                for (int x = a.x; x < a.x + a.width; x++)
                    if (region.wasAlive(x, y)) g.drawRect(x * unit + 2, y * unit + 2, unit - 4, unit - 4);
            g.setColor(EDGE);
            g.setStroke(DASH);
            g.drawRect(a.x * unit + 1, a.y * unit + 1, a.width * unit - 2, a.height * unit - 2);
            badge(g, "❄ " + region.remainingGenerations(), a, unit);
        }
        if (selecting && selection != null) {
            boolean overlaps = manager.overlapsFrozen(selection);
            Color c = overlaps ? new Color(195, 45, 45) : new Color(90, 90, 90);   // gray: distinct from installed blue
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
            g.fillRect(selection.x * unit, selection.y * unit, selection.width * unit, selection.height * unit);
            g.setColor(c);
            g.setStroke(DASH);
            g.drawRect(selection.x * unit + 1, selection.y * unit + 1,
                    selection.width * unit - 2, selection.height * unit - 2);
        }
        g.dispose();
    }

    private void badge(Graphics2D g, String text, Rectangle a, int unit) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
        if (g.getFont().canDisplayUpTo(text) != -1) text = text.replace("❄ ", "F ");
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 6, h = fm.getHeight();
        int x = Math.min(a.x * unit, board.getWidth() - w);
        int y = a.y * unit - h - 1;
        if (y < 0) y = (a.y + a.height) * unit + 1;          // no room above: put it below
        if (y + h > board.getHeight()) y = a.y * unit + 2;  // no room below either: inside
        g.setColor(EDGE);
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 3, y + fm.getAscent());
    }
}
