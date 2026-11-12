package com.holub.life;

import com.holub.ui.MenuSite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class UniverseColorTest {
    private static final Color BLUE = new Color(0x0072B2);
    private static final Color GREEN = new Color(0x009E73);
    private static final Map<String, ActionListener> menuActions = new HashMap<>();
    private static Universe universe;
    private static Clock.Listener clockListener;

    @BeforeAll
    static void initializeUniverseWithoutOpeningWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Clock stoppedClock = mock(Clock.class);
            // Capture the real menu callbacks and clock listener without creating a JFrame or timer.
            try (MockedStatic<MenuSite> menus = mockStatic(MenuSite.class);
                 MockedStatic<Clock> clocks = mockStatic(Clock.class)) {
                clocks.when(Clock::instance).thenReturn(stoppedClock);
                menus.when(() -> MenuSite.addLine(
                    any(), anyString(), anyString(), any(ActionListener.class)
                )).thenAnswer(invocation -> {
                    menuActions.put(invocation.getArgument(1) + " > " + invocation.getArgument(2),
                                    invocation.getArgument(3));
                    return null;
                });
                universe = Universe.instance();
                universe.setSize(universe.getPreferredSize());

                ArgumentCaptor<Clock.Listener> registered = ArgumentCaptor.forClass(Clock.Listener.class);
                verify(stoppedClock).addClockListener(registered.capture());
                clockListener = registered.getValue();
            }
            assertNotNull(menuActions.get("Grid > Clear"));
        });
    }

    @BeforeEach
    @AfterEach
    void clearBoardAndRestoreOriginalColor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Resident.setColorPolicy(new FixedColorPolicy(Color.RED));
            select("Grid > Clear");
        });
    }

    @Test
    void stillLifeStaysRedWhileCyclingIsOff() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            placeBlock();
            tick(30);
        });

        assertEquals(Color.RED, colorAt(0, 0));
    }

    @Test
    void stillLifeCyclesThroughColorsWhenNothingElseChanges() throws Exception {
        // The test sets the rule itself, so it checks Universe's tick and not a menu.
        SwingUtilities.invokeAndWait(() -> {
            Resident.setColorPolicy(new CyclingColorPolicy(10, Color.RED, BLUE, GREEN));
            placeBlock();
            tick(10);
        });
        assertEquals(BLUE, colorAt(0, 0));                 // no cell was born or died, yet the color moved on

        SwingUtilities.invokeAndWait(() -> tick(10));
        assertEquals(GREEN, colorAt(0, 0));

        SwingUtilities.invokeAndWait(() -> tick(10));
        assertEquals(Color.RED, colorAt(0, 0));
    }

    private static void placeBlock() {                     // 2 x 2 block: a pattern that never changes
        click(0, 0); click(1, 0); click(0, 1); click(1, 1);
    }

    private static void click(int x, int y) {
        universe.dispatchEvent(new MouseEvent(
            universe, MouseEvent.MOUSE_PRESSED, 0L, 0, x * 8 + 1, y * 8 + 1, 1, false, MouseEvent.BUTTON1
        ));
    }

    private static void tick(int times) {                  // the listener that Universe gave the clock
        for (int i = 0; i < times; i++) {
            clockListener.tick();
        }
    }

    private static void select(String menuItem) {
        menuActions.get(menuItem).actionPerformed(
            new ActionEvent(universe, ActionEvent.ACTION_PERFORMED, menuItem)
        );
    }

    private static Color colorAt(int x, int y) throws Exception {   // paint the board and read the middle of the cell
        Color[] color = new Color[1];
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(universe.getWidth(), universe.getHeight(),
                                                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            universe.paint(g);
            g.dispose();
            color[0] = new Color(image.getRGB(x * 8 + 4, y * 8 + 4));
        });
        return color[0];
    }
}
