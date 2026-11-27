# Freezing: 상세 변경 내용과 기존 설계·코드와의 비교

- 코드 기준: Freezing을 합친 뒤의 코드. “기존”은 Freezing을 합치기 직전의 코드이고, 원본 Holub 코드와 다른 점은 따로 적었다.
- 이 문서는 최종 기능 명세 보고서가 아닌 다른 기능을 통합한 뒤 코드 변경사항을 기록하기 위한 작업용 문서이다.


## 1. 한눈에 보기

| 파일 | 기존 | 변경 후 | 규모 |
| --- | --- | --- | --- |
| `Cell` | 다음 상태는 `figureNextState()`만 정한다 | `holdNextState(location, alive)` 선언. `DUMMY`는 항상 `false` | +13 |
| `Neighborhood` | — | `holdNextState()`를 좌표가 속한 하위 셀에 위임 | +13 |
| `Resident` | `transition()`이 매 세대 나이를 센다 | `holdNextState()`가 `willBeAlive`만 바꾼다. 고정된 세대에는 나이를 세지 않는다 | +17 / −1 |
| `Universe` | 틱: 계산 → 전이 → 세대 증가 → 이벤트 | 계산과 전이 사이, 세대 증가 뒤에 Freezing 단계. 브러시가 고정된 칸을 건너뜀. Clear·Load 때 초기화. 배치 가드·Esc 연결. 영역 표시 그리기 | +50 / −3 |
| `Life` | 탭 2개(패턴 캡처, 통계) | Freezing 탭 추가, 탭을 떠나면 영역 선택 종료 | +3 |
| 새 파일 6개 | — | `FreezingState`, `NormalState`, `FrozenState`, `FreezingRegion`, `FreezingManager`, `FreezingTools` | 529줄 |

다른 기능의 클래스(`PatternTools`, `BoardEditor`, `PlacePatternCommand`, `Statistics`, `StatisticsPanel`, `BoardChangeEvent`, `SoundEffects`, `CellColorPolicy`와 구현들, `Clock`)는 고치지 않았다.

## 2. 세대 처리: `Universe`의 틱

### 2.1 원본 → 기존 → 변경 후

원본 Holub 코드의 틱은 계산과 전이 두 단계뿐이었고, 변화가 없으면 전이도 부르지 않았다.

```java
// 원본
public void tick()
{   if( outermostCell.figureNextState( Cell.DUMMY, ... ) )
    {   if( outermostCell.transition() )
            refreshNow();
    }
}
```

Freezing을 합치기 전의 코드에서는 색 순환·캡처·통계·사운드가 이미 들어와 아래 순서였다.

```java
// 기존
if (patternTools.isEditing()) return;
patternTools.boardChanged();
outermostCell.figureNextState( Cell.DUMMY, ... );
boolean[][] before = liveCells();
boolean changed = outermostCell.transition();
generation++;
notifyBoardChanged(BoardChangeEvent.Kind.GENERATION, before);
```

Freezing은 여기에 세 군데를 더했다.

```java
// 변경 후 (Universe.java 221~242행)
if (patternTools.isEditing() || freezingTools.isEditing()) return;   // ① 영역 선택 중에는 멈춤
patternTools.boardChanged();
outermostCell.figureNextState( Cell.DUMMY, ... );
boolean[][] before = liveCells();

FreezingManager freezing = freezingTools.manager();
freezing.beforeTransition( outermostCell );                          // ② 고정 영역의 다음 상태를 스냅샷으로

boolean changed = outermostCell.transition();
generation++;
freezing.afterGeneration();                                          // ③ 남은 세대 감소, 0이면 해제
notifyBoardChanged(BoardChangeEvent.Kind.GENERATION, before);
```

### 2.2 무엇이 달라졌나

| 항목 | 기존 | 변경 후 |
| --- | --- | --- |
| 다음 상태를 정하는 곳 | `figureNextState()` 한 곳 | `figureNextState()`가 정한 뒤, 고정된 칸만 `holdNextState()`가 스냅샷 값으로 덮어쓴다 |
| 생존 규칙 코드 | `Resident.figureNextState()`의 `neighbors==3 \|\| (amAlive && neighbors==2)` | 그대로 |
| 이웃이 읽는 값(`amAlive`) | 전이 때만 바뀐다 | 그대로. `holdNextState()`는 `willBeAlive`만 바꾼다 |
| 이벤트의 출생·사망 목록 | 전이 전(`before`)과 전이 후 보드의 차이 | 계산 방식은 그대로. 고정된 칸은 전후가 같아서 목록에 들어가지 않는다 |
| 세대를 건너뛰는 조건 | 캡처·배치 중 | 캡처·배치 중 또는 Freezing 영역 선택 중 |

`before`를 `beforeTransition()`보다 먼저 찍는 순서는 그대로다. `liveCells()`는 현재 상태(`amAlive`)를 읽고 `holdNextState()`는 다음 상태(`willBeAlive`)만 바꾸므로, 두 줄의 순서가 바뀌어도 `before`의 내용은 같다.

## 3. Composite 계층: `holdNextState()`

기존 `Cell` 계층에는 좌표로 칸 하나에 닿는 조회 메서드(`isAliveAt`, `colorAt`)만 있었다. 다음 상태를 바깥에서 바꿀 방법은 없었다.

```java
// Cell.java (추가)
boolean holdNextState( Point location, boolean alive );   // location: x = 열, y = 행

// Cell.DUMMY (추가)
public boolean holdNextState(Point p, boolean a) { return false; }
```

```java
// Neighborhood.java (추가): 좌표가 속한 하위 셀에 넘긴다
public boolean holdNextState(Point location, boolean alive)
{   int subcellWidth = grid[0][0].widthInCells();
    return grid[location.y / subcellWidth][location.x / subcellWidth]
            .holdNextState( new Point( location.x % subcellWidth,
                                       location.y % subcellWidth ), alive );
}
```

```java
// Resident.java (추가): 계산된 다음 상태만 바꾼다
public boolean holdNextState(Point location, boolean alive)
{   assert location.x == 0 && location.y == 0;
    boolean overridden = willBeAlive != alive;
    willBeAlive = alive;
    heldThisGeneration = true;
    return overridden;
}
```

- `Neighborhood`는 블록의 활성 표시(`amActive`)를 건드리지 않는다. 다음 상태가 실제로 덮어써지는 경우는 방금 `figureNextState()`가 그 블록을 “변화 있음”으로 계산한 경우뿐이라, 블록은 이미 활성이고 다음 세대에도 다시 계산된다.
- 반환값은 “계산된 다음 상태가 실제로 바뀌었는가”다. `FreezingRegion.holdSnapshot()`과 `FreezingManager.beforeTransition()`이 이 값을 모아 돌려준다.
- `Cell`에 메서드가 하나 늘었으므로, 앞으로 `Cell` 구현을 추가하면 `holdNextState()`도 구현해야 한다.

## 4. 셀의 나이: `Resident.transition()`

색 순환 기능(PR #8)이 넣은 나이 계산에 조건 하나를 더했다.

```java
// 기존
public boolean transition()
{   boolean changed = amAlive != willBeAlive;
    age = (amAlive && willBeAlive) ? age + 1 : 0;
    amAlive = willBeAlive;
    return changed;
}

// 변경 후
public boolean transition()
{   boolean changed = amAlive != willBeAlive;
    if( !heldThisGeneration )      // 고정된 셀은 나이와 색도 그대로 둔다
        age = (amAlive && willBeAlive) ? age + 1 : 0;
    heldThisGeneration = false;
    amAlive = willBeAlive;
    return changed;
}
```

| 항목 | 기존 | 변경 후 |
| --- | --- | --- |
| 고정된 살아 있는 칸의 나이 | (Freezing 없음) 매 세대 +1 | 고정된 세대에는 그대로. 풀리면 멈췄던 값에서 이어서 센다 |
| 색 규칙(`CellColorPolicy`) | — | 고치지 않았다. 나이가 그대로이므로 어떤 규칙이든 색이 그대로다 |

처음 구현(2026-11-11)의 `holdNextState()`는 `willBeAlive`만 바꿨고 나이는 다루지 않았다. 당시에는 색 순환이 없었기 때문이다. 다른 기능과 합치면서 `heldThisGeneration`을 추가했다.

## 5. 편집 제한: 브러시와 템플릿

### 5.1 일반 클릭·드래그 (`Universe`의 브러시)

```java
// mousePressed (변경 후)
if (patternTools.isEditing() || freezingTools.isEditing()          // + 영역 선택 중
        || !SwingUtilities.isLeftMouseButton(e)
        || !contains(e.getPoint())) return;
lastCell = cellAt( e.getPoint() );
if (freezingTools.manager().isFrozen(lastCell))                    // + 고정된 칸에서는 획을 시작하지 않는다
{   freezingTools.blockedEdit();
    lastCell = null;
    return;
}
```

```java
// setCell (변경 후): 드래그가 지나가는 칸마다 불린다
if( outermostCell.isAliveAt(cell.y, cell.x) == alive ) return false;
if( freezingTools.manager().isFrozen(cell) )                       // + 고정된 칸은 건너뛴다
{   freezingTools.blockedEdit();
    return false;
}
```

| 상황 | 기존 | 변경 후 |
| --- | --- | --- |
| 고정된 칸을 클릭 | (Freezing 없음) 토글 | 무변경, `EDIT` 이벤트 없음, 상태줄에 안내 |
| 고정된 칸에서 드래그 시작 | — | 획 전체를 시작하지 않는다 |
| 바깥에서 시작한 드래그가 영역을 지나감 | — | 영역 안의 칸만 건너뛰고 바깥 칸은 계속 그리거나 지운다 |
| 영역 선택 중의 드래그 | — | 브러시가 동작하지 않는다(드래그는 선택에 쓰인다) |

### 5.2 템플릿 배치 (`BoardEditor`의 가드)

캡처 기능(PR #13)이 `BoardEditor.setPlacementGuard(Predicate<Rectangle>)`를 열어 두었고 기본값은 “막지 않음”이었다. Freezing은 `Universe` 생성자에서 한 줄로 연결했다. 캡처 쪽 코드는 고치지 않았다.

```java
patternTools.setPlacementGuard( area -> freezingTools.manager().overlapsFrozen(area) );
```

| 항목 | 기존 | 변경 후 |
| --- | --- | --- |
| 가드의 내용 | `area -> false` | 템플릿 사각형이 고정 영역과 겹치면 `true` |
| 검사 단위 | — | 사각형 전체. 빈 여백만 겹쳐도 그 배치를 거부한다 |
| 누른 채 끌어서 반복 배치 | 모든 자리에 찍힌다 | 겹치는 자리만 건너뛰고 나머지는 찍힌다(가드는 `place()` 안에서 검사) |
| 배치 되돌리기 | 이력이 남아 있으면 가능 | 영역 선택을 시작할 때 이력을 비운다. 되돌리기가 고정된 칸을 지우는 일을 막는다 |

## 6. 초기화와 연결: Clear, Load, Esc, 탭

```java
// Grid > Clear (변경 후)
outermostCell.clear();
generation = 0;
freezingTools.reset();                                   // + 영역을 모두 지운다
notifyBoardChanged(BoardChangeEvent.Kind.CLEAR);

// doLoad(): 파일을 다 읽은 뒤
outermostCell.transfer(memento, new Point(0, 0), Cell.LOAD);
generation = 0;
freezingTools.reset();                                   // + 읽기에 성공했을 때만
notifyBoardChanged(BoardChangeEvent.Kind.LOAD);
```

| 동작 | 기존 | 변경 후 |
| --- | --- | --- |
| `Grid > Clear` | 보드 비움, 세대 0, `CLEAR` | + Freezing 영역 전부 제거, 영역 선택 종료 |
| `Grid > Load` 성공 | 보드 교체, 세대 0, `LOAD` | + Freezing 영역 전부 제거 |
| `Grid > Load` 취소·읽기 실패 | 무변경 | 무변경(영역도 그대로) |
| `Grid > Store` | 셀만 저장 | 그대로. Freezing 정보는 저장하지 않는다 |
| Esc | 패턴 편집 종료 | 영역 선택 중이면 선택 종료, 아니면 패턴 편집 종료 |
| 사이드바 | 탭 2개. 패턴 캡처 탭을 떠나면 패턴 편집 종료 | 탭 3개. Freezing 탭을 떠나면 영역 선택 종료(설치된 영역은 유지) |
| 그리기 순서 | 셀 → 재생선 → 패턴 미리보기 | 셀 → Freezing 표시 → 재생선 → 패턴 미리보기 |

`Life.createSidebar()`의 변경은 세 줄이다.

```java
JPanel freezing = universe.freezingControls();
tabs.addTab("Freezing", freezing);
// 탭 변경 리스너 안
if (tabs.getSelectedComponent() != freezing) universe.finishFreezingEditing();
```

## 7. 새 클래스

| 클래스 | 책임 | 주요 멤버 |
| --- | --- | --- |
| `FreezingState` | 세대 처리의 두 시점에서 할 일을 정의하는 인터페이스 | `beforeTransition(region, board)`, `afterGeneration(region)`, `isFrozen()`, `remainingGenerations()` |
| `NormalState` | 고정되지 않은 상태. 아무것도 하지 않는다 | `INSTANCE` 하나를 공유 |
| `FrozenState` | 고정 상태. 스냅샷을 적용하고 남은 세대를 줄이다가 0이 되면 영역을 `NormalState`로 바꾼다 | `remaining` |
| `FreezingRegion` | 영역 하나. 사각형, 스냅샷, 현재 상태를 가진다 | `freeze()`, `release()`, `changeState()`, `holdSnapshot()` |
| `FreezingManager` | 영역 목록, 설치 검증(빈 영역·겹침), 해제, 초기화, 세대 처리 두 시점의 전달. Swing 코드 없음 | `install()`, `release()`, `releaseAll()`, `reset()`, `beforeTransition()`, `afterGeneration()`, `isFrozen()`, `overlapsFrozen()`, `addListener()` |
| `FreezingTools` | Freezing 탭 화면, 드래그 선택, 보드 위 표시 | `beginSelection()`, `cancel()`, `reset()`, `blockedEdit()`, `paintOverlay()` |

## 8. 처음 구현에서 병합 뒤로 바뀐 것

Freezing의 처음 구현(2026-11-11)은 색 순환·통계·사운드가 없는 상태에서 만들었다. 다른 기능과 합치면서(2026-11-20) 아래를 고쳤다.

| 항목 | 처음 구현 | 병합 뒤 | 바꾼 이유 |
| --- | --- | --- | --- |
| 세대 번호 | `FreezingManager`가 `private long generation`을 갖고 `beginGeneration()`에서 올림 | 카운터 삭제. `Universe`의 값을 `LongSupplier`로 읽음 | 통계가 들어오면서 `Universe`에도 세대 번호가 생겼다. 두 곳에서 세면 초기화 시점이 어긋날 때 숫자가 달라진다 |
| 스냅샷 읽기 | `board.createMemento()`로 보드 전체를 복사한 뒤 `memento.isAlive(point)` | `board.isAliveAt(row, column)` | 드래그 그리기 기능이 추가한 좌표 조회 메서드가 생겨 필요한 칸만 물을 수 있게 됐다 |
| 고정된 칸의 나이 | 다루지 않음 | `heldThisGeneration`으로 나이 유지 | 색 순환이 들어오면서 고정 중에도 색이 바뀌는 문제가 생겼다 |
| 사이드바 | `Universe`가 자체 `JTabbedPane`에 캡처 탭과 Freezing 탭을 둠 | `Life.createSidebar()`의 공용 탭에 세 번째로 추가 | 통계가 공용 탭을 `Life`에 만들었다 |
| 테스트 | `main()` 방식의 `FreezingFeatureTest`, `FreezingUiTest` | JUnit `FreezingModelTest`, `FreezingIntegrationTest`, `FreezingSoundIntegrationTest` | `gradlew test` 한 번으로 돌리기 위해 |

상태 구조(`FreezingState` / `NormalState` / `FrozenState` / `FreezingRegion`)와 “계산과 전이 사이에서 다음 상태만 교체”하는 방식은 처음 구현부터 그대로다.
