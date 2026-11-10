package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Freezing behavior tests A-F plus a comparison with an independent Life + Freezing reference. No GUI. */
public final class FreezingFeatureTest {
    private static int passed;

    private static Cell board() { return new Neighborhood(8, new Neighborhood(8, new Resident())); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void passed(String name) { passed++; System.out.println("PASS " + name); }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static boolean[][] snapshot(Cell board) {
        Cell.Memento state = (Cell.Memento) board.createMemento();
        int n = board.widthInCells();
        boolean[][] cells = new boolean[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) cells[y][x] = state.isAlive(new Point(x, y));
        return cells;
    }
    private static boolean alive(Cell board, int x, int y) {
        return ((Cell.Memento) board.createMemento()).isAlive(new Point(x, y));
    }
    /** Load live cells through the existing Memento protocol (same path as Grid > Load). */
    private static void set(Cell board, int[]... cells) {
        Cell.Memento state = (Cell.Memento) board.createMemento();
        for (int[] c : cells) state.markAsAlive(new Point(c[0], c[1]));
        board.clear();
        board.transfer(state, new Point(0, 0), Cell.LOAD);
    }
    /** Same order as the Universe Clock listener. */
    private static void tick(Cell board, FreezingManager freezing) {
        freezing.beginGeneration();
        boolean unstable = board.figureNextState(Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY,
                Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY);
        boolean held = freezing.beforeTransition(board);
        if (unstable || held) board.transition();
        freezing.afterGeneration();
    }
    private static boolean[][] conway(boolean[][] cells) {
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

    public static void main(String[] args) {
        testA_liveCellsStay();
        testB_deadCellsStayDead();
        testC_outsideEvolvesNormally();
        testD_boundaryNeighbors();
        testE_autoRelease();
        testF_forceReleaseAndReset();
        testStateTransitionsAndValidation();
        testRandomAgainstReference();
        System.out.println("All " + passed + " Freezing scenarios passed.");
    }

    /** A: a lone live cell would die (0 neighbors), but stays alive while frozen. */
    static void testA_liveCellsStay() {
        Cell board = board();
        FreezingManager freezing = new FreezingManager(board);
        set(board, new int[] { 10, 10 }, new int[] { 20, 20 });
        freezing.install(new Rectangle(9, 9, 3, 3), 3);
        for (int g = 1; g <= 3; g++) {
            tick(board, freezing);
            check(alive(board, 10, 10), "A: frozen live cell died at generation " + g);
            check(!alive(board, 20, 20), "A: unfrozen lone cell should die");
        }
        passed("A frozen live cells do not die");
    }

    /** B: a dead cell with exactly 3 live neighbors would be born, but stays dead while frozen. */
    static void testB_deadCellsStayDead() {
        Cell board = board();
        FreezingManager freezing = new FreezingManager(board);
        // L-shaped block: the empty corner (31,31) would be born next generation.
        set(board, new int[] { 30, 30 }, new int[] { 31, 30 }, new int[] { 30, 31 });
        boolean[][] control = conway(snapshot(board));
        check(control[31][31], "B: setup should produce a birth without Freezing");
        freezing.install(new Rectangle(31, 31, 1, 1), 4);
        for (int g = 1; g <= 4; g++) {
            tick(board, freezing);
            check(!alive(board, 31, 31), "B: frozen dead cell was born at generation " + g);
        }
        passed("B frozen dead cells are not born");
    }

    /** C: a blinker outside the region oscillates exactly as before. */
    static void testC_outsideEvolvesNormally() {
        Cell board = board();
        FreezingManager freezing = new FreezingManager(board);
        set(board, new int[] { 40, 10 }, new int[] { 41, 10 }, new int[] { 42, 10 },   // blinker
                new int[] { 5, 5 }, new int[] { 6, 5 }, new int[] { 7, 5 });             // frozen blinker
        freezing.install(new Rectangle(4, 4, 5, 3), 6);
        for (int g = 1; g <= 6; g++) {
            tick(board, freezing);
            boolean vertical = g % 2 == 1;
            check(alive(board, 41, 10) && alive(board, 41, 9) == vertical && alive(board, 40, 10) == !vertical,
                    "C: outside blinker broke at generation " + g);
            check(alive(board, 5, 5) && alive(board, 6, 5) && alive(board, 7, 5) && !alive(board, 6, 4),
                    "C: frozen blinker moved at generation " + g);
        }
        passed("C cells outside Freezing follow B3/S23");
    }

    /** D: frozen live cells count as neighbors for outside cells (birth across the boundary). */
    static void testD_boundaryNeighbors() {
        Cell board = board();
        FreezingManager freezing = new FreezingManager(board);
        // Region spans a nested-Neighborhood boundary (x = 7|8).
        set(board, new int[] { 7, 30 }, new int[] { 8, 30 }, new int[] { 9, 31 });
        freezing.install(new Rectangle(7, 30, 2, 1), 2);
        tick(board, freezing);
        check(alive(board, 8, 31), "D: outside cell did not see 2 frozen neighbors + 1 outside");
        check(alive(board, 7, 30) && alive(board, 8, 30), "D: frozen cells changed");
        passed("D frozen live cells are counted by outside neighbors");
    }

    /** E: example from the spec. Install 5 at generation 10: 11..15 frozen, 16 evolves. */
    static void testE_autoRelease() {
        Cell board = board();
        FreezingManager freezing = new FreezingManager(board);
        for (int g = 0; g < 10; g++) tick(board, freezing);
        check(freezing.generation() == 10, "E: generation counter");
        set(board, new int[] { 50, 50 });
        FreezingRegion region = freezing.install(new Rectangle(50, 50, 1, 1), 5);
        check(region.startGeneration() == 10 && region.state() instanceof FrozenState, "E: start");
        for (int g = 11; g <= 15; g++) {
            tick(board, freezing);
            check(alive(board, 50, 50), "E: cell must stay alive at generation " + g);
            check(freezing.generation() == g, "E: counter must keep counting while frozen");
        }
        check(region.state() instanceof NormalState && freezing.regions().isEmpty(), "E: not released after 15");
        tick(board, freezing);                                   // generation 16
        check(!alive(board, 50, 50), "E: released cell must follow the rules at generation 16");
        passed("E automatic release after N generations, counter keeps counting");
    }

    /** F: forced release and full reset. */
    static void testF_forceReleaseAndReset() {
        Cell board = board();
        FreezingManager freezing = new FreezingManager(board);
        set(board, new int[] { 12, 12 }, new int[] { 40, 40 });
        FreezingRegion a = freezing.install(new Rectangle(12, 12, 1, 1), 50);
        freezing.install(new Rectangle(40, 40, 1, 1), 50);
        tick(board, freezing);
        check(alive(board, 12, 12) && alive(board, 40, 40), "F: frozen before release");
        freezing.release(a);
        check(a.state() instanceof NormalState && freezing.regions().size() == 1, "F: release one");
        tick(board, freezing);
        check(!alive(board, 12, 12) && alive(board, 40, 40), "F: released cell must evolve, other stays");
        freezing.reset();
        check(freezing.regions().isEmpty() && freezing.generation() == 0, "F: reset clears regions and counter");
        tick(board, freezing);
        check(!alive(board, 40, 40), "F: cell evolves after reset");
        freezing.install(new Rectangle(0, 0, 4, 4), 3);
        freezing.releaseAll();
        check(freezing.regions().isEmpty(), "F: release all");
        passed("F forced release, release all, and reset clean up state");
    }

    static void testStateTransitionsAndValidation() {
        Cell board = board();
        FreezingManager freezing = new FreezingManager(board);
        FreezingRegion region = new FreezingRegion(new Rectangle(1, 1, 2, 2));
        check(region.state() == NormalState.INSTANCE, "starts Normal");
        region.freeze(board, 2, 0);
        check(region.state() instanceof FrozenState && region.remainingGenerations() == 2, "Normal -> Frozen");
        region.afterGeneration();
        check(region.remainingGenerations() == 1, "countdown");
        region.afterGeneration();
        check(region.state() == NormalState.INSTANCE, "Frozen -> Normal");
        freezing.install(new Rectangle(10, 10, 5, 5), 3);
        rejects(() -> freezing.install(new Rectangle(14, 14, 3, 3), 3), "overlap accepted");
        rejects(() -> freezing.install(new Rectangle(0, 0, 2, 2), 0), "zero duration accepted");
        rejects(() -> freezing.install(new Rectangle(80, 80, 2, 2), 3), "outside area accepted");
        FreezingRegion clamped = freezing.install(new Rectangle(62, 62, 5, 5), 3);
        check(clamped.area().equals(new Rectangle(62, 62, 2, 2)), "area clamps to the board");
        passed("State transitions Normal -> Frozen -> Normal, overlap/duration/bounds validation");
    }

    /** Random boards with random Freezing installs and releases, compared every generation
     *  with an independent implementation: next = B3/S23(previous), then frozen cells = snapshot. */
    static void testRandomAgainstReference() {
        final class Ref { Rectangle area; boolean[][] snap; int remaining; FreezingRegion real; }
        int generations = 0;
        for (int seed = 0; seed < 12; seed++) {
            Random random = new Random(seed);
            Cell board = board();
            FreezingManager freezing = new FreezingManager(board);
            List<int[]> live = new ArrayList<>();
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) if (random.nextInt(100) < 32) live.add(new int[] { x, y });
            set(board, live.toArray(new int[0][]));
            List<Ref> refs = new ArrayList<>();
            boolean[][] expected = snapshot(board);
            for (int g = 0; g < 250; g++) {
                if (random.nextInt(10) == 0) {                       // install
                    Rectangle area = new Rectangle(random.nextInt(60), random.nextInt(60),
                            1 + random.nextInt(12), 1 + random.nextInt(12));
                    int duration = 1 + random.nextInt(25);
                    try {
                        FreezingRegion real = freezing.install(area, duration);
                        Ref ref = new Ref();
                        ref.area = real.area();
                        ref.remaining = duration;
                        ref.real = real;
                        ref.snap = new boolean[ref.area.height][ref.area.width];
                        for (int y = 0; y < ref.area.height; y++) for (int x = 0; x < ref.area.width; x++)
                            ref.snap[y][x] = expected[ref.area.y + y][ref.area.x + x];
                        refs.add(ref);
                    } catch (IllegalArgumentException overlap) { /* also rejected by the reference */ }
                }
                if (!refs.isEmpty() && random.nextInt(40) == 0) {      // forced release
                    Ref ref = refs.remove(random.nextInt(refs.size()));
                    freezing.release(ref.real);
                }
                boolean[][] next = conway(expected);
                for (Ref ref : refs)
                    for (int y = 0; y < ref.area.height; y++) for (int x = 0; x < ref.area.width; x++)
                        next[ref.area.y + y][ref.area.x + x] = ref.snap[y][x];
                refs.removeIf(ref -> --ref.remaining == 0);
                expected = next;
                tick(board, freezing);
                boolean[][] actual = snapshot(board);
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
                    check(expected[y][x] == actual[y][x],
                            "reference mismatch seed " + seed + " gen " + g + " at " + x + "," + y);
                check(refs.size() == freezing.regions().size(), "active region count, seed " + seed + " gen " + g);
                generations++;
            }
        }
        passed(generations + " random generations match an independent Life + Freezing reference");
    }
}
