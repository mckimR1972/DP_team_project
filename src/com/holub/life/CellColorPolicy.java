package com.holub.life;

import java.awt.Color;

/** 살아 있는 셀의 색을 나이에 따라 정하는 규칙 (Strategy 패턴) */
public interface CellColorPolicy {
    /** 나이가 age인 셀의 색. age는 0 이상이고 막 태어난 셀은 0 */
    Color colorOf(int age);

    /** 나이에 따라 색이 바뀌는 규칙이면 true (Universe가 매 틱 판 전체를 다시 그림) */
    boolean changesWithAge();
}
