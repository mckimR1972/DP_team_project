package com.holub.life;

import com.holub.life.sound.SoundEffects;
import com.holub.ui.MenuSite;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import static com.holub.life.BoardChangeEvent.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Universe, Statistics and SoundEffects; MIDI and the scheduler are controlled substitutes. */
class SoundEffectsIntegrationTest {
    private record ScheduledNote(Runnable command, long delay) { }
    private record Played(int channel, int pitch, int velocity) { }
    private final List<ScheduledNote> jobs = new ArrayList<>();
    private final List<Played> played = new ArrayList<>();
    private final Map<String, ActionListener> actions = new HashMap<>();
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final Synthesizer synth = mock(Synthesizer.class);
    private final MidiChannel[] channels = new MidiChannel[16];
    private final List<Statistics.Summary> summaries = new ArrayList<>();
    private final Statistics statistics = new Statistics(summaries::add);
    private BoardChangeListener sound;
    private Runnable sweepStep;
    private boolean runFirstStepImmediately;

    @BeforeAll static void initialize() throws Exception { LifeTestSupport.initialize(); }

    @BeforeEach void prepareRealObservers() throws Exception {
        LifeTestSupport.resetBoard();
        for (int channel = 0; channel < channels.length; channel++) {
            int index = channel;
            channels[channel] = mock(MidiChannel.class);
            doAnswer(call -> {
                played.add(new Played(index, call.getArgument(0), call.getArgument(1)));
                return null;
            }).when(channels[channel]).noteOn(anyInt(), anyInt());
        }
        when(synth.getChannels()).thenReturn(channels);
        doAnswer(call -> {
            jobs.add(new ScheduledNote(call.getArgument(0), call.getArgument(1)));
            return null;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        doAnswer(call -> {
            sweepStep = call.getArgument(0);
            assertEquals(0L, (long) call.getArgument(1));
            assertEquals(150L, (long) call.getArgument(2));
            if (runFirstStepImmediately) sweepStep.run();
            return null;
        }).when(scheduler).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(0, 0);
            LifeTestSupport.universe.addBoardChangeListener(statistics);
            installSound();
        });
        assertNotNull(sound);
        assertNotNull(sweepStep);
    }

    private void installSound() {
        try (MockedStatic<MidiSystem> midi = mockStatic(MidiSystem.class);
             MockedStatic<Executors> executors = mockStatic(Executors.class);
             MockedStatic<MenuSite> menus = mockStatic(MenuSite.class)) {
            midi.when(MidiSystem::getSynthesizer).thenReturn(synth);
            executors.when(() -> Executors.newSingleThreadScheduledExecutor(any(ThreadFactory.class)))
                    .thenReturn(scheduler);
            menus.when(() -> MenuSite.addLine(any(), anyString(), anyString(), any(ActionListener.class)))
                    .thenAnswer(call -> {
                        sound = (BoardChangeListener) call.getArgument(0);
                        actions.put(call.getArgument(1) + "/" + call.getArgument(2), call.getArgument(3));
                        return null;
                    });
            SoundEffects.install(LifeTestSupport.universe);
        }
    }

    @AfterEach void detach() throws Exception {
        LifeTestSupport.onEdt(() -> {
            if (sound != null) LifeTestSupport.universe.removeBoardChangeListener(sound);
            LifeTestSupport.universe.removeBoardChangeListener(statistics);
        });
        LifeTestSupport.resetBoard();
    }

    @Test void initialSnapshotIsReadyBeforeTheFirstScheduledSweep() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.universe.removeBoardChangeListener(sound);
            runFirstStepImmediately = true;
            installSound();
            runNotes();
            assertTrue(played.stream().anyMatch(note -> note.channel() == 7));
            assertEquals(1, summary().livingCells());
            assertEquals(0, summary().generation());
        });
    }

    @Test void realBlinkerUpdatesStatisticsAndSchedulesBirthAndDeathSoundsOnce() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Grid", "Clear");
            LifeTestSupport.click(10, 9); LifeTestSupport.click(10, 10); LifeTestSupport.click(10, 11);
            assertTrue(jobs.isEmpty(), "Editing must not play generation sounds");
            LifeTestSupport.clock.tick();
            assertEquals(1, summary().generation());
            assertEquals(3, summary().livingCells());
            assertEquals(2, summary().births()); assertEquals(2, summary().deaths());
            runNotes();
            assertTrue(played.stream().anyMatch(note -> note.channel() < 5));
            assertEquals(1, played.stream().filter(note -> note.channel() == 9).count());
        });
    }

    @Test void stableAndEmptyGenerationsNeverScheduleGenerationSounds() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Grid", "Clear");
            LifeTestSupport.click(0, 0); LifeTestSupport.click(0, 1);
            LifeTestSupport.click(1, 0); LifeTestSupport.click(1, 1);
            for (int i = 0; i < 10; i++) LifeTestSupport.clock.tick();
            assertEquals(10, summary().generation()); assertEquals(4, summary().livingCells());
            assertTrue(jobs.isEmpty());
            LifeTestSupport.select("Grid", "Clear");
            for (int i = 0; i < 3; i++) LifeTestSupport.clock.tick();
            assertEquals(3, summary().generation()); assertTrue(jobs.isEmpty());
        });
    }

    @ParameterizedTest
    @EnumSource(value = BoardChangeEvent.Kind.class, names = {"GENERATION"}, mode = EnumSource.Mode.EXCLUDE)
    void nonGenerationEventUpdatesSweepWithoutGenerationSounds(BoardChangeEvent.Kind kind) throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Grid", "Clear");
            boolean[][] cells = new boolean[64][64];
            cells[20][0] = kind != CLEAR;
            BoardChangeEvent event = new BoardChangeEvent(kind, 0, 64, cells, List.of(), List.of(), LifeTestSupport.redColors(cells));
            sound.onBoardChanged(event);
            statistics.onBoardChanged(event);
            assertTrue(jobs.isEmpty());
            assertFalse(summary().hasGenerationDelta());
            sweepStep.run(); runNotes();
            assertEquals(kind != CLEAR, played.stream().anyMatch(note -> note.channel() == 7));
            assertEquals(kind == CLEAR ? 0 : 1, summary().livingCells());
        });
    }

    @Test void realColorMenuRefreshesStatisticsWithoutSchedulingGenerationSound() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Grid", "Clear");
            LifeTestSupport.click(10, 9); LifeTestSupport.click(10, 10); LifeTestSupport.click(10, 11);
            for (int i = 0; i < 10; i++) LifeTestSupport.clock.tick();
            jobs.clear();
            LifeTestSupport.select("Color", "Cycle by Generation");
            assertTrue(jobs.isEmpty());
            assertEquals(10, summary().generation());
            assertEquals(2, summary().births()); assertEquals(2, summary().deaths());
            assertEquals(2, summary().colorCounts().size()); // age 10 center and age 0 ends
            LifeTestSupport.select("Color", "Single Color");
            assertTrue(jobs.isEmpty());
            assertEquals(1, summary().colorCounts().size());
            assertEquals(3, summary().colorCounts().get(0).cells());
        });
    }

    @Test void disabledSoundStillReceivesEditsForTheNextSweep() throws Exception {
        LifeTestSupport.onEdt(() -> {
            actions.get("Sound/Off").actionPerformed(null);
            LifeTestSupport.select("Grid", "Clear");
            LifeTestSupport.click(20, 0);
            sweepStep.run(); assertTrue(jobs.isEmpty());
            actions.get("Sound/On").actionPerformed(null);
            sweepStep.run(); runNotes();
            assertTrue(played.stream().anyMatch(note -> note.channel() == 7));
            assertEquals(1, summary().livingCells());
        });
    }

    @Test void extinctionPlaysOneEndingAndLaterEmptyTicksStaySilent() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.clock.tick();
            assertEquals(1, summary().deaths()); assertEquals(0, summary().livingCells());
            assertEquals(List.of(0L, 900L, 90L, 990L, 180L, 1080L),
                    jobs.stream().map(ScheduledNote::delay).toList());
            runNotes();
            assertEquals(3, played.size());
            jobs.clear(); played.clear();
            LifeTestSupport.clock.tick(); LifeTestSupport.clock.tick();
            assertTrue(jobs.isEmpty()); assertTrue(played.isEmpty());
        });
    }

    @Test void birthStrumNoteLengthsAndPercussionDelayRemainUnchanged() throws Exception {
        LifeTestSupport.onEdt(() -> {
            boolean[][] cells = new boolean[64][64]; cells[1][2] = cells[60][60] = true;
            sound.onBoardChanged(new BoardChangeEvent(GENERATION, 1, 64, cells,
                    List.of(new Point(2, 1), new Point(60, 60)), List.of(new Point(3, 4)), LifeTestSupport.redColors(cells)));
            assertEquals(5, jobs.size());
            assertTrue(jobs.get(0).delay() >= 0 && jobs.get(0).delay() <= 15);
            assertEquals(220, jobs.get(1).delay() - jobs.get(0).delay());
            assertTrue(jobs.get(2).delay() >= 25 && jobs.get(2).delay() <= 40);
            assertEquals(220, jobs.get(3).delay() - jobs.get(2).delay());
            assertEquals(60, jobs.get(4).delay());
            runNotes();
            assertEquals(2, played.stream().filter(note -> note.channel() < 5).count());
            assertEquals(1, played.stream().filter(note -> note.channel() == 9 && note.pitch() == 82).count());
        });
    }

    @Test void largeBirthEventKeepsTheBurstLayer() throws Exception {
        LifeTestSupport.onEdt(() -> {
            boolean[][] cells = new boolean[64][64];
            List<Point> births = new ArrayList<>();
            for (int row = 0; row < 60; row += 5) { cells[row][0] = true; births.add(new Point(0, row)); }
            sound.onBoardChanged(new BoardChangeEvent(GENERATION, 1, 64, cells, births, List.of(), LifeTestSupport.redColors(cells)));
            runNotes();
            assertTrue(played.stream().anyMatch(note -> note.channel() == 5));
        });
    }

    @Test void sweepUsesAnIndependentSnapshotOnItsWorkerThread() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.select("Grid", "Clear");
            boolean[][] cells = new boolean[64][64]; cells[20][0] = true;
            BoardChangeEvent event = new BoardChangeEvent(EDIT, 0, 64, cells, List.of(), List.of(), LifeTestSupport.redColors(cells));
            sound.onBoardChanged(event);
            cells[20][0] = false;
            event.copyAliveCells()[20][0] = false;
        });
        Thread worker = new Thread(sweepStep, "test-sound-step");
        worker.start(); worker.join(5000);
        assertFalse(worker.isAlive());
        runNotes();
        assertTrue(played.stream().anyMatch(note -> note.channel() == 7));
        LifeTestSupport.onEdt(() -> assertFalse(LifeTestSupport.universe.liveCells()[20][0]));
    }

    @Test void missingMidiDeviceDoesNotPreventStatisticsFromAdvancing() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.universe.removeBoardChangeListener(sound);
            try (MockedStatic<MidiSystem> midi = mockStatic(MidiSystem.class)) {
                midi.when(MidiSystem::getSynthesizer).thenThrow(new MidiUnavailableException("test device unavailable"));
                assertDoesNotThrow(() -> SoundEffects.install(LifeTestSupport.universe));
            }
            LifeTestSupport.clock.tick();
            assertEquals(1, summary().generation());
            assertEquals(1, summary().deaths()); assertTrue(jobs.isEmpty());
        });
    }

    private void runNotes() { for (ScheduledNote job : List.copyOf(jobs)) job.command().run(); }
    private Statistics.Summary summary() { return summaries.get(summaries.size() - 1); }
}
