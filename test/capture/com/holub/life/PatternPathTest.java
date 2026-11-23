package com.holub.life;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class PatternPathTest {
    @ParameterizedTest
    @CsvSource({"1,1,9,1", "9,1,1,1", "1,9,1,1", "1,1,1,9", "1,1,9,9", "9,9,1,1", "1,1,9,4", "9,4,1,1", "3,3,3,3"})
    void visitsEveryStepWithoutGaps(int x1, int y1, int x2, int y2) {
        List<Point> path = new ArrayList<>();
        Point start = new Point(x1, y1), end = new Point(x2, y2);
        PatternPath.visit(start, end, path::add);
        assertEquals(start, path.get(0));
        assertEquals(end, path.get(path.size() - 1));
        assertEquals(Math.max(Math.abs(x2-x1), Math.abs(y2-y1)) + 1, path.size());
        for (int i = 1; i < path.size(); i++) {
            assertEquals(1, Math.max(Math.abs(path.get(i).x-path.get(i-1).x), Math.abs(path.get(i).y-path.get(i-1).y)));
        }
    }
}
