package com.holub.life.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.holub.life.sound.SoundSettings.Feature;
import com.holub.life.sound.SoundSettings.Instrument;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SoundSettingsTest
{
	private final SoundSettings settings = new SoundSettings();

	@Test
	void everyFeatureStartsOnWithItsFirstInstrument()
	{	for( Feature feature : Feature.values() )
		{	assertTrue( settings.isOn(feature) );
			if( !feature.instruments.isEmpty() )
				assertEquals( feature.instruments.get(0),
							  settings.instrumentOf(feature) );
		}
	}

	@Test
	void turningOneFeatureOffLeavesTheOthersOn()
	{	settings.setOn( Feature.STRUM, false );

		assertFalse( settings.isOn(Feature.STRUM) );
		assertTrue( settings.isOn(Feature.SWEEP) );
	}

	@Test
	void changingAnInstrumentNotifiesTheListener()
	{	List<String> calls = new ArrayList<>();
		settings.onInstrumentChange( () -> calls.add("changed") );

		Instrument harp = Feature.SWEEP.instruments.get(2);
		settings.setInstrument( Feature.SWEEP, harp );

		assertEquals( harp, settings.instrumentOf(Feature.SWEEP) );
		assertEquals( 1, calls.size() );
	}
}
