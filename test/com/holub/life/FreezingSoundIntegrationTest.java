package com.holub.life;

import com.holub.life.sound.SoundEffects;
import com.holub.ui.MenuSite;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Freezing with the real SoundEffects object on the real board and clock.
 * As in SoundEffectsIntegrationTest, only the MIDI device and the note scheduler
 * are substitutes, so what would be played can be read without a sound card.
 * MIDI channels: 0-4 birth chord, 7 playback line (sweep), 9 percussion for deaths.
 */
class FreezingSoundIntegrationTest {
    private static final int SWEEP_CHANNEL = 7;
    private static final int PERCUSSION_CHANNEL = 9;

    private record Played(int channel, int pitch, int velocity) { }
    private final List<Runnable> jobs = new ArrayList<>();
    private final List<Played> played = new ArrayList<>();
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final Synthesizer synth = mock(Synthesizer.class);
    private final MidiChannel[] channels = new MidiChannel[16];
    private BoardChangeListener sound;
    private Runnable sweepStep;

    @BeforeAll
    static void initialize() throws Exception { LifeTestSupport.initialize(); }

    @BeforeEach
    void installRealSoundEffects() throws Exception {
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
            jobs.add(call.getArgument(0));
            return null;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        doAnswer(call -> {
            sweepStep = call.getArgument(0);
            return null;
        }).when(scheduler).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        LifeTestSupport.onEdt(() -> {
            try (MockedStatic<MidiSystem> midi = mockStatic(MidiSystem.class);
                 MockedStatic<Executors> executors = mockStatic(Executors.class);
                 MockedStatic<MenuSite> menus = mockStatic(MenuSite.class)) {
                midi.when(MidiSystem::getSynthesizer).thenReturn(synth);
                executors.when(() -> Executors.newSingleThreadScheduledExecutor(any(ThreadFactory.class)))
                        .thenReturn(scheduler);
                menus.when(() -> MenuSite.addLine(any(), anyString(), anyString(), any(ActionListener.class)))
                        .thenAnswer(call -> {
                            sound = (BoardChangeListener) call.getArgument(0);
                            return null;
                        });
                SoundEffects.install(LifeTestSupport.universe);
            }
        });
        assertNotNull(sound, "SoundEffects must be subscribed");
        assertNotNull(sweepStep);
    }

    @AfterEach
    void detach() throws Exception {
        LifeTestSupport.onEdt(() -> {
            if (sound != null) LifeTestSupport.universe.removeBoardChangeListener(sound);
        });
        LifeTestSupport.resetBoard();
    }

    @Test
    void aFrozenBlinkerIsSilentAndIsHeardAgainOnItsFirstFreeGeneration() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(10, 9); LifeTestSupport.click(10, 10); LifeTestSupport.click(10, 11);
            LifeTestSupport.universe.freezing().install(new Rectangle(8, 9, 5, 3), 3);
            for (int g = 1; g <= 3; g++) {
                LifeTestSupport.clock.tick();
                assertTrue(jobs.isEmpty(), "a frozen blinker must not schedule a note at generation " + g);
            }
            assertTrue(played.isEmpty());

            LifeTestSupport.clock.tick();                                  // released: 2 births, 2 deaths
            assertFalse(jobs.isEmpty(), "the first free generation is heard");
            runNotes();
            assertTrue(played.stream().anyMatch(note -> note.channel() < 5), "birth chord");
            assertEquals(1, played.stream().filter(note -> note.channel() == PERCUSSION_CHANNEL).count(),
                    "one percussion hit for the deaths");
        });
    }

    @Test
    void onlyTheFreeCellIsHeardDyingAndTheFrozenCellIsHeardWhenItIsReleased() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(5, 5);                                   // frozen lone cell
            LifeTestSupport.click(40, 40);                                 // free lone cell
            LifeTestSupport.universe.freezing().install(new Rectangle(5, 5, 1, 1), 2);

            LifeTestSupport.clock.tick();                                  // only the free cell dies
            runNotes();
            assertEquals(List.of(PERCUSSION_CHANNEL), played.stream().map(Played::channel).toList(),
                    "one death, no birth, and no ending chord because a (frozen) cell is still alive");
            jobs.clear(); played.clear();

            LifeTestSupport.clock.tick();                                  // nothing changes
            assertTrue(jobs.isEmpty());

            LifeTestSupport.clock.tick();                                  // released cell dies: the board is empty
            runNotes();
            assertEquals(3, played.size(), "the ending chord of an extinct board: " + played);
            assertTrue(played.stream().noneMatch(note -> note.channel() == PERCUSSION_CHANNEL));
        });
    }

    @Test
    void thePlaybackLineKeepsPlayingAFrozenLiveCellThatWouldOtherwiseBeGone() throws Exception {
        LifeTestSupport.onEdt(() -> {
            LifeTestSupport.click(20, 0);                                  // lone cell in the first swept columns
            LifeTestSupport.universe.freezing().install(new Rectangle(0, 20, 1, 1), 5);
            LifeTestSupport.clock.tick(); LifeTestSupport.clock.tick();
            assertTrue(jobs.isEmpty(), "no generation sound: nothing was born or died");
            sweepStep.run();
            runNotes();
            assertTrue(played.stream().anyMatch(note -> note.channel() == SWEEP_CHANNEL),
                    "the sweep reads the frozen cell from the generation event: " + played);
        });
    }

    private void runNotes() { for (Runnable job : List.copyOf(jobs)) job.run(); }
}
