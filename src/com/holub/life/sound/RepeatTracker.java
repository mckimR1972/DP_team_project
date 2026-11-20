package com.holub.life.sound;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 판이 진동자로 자리 잡으면 같은 변화가 반복된다. 이걸 감지해서
 * 점점 줄어드는 볼륨으로 바꾼다.
 */
final class RepeatTracker
{
	private static final int HISTORY	  = 8;	// 이 길이까지의 주기를 찾는다
	private static final int FADE_REPEATS = 12;

	private final Deque<Integer> recent = new ArrayDeque<>();
	private int repeats = 0;

	/** 세대를 기록하고 볼륨을 반환한다. 새로운 변화면 1, 한동안
	 *  반복된 패턴이면 0까지 내려간다.
	 */
	double gainFor( List<Point> births, List<Point> deaths )
	{
		int signature = 31 * births.hashCode() + deaths.hashCode();

		repeats = recent.contains(signature) ? repeats + 1 : 0;

		recent.addLast( signature );
		if( recent.size() > HISTORY )
			recent.removeFirst();

		return Math.max( 0.0, 1.0 - (double)repeats / FADE_REPEATS );
	}
}
