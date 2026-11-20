# fix-single-step-halt: 실행 중 Tick(Single Step)이 멈추지 않는 문제

- 브랜치: `fix/single-step-halt` (dev `94f50b3`에서 만듦)
- 수정 커밋: `d6555b8` (`fix: 실행 중 Tick(Single Step)이 시계를 멈추지 않고 계속 진행하던 문제 수정`)
- 고친 파일: `Clock.java` 2줄, `ClockIntegrationTest.java` 테스트 1개

## 문제

Go > Agonizing, Slow, Medium, Fast 중 하나로 게임을 실행하다가 Go > Tick (Single Step)을 누르면, 한 세대만 진행하고 멈춰야 할 게임이 그 속도로 계속 진행된다. 시계가 멈춘 상태(처음 실행, Halt, Load, Store, 캡처·배치 시작 뒤)에서 누르면 한 세대만 진행해 정상으로 보인다. 그래서 같은 조작이 어떤 때는 되고 어떤 때는 안 되는 것처럼 보인다.

화면에는 시계가 돌고 있는지 알려 주는 표시가 없다. 실행 중이라도 아래 경우에는 판이 멈춘 것처럼 보여서 이 문제를 만나기 쉽다.

- 실행 중에 Grid > Clear를 누른 경우. Clear는 시계를 멈추지 않는다.
- 판이 비었거나 2×2 블록처럼 멈춘 패턴만 남은 경우. 색 순환이 꺼져 있으면 바뀌는 셀이 없는 틱에는 다시 그리지 않는다.

## 원인

원본 `Clock`의 Go 메뉴 처리는 Tick을 고르면 `tick()`만 부르고, 돌고 있는 타이머는 그대로 둔다.

```java
if( toDo=='T' )
	tick();				      // single tick
```

실행 중에 누르면 한 세대가 더 진행되지만, 타이머가 계속 다음 세대를 만든다. 가장 느린 Agonizing도 1초에 2세대이므로 더 진행된 한 세대는 화면에서 구분되지 않는다. 사용자가 보기에는 Tick이 아무 일도 하지 않는 것과 같다.

## 수정

Tick 메뉴를 고르면 먼저 시계를 멈추고 한 세대만 진행한다.

```java
if( toDo=='T' )
{	stop();		// 실행 중이면 멈추고 한 세대만 진행
	tick();				      // single tick
}
```

- `stop()`을 `tick()`보다 먼저 부른다. 메뉴 처리와 타이머의 틱은 모두 이벤트 스레드에서 돌고, `stop()`은 이미 예약된 다음 틱도 취소한다. 기존 테스트 `haltBeforeFirstDeliveryCancelsPendingAutomaticTick`이 이 동작을 확인한다.
- 시계가 멈춘 상태에서 누르면 `stop()`은 아무 일도 하지 않으므로 원본과 동작이 같다.
- 다시 실행하려면 속도 메뉴를 다시 고른다. Halt 뒤와 같다.

## 검토한 다른 방법

| 방법 | 결과 |
| --- | --- |
| Tick 메뉴 처리에서 `stop()` 뒤 `tick()` (선택) | 메뉴 이름대로 한 세대만 진행한다. 바뀌는 곳이 메뉴 처리 한 곳이다 |
| `Clock.tick()` 안에서 `stop()` | 타이머도 틱마다 같은 `tick()`을 부르므로 자동 실행이 첫 틱에서 멈춘다 |
| 실행 중에는 Tick 메뉴를 비활성화 | 실행 여부를 따로 저장하고 메뉴를 켜고 끄는 코드가 속도 메뉴마다 필요하다. 사용자는 여전히 Halt를 먼저 눌러야 한다 |

## 테스트

`ClockIntegrationTest.singleStepWhileRunningAdvancesOneGenerationAndHalts`를 더했다. 10ms 간격으로 실행하는 중에 실제 Go > Tick (Single Step) 메뉴 콜백을 부르고, 그 자리에서 세대가 1개 진행됐는지와 80ms 뒤에도 1개 그대로인지 확인한다. 게임 코드에 테스트용 메서드는 더하지 않았고, 기존 `LifeTestSupport`와 `GENERATION` 이벤트로 확인한다.

| 소스 | 결과 |
| --- | --- |
| 수정 전 (dev `94f50b3` + 새 테스트) | `ClockIntegrationTest` 7개 중 1개 실패: `expected: <1> but was: <6>` |
| 수정 후 (`d6555b8`) | 전체 294개 통과 |

## 남은 일

Grid > Clear는 여전히 시계를 멈추지 않는다. Load, Store, 캡처·배치 시작, 배치 Undo는 시계를 멈추므로 Clear만 다르다. Clear도 멈출지는 따로 정한다.
