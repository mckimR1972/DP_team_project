package com.holub.life;

/** An immutable, rectangular pattern. Empty cells are transparent when placed. */
public final class PatternTemplate {
    public static final int MAX_SIZE = 64;
    private final String name;
    private final boolean[][] cells;

    public PatternTemplate(String name, boolean[][] cells) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 80
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("이름은 줄바꿈 없이 1~80자로 입력하세요.");
        }
        if (cells == null || cells.length == 0 || cells.length > MAX_SIZE
                || cells[0] == null || cells[0].length == 0 || cells[0].length > MAX_SIZE) {
            throw new IllegalArgumentException("패턴 크기는 1~64칸이어야 합니다.");
        }
        this.name = name.trim();
        this.cells = new boolean[cells.length][cells[0].length];
        for (int y = 0; y < cells.length; y++) {
            if (cells[y] == null || cells[y].length != width()) {
                throw new IllegalArgumentException("패턴의 모든 행은 길이가 같아야 합니다.");
            }
            System.arraycopy(cells[y], 0, this.cells[y], 0, width());
        }
    }

    public static PatternTemplate fromRows(String name, String... rows) {
        if (rows.length == 0) throw new IllegalArgumentException("빈 패턴입니다.");
        boolean[][] cells = new boolean[rows.length][];
        for (int y = 0; y < rows.length; y++) {
            cells[y] = new boolean[rows[y].length()];
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                if (c != '.' && c != 'O') throw new IllegalArgumentException("잘못된 셀 문자입니다.");
                cells[y][x] = c == 'O';
            }
        }
        return new PatternTemplate(name, cells);
    }

    public String name() { return name; }
    public int width() { return cells[0].length; }
    public int height() { return cells.length; }
    public boolean isAlive(int x, int y) { return cells[y][x]; }
    public int liveCount() {
        int count = 0;
        for (boolean[] row : cells) for (boolean alive : row) if (alive) count++;
        return count;
    }
    @Override public String toString() { return name + "  (" + width() + " x " + height() + ")"; }
}
