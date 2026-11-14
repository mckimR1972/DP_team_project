package com.holub.life.sound;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

	private static final int SCALE_LENGTH = 5;
	private static final int LOWEST_NOTE  = 48;	// C3
	private static final int NOTE_COUNT   = 15;	// 3옥타브
	private static final int MAX_PAN      = 127;

	/** MIDI 음 하나. pan과 velocity는 0~127. */
	record Note( int pitch, int pan, int velocity ) {}

	/** 생성된 셀들에서 최대 {@link #MAX_VOICES}개의 음을 고른다.
	 *  @param births		셀 위치 (x = 열, y = 행)
	 *  @param boardWidth	판의 가로(=세로) 셀 수
	 *  @param chord		코드 진행에서의 위치. 끝나면 처음으로 돌아간다.
	 */
	List<Note> notesFor( List<Point> births, int boardWidth, int chord )
	{	return notesFor( births, boardWidth, chord, MAX_VOICES );
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
		return notes;
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
