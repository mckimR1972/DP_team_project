package com.holub.life;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Resident의 기본 색 규칙을 확인하므로 여기서는 규칙을 정하지 않는다.
// forkEvery = 1이라 다른 테스트 클래스가 먼저 규칙을 바꿔 놓을 수 없다.
class DefaultCellColorTest {
    private static final int CELL = 8;                     // 셀 한 칸 8px
    private static final int BOARD = 64 * CELL;

    private final Cell board = new Neighborhood(8, new Neighborhood(8, new Resident()));

    @Test
    void liveCellsStayRedWhenNoColorPolicyIsChosen() {
        click(0, 0); click(1, 0); click(0, 1); click(1, 1);        // 2 x 2 블록

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

    private Color colorAt(int x, int y) {                  // 판을 그린 뒤 셀 가운데 색을 읽음
        BufferedImage image = new BufferedImage(BOARD, BOARD, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        board.redraw(g, new Rectangle(0, 0, BOARD, BOARD), true);
        g.dispose();
        return new Color(image.getRGB(x * CELL + 4, y * CELL + 4));
    }
}
