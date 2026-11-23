package com.holub.life.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NoteMapperTest
{
	private static final int WIDTH = 64;
	private static final int CHORDS = 4;
	private final NoteMapper mapper = new NoteMapper();

	private int pitchAt( int row, int chord )
	{	return mapper.notesFor( List.of(new Point(0, row)), WIDTH, chord )
					 .get(0).pitch();
	}

	@Test
	void noBirthsMakeNoNotes()
	{	assertTrue( mapper.notesFor(List.of(), WIDTH, 0).isEmpty() );
	}

	@Test
	void everyPitchIsInCMajor()
	{	Set<Integer> cMajor = Set.of( 0, 2, 4, 5, 7, 9, 11 );
		for( int chord = 0; chord < CHORDS; ++chord )
			for( int row = 0; row < WIDTH; ++row )
				assertTrue( cMajor.contains(pitchAt(row, chord) % 12),
							"chord " + chord + " row " + row );
	}

	@ParameterizedTest
	@ValueSource( ints = {0, 1, 2, 3} )
	void higherOnTheBoardNeverSoundsLower( int chord )
	{	for( int row = 1; row < WIDTH; ++row )
			assertTrue( pitchAt(row - 1, chord) >= pitchAt(row, chord),
						"row " + row );
	}

	@Test
	void sameCellChangesPitchAcrossTheProgression()
	{	Set<List<Integer>> voicings = new HashSet<>();
		for( int chord = 0; chord < CHORDS; ++chord )
		{	List<Integer> voicing = new ArrayList<>();
			for( int row = 0; row < WIDTH; ++row )
				voicing.add( pitchAt(row, chord) );
			voicings.add( voicing );
		}
		assertEquals( CHORDS, voicings.size() );
	}

	@Test
	void progressionWrapsAround()
	{	assertEquals( pitchAt(30, 1), pitchAt(30, 1 + CHORDS) );
		assertNotEquals( pitchAt(5, 0), pitchAt(5, 3) );
	}

	@Test
	void notesAreOrderedLeftToRightForStrumming()
	{	List<Point> births = List.of( new Point(60, 0), new Point(5, 40),
									  new Point(30, 20) );

		List<NoteMapper.Note> notes = mapper.notesFor( births, WIDTH, 0 );

		assertEquals( 3, notes.size() );
		assertTrue(    notes.get(0).pan() < notes.get(1).pan()
					&& notes.get(1).pan() < notes.get(2).pan() );
	}

	@Test
	void columnSetsStereoPosition()
	{	assertEquals( 0, mapper.notesFor(
						List.of(new Point(0, 0)), WIDTH, 0 ).get(0).pan() );
		assertEquals( 127, mapper.notesFor(
						List.of(new Point(WIDTH-1, 0)), WIDTH, 0 ).get(0).pan() );
	}

	@Test
	void fullBoardStaysWithinVoiceLimitAndValidMidiRange()
	{	List<Point> births = new ArrayList<>();
		for( int row = 0; row < WIDTH; ++row )
			for( int column = 0; column < WIDTH; ++column )
				births.add( new Point(column, row) );

		List<NoteMapper.Note> notes = mapper.notesFor( births, WIDTH, 0 );

		assertEquals( NoteMapper.MAX_VOICES, notes.size() );

		Set<Integer> pitches = new HashSet<>();
		for( NoteMapper.Note note : notes )
		{	pitches.add( note.pitch() );
			assertTrue( note.velocity() > 0 && note.velocity() <= 127 );
		}
		assertEquals( notes.size(), pitches.size() );
	}

	@Test
	void sweepPlaysOnlyCellsInTheColumnBlock()
	{	boolean[][] cells = new boolean[WIDTH][WIDTH];
		cells[10][4] = true;	// 구간 안
		cells[50][8] = true;	// 구간 바로 다음

		List<NoteMapper.Note> notes =
					mapper.sweepNotesFor( cells, 4, 4, 0, false );

		assertEquals( 1, notes.size() );
		assertEquals( pitchAt(10, 0), notes.get(0).pitch() );
	}

	@Test
	void sweepOfAnEmptyBlockIsSilent()
	{	assertTrue( mapper.sweepNotesFor( new boolean[WIDTH][WIDTH],
										  0, 4, 0, true ).isEmpty() );
	}

	@Test
	void sweepLimitsVoicesAndAccentsTheBeat()
	{	boolean[][] cells = new boolean[WIDTH][WIDTH];
		for( int row = 0; row < WIDTH; ++row )
			cells[row][0] = true;

		List<NoteMapper.Note> plain =
					mapper.sweepNotesFor( cells, 0, 4, 0, false );
		List<NoteMapper.Note> accent =
					mapper.sweepNotesFor( cells, 0, 4, 0, true );

		assertEquals( 3, plain.size() );
		assertTrue( accent.get(0).velocity() > plain.get(0).velocity() );
	}

	@Test
	void humanizingKeepsNotesValidAndVariesThem()
	{	NoteMapper seeded = new NoteMapper( new Random(42) );
		List<NoteMapper.Note> notes = List.of( new NoteMapper.Note(60, 64, 60) );

		Set<Integer> velocities = new HashSet<>();
		Set<Integer> pitches = new HashSet<>();
		for( int i = 0; i < 500; ++i )
		{	NoteMapper.Note note = seeded.humanized(notes).get(0);
			velocities.add( note.velocity() );
			pitches.add( note.pitch() );
			assertEquals( 64, note.pan() );
			assertTrue( note.velocity() >= 52 && note.velocity() <= 68 );
		}
		assertTrue( velocities.size() > 5 );
		assertEquals( Set.of(60, 72), pitches );
	}

	@Test
	void scalingLowersLoudnessOnly()
	{	NoteMapper.Note note = new NoteMapper.Note( 60, 30, 80 );

		NoteMapper.Note half = mapper.scaled( List.of(note), 0.5 ).get(0);

		assertEquals( new NoteMapper.Note(60, 30, 40), half );
	}

	@Test
	void endingChordIsAboveThePadOfTheSameChord()
	{	List<NoteMapper.Note> ending = mapper.endingChordFor( 2 );

		assertEquals( 3, ending.size() );
		assertEquals( mapper.padNotesFor(2)[0] + 24, ending.get(0).pitch() );
	}

	@Test
	void timingJitterStaysSmall()
	{	for( int i = 0; i < 200; ++i )
		{	int jitter = mapper.timingJitterMs();
			assertTrue( jitter >= 0 && jitter <= 15 );
		}
	}

	@Test
	void smallGenerationsHaveNoBurstLayer()
	{	List<NoteMapper.Note> notes =
			mapper.notesFor( List.of(new Point(0, 0)), WIDTH, 0 );
		assertTrue( mapper.burstLayerFor(11, notes).isEmpty() );
	}

	@Test
	void burstLayerDoublesNotesAnOctaveHigherAndQuieter()
	{	List<NoteMapper.Note> notes =
			mapper.notesFor( List.of(new Point(0, 0)), WIDTH, 0 );

		NoteMapper.Note note  = notes.get(0);
		NoteMapper.Note layer = mapper.burstLayerFor(12, notes).get(0);

		assertEquals( note.pitch() + 12, layer.pitch() );
		assertEquals( note.pan(), layer.pan() );
		assertTrue( layer.velocity() < note.velocity() );
	}

	@Test
	void padFollowsTheProgression()
	{	assertEquals( 36, mapper.padNotesFor(0)[0] );	// C
		assertEquals( 33, mapper.padNotesFor(1)[0] );	// A
		assertEquals( 36, mapper.padNotesFor(CHORDS)[0] );
	}

	@Test
	void padGrowsWithLiveCellsUpToACapAndStopsOnAnEmptyBoard()
	{	assertEquals( 0, mapper.padLevelFor(0) );
		assertTrue( mapper.padLevelFor(10) < mapper.padLevelFor(200) );
		assertEquals( 100, mapper.padLevelFor(4096) );
	}

	@Test
	void moreDeathsHitHarderUpToACap()
	{	assertTrue( mapper.percussionVelocityFor(1)
				  < mapper.percussionVelocityFor(30) );
		assertEquals( 70, mapper.percussionVelocityFor(4096) );
	}
}
