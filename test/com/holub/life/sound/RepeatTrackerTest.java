package com.holub.life.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.List;

import org.junit.jupiter.api.Test;

class RepeatTrackerTest
{
	private static final List<Point> A = List.of( new Point(1, 1) );
	private static final List<Point> B = List.of( new Point(2, 2) );

	@Test
	void newGenerationsStayAtFullVolume()
	{	RepeatTracker tracker = new RepeatTracker();
		for( int i = 0; i < 30; ++i )
			assertEquals( 1.0, tracker.gainFor(
						List.of(new Point(i, 0)), List.of()) );
	}

	@Test
	void oscillatorFadesToSilence()
	{	RepeatTracker tracker = new RepeatTracker();
		double gain = 1;
		for( int i = 0; i < 16; ++i )
		{	double next = tracker.gainFor( i % 2 == 0 ? A : B,
										   i % 2 == 0 ? B : A );
			assertTrue( next <= gain );
			gain = next;
		}
		assertEquals( 0.0, gain );
	}

	@Test
	void somethingNewRestoresTheVolume()
	{	RepeatTracker tracker = new RepeatTracker();
		for( int i = 0; i < 16; ++i )
			tracker.gainFor( A, B );

		assertEquals( 1.0, tracker.gainFor(B, B) );
	}
}
