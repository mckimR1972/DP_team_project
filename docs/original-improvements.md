# 원본 코드 대비 개선 사항

Holub의 Life Game 원본에서 무엇을 고치고 어떻게 바꿨는지 적는다. 1절은 기능 오류, 2절은 실행 구조, 3절은 설계, 4절은 이를 확인하는 JUnit 테스트다. 다섯 기능(색 순환, Freezing, 패턴 캡처·템플릿, 통계, 사운드)의 자세한 설계는 각 기능 문서에 있다.

## 용어

- **원본**: Holub의 Life Game 코드 그대로.
- **확장판**: 원본에 다섯 기능을 더하고 이 문서의 개선을 적용한 지금의 코드.
- **셀**: 판의 한 칸. 판은 가로세로 64개, 모두 4,096개의 셀로 이루어진다.
- **블록**: 셀 8×8개의 묶음(`Neighborhood`). 판은 블록 8×8개로 이루어진다.
- **이벤트 스레드(EDT)**: Swing이 마우스·메뉴 입력과 화면 그리기를 차례로 처리하는 스레드. 처리할 작업은 이벤트 큐에 들어온 순서대로 하나씩 꺼내 실행한다.
- **이중 버퍼**: 화면에 바로 그리지 않고 보이지 않는 이미지에 먼저 그린 뒤 한 번에 화면으로 옮기는 방식. Swing이 `paintComponent()`를 부를 때 쓴다.
- **Memento**: 판을 저장하고 불러올 때 쓰는 저장용 객체(`Cell.Memento`).
- **나이**: 셀이 연속으로 살아남은 세대 수. 색 순환에서 셀 색을 정한다.
- **템플릿**: 판의 한 영역을 저장해 둔 모양. 판의 다른 곳에 다시 놓을 수 있다.
- Go 메뉴의 속도는 Agonizing, Slow, Medium, Fast이며 세대 간격은 각각 500, 150, 70, 30 ms다.

확장판에 더한 다섯 기능은 다음과 같다.

- **색 순환**: 살아 있는 셀의 색을 나이에 따라 바꾼다.
- **Freezing**: 사용자가 고른 사각형 영역의 셀을 정한 세대 수 동안 그대로 고정한다.
- **패턴 캡처·템플릿**: 판의 한 영역을 템플릿으로 저장하고, 다른 곳에 놓고, 놓은 것을 되돌린다.
- **통계**: 세대 수, 살아 있는 셀 수, 태어난 셀과 죽은 셀 수, 색별 셀 수를 보여 준다.
- **사운드**: 판의 변화를 소리로 들려준다.

## 요약

| 구분 | 항목 | 원본 | 확장판 |
| --- | --- | --- | --- |
| 오류 | `Resident.transition()` 반환값 | 상태가 바뀌면 `false` (반대) | 상태가 바뀌면 `true` |
| 오류 | `Neighborhood.isAlive()` | 항상 `true` | 하위 셀을 확인 |
| 오류 | Store | 좌표를 두 번 저장 | 한 번 저장 |
| 오류 | 실행 중 Tick | 시계가 계속 돈다 | 멈추고 한 세대만 진행 |
| 오류 | Load 뒤 첫 세대 | 불러온 패턴이 빈 블록으로 자라지 않는다 | 판 전체를 한 번 계산 |
| 오류 | Load 읽기 실패 | 빈 판이 남는다 | 판을 그대로 둔다 |
| 오류 | Load 취소 | "Read Failed!" 창이 뜬다 | 아무 일도 하지 않는다 |
| 구조 | 스레드 | 시계 스레드와 이벤트 스레드가 판을 함께 바꾼다 | 이벤트 스레드 하나 |
| 구조 | 화면 그리기 | `getGraphics()`로 화면에 바로 그린다 | `repaint()`와 `paintComponent()` |
| 설계 | 판 변화 알림 | 없음 | Observer |
| 설계 | 셀 색 | `Resident`의 상수 | Strategy |
| 설계 | 영역 고정 | 없음 | State |
| 설계 | 배치 되돌리기 | 없음 | Command |
| 설계 | 셀 하나 조회와 지정 | 판 전체를 Memento로 옮겨야 한다 | `Cell`에 조회·지정 메서드 |

원본의 29개 클래스(인터페이스 포함) 중 바꾼 것은 `Cell`, `Clock`, `Life`, `Neighborhood`, `Resident`, `Universe` 6개다. 나머지 23개는 코드가 그대로다.

## 1. 기능 오류 수정

### 1.1 `Resident.transition()`이 반대 값을 돌려준다

`Cell.transition()`은 상태가 바뀌었으면 `true`를 돌려줘야 한다. 원본은 "다음 상태가 지금과 같은가"(`isStable()`)를 그대로 돌려줘서 결과가 반대였다. `Neighborhood`는 이 값으로 블록이 바뀌었는지 판단하고, `Universe`는 다시 그릴지 정한다.

```java
// 원본
public boolean transition()
{	boolean changed = isStable();
	amAlive = willBeAlive;
	return changed;
}

// 확장판
public boolean transition()
{	boolean changed = amAlive != willBeAlive;
	amAlive = willBeAlive;
	return changed;
}
```

| 지금 → 다음 | 원본 반환값 | 확장판 반환값 |
| --- | --- | --- |
| 죽음 → 죽음 | `true` | `false` |
| 생존 → 생존 | `true` | `false` |
| 죽음 → 생존 | `false` | `true` |
| 생존 → 죽음 | `false` | `true` |

### 1.2 `Neighborhood.isAlive()`가 항상 `true`다

`Cell.isAlive()`는 자신이나 하위 셀 중 하나라도 살아 있으면 `true`다. 원본 `Neighborhood`는 하위 셀을 보지 않고 `true`만 돌려줘서, 빈 판도 살아 있다고 답했다.

```java
// 원본
public boolean isAlive()
{	return true;
}

// 확장판
public boolean isAlive()
{	for( int row = 0; row < gridSize; ++row )
		for( int column = 0; column < gridSize; ++column )
			if( grid[row][column].isAlive() )
				return true;
	return false;
}
```

하위 셀이 `Neighborhood`이면 같은 메서드를 다시 부르므로 블록이 몇 겹이어도 같은 기준으로 동작한다.

### 1.3 Store가 같은 좌표를 두 번 저장한다

`createMemento()`는 안에서 이미 `transfer(..., Cell.STORE)`로 살아 있는 셀을 모은다. 원본 `doStore()`는 그 뒤에 같은 호출을 한 번 더 해서, 셀 하나를 저장하면 좌표가 두 개 들어갔다.

```java
// 원본
Storable memento = outermostCell.createMemento();
outermostCell.transfer( memento, new Point(0,0), Cell.STORE );
memento.flush( out );

// 확장판
Storable memento = outermostCell.createMemento();
memento.flush( out );
```

### 1.4 실행 중에 Tick을 누르면 멈추지 않는다

Go > Tick (Single Step)은 한 세대만 진행하는 메뉴다. 원본은 `tick()`만 부르고 돌고 있는 시계는 그대로 둬서, 실행 중에 누르면 한 세대가 더 진행될 뿐 게임은 계속 돌았다. 가장 느린 Agonizing도 1초에 2세대라, 사용자에게는 Tick이 아무 일도 하지 않는 것처럼 보였다.

```java
// 원본
if( toDo=='T' )
	tick();

// 확장판
if( toDo=='T' )
{	stop();		// 실행 중이면 멈추고 한 세대만 진행
	tick();
}
```

| 검토한 방법 | 결과 |
| --- | --- |
| **Tick 메뉴 처리에서 `stop()` 뒤 `tick()` (선택)** | 메뉴 이름대로 동작한다. 바뀌는 곳이 메뉴 처리 한 곳이다 |
| `Clock.tick()` 안에서 `stop()` | 자동 실행도 같은 `tick()`을 부르므로 첫 세대에서 멈춘다 |
| 실행 중에는 Tick 메뉴를 끈다 | 실행 여부를 따로 저장하고 메뉴를 켜고 끄는 코드가 속도 메뉴마다 필요하다 |

시계가 멈춘 상태에서 누르면 `stop()`은 아무 일도 하지 않으므로 원본과 같다.

### 1.5 Load 뒤 첫 세대에서 태어나야 할 셀이 빠진다

원본은 세대마다 판 전체가 아니라 바뀐 블록과 그 옆만 계산한다. 그런데 `Neighborhood.transfer()`는 Load로 셀을 채운 블록만 계산 대상으로 표시했다. 그래서 불러온 패턴이 비어 있던 블록 쪽으로 자라야 하면 그 블록은 계산되지 않았다.

아래에서 좌표는 (열, 행)으로 적는다. 블록 경계 바로 위의 (7,7), (8,7), (9,7)에 셀 세 개로 된 가로 막대를 불러오면, 다음 세대에 (8,6), (8,7), (8,8)의 세로 막대가 되어야 한다. 원본 코드에서 그대로 실행하면 (8,8)이 빠지고 셀 두 개만 남는다. 행 8부터는 비어 있던 아래 블록이기 때문이다.

```java
// 확장판: Neighborhood.transfer() 끝에 추가
if (load) {
	amActive = true;
	activeEdges.add(Direction.ALL);
	oneLastRefreshRequired = true;
}
```

불러온 직후 한 번은 모든 블록과 가장자리를 계산 대상으로 둔다. 다음 세대부터는 원본처럼 바뀐 블록만 계산한다.

### 1.6 Load 읽기에 실패하면 판이 사라지고, 취소해도 오류 창이 뜬다

원본은 파일을 고른 뒤 판을 먼저 비우고 나서 파일을 읽었다. 그래서 읽기에 실패하면 빈 판이 남았다. 또 파일 선택을 취소하면 `FileNotFoundException`이 나는데, 읽기 실패와 같은 곳에서 잡혀 "Read Failed!"가 떴다. 취소했을 때는 판을 비우기 전에 예외가 나므로 판은 원본에서도 그대로였다.

```java
// 원본 (줄임)
FileInputStream in = new FileInputStream( Files.userSelected(...) );
Clock.instance().stop();
outermostCell.clear();				// 먼저 비운다
memento.load( in );					// 여기서 실패하면 빈 판
outermostCell.transfer( memento, new Point(0,0), Cell.LOAD );

// 확장판 (줄임)
try { source = Files.userSelected(...); }
catch (FileNotFoundException cancelled) { return; }	// 취소: 아무것도 안 함

try (FileInputStream in = new FileInputStream(source)) { memento.load(in); }
catch (IOException exception) { /* "Read Failed!" */ return; }	// 실패: 판 그대로

Clock.instance().stop();			// 다 읽은 뒤에 판을 바꾼다
outermostCell.clear();
outermostCell.transfer(memento, new Point(0, 0), Cell.LOAD);
```

파일 선택, 읽기, 판 교체를 순서대로 나눠서 앞 단계가 실패하면 판을 건드리지 않는다. 파일은 `try`-with-resources로 닫는다.

Store도 원본과 같은 구조라 파일 선택을 취소하면 "Write Failed!"가 뜬다. 이것은 아직 고치지 않았다(5절).

## 2. 구조 개선

### 2.1 판을 이벤트 스레드 하나에서만 바꾼다

**원본 구조.** 시계는 `java.util.Timer`로 만든 별도 스레드에서 `tick()`을 부르고, `Universe`는 그 스레드에서 세대를 계산하고 판을 바꾼다. 마우스 클릭, 메뉴, 화면 그리기는 이벤트 스레드에서 한다.

```java
// 원본 Clock
private Timer clock = new Timer();		// java.util.Timer
tick = new TimerTask() { public void run(){ tick(); } };
clock.scheduleAtFixedRate( tick, 0, millisecondsBetweenTicks );
```

**문제.** 두 스레드가 같은 셀을 동기화 없이 읽고 쓴다.

- 시계 스레드가 다음 상태를 계산한 뒤(`figureNextState()`) 바꾸기(`transition()`) 전에 사용자가 셀을 클릭하면, 클릭으로 바꾼 상태를 `transition()`이 계산해 둔 값으로 덮어써서 클릭이 사라진다.
- 이벤트 스레드가 판을 그리는 도중에 시계 스레드가 판을 바꾸면, 두 세대가 섞인 화면이 그려질 수 있다.
- 판을 읽는 기능(통계, 사운드, 패턴 캡처, Freezing)이 늘수록 같은 문제가 기능마다 생긴다.

**바꾼 구조.** 시계를 `javax.swing.Timer`로 바꿨다. 이 타이머는 이벤트 스레드에서 `tick()`을 부르므로, 세대 진행, 클릭, 메뉴, 그리기가 한 줄로 차례로 실행된다. 다른 스레드에서 `tick()`을 불러도 이벤트 스레드로 넘긴다. `Life.main()`도 이벤트 스레드에서 창을 만든다.

```java
// 확장판 Clock
clock = new Timer(millisecondsBetweenTicks, e -> tick());	// javax.swing.Timer
clock.start();

public void tick()
{	if (!SwingUtilities.isEventDispatchThread()) {
		SwingUtilities.invokeLater(this::tick);
		return;
	}
	publisher.publish( ... );			// 원본과 같다
}
```

| 검토한 방법 | 판단 |
| --- | --- |
| 시계는 별도 스레드에 두고 판 변경, 집계, 표시에 동기화를 더한다 | 판을 읽는 기능마다 동기화를 맞춰야 하고, 실행 순서를 따라가기 어렵다 |
| **시계를 Swing 타이머로 바꿔 판 변경과 화면을 한 스레드에 둔다 (선택)** | 바뀌는 곳이 `Clock` 하나다. 판 변경 → 집계 → 표시가 한 스레드에서 차례로 돌아 순서를 이해하기 쉽다 |

**원본과 같은 것과 다른 것.**

- 같다: 속도 메뉴와 Halt, 메뉴가 열려 있는 동안 세대를 진행하지 않는 것, `Clock.Listener`로 알리는 방식.
- 다르다: 원본은 속도를 고르면 바로 첫 세대가 진행됐지만, 확장판은 세대 간격 한 번이 지난 뒤에 진행된다. Swing 타이머의 첫 실행이 간격만큼 늦기 때문이다.

**예외.** 사운드는 소리를 정확한 시간에 내려고 별도 스레드에서 음을 예약한다. 이 스레드는 판을 바꾸지 않는다. 판 변화 알림(3.2절)으로 받은 복사본만 읽고, 판 위에 그리는 재생 위치 표시만 바꾼 뒤 다시 그리기를 요청한다.

### 2.2 Swing의 그리기 방식을 따른다

**원본 구조.** `Universe`는 `paint()`를 덮어썼고, 세대가 바뀌면 `refreshNow()`가 `getGraphics()`로 얻은 그래픽 객체에 바뀐 블록을 바로 그렸다.

```java
// 원본
private void refreshNow()
{	SwingUtilities.invokeLater( new Runnable()
	{	public void run()
		{	Graphics g = getGraphics();
			...
			outermostCell.redraw(g, panelBounds, false);
			g.dispose();
		}
	});
}
```

**문제.** 화면에 셀을 하나씩 바로 그리면 판 전체를 그리는 데 약 80~140 ms가 걸린다. 판 대부분이 계속 바뀌는 상태로 Fast(30 ms)나 Medium(70 ms)으로 돌리면, 그리기 요청이 처리 속도보다 빨리 이벤트 큐에 쌓인다. 그러면 클릭, 메뉴, Halt가 쌓인 그리기 뒤에서 기다려 늦게 반영된다. 원본에서는 시계가 별도 스레드라 앞 그리기가 끝나지 않아도 요청이 계속 들어갔다. 또 판 위에 다른 것(선택 영역, 미리보기)을 겹쳐 그릴 자리가 없다.

**바꾼 구조.** `refreshNow()`는 `repaint()`만 부르고, 그리기는 `paintComponent()`에서 한다.

```java
// 확장판
private void refreshNow()
{	repaint();
}

protected void paintComponent(Graphics g)
{	super.paintComponent(g);
	...
	outermostCell.redraw(g, panelBounds, true);		// 판
	freezingTools.paintOverlay((Graphics2D) g);		// 고정 영역
	drawPlayhead( g );								// 사운드 재생 위치
	patternTools.paintOverlay((Graphics2D) g);		// 선택 영역과 템플릿 미리보기
}
```

| 검토한 방법 | 결과 |
| --- | --- |
| **`repaint()`로 요청 (선택)** | Swing이 밀린 요청을 하나로 합치고 이중 버퍼로 그린다. 판 전체 그리기가 약 2~5 ms로 줄고 요청이 쌓이지 않는다 |
| 원본 방식을 두고, 기다리는 요청이 있으면 새 요청을 버린다 | 쌓임은 줄지만 한 번 그리는 데 여전히 80 ms 이상 걸려 지연이 남는다 |
| 원본 방식을 두고, 바뀐 블록만 골라 그린다 | 판 대부분이 바뀌면 판 전체를 그리는 것과 같아진다. `Neighborhood`와 `Cell`도 고쳐야 한다 |

효과는 두 가지다.

- 계속 돌려도 클릭과 메뉴가 밀리지 않는다. 실제 창에서 잰 입력 대기는 최대 약 3 ms였다.
- 판 위에 겹쳐 그리는 것들을 한 메서드에서 정한 순서로 그린다. 창이 가려졌다 나타나도 Swing이 같은 메서드로 다시 그린다.

대가는 원본의 "바뀐 블록만 그리기"를 포기한 것이다. 확장판은 다시 그릴 때마다 판 전체를 그린다. 판 대부분이 바뀔 때는 이중 버퍼 덕분에 원본보다 훨씬 빠르다. 블록 몇 개만 바뀔 때는 원본보다 많이 그리지만, 한 번에 약 2~5 ms라 가장 짧은 세대 간격(30 ms)보다 짧다.

측정 방법과 결과는 색 순환 문서의 "판 전체를 다시 그리는 방법" 절에 있다.

## 3. 설계 개선

### 3.1 원본 설계와 확장의 원칙

원본은 다음 패턴으로 이루어져 있고, 확장판은 이를 그대로 둔다.

| 원본 패턴 | 클래스 | 역할 |
| --- | --- | --- |
| Composite | `Cell`, `Neighborhood`, `Resident` | 셀과 블록을 같은 인터페이스로 다룬다 |
| Singleton | `Universe`, `Clock` | 판과 시계가 하나뿐이다 |
| Memento | `Cell.Memento`, `Neighborhood.NeighborhoodState` | 판을 저장하고 되살린다 |
| Observer | `Clock`, `Publisher`, `Clock.Listener` | 시계 신호를 구독자에게 보낸다 |
| Prototype | `Resident.create()` | 판을 만들 때 셀을 복제한다 |
| Mediator | `Universe` | Swing 이벤트(마우스, 메뉴)와 게임 클래스 사이를 잇는다 |

원본의 한계는 기능이 클래스 안에 고정되어 있다는 점이다. 셀 색은 `Resident`의 상수이고, 판이 바뀐 것을 밖에서 알 방법이 없고, 편집은 클릭으로 셀 하나를 뒤집는 것뿐이다. 그래서 기능 하나를 더하려면 `Universe`나 `Resident`를 계속 고쳐야 한다.

확장판은 새 기능이 원본 클래스를 덜 건드리도록 아래 확장 지점을 두었다. 패턴은 문제가 실제로 있는 곳에만 썼다.

### 3.2 판 변화 알림 (Observer)

**원본의 한계.** 판이 바뀐 것을 `Universe` 밖에서 알 방법이 없다. 통계와 사운드처럼 판을 지켜보는 기능을 붙이려면 `Universe`가 그 기능들을 직접 불러야 한다.

**바꾼 구조.**

| 참여자 | 클래스 |
| --- | --- |
| Subject | `Universe` (`addBoardChangeListener()`, `removeBoardChangeListener()`) |
| Observer | `BoardChangeListener` |
| ConcreteObserver | `Statistics`, `SoundEffects` |
| 전달하는 값 | `BoardChangeEvent` |

`Universe`는 판을 바꾸는 모든 곳(세대 진행, 마우스 편집, 템플릿 배치와 되돌리기, Clear, Load, 색 규칙 변경) 뒤에 이벤트를 만들어 등록된 구독자에게 보낸다. 이벤트에는 세대 수, 살아 있는 셀, 그 세대에 태어난 셀과 죽은 셀, 셀 색이 들어 있다.

```java
private void notifyBoardChanged(BoardChangeEvent.Kind kind, boolean[][] before) {
    requireEventDispatchThread();
    if (boardListeners.isEmpty()) return;
    BoardChangeEvent event = createBoardChangeEvent(kind, before);
    for (BoardChangeListener listener : boardListeners.toArray(new BoardChangeListener[0]))
        listener.onBoardChanged(event);
}
```

설계에서 정한 것은 다음과 같다.

- **이벤트는 판의 복사본이다.** 만든 뒤 바뀌지 않고, 구독자는 판을 직접 만지지 않는다. 그래서 사운드 스레드가 이벤트를 읽어도 2.1절의 한 스레드 구조가 깨지지 않는다.
- **세대 진행 이벤트는 실제 세대 진행에서만 만든다.** 밖에서 `GENERATION`을 보내려 하면 예외를 던진다. 태어난 셀과 죽은 셀 목록이 실제 계산과 어긋나지 않게 하려는 것이다.
- **등록하면 현재 상태를 한 번 받는다(`INITIAL`).** 구독자가 늦게 붙어도 처음 상태를 안다.

**Observer로 붙이는 기능의 기준.** 판을 읽기만 하는 기능(통계, 사운드)은 Observer로 붙는다. `Universe`는 이 기능들을 모르고, 기능끼리도 서로 모른다. 판을 바꾸는 기능(Freezing, 패턴 캡처·템플릿)은 세대 진행이나 편집 도중에 끼어들어야 하므로 `Universe`가 직접 연결한다. 원본에서부터 `Universe`가 Swing 이벤트와 게임 사이의 Mediator이기 때문이다(3.1절). 이 기능들끼리의 규칙(고정 영역에는 템플릿을 놓지 않는다 등)도 `Universe`에 모았다. 5절의 `Universe`가 커진 것은 이 연결 때문이다.

| 검토한 방법 | 판단 |
| --- | --- |
| `Universe`가 `Statistics`, `SoundEffects`를 직접 부른다 | 기능이 늘 때마다 `Universe`를 고쳐야 한다. 기능끼리도 서로 알게 된다 |
| 사운드가 원본 `Clock`을 직접 구독한다 | `Universe`는 고치지 않아도 된다. 그러나 `Publisher`는 새 구독자를 목록 맨 앞에 넣어서, 나중에 등록한 사운드가 `Universe`보다 먼저 불려 바뀌기 전의 판을 본다. 무엇이 바뀌었는지도 사운드가 직접 계산해야 한다 |
| **`Universe`가 바뀐 결과를 계산해 이벤트로 보낸다 (선택)** | 비교 코드가 한 곳에 있고 구독자를 더하기 쉽다. 대가로 세대마다 바꾸기 전에 한 번, 바꾼 뒤에 한 번으로 총 두 번 읽지만 다시 그리는 비용보다 훨씬 작다 |

### 3.3 셀 색 규칙 (Strategy)

**원본의 한계.** 살아 있는 셀의 색은 `Resident`의 상수 `LIVE_COLOR`(빨강) 하나다. `Resident`는 `final` 클래스라 상속으로 색을 바꿀 수 없고, 4,096개 셀은 판을 만들 때 한 번 만들어져 실행 중에 다른 객체로 바꿀 수 없다.

**바꾼 구조.**

| 참여자 | 클래스 |
| --- | --- |
| Strategy | `CellColorPolicy` (`colorOf(int age)`, `changesWithAge()`) |
| ConcreteStrategy | `FixedColorPolicy`(한 색), `CyclingColorPolicy`(일정 세대마다 다음 색) |
| Context | `Resident` (나이를 세고 규칙에 색을 묻는다) |
| Client | `Universe` (Color 메뉴에서 규칙을 바꿔 끼운다) |

```java
// 원본 Resident.redraw()
g.setColor(amAlive ? LIVE_COLOR : DEAD_COLOR );

// 확장판
g.setColor(amAlive ? colorPolicy.colorOf(age) : DEAD_COLOR );
```

기본 규칙은 `FixedColorPolicy(LIVE_COLOR)`라 색 순환을 켜지 않으면 원본과 같은 빨강이다. 새 규칙은 구현 클래스 하나와 메뉴 항목 하나를 더하면 되고 `Resident`는 고치지 않는다.

나이를 세려고 세대 처리 흐름도 하나 바꿨다. 원본은 다음 상태를 계산한 결과 바뀔 셀이 없으면 `transition()`을 건너뛰었다. 확장판은 멈춘 패턴의 셀도 나이를 먹어야 하므로 매 세대 `transition()`을 부른다. 원본의 절약 하나를 포기한 것이지만, 바뀐 셀이 없으면 다시 그리지 않는 것은 그대로다(색 순환이 꺼져 있을 때).

| 검토한 방법 | 판단 |
| --- | --- |
| **Strategy (선택)** | 원본 빨강과 순환이 각자의 클래스다. 메뉴는 규칙 객체 하나만 바꿔 끼우고, 계산 방식이 다른 규칙도 클래스 하나로 더한다 |
| 패턴 없이 값으로만 (차선책) | 색 목록과 간격을 값으로 두고 `Resident` 안에서 계산한다. 코드는 가장 적지만 원본 빨강을 "색이 1개인 순환"으로 흉내 내야 하고, 계산 방식이 다른 규칙은 `Resident`를 고쳐야 한다 |
| State | 사용자가 바꾸는 것은 셀의 상태가 아니라 판 전체의 규칙이다. 색은 나이에서 바로 계산되므로 셀마다 상태 객체를 두면 같은 정보를 두 번 갖는다 |
| Decorator | 원본 판은 격자의 셀이 `Resident`라고 가정한다. 감싸는 객체를 끼우면 맞출 곳이 늘고, 켜고 끄기에는 여전히 규칙 객체가 필요하다 |

### 3.4 영역 고정 (State)

**원본의 한계.** 셀은 규칙대로만 바뀐다. 특정 영역을 정해진 세대 동안 묶어 두는 방법이 없다.

**바꾼 구조.**

| 참여자 | 클래스 |
| --- | --- |
| Context | `FreezingRegion` (영역 하나) |
| State | `FreezingState` |
| ConcreteState | `NormalState`(규칙대로), `FrozenState`(고정, 남은 세대 수를 센다) |

`Universe`는 다음 상태 계산과 바꾸기 사이에 영역들에게 차례를 준다. `FrozenState`는 고정을 시작할 때의 셀 상태로 다음 상태를 덮어쓰고, 세대가 끝나면 남은 세대를 하나 줄여 0이 되면 스스로 `NormalState`로 바꾼다. 영역은 자기가 고정 중인지 `if`로 확인하지 않고 현재 상태 객체에 맡긴다.

```java
outermostCell.figureNextState( ... );		// 원본 규칙으로 계산
freezing.beforeTransition( outermostCell );	// 고정 영역만 다음 상태를 덮어씀
outermostCell.transition();					// 원본대로 바꾼다
freezing.afterGeneration();					// 남은 세대 감소, 0이면 Normal로
```

이 설계에는 두 가지 결정이 있었다.

**고정을 어디에서 적용할까.**

| 검토한 방법 | 판단 |
| --- | --- |
| `Resident.figureNextState()` 안에서 고정 여부를 본다 | 규칙 코드에 분기가 섞이고, `Resident`는 자기 좌표를 몰라 영역을 판단할 수 없다 |
| `transition()` 뒤에 고정을 시작할 때의 셀 상태로 되돌린다 | `Cell`은 고치지 않아도 되지만, 셀이 한 번 실제로 바뀌었다가 돌아가서 태어난 셀·죽은 셀 목록과 나이가 어긋난다 |
| 판 변화 알림(3.2절)을 받아 되돌린다 | 다른 기능과 같은 방식이지만, 구독자는 판을 바꾸지 않는다는 약속을 어기고 통계와 사운드가 되돌리기 전의 판을 받는다 |
| **계산과 바꾸기 사이에서 다음 상태만 덮어쓴다 (선택)** | 원본 규칙 코드가 그대로이고 셀이 한 번도 바뀌지 않는다. 대신 `Cell`에 메서드 하나(`holdNextState()`)가 늘었다 |

**고정 여부를 어떻게 표현할까.** Strategy가 아니라 State를 골랐다. 고정은 사용자가 골라 끼우는 규칙이 아니라 시간이 지나면 스스로 끝나는 상태이므로, 끝나는 시점을 상태 객체가 직접 정하는 구조가 맞다. 상태 객체는 셀마다가 아니라 영역마다 하나라서 4,096개 셀에 필드를 달 필요가 없다.

### 3.5 템플릿 배치 되돌리기 (Command)

**원본의 한계.** 편집을 되돌릴 방법이 없다.

**바꾼 구조.** `PlacePatternCommand`가 배치 한 번을 나타낸다. `execute()`는 템플릿의 살아 있는 셀을 판에 놓고, 원래 죽어 있다가 이 배치로 살아난 셀만 기억한다. `undo()`는 그 셀만 다시 죽인다. `BoardEditor`가 명령을 최대 100개까지 쌓아 두고 하나씩 되돌린다. 버튼을 누른 채 끌며 여러 번 찍은 것은 한 명령으로 합쳐 한 번에 되돌린다.

기억한 좌표는 배치 직후의 판에서만 의미가 있다. 그래서 세대가 진행되거나 다른 편집(클릭, Clear, Load 등)이 있으면 쌓아 둔 명령을 모두 비운다.

| 검토한 방법 | 판단 |
| --- | --- |
| 배치 전 판 전체를 원본 Memento로 저장한다 | 범용 복원에는 간단하지만 배치마다 판 전체 크기의 상태를 저장한다 |
| 셀마다 이전·이후 상태를 함께 기억한다 | 이미 살아 있던 셀을 덮어쓰는 편집까지 되돌릴 때 맞다. 지금 배치는 셀을 살리기만 하므로 필요 이상이다 |
| **살아난 셀만 기억하는 명령 객체 (선택)** | 저장하는 양이 새로 살아난 셀 수만큼이다. 이미 살아 있던 셀은 건드리지 않는다 |

### 3.6 셀 하나 조회와 지정 (Composite 확장)

**원본의 한계.** `Cell`에는 셀 하나의 상태를 묻거나 정하는 메서드가 없다. 특정 셀을 읽거나 정해야 하는 기능(통계, 사운드, 템플릿 배치, Freezing)을 원본만으로 만들려면 판 전체를 Memento로 옮겨야 한다.

**바꾼 구조.** `Cell`에 메서드 3개를 더하고 `Neighborhood`, `Resident`, `Cell.DUMMY`가 구현한다.

| 메서드 | 하는 일 | 쓰는 기능 |
| --- | --- | --- |
| `isAliveAt(row, column)` | 그 셀이 살아 있는가 | 판 변화 이벤트 생성, 템플릿 배치, Freezing, 마우스 편집 |
| `colorAt(row, column)` | 그 셀의 지금 색 | 색별 통계 |
| `holdNextState(location, alive)` | 그 셀의 다음 상태를 정해 둔다 | Freezing |

`Neighborhood`는 좌표로 해당 하위 셀을 찾아 같은 메서드를 부르고, `Resident`에 닿으면 답한다. 원본 Composite가 `figureNextState()`, `redraw()`를 하위 셀로 넘기는 방식과 같다.

```java
public boolean isAliveAt(int row, int column)
{	int subcellWidth = grid[0][0].widthInCells();
	return grid[row / subcellWidth][column / subcellWidth]
				.isAliveAt( row % subcellWidth, column % subcellWidth );
}
```

| 검토한 방법 | 판단 |
| --- | --- |
| 원본 Memento(`createMemento()`, `transfer()`)로 판 전체를 옮긴다 | `Cell`은 고치지 않지만, 셀 하나만 궁금해도 판 전체를 복사한다. `transfer()`는 계산 대상 블록 표시를 바꾸므로, 저장할 때만 쓰던 경로를 세대마다 부르게 된다 |
| `Universe`가 따로 `boolean[][]`을 들고 있는다 | `Cell`은 고치지 않지만, 클릭, Clear, Load, 세대 진행마다 실제 셀과 맞춰야 하고 한 곳이라도 빠지면 어긋난다 |
| 안쪽 `Resident`를 꺼내 주는 메서드를 둔다 | 바깥에서 셀을 직접 바꿀 수 있어 Composite의 캡슐화가 깨진다 |
| **좌표로 묻는 메서드를 Composite 구조로 내려보낸다 (선택)** | 셀 상태가 한 곳에만 있다. 대신 `Cell`을 구현한 클래스 3개를 모두 고쳐야 했다 |

패턴 캡처는 영역 전체를 한 번 읽고 끝나므로 지금도 Memento로 판을 읽는다. 조회 메서드는 셀 하나를 자주 읽거나 정하는 기능에 쓴다.

## 4. JUnit 테스트

### 4.1 목적과 방식

원본 기능의 테스트 코드는 `test/original`에 있고, Load 테스트만 `test/statistics`의 `UniverseEventsTest`에 있다. 테스트의 목적은 두 가지다.

- **원본 오류가 다시 생기지 않게 한다.** 1절의 오류마다 그 오류를 직접 잡는 테스트를 두었다. 같은 테스트를 원본 코드에 돌리면 실패해야 의미가 있으므로, 가능한 것은 원본에서 실패하는 것을 확인했다(4.3절).
- **원본 기능이 확장 뒤에도 원래대로 동작하는지 확인한다.** 시계 메뉴, 저장, 불러오기를 실제 메뉴로 실행해 본다.

방식은 다음과 같다.

- 메뉴는 이름으로 찾아 실제 콜백을 실행한다. 메서드를 직접 부르는 것보다 사용자가 누르는 것에 가깝다.
- 화면을 띄우지 않으려고 메뉴 등록(`MenuSite`), 파일 선택(`Files`), 오류 창(`JOptionPane`)만 Mockito로 대신한다. 판, 시계, 메뉴 콜백, 저장과 불러오기는 실제 코드를 돌린다.
- Swing 작업은 이벤트 스레드에서 실행하고, 테스트마다 시계를 멈추고 판을 비운다. 판과 시계가 Singleton이라, 테스트 클래스마다 새 JVM에서 실행해 서로 영향을 주지 않게 했다.
- 원본 기능 테스트를 위해 게임 코드에 테스트용 메서드를 더하지 않았다. 결과는 판 변화 이벤트와 저장된 파일로 확인한다.

### 4.2 테스트 목록

아래 실행 수는 원본 기능을 확인하는 것만 센 것이다. `NeighborhoodTest`에는 조회 메서드(3.6절)를 확인하는 1개가, `UniverseStoreTest`에는 확장 기능(마우스 끌기, 재생 위치 표시 등)을 확인하는 5개가 더 있어 실제 실행 수는 각각 12개, 8개다.

| 테스트 | 실행 수 | 확인하는 것 | 관련 절 |
| --- | ---: | --- | --- |
| `ResidentTest` | 18 | 이웃 수 0~8개와 지금 생존 여부의 모든 조합에서 다음 상태와 `transition()` 반환값 | 1.1 |
| `NeighborhoodTest` | 11 | 빈 판, 셀 하나, 두 겹 블록, 마지막 셀이 죽은 판, Clear한 판에서 `isAlive()` | 1.2 |
| `UniverseStoreTest` (저장) | 3 | 실제 Store 메뉴로 저장한 파일의 좌표 개수와 좌표 값 (셀 0개, 1개, 4개) | 1.3 |
| `ClockIntegrationTest` | 7 | 아래 표 | 1.4, 2.1 |
| `UniverseEventsTest` (Load) | 3 | 아래 표 | 1.5, 1.6 |

`UniverseStoreTest`는 `createMemento()`만 부르지 않고 실제 Store 메뉴를 실행한다. 중복 저장은 `doStore()` 안에서 생기므로 메뉴를 거쳐야 잡힌다. 좌표 개수와 좌표 값을 함께 봐서 중복뿐 아니라 잘못된 좌표도 잡는다.

`ClockIntegrationTest`:

| 메서드 | 확인하는 것 |
| --- | --- |
| `singleStepMenuUsesTheSharedGenerationPathOnEdt` | Tick 메뉴가 이벤트 스레드에서 한 세대를 진행한다 |
| `automaticTimerPublishesOrderedGenerationsOnEdtAndStops` | 자동 실행의 세대가 1, 2, 3 순서로 이벤트 스레드에서 진행되고, 멈추면 더 진행하지 않는다 |
| `haltBeforeFirstDeliveryCancelsPendingAutomaticTick` | Halt가 이미 예약된 다음 세대도 취소한다 |
| `singleStepWhileRunningAdvancesOneGenerationAndHalts` | 실행 중 Tick이 한 세대만 진행하고, 80 ms 뒤에도 더 진행하지 않는다 |
| `changingSpeedRestartsOneTimerWithoutLeavingOldTicksRunning` | 속도를 바꿔도 시계가 하나만 돈다 |
| `selectedMenuSuppressesTicksUntilSelectionEnds` | 메뉴가 열려 있는 동안 세대를 진행하지 않는다 (원본 동작 유지) |
| `tickOutsideEdtIsDeliveredOnceOnEdt` | 다른 스레드에서 부른 `tick()`이 이벤트 스레드에서 한 번만 실행된다 |

`UniverseEventsTest`의 Load 테스트는 파일 선택 창 대신 정해진 파일을 돌려주게 하고 실제 Load 메뉴를 실행한다.

| 메서드 | 확인하는 것 |
| --- | --- |
| `loadPublishesRestoredStateOnceAndResetsComparisonBaseline` | 1.5절의 가로 막대를 불러와 한 세대 진행하면 아래 블록의 셀까지 셀 세 개의 세로 막대가 된다. 세대 수는 0부터 다시 센다 |
| `failedReadPreservesBoardGenerationAndStatisticsWithoutPublishing` | 읽을 수 없는 파일이면 "Read Failed!"를 띄우고 판과 세대 수가 그대로다 |
| `cancelledLoadPreservesCurrentStateWithoutPublishingOrShowingError` | 파일 선택을 취소하면 오류 창 없이 판이 그대로다 |

### 4.3 원본 코드에서의 결과

4.2절의 테스트 중 `ResidentTest`, `NeighborhoodTest`, `UniverseStoreTest`(저장)의 32개는 게임 코드만 쓰므로 원본 코드에 그대로 돌릴 수 있다. Gradle 9.5.1, JDK 17에서 원본 코드와 세 오류만 고친 코드에 같은 테스트를 돌린 결과다.

| 테스트 | 실행 수 | 원본 통과 / 실패 | 수정 후 통과 / 실패 |
| --- | ---: | ---: | ---: |
| `ResidentTest` | 18 | 0 / 18 | 18 / 0 |
| `NeighborhoodTest` | 11 | 7 / 4 | 11 / 0 |
| `UniverseStoreTest` (저장) | 3 | 1 / 2 | 3 / 0 |
| **합계** | **32** | **8 / 24** | **32 / 0** |

원본에서의 실패는 다음과 같았다.

- 상태가 그대로인 셀에서 `false` 대신 `true`, 바뀌는 셀에서 `true` 대신 `false`를 돌려줬다.
- 빈 판, 마지막 셀을 죽인 판, Clear한 판에서 `isAlive()`가 `false` 대신 `true`를 돌려줬다.
- 셀 1개를 저장하면 좌표가 2개, 4개를 저장하면 8개 기록됐다.

원본에서 통과한 8개(셀이 살아 있는 판에서 `isAlive()`가 `true`, 빈 판 저장)는 수정 후에도 통과한다.

나머지 테스트는 판 변화 이벤트로 결과를 확인하므로 원본에서는 컴파일되지 않는다. 그래서 다음과 같이 확인했다.

- **Tick (1.4절)**: 확장판에서 Tick 수정만 되돌린 코드에 새 테스트를 돌리면 `ClockIntegrationTest` 7개 중 `singleStepWhileRunningAdvancesOneGenerationAndHalts` 1개가 `expected: <1> but was: <6>`으로 실패한다. 한 세대만 진행해야 할 동안 6세대가 진행됐다는 뜻이다. 고친 뒤에는 7개 모두 통과한다.
- **Load 뒤 첫 세대 (1.5절)**: JUnit이 아니라 원본 코드에 같은 조작을 직접 실행했다. 가로 막대를 불러와 한 세대 진행하면 살아 있는 셀이 (8,6), (8,7) 두 개뿐이다. 확장판에서는 (8,8)까지 세 개다.

확장 기능 테스트를 포함한 전체 384개는 확장판에서 모두 통과한다. 실행 방법은 README.md에 있다.

## 5. 남은 한계

- `Universe`가 세대 진행, 마우스 편집, 판 변화 알림, 판을 바꾸는 기능의 연결을 모두 맡아 원본의 두 배 크기(256줄에서 514줄)가 되었다.
- 색 규칙은 `Resident`의 `static` 필드라 판 전체가 한 규칙을 쓰고, 테스트마다 기본 규칙으로 되돌려야 한다.
- `Universe`와 `Clock`은 원본대로 Singleton이라, 테스트에서 메뉴 등록을 대체 객체로 바꾸고 테스트 클래스마다 JVM을 새로 띄운다.
- Store의 파일 선택을 취소하면 원본처럼 "Write Failed!"가 뜬다.
- Grid > Clear는 원본처럼 시계를 멈추지 않는다.
