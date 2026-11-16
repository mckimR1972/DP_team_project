package com.holub.life.sound;

import java.awt.Point;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;
import javax.swing.SwingUtilities;

import com.holub.life.Universe;
import com.holub.life.sound.SoundSettings.Feature;
import com.holub.ui.MenuSite;

/**
 * 판을 두 겹의 음악으로 바꾼다.
 * <p>
 * 재생선이 일정한 박자로 판을 훑으며 지나가는 산 셀을 칼림바로 연주하고,
 * 그 아래에서 패드가 현재 코드를 낸다. 판이 붐빌수록 패드가 커진다.
 * 한 바퀴마다 코드가 바뀌어서 게임을 멈춰도 소리가 계속 난다.
 * <p>
 * 그 위에 세대마다 짧은 소리를 더한다. 생긴 셀은 마림바 화음을 왼쪽부터
 * 스트럼하고, 한꺼번에 많이 생기면 비브라폰을 겹치고, 사라진 셀은
 * 타악기를 친다. 같은 변화가 반복되면 점점 작아지고, 판이 전멸하면
 * 마무리 화음을 낸다.
 * <p>
 * 각 기능은 {@link SoundSettings}에서 끄거나 악기를 바꿀 수 있다.
 * {@link #install}로 붙이며, 붙이지 않아도 게임은 똑같이 동작한다.
 */
public final class SoundEffects implements Universe.GenerationListener
{
	private static final int PAN_CONTROLLER     = 10;
	private static final int EXPRESSION_CONTROLLER = 11;
	private static final int BURST_CHANNEL      = 5;
	private static final int PAD_CHANNEL        = 6;
	private static final int SWEEP_CHANNEL      = 7;
	private static final int PERCUSSION_CHANNEL = 9;	// General MIDI 번호
	private static final int NOTE_LENGTH_MS     = 220;
	private static final int SWEEP_NOTE_LENGTH_MS = 400;
	private static final int STRUM_GAP_MS       = 25;
	private static final int ENDING_GAP_MS      = 90;
	private static final int ENDING_NOTE_LENGTH_MS = 900;
	private static final int PERCUSSION_DELAY_MS = 60;
	private static final int MIN_PHRASE_GAP_MS  = 120;	// 클럭이 빠를 때 소리가 몰리지 않게
	private static final int PAD_VELOCITY       = 80;
	private static final int STEP_MS            = 150;
	private static final int STEPS_PER_SWEEP    = 16;
	private static final int STEPS_PER_BEAT     = 4;

	private final Universe			universe;
	private final MidiChannel[]		voices = new MidiChannel[NoteMapper.MAX_VOICES];
	private final MidiChannel		burst;
	private final MidiChannel		pad;
	private final MidiChannel		sweep;
	private final MidiChannel		percussion;
	private final NoteMapper		mapper = new NoteMapper();
	private final SoundSettings		settings = new SoundSettings();
	private final RepeatTracker		repeats = new RepeatTracker();
	private final ScheduledExecutorService noteScheduler;

	private volatile boolean enabled = true;
	private volatile int	 chord	 = 0;
	private long  lastPhraseNanos = 0;
	private int   step	   = 0;			// 스케줄러 스레드에서만 사용
	private int[] padNotes = {};		// 스케줄러 스레드에서만 사용
	private SoundSettingsDialog settingsDialog;	// Swing 스레드에서만 사용

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
			voices[voice] = channels[voice];

		percussion = channels[PERCUSSION_CHANNEL];
		burst	   = channels[BURST_CHANNEL];
		pad		   = channels[PAD_CHANNEL];
		sweep	   = channels[SWEEP_CHANNEL];

		noteScheduler = Executors.newSingleThreadScheduledExecutor
		(	task ->
			{	Thread thread = new Thread( task, "sound-notes" );
				thread.setDaemon( true );
				return thread;
			}
		);

		applyInstruments();
		settings.onInstrumentChange
		(	() -> noteScheduler.execute( this::applyInstruments )
		);

		noteScheduler.scheduleAtFixedRate( this::playStep, 0, STEP_MS,
										   TimeUnit.MILLISECONDS );
	}

	private void applyInstruments()
	{	for( MidiChannel voice : voices )
			voice.programChange( programOf(Feature.BIRTHS) );

		burst.programChange( programOf(Feature.BURST) );
		sweep.programChange( programOf(Feature.SWEEP) );

		stopPad();		// 이미 누르고 있는 음은 이전 악기 소리로 남기 때문
		pad.programChange( programOf(Feature.DRONE) );
	}

	private int programOf( Feature feature )
	{	return settings.instrumentOf( feature ).code();
	}

	private void showSettings()
	{	if( settingsDialog == null )
		{	Window owner = SwingUtilities.getWindowAncestor( universe );
			settingsDialog = new SoundSettingsDialog( owner, settings );
			settingsDialog.setLocation( owner.getX() + owner.getWidth(),
										owner.getY() );
		}
		settingsDialog.setVisible( true );
	}

	/** 지금 쓸 코드를 반환한다. 코드 진행을 끄면 항상 C. */
	private int currentChord()
	{	return settings.isOn(Feature.PROGRESSION) ? chord : 0;
	}

	private List<NoteMapper.Note> varied( List<NoteMapper.Note> notes )
	{	return settings.isOn(Feature.VARIATION) ? mapper.humanized(notes)
												: notes;
	}

	private int jitterMs()
	{	return settings.isOn(Feature.VARIATION) ? mapper.timingJitterMs() : 0;
	}

	private void createMenus()
	{	MenuSite.addLine( this, "Sound", "On",  e -> enabled = true  );
		MenuSite.addLine( this, "Sound", "Off", e -> enabled = false );
		MenuSite.addLine( this, "Sound", "Settings", e -> showSettings() );
	}

	/** 재생선을 한 스텝 옮기고 그 아래 셀을 연주한다. */
	private void playStep()
	{
		if( !enabled )
		{	stopPad();
			universe.setPlayhead( -1 );
			return;
		}

		boolean[][] cells  = universe.liveCells();
		int columnsPerStep = cells.length / STEPS_PER_SWEEP;
		int firstColumn	   = step * columnsPerStep;

		if( settings.isOn(Feature.SWEEP) )
		{	universe.setPlayhead( firstColumn );

			List<NoteMapper.Note> notes = varied
			(	mapper.sweepNotesFor( cells, firstColumn, columnsPerStep,
							currentChord(), step % STEPS_PER_BEAT == 0 )
			);
			for( NoteMapper.Note note : notes )
				play( sweep, note, jitterMs(), SWEEP_NOTE_LENGTH_MS );
		}
		else
			universe.setPlayhead( -1 );

		updatePad( mapper.padNotesFor( currentChord() ),
				   settings.isOn(Feature.DRONE)
						? mapper.padLevelFor(countLive(cells)) : 0 );

		step = (step + 1) % STEPS_PER_SWEEP;
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
		if( !enabled )
			return;

		boolean fades = settings.isOn( Feature.FADE );
		double  gain  = fades ? repeats.gainFor(births, deaths) : 1.0;

		if( fades && boardDiedOut(births) )
		{	playEnding();
			return;
		}

		long now = System.nanoTime();
		if(		gain == 0.0
			||	now - lastPhraseNanos < MIN_PHRASE_GAP_MS * 1_000_000L )
			return;

		lastPhraseNanos = now;

		List<NoteMapper.Note> notes = mapper.scaled
		(	varied( mapper.notesFor( births, universe.widthInCells(),
									 currentChord() ) ),
			gain
		);

		if( settings.isOn(Feature.BIRTHS) )
			for( int voice = 0; voice < notes.size(); ++voice )
				play( voices[voice], notes.get(voice), strumDelay(voice),
					  NOTE_LENGTH_MS );

		if( settings.isOn(Feature.BURST) )
		{	List<NoteMapper.Note> layer =
							mapper.burstLayerFor( births.size(), notes );

			for( int voice = 0; voice < layer.size(); ++voice )
				play( burst, layer.get(voice), strumDelay(voice),
					  NOTE_LENGTH_MS );
		}

		if( settings.isOn(Feature.DEATHS) && !deaths.isEmpty() )
			playPercussion(
				(int)(mapper.percussionVelocityFor(deaths.size()) * gain) );
	}

	private boolean boardDiedOut( List<Point> births )
	{	return births.isEmpty() && countLive( universe.liveCells() ) == 0;
	}

	private void playEnding()
	{	List<NoteMapper.Note> notes = mapper.endingChordFor( currentChord() );
		for( int voice = 0; voice < notes.size(); ++voice )
			play( voices[voice], notes.get(voice), voice * ENDING_GAP_MS,
				  ENDING_NOTE_LENGTH_MS );
	}

	private int strumDelay( int voice )
	{	int strum = settings.isOn(Feature.STRUM) ? voice * STRUM_GAP_MS : 0;
		return strum + jitterMs();
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

	private void playPercussion( int velocity )
	{	int note = settings.instrumentOf( Feature.DEATHS ).code();
		noteScheduler.schedule( () -> percussion.noteOn(note, velocity),
								PERCUSSION_DELAY_MS, TimeUnit.MILLISECONDS );
	}
}
