# 다이어그램 표기 통일안 (유스케이스·클래스)

기능별 문서의 유스케이스 다이어그램과 클래스 다이어그램을 나란히 놓으면 표기가 서로 다르다. 제출 전에 맞출 기준과, 기능별로 고칠 곳을 정리했다. 비교한 것은 2026-11-26 기준으로 열려 있는 각 기능의 문서 PR이다.

## 1. 그리는 범위

- 기능별 문서에는 자기 기능의 유스케이스와 클래스만 그린다.
- 원본 유스케이스와 원본 클래스는 자기 기능이 직접 맞물리는 것만 그린다. 예를 들어 유스케이스는 포함·확장 관계가 있는 것, 클래스는 고쳤거나 호출하는 것이다.
- 다른 담당의 기능은 그리지 않는다. 연결 지점 때문에 다른 담당의 클래스가 필요하면 클래스 이름만 두고 멤버는 적지 않는다.
- 자기 기능이 고친 클래스 안에서 다른 기능이 더한 멤버는, 자기 기능의 흐름에서 쓰이면 이유를 밝히고 남길 수 있다.
- 다섯 기능을 한꺼번에 보이는 그림이 필요하면 팀 전체 그림 한 장으로 따로 둔다.

## 2. 유스케이스 다이어그램

| 항목 | 기준 | 이유 |
| --- | --- | --- |
| 형식 | UML 유스케이스 표기로 그린다. 행위자는 막대 인형, 유스케이스는 타원, 시스템 경계는 "Life Game" 상자 하나다 | 강의 표기. 흐름도 도구로 그리면 이 표기가 나오지 않는다 |
| 행위자 | 이름은 "사용자" 하나로 쓴다 | 다섯 기능의 행위자는 모두 게임을 하는 같은 사람이다 |
| 경계 안의 상자 | 시스템 경계 안에 담당별 상자를 겹쳐 넣지 않는다 | 경계는 시스템 하나를 나타낸다 |
| 유스케이스 이름 | 영어, 동사로 시작한다 | 강의의 이름 규칙 |
| 원본 유스케이스 이름 | Edit Cells, Run / Halt Game, Step One Generation, Advance Generation, Clear Board, Store / Load Board, Exit Game | 같은 유스케이스가 문서마다 다른 이름으로 나오지 않게 한다 |
| 확장 유스케이스 표시 | 이름 끝에 `(E)`를 붙인다. 예: `Turn On Color Cycling(E)`, `Install Freezing(E)` | 과제 개요서는 "확장된 기능들은 유스케이스 이름에 'E'를 붙여서 명명할 것"이라고만 정한다. 지금의 `(E)`, `E`, `E-01`은 모두 이 조건을 지키지만, 괄호로 묶으면 이름의 일부가 아니라 표시라는 것이 바로 보인다 |
| 세대 진행에 붙는 기능 | Run / Halt Game과 Step One Generation이 Advance Generation을 «include»한다. 세대마다 일어나는 확장 기능은 Advance Generation의 확장 지점 "세대 확정 후"에 «extend»로 붙인다 | 색 순환, Freezing, 사운드가 이미 이 구조를 쓴다 |
| 확장 지점과 조건 | 확장 지점은 확장 대상 타원 안에 `extension points` 칸으로 적는다. 조건은 «extend» 선 옆 노트에 `condition: {…}`으로 적는다 | 강의 표기 |
| 색 | 흑백으로 그린다. 기존 기능과 확장 기능은 색이 아니라 이름 끝 표시로 구분한다 | 문서마다 색 뜻이 달라지지 않게 한다 |

## 3. 클래스 다이어그램

| 항목 | 기준 | 이유 |
| --- | --- | --- |
| 형식 | PlantUML로 그려 PNG로 넣는다. 클래스 아이콘(C, I 원)은 끈다 | 강의 그림에 없는 표시다 |
| 멤버 | 속성은 `가시성 이름 : 타입`, 연산은 `가시성 이름(매개변수 : 타입) : 반환 타입`으로 적는다. 한 줄에 멤버 하나만 적는다 | 강의 표기. 이름만 적으면 무엇을 주고받는지 그림에서 알 수 없다 |
| 반환 타입 `void` | 적지 않는다. 예: `+ tick()` | 돌려주는 값이 없다는 것은 반환 타입이 없는 것으로 충분히 보인다 |
| 안쪽 타입 | 바깥 클래스 이름을 붙여 적는다. 예: `BoardChangeEvent.Kind` | `Kind`만 적으면 어느 클래스의 타입인지 알 수 없다 |
| 인터페이스 | `Cell` 같은 인터페이스는 «interface»를 그대로 둔다 | 패턴 역할 이름으로 바꾸면 인터페이스인지 알 수 없다 |
| 패턴 역할 | 스테레오타입으로 적지 않는다. 노트나 본문 표에 적는다 | «Leaf», «Command» 같은 말은 UML 스테레오타입이 아니라 패턴에서의 역할이다 |
| Neighborhood–Cell | 합성(검은 마름모), Neighborhood 쪽 1, Cell 쪽 64 | Neighborhood가 자기 칸 64개를 직접 만들고, 칸은 다른 블록과 함께 쓰이지 않는다 |
| 의존 선 | «create», «use» 같은 스테레오타입을 쓴다. 긴 설명은 노트로 옮긴다 | 선 위의 글이 길면 관계 종류가 읽히지 않는다 |
| 다중성 | 연관과 합성에는 양쪽 끝 모두 다중성을 적는다 | 강의 표기 |
| 역할 이름 | 연관 끝에는 코드의 필드 이름을 적고, 가시성 기호(`-` 등)는 붙이지 않는다 | 가시성은 클래스 칸의 속성에서 보인다 |
| 여러 기능에 같이 나오는 관계 | 코드의 필드대로 연관으로 그린다. `Universe "1" --> "1" Cell : outermostCell`, `Life "1" --> "1" Universe : universe`, `Universe "1" --> "0..*" BoardChangeListener : boardListeners` | 같은 관계가 그림마다 연관, 집합, 의존으로 다르게 그려지지 않게 한다. 세 관계 모두 코드에서 필드다 |
| static | 밑줄로 적는다 | 강의 표기 |
| 그림 속 이력 | 커밋 번호와 PR 번호를 그림에 넣지 않는다 | 제출 문서에는 작업 이력을 넣지 않는다 |

## 4. "원본"의 기준

- 바꾸기 전 그림과 본문의 "원본"은 Holub 코드 그대로(저장소의 첫 커밋)로 한다.
- Holub 코드의 오류를 고친 것(`Resident.transition()`이 반대 값을 돌려주던 것 등)은 원본이 아니라 바뀐 점이다. 고친 내용은 `docs/original-improvements.md`를 가리킨다.
- 바꾸기 전 그림이 이 오류를 따로 보일 필요는 없다. 다만 그림이나 본문에 "원본과 같다"고 적는 곳이 오류를 고친 부분이면, 고친 뒤와 같다고 적는다.

## 5. 기능별로 고칠 곳

**색 순환**

- 클래스 다이어그램 두 장의 연관 끝에 역할 이름을 적는다. 두 그림 모두 `Neighborhood` → `Cell`에 `grid`, `Universe` → `Cell`에 `outermostCell`을 적고, 바꾼 뒤 그림은 `Resident` → `CellColorPolicy`에 `colorPolicy`도 적는다. `colorPolicy`는 static 필드이므로 밑줄을 긋는다.

**Freezing**

- 클래스 다이어그램의 역할 이름에서 가시성 기호를 뺀다. 예: `- grid` → `grid`, `- outermostCell` → `outermostCell`, `- universe` → `universe`.
- 연관의 출발 쪽에도 다중성을 적는다. 예: `Universe "1" --> "1" Cell`, `Life "1" --> "1" Universe`.

**사운드**

- 클래스 다이어그램의 `Life ..> Universe : <<use>>`(의존)를 연관 `Life "1" --> "1" Universe : universe`로 바꾼다.
- `Universe "1" o-- "*" BoardChangeListener`(집합)를 연관 `Universe "1" --> "0..*" BoardChangeListener : boardListeners`로 바꾼다.
- `Kind`를 `BoardChangeEvent.Kind`로 적는다.
- `Universe`의 `widthInCells()`를 뺀다. 게임 코드 어디에서도 부르지 않고, 사운드도 쓰지 않는다.

**통계**

- 유스케이스 다이어그램의 행위자 이름 "Game User"를 "사용자"로 바꾼다.
- 통계도 세대마다 갱신되므로, 다른 기능처럼 Advance Generation의 확장 지점 "세대 확정 후"에 «extend»로 붙인다. 붙이지 않는다면 그 이유를 본문에 적는다.
- 클래스 다이어그램의 다중성을 코드에 맞춘다. `Universe "1" --> "1" Cell : outermostCell`, `Life "1" --> "1" Universe : universe`, `Universe "1" --> "0..*" BoardChangeListener : boardListeners`.
- 연관 끝에 역할 이름을 적는다. 예: `Life` → `Statistics`에 `statistics`.
- 연산의 `: void`를 뺀다.
- 글꼴을 다른 그림과 같은 "Malgun Gothic"으로 바꾼다. 지금 지정한 "Apple SD Gothic Neo"는 Windows에 없어서 다시 그리면 글꼴이 바뀐다.

**캡처·템플릿**

- 유스케이스 다이어그램의 행위자 이름 "게임 사용자"를 "사용자"로 바꾼다.
- 클래스 다이어그램 연산의 `: void`를 뺀다.

**코드를 정리한 뒤에 맞출 것**

아래 멤버는 게임 코드에서는 쓰지 않고 테스트에서만 쓴다. 지금 그림은 코드와 맞으므로 그림만 먼저 고치지 않는다. 코드에서 지우면 그림에서도 함께 지운다.

- 통계: `Statistics`의 `current()`.
- Freezing: `Universe`의 `freezing()`, `FreezingRegion`의 `startGeneration`, `duration`.
