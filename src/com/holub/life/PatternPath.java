package com.holub.life;

import java.awt.Point;
import java.util.function.Consumer;

/** Visits cell anchors between mouse samples, including both endpoints. */
final class PatternPath {
    private PatternPath() { }

    static void visit(Point from, Point to, Consumer<Point> visitor) {
        int dx = to.x - from.x, dy = to.y - from.y;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) { visitor.accept(new Point(to)); return; }
        for (int step = 0; step <= steps; step++) {
            visitor.accept(new Point(from.x + (int)Math.round((double)dx * step / steps),
                    from.y + (int)Math.round((double)dy * step / steps)));
        }
    }
}
