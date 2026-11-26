package com.holub.life;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Freezing model on its own board: no Universe, no Swing.
 * The tick helper runs the same steps, in the same order, as the Universe clock listener.
 */
class FreezingModelTest {
    private static final int SIZE = 64;
    /** Period 1 and seven colors: the color of a live cell tells its age modulo 7. */
    private static final Color[] PALETTE = {
        new Color(10, 0, 0), new Color(20, 0, 0), new Color(30, 0, 0), new Color(40, 0, 0),
        new Color(50, 0, 0), new Color(60, 0, 0), new Color(70, 0, 0)
    };

    private Cell board;
    private long generation;
    private FreezingManager freezing;

    @BeforeEach
    void createBoard() {
        board = new Neighborhood(8, new Neighborhood(8, new Resident()));
        generation = 0;
        freezing = new FreezingManager(board, () -> generation);
        Resident.setColorPolicy(new CyclingColorPolicy(1, PALETTE));
    }

    @AfterEach
    void restoreColor() {
        Resident.setColorPolicy(new FixedColorPolicy(Resident.LIVE_COLOR));
    }

    // ---------------------------------------------------------------- A - F

    @Test
    void a_frozenLiveCellDoesNotDieWhileAnUnfrozenLoneCellDoes() {
        set(new int[] {10, 10}, new int[] {20, 20});
        freezing.install(new Rectangle(9, 9, 3, 3), 3);
        for (int g = 1; g <= 3; g++) {
            tick();
            assertTrue(alive(10, 10), "frozen live cell died at generation " + g);
            assertFalse(alive(20, 20), "unfrozen lone cell must die");
        }
    }

    @Test
    void b_frozenDeadCellIsNotBornEvenWithThreeNeighbors() {
        set(new int[] {30, 30}, new int[] {31, 30}, new int[] {30, 31});
        assertTrue(conway(cells())[31][31], "setup must produce a birth without Freezing");
        freezing.install(new Rectangle(31, 31, 1, 1), 4);
        for (int g = 1; g <= 4; g++) {
            tick();
            assertFalse(alive(31, 31), "frozen dead cell was born at generation " + g);
        }
    }

    @Test
    void c_cellsOutsideFollowB3S23() {
        set(new int[] {40, 10}, new int[] {41, 10}, new int[] {42, 10},
                new int[] {5, 5}, new int[] {6, 5}, new int[] {7, 5});
        freezing.install(new Rectangle(4, 4, 5, 3), 6);
        for (int g = 1; g <= 6; g++) {
            tick();
            boolean vertical = g % 2 == 1;
            assertTrue(alive(41, 10));
            assertEquals(vertical, alive(41, 9), "outside blinker broke at generation " + g);
            assertEquals(!vertical, alive(40, 10), "outside blinker broke at generation " + g);
            assertTrue(alive(5, 5) && alive(6, 5) && alive(7, 5) && !alive(6, 4),
                    "frozen blinker moved at generation " + g);
        }
    }

    @Test
    void d_frozenLiveCellsCountAsNeighborsOfOutsideCells() {
        // Two frozen live cells and one free live cell give the dead cell (11,11) three neighbors.
        set(new int[] {10, 10}, new int[] {11, 10}, new int[] {12, 11});
        freezing.install(new Rectangle(10, 10, 2, 1), 5);
        tick();
        assertTrue(alive(11, 11), "outside cell must be born from frozen neighbors");
        assertTrue(alive(10, 10) && alive(11, 10), "frozen cells must stay");
    }

    @Test
    void d_outsideCellsDoNotChangeFrozenCells() {
        // A block around a frozen dead cell would normally fill it; a crowd would normally kill a live one.
        set(new int[] {20, 20}, new int[] {21, 20}, new int[] {22, 20},
                new int[] {20, 21}, new int[] {21, 21}, new int[] {22, 21},
                new int[] {20, 22}, new int[] {21, 22}, new int[] {22, 22});
        freezing.install(new Rectangle(21, 21, 1, 1), 8);          // crowded live cell
        for (int g = 1; g <= 8; g++) {
            tick();
            assertTrue(alive(21, 21), "crowded frozen cell died at generation " + g);
        }
    }

    @Test
    void e_installAtGenerationTenForFiveIsFrozenThroughFifteenAndFreeFromSixteen() {
        set(new int[] {10, 10});
        for (int g = 1; g <= 10; g++) tick();                      // the lone cell dies at generation 1
        set(new int[] {10, 10});                                   // put it back at generation 10
        FreezingRegion region = freezing.install(new Rectangle(10, 10, 1, 1), 5);
        assertEquals(5, region.remainingGenerations());
        for (int g = 11; g <= 15; g++) {
            assertTrue(region.isFrozen());
            assertEquals(16 - g, region.remainingGenerations());
            tick();
            assertEquals(g, freezing.generation());
            assertTrue(alive(10, 10), "must be frozen during generation " + g);
        }
        assertFalse(region.isFrozen(), "released after the 15th generation update");
        assertSame(NormalState.INSTANCE, stateOf(region));
        assertTrue(freezing.regions().isEmpty());
        tick();
        assertEquals(16, freezing.generation());
        assertFalse(alive(10, 10), "normal rules apply from generation 16");
    }

    @Test
    void f_forcedReleaseReleaseAllAndResetLeaveNothingBehind() {
        set(new int[] {10, 10}, new int[] {30, 30}, new int[] {50, 50});
        FreezingRegion first = freezing.install(new Rectangle(10, 10, 1, 1), 100);
        freezing.install(new Rectangle(30, 30, 1, 1), 100);
        freezing.install(new Rectangle(50, 50, 1, 1), 100);
        tick();
        freezing.release(first);
        assertFalse(first.isFrozen());
        assertEquals(2, freezing.regions().size());
        assertFalse(freezing.isFrozen(new Point(10, 10)));
        tick();
        assertFalse(alive(10, 10), "released cell follows the rules on the very next generation");
        assertTrue(alive(30, 30) && alive(50, 50));
        freezing.releaseAll();
        assertTrue(freezing.regions().isEmpty());
        tick();
        assertFalse(alive(30, 30) || alive(50, 50));
        set(new int[] {5, 5});
        freezing.install(new Rectangle(5, 5, 1, 1), 9);
        freezing.reset();
        assertTrue(freezing.regions().isEmpty());
        assertFalse(freezing.overlapsFrozen(new Rectangle(0, 0, SIZE, SIZE)));
        freezing.release(first);                                   // releasing an unknown region is harmless
        freezing.release(null);
        freezing.releaseAll();
    }

    // ------------------------------------------------------- State pattern

    @Test
    void stateObjectsDriveTheRegion() {
        FreezingRegion region = new FreezingRegion(new Rectangle(1, 1, 2, 2));
        assertSame(NormalState.INSTANCE, stateOf(region));
        assertFalse(region.isFrozen());
        assertEquals(0, region.remainingGenerations());
        assertFalse(region.beforeTransition(board), "NormalState must not touch the board");
        region.afterGeneration();
        assertSame(NormalState.INSTANCE, stateOf(region));

        region.freeze(board, 2);
        assertInstanceOf(FrozenState.class, stateOf(region));
        assertEquals(2, region.remainingGenerations());
        region.afterGeneration();
        assertTrue(region.isFrozen());
        assertEquals(1, region.remainingGenerations());
        region.afterGeneration();
        assertSame(NormalState.INSTANCE, stateOf(region), "FrozenState switches the context itself");

        region.freeze(board, 3);
        region.release();
        assertSame(NormalState.INSTANCE, stateOf(region));
        region.afterGeneration();                                  // a released region ignores later updates
        assertFalse(region.isFrozen());
    }

    @Test
    void normalStateIsStatelessAndShared() {
        FreezingRegion one = new FreezingRegion(new Rectangle(0, 0, 1, 1));
        FreezingRegion two = new FreezingRegion(new Rectangle(5, 5, 1, 1));
        assertSame(stateOf(one), stateOf(two));
        one.freeze(board, 1);
        two.freeze(board, 1);
        assertNotSame(stateOf(one), stateOf(two), "each frozen region counts down on its own state object");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, Integer.MIN_VALUE})
    void durationBelowOneIsRejectedAndLeavesNoRegion(int duration) {
        assertThrows(IllegalArgumentException.class,
                () -> freezing.install(new Rectangle(1, 1, 2, 2), duration));
        assertTrue(freezing.regions().isEmpty());
        assertFalse(freezing.isFrozen(new Point(1, 1)));
    }

    @Test
    void emptyNullAndOutsideAreasAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> freezing.install(null, 3));
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(3, 3, 0, 4), 3));
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(3, 3, 4, 0), 3));
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(64, 0, 5, 5), 3));
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(-9, -9, 5, 5), 3));
        assertThrows(IllegalArgumentException.class, () -> new FreezingRegion(null));
        assertThrows(IllegalArgumentException.class, () -> new FreezingRegion(new Rectangle(0, 0, 0, 0)));
        assertTrue(freezing.regions().isEmpty());
    }

    @Test
    void areaIsClampedToTheBoard() {
        FreezingRegion region = freezing.install(new Rectangle(60, 61, 20, 20), 2);
        assertEquals(new Rectangle(60, 61, 4, 3), region.area());
        FreezingRegion corner = freezing.install(new Rectangle(-5, -5, 7, 8), 2);
        assertEquals(new Rectangle(0, 0, 2, 3), corner.area());
    }

    @Test
    void overlapIsRejectedButTouchingAreasAreAllowed() {
        freezing.install(new Rectangle(10, 10, 4, 4), 5);
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(13, 13, 4, 4), 5));
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(11, 11, 1, 1), 5));
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(0, 0, 64, 64), 5));
        assertEquals(1, freezing.regions().size());
        freezing.install(new Rectangle(14, 10, 4, 4), 5);          // shares only an edge
        freezing.install(new Rectangle(10, 14, 4, 4), 5);
        assertEquals(3, freezing.regions().size());
        assertTrue(freezing.overlapsFrozen(new Rectangle(13, 13, 2, 2)));
        assertFalse(freezing.overlapsFrozen(new Rectangle(18, 18, 2, 2)));
        assertTrue(freezing.isFrozen(new Point(13, 13)));
        assertFalse(freezing.isFrozen(new Point(18, 14)));
        assertFalse(freezing.isFrozen(null));
    }

    @Test
    void anExpiredOrReleasedAreaCanBeFrozenAgain() {
        Rectangle area = new Rectangle(8, 8, 3, 3);
        FreezingRegion first = freezing.install(area, 1);
        tick();
        assertTrue(freezing.regions().isEmpty());
        FreezingRegion second = freezing.install(area, 2);
        assertNotSame(first, second);
        freezing.release(second);
        freezing.install(area, 2);
        assertEquals(1, freezing.regions().size());
    }

    @Test
    void regionsWithDifferentDurationsExpireIndependently() {
        set(new int[] {5, 5}, new int[] {25, 25}, new int[] {45, 45});
        freezing.install(new Rectangle(5, 5, 1, 1), 1);
        freezing.install(new Rectangle(25, 25, 1, 1), 3);
        freezing.install(new Rectangle(45, 45, 1, 1), 6);
        int[] expectedRegions = {2, 2, 1, 1, 1, 0, 0};
        for (int g = 1; g <= 7; g++) {
            tick();
            assertEquals(expectedRegions[g - 1], freezing.regions().size(), "regions after generation " + g);
            assertEquals(g <= 1, alive(5, 5), "cell A at generation " + g);
            assertEquals(g <= 3, alive(25, 25), "cell B at generation " + g);
            assertEquals(g <= 6, alive(45, 45), "cell C at generation " + g);
        }
    }

    @Test
    void theListOfRegionsCannotBeChangedFromOutside() {
        freezing.install(new Rectangle(1, 1, 1, 1), 2);
        assertThrows(UnsupportedOperationException.class, () -> freezing.regions().clear());
        Rectangle copy = freezing.regions().get(0).area();
        copy.setBounds(0, 0, 64, 64);
        assertEquals(new Rectangle(1, 1, 1, 1), freezing.regions().get(0).area());
    }

    @Test
    void listenersHearInstallReleaseGenerationAndReset() {
        int[] calls = {0};
        freezing.addListener(() -> calls[0]++);
        FreezingRegion region = freezing.install(new Rectangle(1, 1, 1, 1), 5);
        assertEquals(1, calls[0]);
        tick();
        assertEquals(2, calls[0]);
        freezing.release(region);
        assertEquals(3, calls[0]);
        freezing.releaseAll();                                     // nothing to release: no notification
        assertEquals(3, calls[0]);
        freezing.reset();
        assertEquals(4, calls[0]);
        assertThrows(IllegalArgumentException.class, () -> freezing.install(new Rectangle(1, 1, 1, 1), 0));
        assertEquals(4, calls[0], "a rejected install must not notify");
    }

    // --------------------------------------------- Composite: holdNextState

    @Test
    void holdNextStateReplacesOnlyThePendingStateUntilTransition() {
        set(new int[] {10, 10});
        figure();
        assertTrue(board.isAliveAt(10, 10));
        assertTrue(board.holdNextState(new Point(10, 10), true), "the computed death is overridden");
        assertTrue(board.isAliveAt(10, 10), "current state is not touched");
        assertFalse(board.holdNextState(new Point(10, 10), true), "already the pending state");
        board.transition();
        assertTrue(board.isAliveAt(10, 10));

        figure();
        assertFalse(board.holdNextState(new Point(40, 40), false), "an empty cell stays empty anyway");
        board.transition();
        assertFalse(board.isAliveAt(10, 10), "without a hold the cell follows the rule");
    }

    @Test
    void theNullObjectCellIgnoresHolds() {
        assertFalse(Cell.DUMMY.holdNextState(new Point(0, 0), true));
        assertFalse(Cell.DUMMY.isAlive());
    }

    @ParameterizedTest(name = "1x1 region at ({0},{1})")
    @CsvSource({"0,0", "63,0", "0,63", "63,63", "7,7", "8,8", "7,8", "31,32", "56,55"})
    void singleCellRegionsWorkAtCornersAndBlockBorders(int x, int y) {
        set(new int[] {x, y});
        freezing.install(new Rectangle(x, y, 1, 1), 4);
        for (int g = 1; g <= 4; g++) {
            tick();
            assertTrue(alive(x, y), "generation " + g);
            assertEquals(1, liveCount());
        }
        tick();
        assertEquals(0, liveCount());
    }

    @Test
    void aRegionAcrossFourNeighborhoodBlocksIsHeldAsOne() {
        // A blinker centered on the corner shared by four 8x8 blocks.
        set(new int[] {7, 8}, new int[] {8, 8}, new int[] {9, 8});
        boolean[][] installed = cells();
        freezing.install(new Rectangle(6, 6, 5, 5), 7);
        for (int g = 1; g <= 7; g++) {
            tick();
            assertArrayEquals(installed, cells(), "generation " + g);
        }
        tick();
        assertTrue(alive(8, 7) && alive(8, 8) && alive(8, 9) && !alive(7, 8), "blinker turns after release");
    }

    @Test
    void theWholeBoardCanBeFrozen() {
        Random random = new Random(11);
        List<int[]> live = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            if (random.nextInt(100) < 40) live.add(new int[] {x, y});
        set(live.toArray(new int[0][]));
        boolean[][] installed = cells();
        freezing.install(new Rectangle(0, 0, SIZE, SIZE), 5);
        for (int g = 1; g <= 5; g++) {
            tick();
            assertArrayEquals(installed, cells(), "generation " + g);
        }
        tick();
        assertArrayEquals(conway(installed), cells(), "the first free generation is plain Life");
    }

    @Test
    void aStillLifeInsideAndOutsideStaysTheSame() {
        set(new int[] {10, 10}, new int[] {11, 10}, new int[] {10, 11}, new int[] {11, 11},
                new int[] {40, 40}, new int[] {41, 40}, new int[] {40, 41}, new int[] {41, 41});
        boolean[][] installed = cells();
        freezing.install(new Rectangle(9, 9, 4, 4), 3);
        for (int g = 1; g <= 6; g++) {
            tick();
            assertArrayEquals(installed, cells(), "generation " + g);
        }
    }

    @Test
    void anEmptyRegionOnAnEmptyBoardDoesNothing() {
        freezing.install(new Rectangle(20, 20, 10, 10), 3);
        for (int g = 1; g <= 5; g++) {
            tick();
            assertEquals(0, liveCount());
        }
        assertTrue(freezing.regions().isEmpty());
    }

    @Test
    void aGliderIsStoppedByAFrozenEmptyWallAndStaysStoppedAfterRelease() {
        // Glider moving down-right toward a frozen empty column.
        set(new int[] {1, 0}, new int[] {2, 1}, new int[] {0, 2}, new int[] {1, 2}, new int[] {2, 2});
        freezing.install(new Rectangle(12, 0, 1, SIZE), 60);
        for (int g = 1; g <= 60; g++) {
            tick();
            for (int y = 0; y < SIZE; y++) assertFalse(alive(12, y), "wall cell born at generation " + g);
            for (int y = 0; y < SIZE; y++) for (int x = 13; x < SIZE; x++)
                assertFalse(alive(x, y), "cell crossed the wall at generation " + g);
        }

        // The glider broke up against the wall into a block, a still life, so
        // nothing crosses the column even after the wall is released.
        assertTrue(freezing.regions().isEmpty(), "the wall is released after 60 generations");
        assertEquals(4, liveCount());
        assertTrue(alive(10, 11) && alive(11, 11) && alive(10, 12) && alive(11, 12), "the wreck is a block");
        boolean[][] wreck = cells();
        for (int g = 61; g <= 100; g++) {
            tick();
            assertArrayEquals(wreck, cells(), "the block stays where it stopped at generation " + g);
        }
    }

    // ------------------------------------------- Color feature: age is held

    @Test
    void aFrozenCellKeepsItsAgeAndColorAndResumesAfterRelease() {
        set(new int[] {10, 10}, new int[] {11, 10}, new int[] {10, 11}, new int[] {11, 11},
                new int[] {40, 40}, new int[] {41, 40}, new int[] {40, 41}, new int[] {41, 41});
        tick(); tick();                                            // both blocks reach age 2
        assertEquals(PALETTE[2], board.colorAt(10, 10));
        freezing.install(new Rectangle(10, 10, 2, 2), 3);
        for (int g = 1; g <= 3; g++) {
            tick();
            assertEquals(PALETTE[2], board.colorAt(10, 10), "frozen cell must keep its color");
            assertEquals(PALETTE[2], board.colorAt(11, 11));
            assertEquals(PALETTE[2 + g], board.colorAt(40, 40), "free cell keeps aging");
        }
        tick();
        assertEquals(PALETTE[3], board.colorAt(10, 10), "age continues from where it stopped");
        assertEquals(PALETTE[6], board.colorAt(40, 40));
    }

    @Test
    void aFrozenDeadCellHasNoColorAndABirthAfterReleaseStartsAtAgeZero() {
        set(new int[] {30, 30}, new int[] {31, 30}, new int[] {30, 31});
        freezing.install(new Rectangle(31, 31, 1, 1), 2);
        tick(); tick();
        assertNull(board.colorAt(31, 31));
        tick();
        assertEquals(PALETTE[0], board.colorAt(31, 31), "born on the first free generation");
    }

    // ------------------------------------------------ Random differential

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233})
    void randomBoardsWithRandomInstallsAndReleasesMatchAnIndependentReference(long seed) {
        Random random = new Random(seed);
        boolean[][] alive = new boolean[SIZE][SIZE];
        int[][] age = new int[SIZE][SIZE];
        List<int[]> live = new ArrayList<>();
        int density = 15 + random.nextInt(35);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            if (random.nextInt(100) < density) { alive[y][x] = true; live.add(new int[] {x, y}); }
        set(live.toArray(new int[0][]));

        List<Rectangle> areas = new ArrayList<>();                 // reference regions
        List<int[]> remaining = new ArrayList<>();
        for (int g = 1; g <= 250; g++) {
            // Random user actions before the generation.
            if (random.nextInt(4) == 0) {
                Rectangle area = new Rectangle(random.nextInt(SIZE), random.nextInt(SIZE),
                        1 + random.nextInt(14), 1 + random.nextInt(14)).intersection(new Rectangle(0, 0, SIZE, SIZE));
                int duration = 1 + random.nextInt(12);
                boolean overlaps = areas.stream().anyMatch(area::intersects);
                if (overlaps) {
                    assertThrows(IllegalArgumentException.class, () -> freezing.install(area, duration));
                } else {
                    freezing.install(area, duration);
                    areas.add(area);
                    remaining.add(new int[] {duration});
                }
            }
            if (!areas.isEmpty() && random.nextInt(9) == 0) {
                int index = random.nextInt(areas.size());
                Rectangle target = areas.get(index);
                FreezingRegion region = freezing.regions().stream()
                        .filter(r -> r.area().equals(target)).findFirst().orElseThrow();
                freezing.release(region);
                areas.remove(index);
                remaining.remove(index);
            }
            if (random.nextInt(60) == 0) {
                freezing.releaseAll();
                areas.clear();
                remaining.clear();
            }

            // Reference generation: plain Life, then frozen cells keep state and age.
            boolean[][] next = conway(alive);
            boolean[][] held = new boolean[SIZE][SIZE];
            for (Rectangle area : areas)
                for (int y = area.y; y < area.y + area.height; y++)
                    for (int x = area.x; x < area.x + area.width; x++) {
                        next[y][x] = alive[y][x];
                        held[y][x] = true;
                    }
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
                if (!held[y][x]) age[y][x] = alive[y][x] && next[y][x] ? age[y][x] + 1 : 0;
            alive = next;
            for (int i = areas.size() - 1; i >= 0; i--)
                if (--remaining.get(i)[0] == 0) { areas.remove(i); remaining.remove(i); }

            tick();

            assertEquals(areas.size(), freezing.regions().size(), "regions, seed " + seed + " generation " + g);
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                assertEquals(alive[y][x], board.isAliveAt(y, x),
                        "cell (" + x + "," + y + "), seed " + seed + " generation " + g);
                assertEquals(alive[y][x] ? PALETTE[age[y][x] % PALETTE.length] : null, board.colorAt(y, x),
                        "age of (" + x + "," + y + "), seed " + seed + " generation " + g);
            }
        }
    }

    // --------------------------------------------------------------- helpers

    private void figure() {
        board.figureNextState(Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY,
                Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY);
    }

    /** Same order as the Universe clock listener. */
    private void tick() {
        figure();
        freezing.beforeTransition(board);
        board.transition();
        generation++;
        freezing.afterGeneration();
    }

    /** Loads live cells through the Memento protocol (same path as Grid > Load): every age starts at 0. */
    private void set(int[]... cells) {
        Cell.Memento state = (Cell.Memento) board.createMemento();
        for (int[] c : cells) state.markAsAlive(new Point(c[0], c[1]));
        board.clear();
        board.transfer(state, new Point(0, 0), Cell.LOAD);
    }

    private boolean alive(int x, int y) { return board.isAliveAt(y, x); }

    /** The region's current state object. The game reads this field itself. */
    private static FreezingState stateOf(FreezingRegion region) {
        try {
            java.lang.reflect.Field state = FreezingRegion.class.getDeclaredField("state");
            state.setAccessible(true);
            return (FreezingState) state.get(region);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private boolean[][] cells() {
        boolean[][] cells = new boolean[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) cells[y][x] = board.isAliveAt(y, x);
        return cells;
    }

    private int liveCount() {
        int count = 0;
        for (boolean[] row : cells()) for (boolean cell : row) if (cell) count++;
        return count;
    }

    static boolean[][] conway(boolean[][] cells) {
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
}
