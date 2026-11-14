package com.holub.life.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

}
