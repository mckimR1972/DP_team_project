package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Behavioral scenarios, including a separate reference Life algorithm. */
public final class PatternFeatureTest {
    private static Cell board() { return new Neighborhood(4, new Neighborhood(4, new Resident())); }
    private static PatternTemplate glider() { return PatternTemplate.fromRows("Glider", ".O.", "..O", "OOO"); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid operation was accepted");
    }
    private static boolean[][] snapshot(Cell board) {
        Cell.Memento state = (Cell.Memento) board.createMemento();
        int n = board.widthInCells();
        boolean[][] cells = new boolean[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) cells[y][x] = state.isAlive(new Point(x, y));
        return cells;
    }
    private static void same(boolean[][] expected, boolean[][] actual) {
        for (int y = 0; y < expected.length; y++) for (int x = 0; x < expected[y].length; x++)
            check(expected[y][x] == actual[y][x], "Mismatch at " + x + "," + y);
    }
    private static void step(Cell board) {
        if (board.figureNextState(Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY,
                Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY)) board.transition();
    }
    private static boolean[][] referenceStep(boolean[][] cells) {
        int n = cells.length;
        boolean[][] next = new boolean[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int live = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < n && ny < n && cells[ny][nx]) live++;
            }
            next[y][x] = live == 3 || (cells[y][x] && live == 2);
        }
        return next;
    }

    @Test
    void capturePreservesOffsetsAndMargins() throws Exception {
        Files.createDirectories(Paths.get("build"));
        Cell board = board();
        BoardEditor editor = new BoardEditor(board, kind -> { });
        editor.place(glider(), new Point(3, 4));
        boolean[][] original = snapshot(board);
        PatternTemplate captured = editor.capture("Captured", new Rectangle(2, 3, 5, 5));
        check(captured.width() == 5 && captured.height() == 5 && captured.liveCount() == 5, "Capture dimensions/live count");
        check(captured.isAlive(2, 1) && captured.isAlive(3, 2), "Capture local coordinates");
        same(original, snapshot(board));
    }

    @Test
    void repeatPlacementAndUndo() throws Exception {
        Files.createDirectories(Paths.get("build"));
        Cell board = board();
        BoardEditor editor = new BoardEditor(board, kind -> { });
        editor.place(glider(), new Point(3, 4));
        boolean[][] original = snapshot(board);
        PatternTemplate captured = editor.capture("Captured", new Rectangle(2, 3, 5, 5));

        editor.place(captured, new Point(8, 8));
        check(snapshot(board)[9][10], "Repositioned cell missing");
        check(snapshot(board)[4][4], "Original cell removed");
        editor.undo();
        same(original, snapshot(board));
        editor.undo();
        same(new boolean[16][16], snapshot(board));
    }

    @Test
    void transparentOverlapAndNoOp() throws Exception {
        Files.createDirectories(Paths.get("build"));
        Cell board = board();
        BoardEditor editor = new BoardEditor(board, kind -> { });

        editor.place(PatternTemplate.fromRows("Seed", "O"), new Point(7, 7));
        editor.place(glider(), new Point(7, 7));
        check(snapshot(board)[7][7], "Empty pattern cell erased existing cell");
        boolean[][] combined = snapshot(board);
        check(!editor.place(glider(), new Point(7, 7)), "Duplicate placement should be a no-op");
        editor.undo();
        check(snapshot(board)[7][7] && !snapshot(board)[7][8], "No-op polluted undo history");
    }

    @Test
    void boundsAndEmptyCapture() throws Exception {
        Files.createDirectories(Paths.get("build"));
        Cell board = board();
        BoardEditor editor = new BoardEditor(board, kind -> { });

        boolean[][] beforeInvalid = snapshot(board);
        rejects(() -> editor.place(glider(), new Point(14, 14)));
        rejects(() -> editor.place(glider(), new Point(-1, 0)));
        rejects(() -> editor.capture("Outside", new Rectangle(15, 15, 2, 2)));
        rejects(() -> editor.capture("Empty", new Rectangle(0, 0, 1, 1)));
        same(beforeInvalid, snapshot(board));
        check(editor.fits(glider(), new Point(13, 13)), "Exact bottom-right fit rejected");
        editor.place(glider(), new Point(13, 13));
        check(snapshot(board)[15][15], "Last cell not placed");
    }

    @Test
    void historyInvalidation() throws Exception {
        Files.createDirectories(Paths.get("build"));
        Cell board = board();
        BoardEditor editor = new BoardEditor(board, kind -> { });
        editor.place(glider(), new Point(3, 4));

        editor.clearHistory();
        check(!editor.canUndo(), "History should be invalidated after unrelated edits/ticks");
        boolean[][] afterClear = snapshot(board);
        editor.undo();
        same(afterClear, snapshot(board));
    }

    @Test
    void templateValidation() throws Exception {
        Files.createDirectories(Paths.get("build"));

        boolean[][] source = {{true, false}};
        PatternTemplate immutable = new PatternTemplate("Immutable", source);
        source[0][0] = false;
        check(immutable.isAlive(0, 0), "Template shares mutable input");
        rejects(() -> new PatternTemplate("Bad", new boolean[][] {{true}, {true, false}}));
        rejects(() -> PatternTemplate.fromRows("Bad", "X"));
        rejects(() -> PatternTemplate.fromRows("Bad\nName", "O"));
    }

    @Test
    void saveReloadAndDuplicateNames() throws Exception {
        Files.createDirectories(Paths.get("build"));
        PatternTemplate captured = PatternTemplate.fromRows("Saved", ".O.", "..O", "OOO");

        Path folder = Files.createTempDirectory(Paths.get("build"), "pattern-tests-");
        PatternLibrary library = new PatternLibrary(folder);
        library.save(captured);
        PatternLibrary reopened = new PatternLibrary(folder);
        check(reopened.patterns().size() == 6, "Saved pattern did not reload");
        PatternTemplate loaded = reopened.patterns().get(5);
        check(loaded.name().equals(captured.name()) && loaded.width() == captured.width(), "Name or size not persisted");
        for (int y = 0; y < captured.height(); y++) for (int x = 0; x < captured.width(); x++)
            check(loaded.isAlive(x, y) == captured.isAlive(x, y), "Persisted cells differ");
        try { reopened.save(captured); throw new AssertionError("Duplicate saved"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test
    void corruptFilesAndSaveFailure() throws Exception {
        Files.createDirectories(Paths.get("build"));
        Path folder = Files.createTempDirectory(Paths.get("build"), "pattern-tests-");
        PatternTemplate captured = PatternTemplate.fromRows("Saved", ".O.", "..O", "OOO");
        new PatternLibrary(folder).save(captured);

        Files.writeString(folder.resolve("broken.lifepattern"), "LIFE_PATTERN 1\nBroken\n99 2\nO\n", StandardCharsets.UTF_8);
        PatternLibrary withBroken = new PatternLibrary(folder);
        check(withBroken.patterns().size() == 6 && withBroken.warnings().size() == 1, "Corrupt file affected valid patterns");
        Path notDirectory = Files.createTempFile(Paths.get("build"), "not-folder-", ".txt");
        PatternLibrary unavailable = new PatternLibrary(notDirectory);
        try { unavailable.save(captured); throw new AssertionError("Save unexpectedly succeeded"); }
        catch (java.io.IOException expected) { }
        check(unavailable.patterns().size() == 5, "Failed save changed library");
    }

    @Test
    void gliderAcrossNeighborhoods() throws Exception {
        Files.createDirectories(Paths.get("build"));

        Cell crossing = board();
        new BoardEditor(crossing, kind -> { }).place(glider(), new Point(2, 2));
        boolean[][] expected = snapshot(crossing);
        for (int generation = 0; generation < 40; generation++) {
            expected = referenceStep(expected);
            step(crossing);
            same(expected, snapshot(crossing));
        }
    }

    @Test
    void randomLifeComparison() throws Exception {
        Files.createDirectories(Paths.get("build"));
        boolean[][] expected;

        Random random = new Random(1972);
        for (int trial = 0; trial < 12; trial++) {
            Cell randomBoard = board();
            boolean[][] seed = new boolean[16][16];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) seed[y][x] = random.nextDouble() < 0.22;
            new BoardEditor(randomBoard, kind -> { }).place(new PatternTemplate("Random", seed), new Point());
            expected = seed;
            for (int generation = 0; generation < 30; generation++) {
                expected = referenceStep(expected);
                step(randomBoard);
                same(expected, snapshot(randomBoard));
            }
        }
    }

}
