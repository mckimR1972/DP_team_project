package com.holub.life.sound;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * 한 세대에 생긴 셀들을 작은 화음으로 바꾼다. 판의 높이로 C-Am-F-G
 * 진행 중 현재 코드에 맞는 5음 중 하나를 고르고, 열로 좌우 위치를
 * 정한다. 어떤 음이 함께 나도 어울리게 하기 위해서다.
 */
final class NoteMapper
{
	static final int MAX_VOICES = 5;

	/** 코드별 5음. C에서 몇 반음 위인지, 낮은 음부터. */
	private static final int[][] CHORD_SCALES =
	{	{ 0, 2, 4, 7,  9 },		// C
		{ 0, 4, 7, 9, 11 },		// Am
		{ 0, 2, 5, 7,  9 },		// F
		{ 2, 4, 7, 9, 11 },		// G
	};

	/** 코드별 패드 음: 낮은 근음, 5음, 3음. */
	private static final int[][] PAD_CHORDS =
	{	{ 36, 43, 52 },		// C
		{ 33, 45, 52 },		// Am
		{ 41, 48, 57 },		// F
		{ 43, 50, 59 },		// G
	};

	private static final int BURST_BIRTHS = 12;
	private static final int SCALE_LENGTH = 5;
	private static final int LOWEST_NOTE  = 48;	// C3
	private static final int NOTE_COUNT   = 15;	// 3옥타브
	private static final int MAX_PAN      = 127;

	private static final int ENDING_VELOCITY  = 60;
	private static final int MAX_SWEEP_VOICES = 3;
	private static final int SWEEP_VELOCITY   = 45;
	private static final int SWEEP_ACCENT     = 70;

	private static final int	VELOCITY_SPREAD		 = 8;
	private static final int	MAX_TIMING_JITTER_MS = 15;
	private static final double OCTAVE_JUMP_CHANCE	 = 0.12;

	private final Random random;

	NoteMapper()
	{	this( new Random() );
	}

	NoteMapper( Random random )
	{	this.random = random;
	}

	/** MIDI 음 하나. pan과 velocity는 0~127. */
	record Note( int pitch, int pan, int velocity ) {}

	/** 생성된 셀들에서 최대 {@link #MAX_VOICES}개의 음을 고른다.
	 *  스트럼할 수 있게 왼쪽부터 정렬한다.
	 *  @param births		셀 위치 (x = 열, y = 행)
	 *  @param boardWidth	판의 가로(=세로) 셀 수
	 *  @param chord		코드 진행에서의 위치. 끝나면 처음으로 돌아간다.
	 */
	List<Note> notesFor( List<Point> births, int boardWidth, int chord )
	{	return notesFor( births, boardWidth, chord, MAX_VOICES );
	}

	/** 재생선 한 스텝에서 낼 음을 고른다. 몇 개 열 안의 산 셀을
	 *  정해진 세기로 연주한다.
	 *  @param liveCells	판 전체, [행][열] 순서
	 *  @param accented		강박 스텝이면 true
	 */
	List<Note> sweepNotesFor( boolean[][] liveCells, int firstColumn,
							  int columns, int chord, boolean accented )
	{
		List<Point> cells = new ArrayList<>();
		for( int row = 0; row < liveCells.length; ++row )
			for( int column = firstColumn;
						column < firstColumn + columns; ++column )
				if( liveCells[row][column] )
					cells.add( new Point(column, row) );

		int velocity = accented ? SWEEP_ACCENT : SWEEP_VELOCITY;

		List<Note> notes = new ArrayList<>();
		for( Note note : notesFor( cells, liveCells.length, chord,
								   MAX_SWEEP_VOICES ) )
			notes.add( new Note(note.pitch(), note.pan(), velocity) );
		return notes;
	}

	/** 반복되는 화음이 기계적으로 들리지 않도록 세기를 조금 바꾸고,
	 *  가끔 한 옥타브 올린 음을 반환한다.
	 */
	List<Note> humanized( List<Note> notes )
	{	List<Note> result = new ArrayList<>();
		for( Note note : notes )
		{	int velocity = note.velocity()
					+ random.nextInt( 2 * VELOCITY_SPREAD + 1 ) - VELOCITY_SPREAD;

			int pitch = note.pitch();
			if( random.nextDouble() < OCTAVE_JUMP_CHANCE )
				pitch += 12;

			result.add( new Note( pitch, note.pan(),
								  Math.max(1, Math.min(127, velocity)) ) );
		}
		return result;
	}

	/** 세기만 gain 비율로 줄인 음을 반환한다. */
	List<Note> scaled( List<Note> notes, double gain )
	{	List<Note> result = new ArrayList<>();
		for( Note note : notes )
			result.add( new Note( note.pitch(), note.pan(),
								  (int)(note.velocity() * gain) ) );
		return result;
	}

	/** 판이 전멸할 때 낼 마무리 화음을 반환한다. */
	List<Note> endingChordFor( int chord )
	{	List<Note> notes = new ArrayList<>();
		for( int pitch : padNotesFor(chord) )
			notes.add( new Note(pitch + 24, MAX_PAN / 2, ENDING_VELOCITY) );
		return notes;
	}

	/** 음 타이밍을 살짝 흐트러뜨릴 작은 지연(ms)을 반환한다. */
	int timingJitterMs()
	{	return random.nextInt( MAX_TIMING_JITTER_MS + 1 );
	}

	private List<Note> notesFor( List<Point> births, int boardWidth,
								 int chord, int maxVoices )
	{
		int[] scale = CHORD_SCALES[ chord % CHORD_SCALES.length ];

		// 같은 음이 되는 셀은 처음 것만 남긴다.
		TreeMap<Integer,Point> byPitch = new TreeMap<>();
		for( Point cell : births )
			byPitch.putIfAbsent( pitchOfRow(cell.y, boardWidth, scale), cell );

		List<Map.Entry<Integer,Point>> candidates =
									new ArrayList<>( byPitch.entrySet() );

		int voices	 = Math.min( maxVoices, candidates.size() );
		int velocity = velocityFor( births.size(), voices );

		List<Note> notes = new ArrayList<>();
		for( int voice = 0; voice < voices; ++voice )
		{	// 고를 수 있는 음 사이에 고르게 퍼뜨린다.
			Map.Entry<Integer,Point> chosen =
						candidates.get( voice * candidates.size() / voices );
			notes.add( new Note( chosen.getKey(),
								 panOfColumn(chosen.getValue().x, boardWidth),
								 velocity ) );
		}
		notes.sort( Comparator.comparingInt(Note::pan) );
		return notes;
	}

	/** 한 세대에 많이 생기면 한 옥타브 위 레이어를 더한다. 적으면
	 *  빈 목록을 반환한다.
	 *  @param births 이번 세대에 생긴 셀 수
	 *  @param notes  그 셀들로 고른 화음
	 */
	List<Note> burstLayerFor( int births, List<Note> notes )
	{	List<Note> layer = new ArrayList<>();
		if( births >= BURST_BIRTHS )
			for( Note note : notes )
				layer.add( new Note( note.pitch() + 12, note.pan(),
									 note.velocity() * 3 / 4 ) );
		return layer;
	}

	/** 코드의 패드 음을 반환한다. */
	int[] padNotesFor( int chord )
	{	return PAD_CHORDS[ chord % PAD_CHORDS.length ];
	}

	/** 산 셀 수에 맞는 패드 세기(0~127)를 반환한다. 판이 붐빌수록
	 *  소리가 두꺼워진다.
	 */
	int padLevelFor( int liveCells )
	{	return liveCells == 0
				? 0 : (int)Math.min( 100, 20 + 5 * Math.sqrt(liveCells) );
	}

	/** 사라진 셀 수에 맞는 타악기 세기를 반환한다. */
	int percussionVelocityFor( int deaths )
	{	return (int)Math.min( 70, 25 + 6 * Math.sqrt(deaths) );
	}

	private int pitchOfRow( int row, int boardWidth, int[] scale )
	{	int step = (boardWidth - 1 - row) * NOTE_COUNT / boardWidth;
		return LOWEST_NOTE + 12 * (step / SCALE_LENGTH)
						   + scale[ step % SCALE_LENGTH ];
	}

	private int panOfColumn( int column, int boardWidth )
	{	return boardWidth <= 1 ? MAX_PAN / 2
							   : column * MAX_PAN / (boardWidth - 1);
	}

	/** 셀이 많이 생길수록 크게 내지만, 화음 전체에 상한을 둬서 판이
	 *  붐벼도 거슬리지 않게 한다.
	 */
	private int velocityFor( int births, int voices )
	{	double loudness = Math.min( 90, 55 + 4 * Math.sqrt(births) );
		return (int)( loudness / Math.pow(Math.max(voices, 1), 0.25) );
	}
}
