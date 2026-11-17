package com.holub.life;

import com.holub.ui.MenuSite;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** In-process Swing integration test; no desktop input or screen scraping. */
public final class PatternUiTest {
    private static JFrame frame;
    private static Universe board;
    private static Cell cells;
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static JButton button(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton && ((JButton) child).getText().equals(name)) return (JButton) child;
            if (child instanceof Container) {
                JButton found = button((Container) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static JOptionPane optionPane(Container root) {
        if (root instanceof JOptionPane) return (JOptionPane) root;
        for (Component child : root.getComponents()) if (child instanceof Container) {
            JOptionPane found = optionPane((Container) child);
            if (found != null) return found;
        }
        return null;
    }
    private static void mouse(int id, int x, int y, int button) {
        board.dispatchEvent(new MouseEvent(board, id, System.currentTimeMillis(),
                id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0,
                x, y, 1, false, button));
    }
    private static int liveCount() {
        Cell.Memento state = (Cell.Memento) cells.createMemento();
        int count = 0;
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            if (state.isAlive(new Point(x, y))) count++;
        return count;
    }
    private static void render(Path path) throws Exception {
        Container pane = frame.getContentPane();
        BufferedImage image = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        pane.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    public static void main(String[] args) throws Exception {
        Path fixture = Files.createTempDirectory(Paths.get("build"), "ui-patterns-");
        System.setProperty("life.templates", fixture.toAbsolutePath().toString());
        Path renderFolder = Paths.get(args.length > 0 ? args[0] : "build");
        Files.createDirectories(renderFolder);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Life component test");
                MenuSite.establish(frame);
                board = Universe.instance();
                frame.add(board, BorderLayout.CENTER);
                frame.add(board.patternControls(), BorderLayout.EAST);
                frame.pack();
                try {
                    Field field = Universe.class.getDeclaredField("outermostCell");
                    field.setAccessible(true);
                    cells = (Cell) field.get(board);
                    check(board.getWidth() == 512 && board.getHeight() == 512, "Board layout size");
                    button(frame, "선택한 템플릿 배치").doClick();
                    mouse(MouseEvent.MOUSE_MOVED, 84, 84, MouseEvent.NOBUTTON);
                    mouse(MouseEvent.MOUSE_PRESSED, 84, 84, MouseEvent.BUTTON1);
                    check(liveCount() == 5, "Built-in placement or duplicate normal-click handler");
                    mouse(MouseEvent.MOUSE_PRESSED, 508, 508, MouseEvent.BUTTON1);
                    check(liveCount() == 5, "Overflow placement changed board");
                    button(frame, "영역 캡처").doClick();
                    mouse(MouseEvent.MOUSE_PRESSED, 116, 116, MouseEvent.BUTTON1);
                    mouse(MouseEvent.MOUSE_DRAGGED, 76, 76, MouseEvent.NOBUTTON);
                    mouse(MouseEvent.MOUSE_RELEASED, 76, 76, MouseEvent.BUTTON1);
                    check(liveCount() == 5, "Capture modified cells");
                    check(button(frame, "선택 영역 저장").isEnabled(), "Selection not ready for saving");
                    render(renderFolder.resolve("life-selection.png"));
                    Timer answer = new Timer(150, e -> {
                        for (Window window : Window.getWindows()) if (window instanceof JDialog && window.isVisible()) {
                            JOptionPane pane = optionPane((Container) window);
                            if (pane != null) {
                                pane.setInputValue("UI test capture");
                                pane.setValue(JOptionPane.OK_OPTION);
                            }
                        }
                    });
                    answer.setRepeats(false);
                    answer.start();
                    button(frame, "선택 영역 저장").doClick();
                    PatternLibrary reloaded = new PatternLibrary(fixture);
                    check(reloaded.patterns().size() == 6, "Save dialog failed to persist capture");
                    PatternTemplate captured = reloaded.patterns().get(5);
                    check(captured.width() == 6 && captured.height() == 6 && captured.liveCount() == 5,
                            "Reverse drag selection coordinates");
                    mouse(MouseEvent.MOUSE_MOVED, 244, 244, MouseEvent.NOBUTTON);
                    render(renderFolder.resolve("life-placement.png"));
                    mouse(MouseEvent.MOUSE_PRESSED, 244, 244, MouseEvent.BUTTON1);
                    check(liveCount() == 10, "Captured template not placed");
                    board.getActionMap().get("undo-pattern").actionPerformed(null);
                    check(liveCount() == 5, "Undo shortcut failed");
                    board.getActionMap().get("cancel-pattern").actionPerformed(null);
                    mouse(MouseEvent.MOUSE_PRESSED, 164, 164, MouseEvent.BUTTON1);
                    check(liveCount() == 6, "Escape did not restore drawing mode");
                    check(!button(frame, "배치 되돌리기").isEnabled(), "Manual edit did not invalidate undo");
                    Clock.instance().tick();
                    check(liveCount() == 5, "Single-step simulation did not advance after editing");
                    button(frame, "영역 캡처").doClick();
                    mouse(MouseEvent.MOUSE_PRESSED, 508, 508, MouseEvent.BUTTON1);
                    mouse(MouseEvent.MOUSE_DRAGGED, 580, 580, MouseEvent.NOBUTTON);
                    mouse(MouseEvent.MOUSE_RELEASED, 580, 580, MouseEvent.BUTTON1);
                    check(button(frame, "선택 영역 저장").isEnabled(), "Outside drag release did not clamp to board");
                    mouse(MouseEvent.MOUSE_PRESSED, 100, 100, MouseEvent.BUTTON3);
                    check(!button(frame, "선택 영역 저장").isEnabled(), "Right-click did not cancel capture");
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            System.out.println("PASS " + checks + " Swing integration checks: place, bounds, reverse drag, save dialog, reload, undo, escape, draw, tick, cancel.");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                Clock.instance().stop();
                for (Window window : Window.getWindows()) window.dispose();
            });
        }
    }
}
