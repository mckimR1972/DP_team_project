package com.holub.life;

/**
 * Universe가 전달하는 게임판 상태와 변경 정보를 받는 구독자.
 *
 * <p>하나의 변경 이벤트를 여러 구독자가 공유하며, 각 구독자는 필요한 종류와 정보만 사용한다.
 * GENERATION 이벤트에는 한 세대 진행으로 발생한 탄생·사망 좌표가 포함된다.
 *
 * <p>Universe는 Swing 이벤트 처리 스레드(EDT)에서 구독자를 동기 호출한다.
 * 콜백은 짧게 처리하고, 게임판을 다시 변경하지 말고 이벤트에 담긴 상태를 사용해야 한다.
 */
@FunctionalInterface
public interface BoardChangeListener {
    void onBoardChanged(BoardChangeEvent event);
}
