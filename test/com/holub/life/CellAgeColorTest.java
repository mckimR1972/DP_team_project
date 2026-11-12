package com.holub.life;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import com.holub.ui.Colors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CellAgeColorTest {
    private static final int CELL = 8;                     // one cell is 8 pixels wide
    private static final int SIZE = 64;                    // the board is 64 x 64 cells
    private static final int BOARD = SIZE * CELL;
    private static final Color BLUE = new Color(0x0072B2);
    private static final Color GREEN = new Color(0x009E73);
    private static final Color DEAD = Colors.LIGHT_YELLOW;

    // The same structure as Universe: 8 x 8 blocks of 8 x 8 cells.
    private final Cell board = new Neighborhood(8, new Neighborhood(8, new Resident()));

    @AfterEach
    void restoreOriginalColor() {                          // every cell shares the policy
        Resident.setColorPolicy(new FixedColorPolicy(Color.RED));
    }

    @Test
    void stillLifeChangesColorEveryTenGenerationsAndWrapsAround() {
        useCyclingColors();
        placeBlock(0, 0);

        tick(9);
        assertEquals(Color.RED, colorAt(0, 0));
        tick(1);
        assertEquals(BLUE, colorAt(0, 0));                 // generation 10
        tick(10);
        assertEquals(GREEN, colorAt(0, 0));                // generation 20
        tick(10);
        assertEquals(Color.RED, colorAt(0, 0));            // generation 30: back to the first color
    }

    @Test
    void survivorAgesNewbornStartsAtZeroAndRebornCellStartsOver() {
        // With a period of 1 every age has its own color, so the drawn color is the age.
        Color age0 = Color.RED, age1 = BLUE, age2 = GREEN;
        Resident.setColorPolicy(new CyclingColorPolicy(1, age0, age1, age2));
        click(20, 20); click(21, 20); click(22, 20);       // horizontal blinker

        tick(1);
        assertEquals(age1, colorAt(21, 20));               // center survived
        assertEquals(DEAD, colorAt(20, 20));               // left end died
        assertEquals(age0, colorAt(21, 19));               // top end was born

        tick(1);
        assertEquals(age2, colorAt(21, 20));
        assertEquals(age0, colorAt(20, 20));               // born again: starts over
    }

    @Test
    void cellRevivedByClickStartsAtAgeZero() {
        useCyclingColors();
        placeBlock(10, 10);
        tick(20);
        assertEquals(GREEN, colorAt(10, 10));

        click(10, 10);                                     // kill the cell
        click(10, 10);                                     // and bring it back before the next tick

        assertEquals(Color.RED, colorAt(10, 10));
        assertEquals(GREEN, colorAt(11, 10));              // the other three cells keep their age
        assertEquals(GREEN, colorAt(10, 11));
        assertEquals(GREEN, colorAt(11, 11));
    }

    @Test
    void storeThenLoadKeepsCellsAndRestartsColor() throws IOException {
        useCyclingColors();
        placeBlock(10, 10);
        tick(20);
        assertEquals(GREEN, colorAt(10, 10));
        boolean[] block = liveCells();

        // Grid > Store and Grid > Load in the order Universe runs them,
        // with a byte array in place of the file.
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        board.createMemento().flush(file);
        board.clear();
        Storable loaded = board.createMemento();
        loaded.load(new ByteArrayInputStream(file.toByteArray()));
        board.transfer(loaded, new Point(0, 0), Cell.LOAD);

        assertArrayEquals(block, liveCells());             // the same four cells came back
        assertEquals(Color.RED, colorAt(10, 10));          // ages are not saved
        assertEquals(Color.RED, colorAt(11, 10));
        assertEquals(Color.RED, colorAt(10, 11));
        assertEquals(Color.RED, colorAt(11, 11));

        tick(10);
        assertEquals(BLUE, colorAt(10, 10));               // and they grow older again
    }

    @Test
    void cellClickedAfterClearStartsRed() {
        useCyclingColors();
        placeBlock(10, 10);
        tick(20);
        assertEquals(GREEN, colorAt(10, 10));

        board.clear();                                     // Grid > Clear
        assertArrayEquals(new boolean[SIZE * SIZE], liveCells());
        assertEquals(DEAD, colorAt(10, 10));

        click(10, 10);
        assertEquals(Color.RED, colorAt(10, 10));          // the age left by Clear does not show
    }

    @Test
    void lifeRuleIsUnchanged() {
        // The four kinds of random soup used in the design document's experiments,
        // as {soup size, percent alive}: big soups keep changing, small ones settle fast.
        int[][] soups = { {64, 30}, {24, 35}, {10, 40}, {4, 50} };
        for (int[] soup : soups) {
            for (long seed = 1; seed <= 8; seed++) {
                Cell soupBoard = new Neighborhood(8, new Neighborhood(8, new Resident()));
                boolean[] expected = placeSoup(soupBoard, soup[0], soup[1], seed);
                for (int generation = 1; generation <= 100; generation++) {
                    tick(soupBoard);
                    expected = nextGeneration(expected);
                    assertArrayEquals(expected, liveCells(soupBoard),
                        soup[0] + "x" + soup[0] + " soup, seed " + seed + ", generation " + generation);
                }
            }
        }
    }

    @Test
    void setColorPolicyRejectsNullAndKeepsThePreviousPolicy() {
        useCyclingColors();
        placeBlock(10, 10);
        tick(10);

        assertThrows(IllegalArgumentException.class, () -> Resident.setColorPolicy(null));
        assertEquals(BLUE, colorAt(10, 10));               // still drawn with the cycling rule
    }

    private static void useCyclingColors() {               // the rule the game cycles with
        Resident.setColorPolicy(new CyclingColorPolicy(10, Color.RED, BLUE, GREEN));
    }

    private void placeBlock(int x, int y) {                // 2 x 2 block: a pattern that never changes
        click(x, y); click(x + 1, y); click(x, y + 1); click(x + 1, y + 1);
    }

    private void click(int x, int y) {
        click(board, x, y);
    }

    private static void click(Cell board, int x, int y) {
        board.userClicked(new Point(x * CELL + 4, y * CELL + 4), new Rectangle(0, 0, BOARD, BOARD));
    }

    private void tick(int generations) {
        for (int i = 0; i < generations; i++) {
            tick(board);
        }
    }

    private static void tick(Cell board) {                 // one generation: figure the next state, then switch to it
        board.figureNextState(Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY,
                              Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY);
        board.transition();
    }

    private Color colorAt(int x, int y) {                  // draw the board and read the middle of the cell
        BufferedImage image = new BufferedImage(BOARD, BOARD, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        board.redraw(g, new Rectangle(0, 0, BOARD, BOARD), true);
        g.dispose();
        return new Color(image.getRGB(x * CELL + 4, y * CELL + 4));
    }

    private boolean[] liveCells() {
        return liveCells(board);
    }

    // Reads the live cells the way Grid > Store does: transfer() into a memento.
    private static boolean[] liveCells(Cell board) {
        boolean[] alive = new boolean[SIZE * SIZE];
        board.transfer(new Cell.Memento() {
            @Override
            public void markAsAlive(Point location) {
                alive[location.y * SIZE + location.x] = true;
            }

            @Override
            public boolean isAlive(Point location) {
                return alive[location.y * SIZE + location.x];
            }

            @Override
            public void load(InputStream in) {
            }

            @Override
            public void flush(OutputStream out) {
            }
        }, new Point(0, 0), Cell.STORE);
        return alive;
    }

    private static boolean[] placeSoup(Cell board, int size, int percentAlive, long seed) {
        boolean[] alive = new boolean[SIZE * SIZE];
        Random random = new Random(seed);
        int offset = (SIZE - size) / 2;                    // centered on the board
        for (int y = offset; y < offset + size; y++) {
            for (int x = offset; x < offset + size; x++) {
                if (random.nextInt(100) < percentAlive) {
                    click(board, x, y);
                    alive[y * SIZE + x] = true;
                }
            }
        }
        return alive;
    }

    // Conway's rule computed directly, on a 64 x 64 board with dead cells all around it.
    private static boolean[] nextGeneration(boolean[] alive) {
        boolean[] next = new boolean[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int neighbors = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dy != 0) && isOnBoard(x + dx, y + dy) && alive[(y + dy) * SIZE + x + dx]) {
                            neighbors++;
                        }
                    }
                }
                next[y * SIZE + x] = neighbors == 3 || (alive[y * SIZE + x] && neighbors == 2);
            }
        }
        return next;
    }

    private static boolean isOnBoard(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }
}
