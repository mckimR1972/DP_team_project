package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentTest {
    @ParameterizedTest(name = "alive={0}, neighbors={1}: unchanged")
    @CsvSource({
        "false, 0", "false, 1", "false, 2", "false, 4",
        "false, 5", "false, 6", "false, 7", "false, 8",
        "true, 2", "true, 3"
    })
    void transitionReturnsFalseWhenUnchanged(boolean alive, int neighborCount) {
        Resident cell = prepareNextState(alive, neighborCount);

        assertFalse(cell.transition(), "An unchanged cell must report false");
        assertEquals(alive, cell.isAlive());
    }

    @ParameterizedTest(name = "alive={0}, neighbors={1}: changed")
    @CsvSource({
        "false, 3", "true, 0", "true, 1", "true, 4",
        "true, 5", "true, 6", "true, 7", "true, 8"
    })
    void transitionReturnsTrueWhenChanged(boolean alive, int neighborCount) {
        Resident cell = prepareNextState(alive, neighborCount);

        assertTrue(cell.transition(), "A changed cell must report true");
        assertEquals(!alive, cell.isAlive());
    }

    private Resident prepareNextState(boolean alive, int neighborCount) {
        Resident cell = resident(alive);
        Cell[] neighbors = new Cell[8];
        for (int i = 0; i < neighbors.length; i++) {
            neighbors[i] = resident(i < neighborCount);
        }
        cell.figureNextState(
            neighbors[0], neighbors[1], neighbors[2], neighbors[3],
            neighbors[4], neighbors[5], neighbors[6], neighbors[7]
        );
        assertEquals(alive, cell.isAlive(), "Calculation must not change the current state");
        return cell;
    }

    private Resident resident(boolean alive) {
        Resident cell = new Resident();
        if (alive) {
            cell.userClicked(new Point(0, 0), new Rectangle(0, 0, 8, 8));
        }
        return cell;
    }
}
