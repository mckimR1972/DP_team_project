package com.holub.life;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Paths;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Swing input/feedback only; cell access and file storage live in separate classes. */
final class PatternTools {
    private enum Mode { DRAW, CAPTURE, PLACE }
    private final JPanel board;
    private final BoardEditor editor;
    private final PatternLibrary library = new PatternLibrary(Paths.get(System.getProperty("life.templates", "templates")));
    private final JPanel controls = new JPanel();
    private final DefaultListModel<PatternTemplate> entries = new DefaultListModel<>();
    private final JList<PatternTemplate> list = new JList<>(entries);
    private final JLabel status = new JLabel();
    private final JButton save = new JButton("선택 영역 저장");
    private final JButton undo = new JButton("배치 되돌리기");
    private final JComponent preview = new JComponent() {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            PatternTemplate pattern = list.getSelectedValue();
            if (pattern == null) return;
            int unit = Math.max(1, Math.min(16, Math.min((getWidth() - 12) / pattern.width(),
                    (getHeight() - 12) / pattern.height())));
            int left = (getWidth() - unit * pattern.width()) / 2;
            int top = (getHeight() - unit * pattern.height()) / 2;
            g.setColor(new Color(222, 228, 232));
            g.fillRect(left, top, unit * pattern.width(), unit * pattern.height());
            g.setColor(new Color(35, 93, 119));
            for (int y = 0; y < pattern.height(); y++) for (int x = 0; x < pattern.width(); x++)
                if (pattern.isAlive(x, y)) g.fillRect(left + x * unit, top + y * unit, unit, unit);
        }
    };
    /** [Freezing] Rejects placements into a frozen area. Set by Universe. */
    private java.util.function.Predicate<Rectangle> placementBlocked = area -> false;
    private Mode mode = Mode.DRAW;
    private Point anchor;
    private Point hover;
    private Rectangle selection;

    PatternTools(JPanel board, Cell cells) {
        this.board = board;
        editor = new BoardEditor(cells);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(new EmptyBorder(14, 14, 14, 14));
        controls.setPreferredSize(new Dimension(258, 512));
        JLabel title = new JLabel("패턴 캡처 · 템플릿");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title);
        gap(12);
        JButton capture = new JButton("영역 캡처");
        capture.addActionListener(e -> {
            begin(Mode.CAPTURE);
            message("보드에서 드래그해 영역을 선택하세요.");
        });
        add(capture);
        gap(6);
        save.addActionListener(e -> saveSelection());
        save.setEnabled(false);
        add(save);
        gap(14);
        for (PatternTemplate pattern : library.patterns()) entries.addElement(pattern);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);
        list.addListSelectionListener(e -> {
            preview.repaint();
            if (!e.getValueIsAdjusting() && mode == Mode.PLACE) beginPlacement();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) beginPlacement();
            }
        });
        list.setSelectedIndex(0);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(230, 136));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        add(scroll);
        preview.setPreferredSize(new Dimension(230, 72));
        preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        add(preview);
        JButton place = new JButton("선택한 템플릿 배치");
        place.addActionListener(e -> beginPlacement());
        add(place);
        gap(6);
        undo.addActionListener(e -> undo());
        undo.setEnabled(false);
        add(undo);
        gap(6);
        JButton cancel = new JButton("편집 끝내기 (Esc)");
        cancel.addActionListener(e -> cancel());
        add(cancel);
        gap(12);
        status.setVerticalAlignment(SwingConstants.TOP);
        status.setPreferredSize(new Dimension(230, 64));
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        add(status);
        message("셀을 클릭해 그리거나 템플릿을 선택하세요.");
        if (!library.warnings().isEmpty()) SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(board, String.join("\n", library.warnings()),
                        "일부 템플릿을 불러오지 못했습니다", JOptionPane.WARNING_MESSAGE));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) { cancel(); return; }
                if (!SwingUtilities.isLeftMouseButton(e) || mode == Mode.DRAW) return;
                Point point = cellAt(e.getPoint(), false);
                if (point == null) return;
                if (mode == Mode.CAPTURE) {
                    anchor = point;
                    selectThrough(point);
                } else {
                    PatternTemplate pattern = list.getSelectedValue();
                    if (!editor.fits(pattern, point)) {
                        message("보드 밖으로 나갑니다. 안쪽에 배치하세요.");
                        return;
                    }
                    if (blocked(pattern, point)) {
                        message("Freezing 영역과 겹칩니다. 다른 곳에 배치하세요.");
                        return;
                    }
                    boolean changed = editor.place(pattern, point);
                    undo.setEnabled(editor.canUndo());
                    message(changed ? "배치했습니다. 다시 클릭하면 반복 배치합니다. Esc로 종료."
                            : "같은 위치에 이미 살아 있는 셀이 있습니다.");
                    board.repaint();
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (mode == Mode.CAPTURE && anchor != null) selectThrough(cellAt(e.getPoint(), true));
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (mode == Mode.CAPTURE && anchor != null && SwingUtilities.isLeftMouseButton(e)) {
                    selectThrough(cellAt(e.getPoint(), true));
                    anchor = null;
                }
            }
            @Override public void mouseMoved(MouseEvent e) {
                hover = cellAt(e.getPoint(), false);
                if (mode == Mode.PLACE) board.repaint();
            }
            @Override public void mouseExited(MouseEvent e) { hover = null; board.repaint(); }
        };
        board.addMouseListener(mouse);
        board.addMouseMotionListener(mouse);
        bind("ESCAPE", "cancel-pattern", this::cancel);
        bind("control Z", "undo-pattern", this::undo);
    }

    JPanel controls() { return controls; }
    void setPlacementGuard(java.util.function.Predicate<Rectangle> guard) { placementBlocked = guard; }
    private boolean blocked(PatternTemplate pattern, Point origin) {
        return placementBlocked.test(new Rectangle(origin.x, origin.y, pattern.width(), pattern.height()));
    }
    boolean isEditing() { return mode != Mode.DRAW; }
    void boardChanged() {
        editor.clearHistory();
        undo.setEnabled(false);
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
    private void bind(String key, String name, Runnable action) {
        board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        board.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }
    private void begin(Mode next) {
        Clock.instance().stop();
        mode = next;
        selection = null;
        anchor = null;
        hover = null;
        save.setEnabled(false);
        board.setCursor(Cursor.getPredefinedCursor(next == Mode.DRAW ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
        board.repaint();
    }
    private void beginPlacement() {
        if (list.getSelectedValue() == null) return;
        begin(Mode.PLACE);
        message("보드를 클릭해 배치하세요. 빈 칸은 기존 셀을 지우지 않습니다.");
    }
    void cancel() {
        begin(Mode.DRAW);
        message("셀 클릭으로 그리기. Go 메뉴에서 시뮬레이션을 시작하세요.");
    }
    private void undo() {
        if (!editor.canUndo()) return;
        Clock.instance().stop();
        editor.undo();
        undo.setEnabled(editor.canUndo());
        board.repaint();
        message("마지막 배치를 되돌렸습니다.");
    }
    private Point cellAt(Point point, boolean clamp) {
        int unit = board.getWidth() / editor.size();
        if (unit <= 0) return null;
        int extent = unit * editor.size();
        if (!clamp && (point.x < 0 || point.y < 0 || point.x >= extent || point.y >= extent)) return null;
        return new Point(Math.max(0, Math.min(editor.size() - 1, point.x / unit)),
                Math.max(0, Math.min(editor.size() - 1, point.y / unit)));
    }
    private void selectThrough(Point end) {
        if (end == null) return;
        selection = new Rectangle(Math.min(anchor.x, end.x), Math.min(anchor.y, end.y),
                Math.abs(end.x - anchor.x) + 1, Math.abs(end.y - anchor.y) + 1);
        save.setEnabled(true);
        message(selection.width + " x " + selection.height + "칸 선택. ‘선택 영역 저장’을 누르세요.");
        board.repaint();
    }
    private void saveSelection() {
        if (selection == null) return;
        String name = JOptionPane.showInputDialog(board, "템플릿 이름", "선택 영역 저장", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        try {
            PatternTemplate pattern = editor.capture(name, selection);
            library.save(pattern);
            entries.addElement(pattern);
            list.setSelectedValue(pattern, true);
            beginPlacement();
            message("저장했습니다. 보드를 클릭하면 이 패턴을 배치합니다.");
        } catch (java.io.IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(board, e.getMessage(), "저장하지 못했습니다", JOptionPane.WARNING_MESSAGE);
        }
    }

    void paintOverlay(Graphics2D original) {
        int unit = board.getWidth() / editor.size();
        if (unit <= 0) return;
        Graphics2D g = (Graphics2D) original.create();
        if (mode == Mode.CAPTURE && selection != null) {
            g.setColor(new Color(45, 110, 180, 55));
            g.fillRect(selection.x * unit, selection.y * unit, selection.width * unit, selection.height * unit);
            g.setColor(new Color(30, 90, 160));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(selection.x * unit + 1, selection.y * unit + 1,
                    selection.width * unit - 2, selection.height * unit - 2);
        } else if (mode == Mode.PLACE && hover != null) {
            PatternTemplate pattern = list.getSelectedValue();
            Color color = editor.fits(pattern, hover) && !blocked(pattern, hover)
                    ? new Color(20, 130, 100) : new Color(195, 45, 45);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 130));
            for (int y = 0; y < pattern.height(); y++) for (int x = 0; x < pattern.width(); x++)
                if (pattern.isAlive(x, y)) g.fillRect((hover.x + x) * unit, (hover.y + y) * unit, unit, unit);
            g.setColor(color);
            g.drawRect(hover.x * unit, hover.y * unit, pattern.width() * unit - 1, pattern.height() * unit - 1);
        }
        g.dispose();
    }
}
