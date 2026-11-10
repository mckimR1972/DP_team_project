package com.holub.life;

import com.holub.ui.MenuSite;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Swing test of the real right-side panel (capture tab + Freezing tab) and Universe ticks. */
public final class FreezingUiTest {
    private static JFrame frame;
    private static Universe board;
    private static Cell cells;
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static <T extends Component> T find(Container root, Class<T> type, String text) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                String label = child instanceof AbstractButton ? ((AbstractButton) child).getText() : null;
                if (text == null || text.equals(label)) return type.cast(child);
            }
            if (child instanceof JMenu) {
                T found = find(((JMenu) child).getPopupMenu(), type, text);
                if (found != null) return found;
            }
            if (child instanceof Container) {
                T found = find((Container) child, type, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static JButton button(String text) { return find(frame, JButton.class, text); }
    private static void mouse(int id, int cellX, int cellY, int button) {
        int x = cellX * 8 + 4, y = cellY * 8 + 4;
        board.dispatchEvent(new MouseEvent(board, id, System.currentTimeMillis(),
                id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0, x, y, 1, false, button));
    }
    private static void drag(int x1, int y1, int x2, int y2) {
        mouse(MouseEvent.MOUSE_PRESSED, x1, y1, MouseEvent.BUTTON1);
        mouse(MouseEvent.MOUSE_DRAGGED, x2, y2, MouseEvent.NOBUTTON);
        mouse(MouseEvent.MOUSE_RELEASED, x2, y2, MouseEvent.BUTTON1);
    }
    private static boolean alive(int x, int y) {
        return ((Cell.Memento) cells.createMemento()).isAlive(new Point(x, y));
    }
    private static void click(int x, int y) { mouse(MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1); }
    private static void render(Path path) throws Exception {
        Container pane = frame.getContentPane();
        BufferedImage image = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        pane.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    public static void main(String[] args) throws Exception {
        Path fixture = Files.createTempDirectory(Paths.get("build"), "ui-freezing-");
        System.setProperty("life.templates", fixture.toAbsolutePath().toString());
        Path renderFolder = Paths.get(args.length > 0 ? args[0] : "build");
        Files.createDirectories(renderFolder);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Freezing component test");
                MenuSite.establish(frame);
                board = Universe.instance();
                frame.add(board, BorderLayout.CENTER);
                frame.add(board.sideControls(), BorderLayout.EAST);
                frame.pack();
                try {
                    Field field = Universe.class.getDeclaredField("outermostCell");
                    field.setAccessible(true);
                    cells = (Cell) field.get(board);
                    JTabbedPane tabs = board.sideControls();
                    check(tabs.getTabCount() == 2 && tabs.getTitleAt(0).equals("패턴 캡처")
                            && tabs.getTitleAt(1).equals("Freezing"), "Right panel tabs");
                    check(board.getWidth() == 512, "Board layout size");

                    // Draw a lone cell and an L-shape whose corner would be born.
                    click(10, 10);
                    click(30, 30); click(31, 30); click(30, 31);
                    tabs.setSelectedIndex(1);
                    ((JSpinner) find(frame, JSpinner.class, null)).setValue(3);
                    button("영역 선택").doClick();
                    check(!button("Freezing 설치").isEnabled(), "Install enabled before selection");
                    drag(12, 12, 8, 8);                                  // reverse drag -> (8,8) 5x5
                    check(button("Freezing 설치").isEnabled(), "Selection not ready");
                    button("Freezing 설치").doClick();
                    button("영역 선택").doClick();
                    drag(31, 31, 31, 31);
                    button("Freezing 설치").doClick();
                    FreezingManager freezing = board.freezing();
                    check(freezing.regions().size() == 2, "Two regions installed");
                    check(freezing.regions().get(0).area().equals(new Rectangle(8, 8, 5, 5)), "Reverse drag area");
                    button("영역 선택").doClick();
                    drag(9, 9, 15, 15);
                    check(!button("Freezing 설치").isEnabled(), "Overlapping selection allowed");
                    board.getActionMap().get("cancel-editing").actionPerformed(null);   // Esc
                    check(freezing.regions().size() == 2 && board.getCursor().getType() == Cursor.DEFAULT_CURSOR,
                            "Esc should end selection and keep installed regions");

                    click(10, 10);
                    check(alive(10, 10), "Click inside a frozen region changed a cell");
                    render(renderFolder.resolve("life-freezing.png"));

                    for (int g = 1; g <= 3; g++) {
                        Clock.instance().tick();
                        check(alive(10, 10), "Frozen lone cell died at tick " + g);
                        check(!alive(31, 31), "Frozen dead cell was born at tick " + g);
                    }
                    check(freezing.regions().isEmpty() && freezing.generation() == 3, "Auto release after 3 ticks");
                    Clock.instance().tick();
                    check(!alive(10, 10) && alive(31, 31), "Released cells did not resume B3/S23");

                    // Placement guard: a template may not land on a frozen area.
                    button("영역 선택").doClick();
                    drag(40, 40, 44, 44);
                    button("Freezing 설치").doClick();
                    tabs.setSelectedIndex(0);
                    button("선택한 템플릿 배치").doClick();
                    mouse(MouseEvent.MOUSE_MOVED, 41, 41, MouseEvent.NOBUTTON);
                    click(41, 41);
                    check(!alive(42, 41) && !alive(43, 42), "Template placed into a frozen area");
                    board.getActionMap().get("cancel-editing").actionPerformed(null);
                    tabs.setSelectedIndex(1);

                    JList<?> list = find(frame, JList.class, null);
                    for (Component c : ((Container) tabs.getComponentAt(1)).getComponents())
                        if (c instanceof JScrollPane) list = (JList<?>) ((JScrollPane) c).getViewport().getView();
                    list.setSelectedIndex(0);
                    button("선택한 Freezing 해제").doClick();
                    check(freezing.regions().isEmpty(), "Forced release from the panel");

                    button("영역 선택").doClick();
                    drag(1, 1, 3, 3);
                    button("Freezing 설치").doClick();
                    find(frame.getJMenuBar(), JMenuItem.class, "Clear").doClick();
                    check(freezing.regions().isEmpty() && freezing.generation() == 0, "Grid > Clear did not reset Freezing");
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            System.out.println("PASS " + checks + " Freezing Swing checks: tabs, select, reverse drag, install, overlap, Esc, "
                    + "blocked click, ticks, auto release, placement guard, forced release, Grid > Clear.");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                Clock.instance().stop();
                for (Window window : Window.getWindows()) window.dispose();
            });
        }
    }
}
