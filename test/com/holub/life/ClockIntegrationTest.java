package com.holub.life;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.holub.life.BoardChangeEvent.Kind.GENERATION;
import static org.junit.jupiter.api.Assertions.*;

class ClockIntegrationTest {
    private final List<BoardChangeListener> subscriptions = new ArrayList<>();

    @BeforeAll
    static void initialize() throws Exception { LifeTestSupport.initialize(); }

    @BeforeEach
    void reset() throws Exception { LifeTestSupport.resetBoard(); }

    @AfterEach
    void stopAndDetach() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.clock.stop();
            subscriptions.forEach(LifeTestSupport.universe::removeBoardChangeListener);
        });
        LifeTestSupport.resetBoard();
    }

    @Test
    void singleStepMenuUsesTheSharedGenerationPathOnEdt() throws Exception {
        LifeTestSupport.onEdt(() -> {
            List<BoardChangeEvent> events = new ArrayList<>();
            listen(event -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                events.add(event);
            });
            LifeTestSupport.select("Go", "Tick (Single Step)");
            assertEquals(1, events.size());
            assertEquals(1, events.get(0).generation());
            assertTrue(events.get(0).copyAliveCells().isEmpty());
        });
    }

    @Test
    void automaticTimerPublishesOrderedGenerationsOnEdtAndStops() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        List<Long> generations = new ArrayList<>();
        AtomicBoolean allOnEdt = new AtomicBoolean(true);
        LifeTestSupport.onEdt(() -> {
            listen(event -> {
                if (!SwingUtilities.isEventDispatchThread()) allOnEdt.set(false);
                generations.add(event.generation());
                if (generations.size() == 3) {
                    LifeTestSupport.clock.stop();
                    completed.countDown();
                }
            });
            LifeTestSupport.clock.startTicking(15);
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS), "Timer must complete three generations");
        awaitEdtDelay(80);
        LifeTestSupport.onEdt(() -> assertEquals(List.of(1L, 2L, 3L), generations));
        assertTrue(allOnEdt.get());
    }

    @Test
    void haltBeforeFirstDeliveryCancelsPendingAutomaticTick() throws Exception {
        List<BoardChangeEvent> events = new ArrayList<>();
        LifeTestSupport.onEdt(() -> {
            listen(events::add);
            LifeTestSupport.clock.startTicking(10);
            LifeTestSupport.select("Go", "Halt");
        });
        awaitEdtDelay(80);
        LifeTestSupport.onEdt(() -> assertTrue(events.isEmpty()));
    }

    @Test
    void changingSpeedRestartsOneTimerWithoutLeavingOldTicksRunning() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        List<Long> generations = new ArrayList<>();
        LifeTestSupport.onEdt(() -> {
            listen(event -> {
                generations.add(event.generation());
                if (generations.size() == 1) LifeTestSupport.clock.startTicking(10);
                else if (generations.size() == 2) {
                    LifeTestSupport.clock.stop();
                    completed.countDown();
                }
            });
            LifeTestSupport.clock.startTicking(150);
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        awaitEdtDelay(200);
        LifeTestSupport.onEdt(() -> assertEquals(List.of(1L, 2L), generations));
    }

    @Test
    void selectedMenuSuppressesTicksUntilSelectionEnds() throws Exception {
        LifeTestSupport.onEdt(() -> {
            List<BoardChangeEvent> events = new ArrayList<>();
            listen(events::add);
            MenuSelectionManager menus = MenuSelectionManager.defaultManager();
            menus.setSelectedPath(new MenuElement[] {new JMenuItem("Selected")});
            LifeTestSupport.clock.tick();
            assertTrue(events.isEmpty());
            menus.clearSelectedPath();
            LifeTestSupport.clock.tick();
            assertEquals(1, events.size());
            assertEquals(1, events.get(0).generation());
        });
    }

    @Test
    void clockControlRejectsCallsOutsideEdt() {
        assertFalse(SwingUtilities.isEventDispatchThread());
        assertThrows(IllegalStateException.class, () -> LifeTestSupport.clock.tick());
        assertThrows(IllegalStateException.class, () -> LifeTestSupport.clock.startTicking(20));
        assertThrows(IllegalStateException.class, () -> LifeTestSupport.clock.stop());
    }

    private void listen(Consumer<BoardChangeEvent> callback) {
        BoardChangeListener listener = event -> {
            if (event.kind() == GENERATION) callback.accept(event);
        };
        subscriptions.add(listener);
        LifeTestSupport.universe.addBoardChangeListener(listener);
    }

    private static void awaitEdtDelay(int milliseconds) throws Exception {
        CountDownLatch marker = new CountDownLatch(1);
        LifeTestSupport.onEdt(() -> {
            Timer delay = new Timer(milliseconds, event -> marker.countDown());
            delay.setRepeats(false);
            delay.start();
        });
        assertTrue(marker.await(5, TimeUnit.SECONDS), "EDT must process the delayed marker");
    }
}
