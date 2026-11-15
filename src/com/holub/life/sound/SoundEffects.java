package com.holub.life.sound;

import java.awt.Point;
import java.util.Arrays;
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
 * 세대마다 짧은 소리를 낸다. 생긴 셀은 마림바 화음을 왼쪽부터
 * 스트럼하고, 사라진 셀은 쉐이커를 친다. 아래에서는 패드가 현재 코드를
 * 내고(판이 붐빌수록 크게), 일정한 박자로 마디마다 코드가 바뀐다.
 * <p>
 * {@link #install}로 붙이며, 붙이지 않아도 게임은 똑같이 동작한다.
 */
public final class SoundEffects implements Universe.GenerationListener
{
	private static final int MARIMBA_PROGRAM    = 12;	// General MIDI 번호
	private static final int WARM_PAD_PROGRAM   = 89;	// General MIDI 번호
	private static final int PAN_CONTROLLER     = 10;
	private static final int EXPRESSION_CONTROLLER = 11;
	private static final int PAD_CHANNEL        = 6;
	private static final int PERCUSSION_CHANNEL = 9;	// General MIDI 번호
	private static final int SHAKER_NOTE        = 82;	// General MIDI 번호
	private static final int NOTE_LENGTH_MS     = 220;
	private static final int STRUM_GAP_MS       = 25;
	private static final int SHAKER_DELAY_MS    = 60;
	private static final int MIN_PHRASE_GAP_MS  = 120;	// 클럭이 빠를 때 소리가 몰리지 않게
	private static final int PAD_VELOCITY       = 80;
	private static final int STEP_MS            = 150;
	private static final int STEPS_PER_BAR      = 16;

	private final Universe			universe;
	private final MidiChannel[]		voices = new MidiChannel[NoteMapper.MAX_VOICES];
	private final MidiChannel		pad;
	private final MidiChannel		percussion;
	private final NoteMapper		mapper = new NoteMapper();
	private final ScheduledExecutorService noteScheduler;

	private volatile boolean enabled = true;
	private volatile int	 chord	 = 0;
	private long  lastPhraseNanos = 0;
	private int   step	   = 0;			// 스케줄러 스레드에서만 사용
	private int[] padNotes = {};		// 스케줄러 스레드에서만 사용

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
		percussion = channels[PERCUSSION_CHANNEL];

		pad = channels[PAD_CHANNEL];
		pad.programChange( WARM_PAD_PROGRAM );

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

	/** 박자를 한 스텝 진행한다. 패드를 판 상태에 맞추고, 마디가
	 *  끝나면 다음 코드로 넘어간다.
	 */
	private void playStep()
	{
		if( !enabled )
		{	stopPad();
			return;
		}

		updatePad( mapper.padNotesFor(chord),
				   mapper.padLevelFor(countLive(universe.liveCells())) );

		step = (step + 1) % STEPS_PER_BAR;
		if( step == 0 )
			++chord;
	}

	private int countLive( boolean[][] cells )
	{	int count = 0;
		for( boolean[] row : cells )
			for( boolean isAlive : row )
				if( isAlive )
					++count;
		return count;
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
			play( voices[voice], notes.get(voice), strumDelay(voice),
				  NOTE_LENGTH_MS );

		if( !deaths.isEmpty() )
			playShaker( mapper.percussionVelocityFor(deaths.size()) );
	}

	private int strumDelay( int voice )
	{	return voice * STRUM_GAP_MS;
	}

	private void updatePad( int[] chordNotes, int level )
	{	if( level == 0 )
		{	stopPad();
			return;
		}

		pad.controlChange( EXPRESSION_CONTROLLER, level );

		if( !Arrays.equals(chordNotes, padNotes) )
		{	stopPad();
			for( int note : chordNotes )
				pad.noteOn( note, PAD_VELOCITY );
			padNotes = chordNotes;
		}
	}

	private void stopPad()
	{	for( int note : padNotes )
			pad.noteOff( note );
		padNotes = new int[0];
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

	private void playShaker( int velocity )
	{	noteScheduler.schedule( () -> percussion.noteOn(SHAKER_NOTE, velocity),
								SHAKER_DELAY_MS, TimeUnit.MILLISECONDS );
	}
}
