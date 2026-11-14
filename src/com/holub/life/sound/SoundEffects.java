package com.holub.life.sound;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;

import com.holub.life.Universe;
import com.holub.ui.MenuSite;

/**
 * 세대마다 생긴 셀을 마림바 화음으로 낸다. 일정한 박자로 마디마다
 * 코드가 바뀐다.
 * <p>
 * {@link #install}로 붙이며, 붙이지 않아도 게임은 똑같이 동작한다.
 */
public final class SoundEffects implements Universe.GenerationListener
{
	private static final int MARIMBA_PROGRAM    = 12;	// General MIDI 번호
	private static final int PAN_CONTROLLER     = 10;
	private static final int NOTE_LENGTH_MS     = 220;
	private static final int MIN_PHRASE_GAP_MS  = 120;	// 클럭이 빠를 때 소리가 몰리지 않게
	private static final int STEP_MS            = 150;
	private static final int STEPS_PER_BAR      = 16;

	private final Universe			universe;
	private final MidiChannel[]		voices = new MidiChannel[NoteMapper.MAX_VOICES];
	private final NoteMapper		mapper = new NoteMapper();
	private final ScheduledExecutorService noteScheduler;

	private volatile boolean enabled = true;
	private volatile int	 chord	 = 0;
	private long  lastPhraseNanos = 0;
	private int   step	   = 0;			// 스케줄러 스레드에서만 사용

	/** Universe를 구독하고 Sound 메뉴를 추가한다. MIDI 신시사이저가
	 *  없으면 소리 없이 실행된다.
	 */
	public static void install( Universe universe )
	{	try
		{	Synthesizer synthesizer = MidiSystem.getSynthesizer();
			synthesizer.open();

			SoundEffects effects = new SoundEffects( universe, synthesizer );
			universe.addGenerationListener( effects );
			effects.createMenus();
		}
		catch( MidiUnavailableException e )
		{	System.err.println( "Sound disabled: " + e.getMessage() );
		}
	}

	private SoundEffects( Universe universe, Synthesizer synthesizer )
	{	this.universe = universe;

		// pan은 MIDI 채널 단위라 음마다 채널을 따로 쓴다.
		MidiChannel[] channels = synthesizer.getChannels();
		for( int voice = 0; voice < voices.length; ++voice )
		{	voices[voice] = channels[voice];
			voices[voice].programChange( MARIMBA_PROGRAM );
		}

		noteScheduler = Executors.newSingleThreadScheduledExecutor
		(	task ->
			{	Thread thread = new Thread( task, "sound-notes" );
				thread.setDaemon( true );
				return thread;
			}
		);

		noteScheduler.scheduleAtFixedRate( this::playStep, 0, STEP_MS,
										   TimeUnit.MILLISECONDS );
	}

	private void createMenus()
	{	MenuSite.addLine( this, "Sound", "On",  e -> enabled = true  );
		MenuSite.addLine( this, "Sound", "Off", e -> enabled = false );
	}

	/** 박자를 세고 마디가 끝나면 다음 코드로 넘어간다. */
	private void playStep()
	{	step = (step + 1) % STEPS_PER_BAR;
		if( step == 0 )
			++chord;
	}

	public void generationChanged( List<Point> births, List<Point> deaths )
	{
		long now = System.nanoTime();
		if(		!enabled
			||	( births.isEmpty() && deaths.isEmpty() )
			||	now - lastPhraseNanos < MIN_PHRASE_GAP_MS * 1_000_000L )
			return;

		lastPhraseNanos = now;

		List<NoteMapper.Note> notes =
					mapper.notesFor( births, universe.widthInCells(), chord );

		for( int voice = 0; voice < notes.size(); ++voice )
			play( voices[voice], notes.get(voice), 0, NOTE_LENGTH_MS );
	}

	private void play( MidiChannel channel, NoteMapper.Note note,
					   int delayMs, int lengthMs )
	{	noteScheduler.schedule
		(	() ->
			{	channel.controlChange( PAN_CONTROLLER, note.pan() );
				channel.noteOn( note.pitch(), note.velocity() );
			},
			delayMs, TimeUnit.MILLISECONDS
		);
		noteScheduler.schedule( () -> channel.noteOff( note.pitch() ),
								delayMs + lengthMs, TimeUnit.MILLISECONDS );
	}
}
