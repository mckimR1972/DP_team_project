package com.holub.life;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Checks the color rule that Resident starts with, so this class never sets one.
// Gradle runs every test class in its own JVM (forkEvery = 1), so no other
// test can have changed the rule before this one runs.
class DefaultCellColorTest {
    private static final int CELL = 8;                     // one cell is 8 pixels wide
    private static final int BOARD = 64 * CELL;

    private final Cell board = new Neighborhood(8, new Neighborhood(8, new Resident()));

    @Test
    void liveCellsStayRedWhenNoColorPolicyIsChosen() {
        click(0, 0); click(1, 0); click(0, 1); click(1, 1);        // 2 x 2 block

        assertEquals(Color.RED, colorAt(0, 0), "generation 0");
        for (int generation = 1; generation <= 25; generation++) {
            board.figureNextState(Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY,
                                  Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY);
            board.transition();
            assertEquals(Color.RED, colorAt(0, 0), "generation " + generation);
        }
    }

    private void click(int x, int y) {
        board.userClicked(new Point(x * CELL + 4, y * CELL + 4), new Rectangle(0, 0, BOARD, BOARD));
    }

    private Color colorAt(int x, int y) {                  // draw the board and read the middle of the cell
        BufferedImage image = new BufferedImage(BOARD, BOARD, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        board.redraw(g, new Rectangle(0, 0, BOARD, BOARD), true);
        g.dispose();
        return new Color(image.getRGB(x * CELL + 4, y * CELL + 4));
    }
}
