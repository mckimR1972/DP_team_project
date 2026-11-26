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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellAgeColorTest {
    private static final int CELL = 8;                     // 셀 한 칸 8px
    private static final int SIZE = 64;                    // 판은 64 x 64칸
    private static final int BOARD = SIZE * CELL;
    private static final Color BLUE = new Color(0x0072B2);
    private static final Color GREEN = new Color(0x009E73);
    private static final Color DEAD = Colors.LIGHT_YELLOW;

    // Universe와 같은 구조 (8 x 8 셀짜리 블록 8 x 8개)
    private final Cell board = new Neighborhood(8, new Neighborhood(8, new Resident()));

    @AfterEach
    void restoreOriginalColor() {                          // 색 규칙은 모든 셀이 공유하므로 되돌려 둔다
        Resident.setColorPolicy(new FixedColorPolicy(Color.RED));
    }

    @Test
    void captureCopiesOnlyLivingCellsRegardlessOfColorAndAge() {
        useCyclingColors();
        placeBlock(0, 0);
        tick(10);
        assertEquals(BLUE, colorAt(0, 0));
        BoardEditor editor = new BoardEditor(board, kind -> { });
        PatternTemplate blue = editor.capture("blue", new Rectangle(0, 0, 3, 3));
        tick(10);
        assertEquals(GREEN, colorAt(0, 0));
        PatternTemplate green = editor.capture("green", new Rectangle(0, 0, 3, 3));
        assertEquals(4, blue.liveCount());
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                assertEquals(blue.isAlive(x, y), green.isAlive(x, y));
        editor.place(blue, new Point(10, 10));
        assertEquals(Color.RED, colorAt(10, 10));
        assertEquals(GREEN, colorAt(0, 0));
        assertEquals(DEAD, colorAt(12, 12));
    }

    @Test
    void stillLifeChangesColorEveryTenGenerationsAndWrapsAround() {
        useCyclingColors();
        placeBlock(0, 0);

        tick(9);
        assertEquals(Color.RED, colorAt(0, 0));
        tick(1);
        assertEquals(BLUE, colorAt(0, 0));                 // 10세대
        tick(10);
        assertEquals(GREEN, colorAt(0, 0));                // 20세대
        tick(10);
        assertEquals(Color.RED, colorAt(0, 0));            // 30세대: 다시 처음 색
    }

    @Test
    void survivorAgesNewbornStartsAtZeroAndRebornCellStartsOver() {
        // 주기가 1이면 나이마다 색이 달라서 색으로 나이를 알 수 있다
        Color age0 = Color.RED, age1 = BLUE, age2 = GREEN;
        Resident.setColorPolicy(new CyclingColorPolicy(1, age0, age1, age2));
        click(20, 20); click(21, 20); click(22, 20);       // 가로 블링커

        tick(1);
        assertEquals(age1, colorAt(21, 20));               // 가운데: 살아남음
        assertEquals(DEAD, colorAt(20, 20));               // 왼쪽 끝: 죽음
        assertEquals(age0, colorAt(21, 19));               // 위: 새로 태어남

        tick(1);
        assertEquals(age2, colorAt(21, 20));
        assertEquals(age0, colorAt(20, 20));               // 다시 태어나면 0부터
    }

    @Test
    void cellRevivedByClickStartsAtAgeZero() {
        useCyclingColors();
        placeBlock(10, 10);
        tick(20);
        assertEquals(GREEN, colorAt(10, 10));

        click(10, 10);                                     // 죽였다가
        click(10, 10);                                     // 다음 틱 전에 다시 살림

        assertEquals(Color.RED, colorAt(10, 10));
        assertEquals(GREEN, colorAt(11, 10));              // 나머지 세 칸은 나이 그대로
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

        // Universe의 Grid > Store, Load와 같은 순서 (파일 대신 바이트 배열)
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        board.createMemento().flush(file);
        board.clear();
        Storable loaded = board.createMemento();
        loaded.load(new ByteArrayInputStream(file.toByteArray()));
        board.transfer(loaded, new Point(0, 0), Cell.LOAD);

        assertArrayEquals(block, liveCells());             // 같은 네 칸이 돌아옴
        assertEquals(Color.RED, colorAt(10, 10));          // 나이는 저장되지 않음
        assertEquals(Color.RED, colorAt(11, 10));
        assertEquals(Color.RED, colorAt(10, 11));
        assertEquals(Color.RED, colorAt(11, 11));

        tick(10);
        assertEquals(BLUE, colorAt(10, 10));               // 다시 나이를 먹음
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
        assertEquals(Color.RED, colorAt(10, 10));          // Clear 전 나이가 드러나지 않음
    }

    @Test
    void lifeRuleIsUnchanged() {
        // 설계 문서 실험의 무작위 수프 {크기, 생존 %}: 크면 오래 변하고 작으면 금방 멈춘다
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
        assertEquals(BLUE, colorAt(10, 10));               // 순환 규칙 그대로
    }

    @Test
    void colorChangesWithAgeFollowsTheCurrentPolicy() {
        useCyclingColors();
        assertTrue(Resident.colorChangesWithAge());

        Resident.setColorPolicy(new FixedColorPolicy(Color.RED));
        assertFalse(Resident.colorChangesWithAge());
    }

    private static void useCyclingColors() {               // Color 메뉴의 순환 규칙과 같음
        Resident.setColorPolicy(new CyclingColorPolicy(10, Color.RED, BLUE, GREEN));
    }

    private void placeBlock(int x, int y) {                // 2 x 2 블록: 변하지 않는 패턴
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

    private static void tick(Cell board) {                 // 한 세대 진행
        board.figureNextState(Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY,
                              Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY);
        board.transition();
    }

    private Color colorAt(int x, int y) {                  // 판을 그린 뒤 셀 가운데 색을 읽음
        BufferedImage image = new BufferedImage(BOARD, BOARD, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        board.redraw(g, new Rectangle(0, 0, BOARD, BOARD), true);
        g.dispose();
        Color rendered = new Color(image.getRGB(x * CELL + 4, y * CELL + 4));
        if (board.isAliveAt(y, x)) assertEquals(rendered, board.colorAt(y, x));
        else assertNull(board.colorAt(y, x));
        return rendered;
    }

    private boolean[] liveCells() {
        return liveCells(board);
    }

    // Grid > Store처럼 transfer()로 살아 있는 셀을 읽음
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
        int offset = (SIZE - size) / 2;                    // 판 가운데에 둠
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

    // 콘웨이 규칙을 직접 계산 (판 바깥은 죽은 셀)
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
