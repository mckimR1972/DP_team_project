package com.holub.life.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class NoteMapperTest
{
	private static final int WIDTH = 64;
	private final NoteMapper mapper = new NoteMapper();

	private int pitchAt( int row )
	{	return mapper.notesFor( List.of(new Point(0, row)), WIDTH )
					 .get(0).pitch();
	}

	@Test
	void noBirthsMakeNoNotes()
	{	assertTrue( mapper.notesFor(List.of(), WIDTH).isEmpty() );
	}

	@Test
	void everyPitchIsInThePentatonicScale()
	{	Set<Integer> pentatonic = Set.of( 0, 2, 4, 7, 9 );
		for( int row = 0; row < WIDTH; ++row )
			assertTrue( pentatonic.contains(pitchAt(row) % 12), "row " + row );
	}

	@Test
	void higherOnTheBoardNeverSoundsLower()
	{	for( int row = 1; row < WIDTH; ++row )
			assertTrue( pitchAt(row - 1) >= pitchAt(row), "row " + row );
	}

	@Test
	void columnSetsStereoPosition()
	{	assertEquals( 0, mapper.notesFor(
						List.of(new Point(0, 0)), WIDTH ).get(0).pan() );
		assertEquals( 127, mapper.notesFor(
						List.of(new Point(WIDTH-1, 0)), WIDTH ).get(0).pan() );
	}

	@Test
	void fullBoardStaysWithinVoiceLimitAndValidMidiRange()
	{	List<Point> births = new ArrayList<>();
		for( int row = 0; row < WIDTH; ++row )
			for( int column = 0; column < WIDTH; ++column )
				births.add( new Point(column, row) );

		List<NoteMapper.Note> notes = mapper.notesFor( births, WIDTH );

		assertEquals( NoteMapper.MAX_VOICES, notes.size() );

		Set<Integer> pitches = new HashSet<>();
		for( NoteMapper.Note note : notes )
		{	pitches.add( note.pitch() );
			assertTrue( note.velocity() > 0 && note.velocity() <= 127 );
		}
		assertEquals( notes.size(), pitches.size() );
	}
}
