package com.holub.life.sound;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 어떤 사운드 기능이 켜져 있고 각각 어떤 악기를 쓰는지 저장한다.
 * 설정 창이 쓰고 사운드 스레드가 읽으므로 모든 접근을 동기화한다.
 */
final class SoundSettings
{
	/** 고를 수 있는 소리. code는 General MIDI 프로그램 번호이고,
	 *  {@link Feature#DEATHS}만 타악기 음 번호다.
	 */
	record Instrument( String name, int code )
	{	@Override public String toString(){ return name; }
	}

	/** 켜고 끌 수 있는 사운드 기능. */
	enum Feature
	{	BIRTHS		( "생성음", List.of(
						new Instrument("마림바", 12),
						new Instrument("실로폰", 13),
						new Instrument("하프", 46),
						new Instrument("피아노", 0) ) ),
		PROGRESSION	( "1. 코드 진행" ),
		STRUM		( "2. 스트럼" ),
		DEATHS		( "3. 소멸 타악기", List.of(
						new Instrument("쉐이커", 82),
						new Instrument("하이햇", 42),
						new Instrument("카바사", 69),
						new Instrument("탬버린", 54) ) ),
		DRONE		( "4. 저음 패드", List.of(
						new Instrument("웜 패드", 89),
						new Instrument("스트링", 48),
						new Instrument("합창", 52),
						new Instrument("오르간", 19) ) ),
		BURST		( "5. 대량 생성 악기", List.of(
						new Instrument("비브라폰", 11),
						new Instrument("글로켄슈필", 9),
						new Instrument("첼레스타", 8),
						new Instrument("튜블러 벨", 14) ) ),
		FADE		( "6. 반복 페이드·마무리" ),
		VARIATION	( "7. 작은 흔들림" ),
		SWEEP		( "8. 재생선", List.of(
						new Instrument("칼림바", 108),
						new Instrument("뮤직박스", 10),
						new Instrument("하프", 46),
						new Instrument("기타", 24) ) );

		final String		   label;
		final List<Instrument> instruments;

		Feature( String label )
		{	this( label, List.of() );
		}

		Feature( String label, List<Instrument> instruments )
		{	this.label		 = label;
			this.instruments = instruments;
		}
	}

	private final Map<Feature,Boolean>	  switches =
										new EnumMap<>( Feature.class );
	private final Map<Feature,Instrument> choices  =
										new EnumMap<>( Feature.class );

	private Runnable instrumentListener = () -> {};

	SoundSettings()
	{	for( Feature feature : Feature.values() )
		{	switches.put( feature, true );
			if( !feature.instruments.isEmpty() )
				choices.put( feature, feature.instruments.get(0) );
		}
	}

	synchronized boolean isOn( Feature feature )
	{	return switches.get( feature );
	}

	synchronized void setOn( Feature feature, boolean on )
	{	switches.put( feature, on );
	}

	/** 악기가 있는 기능의 현재 악기를 반환한다. */
	synchronized Instrument instrumentOf( Feature feature )
	{	return choices.get( feature );
	}

	void setInstrument( Feature feature, Instrument instrument )
	{	Runnable listener;
		synchronized( this )
		{	choices.put( feature, instrument );
			listener = instrumentListener;
		}
		listener.run();
	}

	/** 악기가 바뀔 때마다 실행할 코드를 정한다. */
	synchronized void onInstrumentChange( Runnable listener )
	{	instrumentListener = listener;
	}
}
