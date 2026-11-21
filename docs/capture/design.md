# 캡처 설계 과정과 패턴 적용

기준: dev `838182c` / 2026-11-22. 요구사항은 [기능 명세](feature-spec.md)의 R/E ID를 따른다. 아래의 대안 비교는 구현·기존 기록을 근거로 한 설계 설명이다. 모든 대안을 실제로 구현하거나 성능 측정했다는 뜻은 아니다.

## 1. 설계가 발전한 과정

| 단계 | 확인 가능한 변경 | 근거 |
|---|---|---|
| 초기 캡처 | 영역을 Memento로 읽고 불변 템플릿과 파일 저장을 분리했다. 배치 Command는 전체 보드를 저장·복원했다. | 초기 커밋 [cd2fdb1](https://github.com/mckimR1972/DP_team_project/commit/cd2fdb1), PR #9 |
| dev 통합 | PR #9에서 색상 기능과 통합하며 기존 셀의 나이와 갱신 경로를 보존했다. 이후 일반 드래그(#5)·사운드(#3)가 반영된 dev를 기준으로 PR #13을 보완했다. | [PR #9](https://github.com/mckimR1972/DP_team_project/pull/9), [PR #5](https://github.com/mckimR1972/DP_team_project/pull/5), [PR #3](https://github.com/mckimR1972/DP_team_project/pull/3), [PR #13](https://github.com/mckimR1972/DP_team_project/pull/13) |
| 템플릿 드래그 보완 | 시나리오를 독립 JUnit으로 나누고, 좌표 편집을 분리한 뒤 변경 좌표 Undo·누름 반복·보간·작업 묶음을 차례로 도입했다. | [PR #13의 10개 커밋](https://github.com/mckimR1972/DP_team_project/pull/13/commits) |
| 입력·연계 안정화 | 해제·포커스 상실·취소를 정리하고 모델 수준 guard와 완료 알림을 추가했다. | PR #13의 `8bb9142`, `5c88c29`, `c9162b6` |
| 현재 문서화 | 요구·결정·검사·그림을 연결하고 프리징·통계까지 통합된 접점과 검증 근거를 연결한다. | 이 문서 묶음 |

초기 전체 복원은 단순했지만 `Resident`의 복원 과정이 나이를 보존하지 않는다. 추가 배치의 Undo를 위해 관련 없는 셀까지 복원할 필요도 없다. 현재 설계는 실제 변경 좌표만 기록하는 쪽으로 책임과 복원 범위를 좁혔다.

## 2. 객체의 책임과 경계

| 객체 | 책임 | 맡기지 않는 책임 |
|---|---|---|
| `PatternTools` | Swing 입력, 모드, Timer, 좌표 변환, 선택/배치 미리보기, 상태 안내 | 파일 포맷, 셀 변경 이력의 내용, 생명 규칙 |
| `BoardEditor` | 캡처, 범위·guard 검사, 작업 묶음, 최대 100건 이력, 완료 콜백 | 마우스 픽셀, 대화상자, 통계 계산 |
| `PlacePatternCommand` | 새로 살아난 좌표를 기록·병합하고 그 좌표만 Undo | GUI, 파일 저장, 세대 계산 |
| `PatternCells` | `Point(x,y)`와 `Cell.isAliveAt(row,column)` 사이의 편집 어댑터 | 공용 Cell API 확장, 색상 정책 결정 |
| `PatternPath` | 두 마우스 샘플 사이의 정수 기준점 열거 | 보드 수정, Timer, Undo |
| `PatternTemplate` | 이름·크기·생존 배열의 불변 값, 입력 검증 | 상속에 의한 알고리즘 재정의, 색·나이 저장 |
| `PatternLibrary` | 기본 목록, 사용자 파일 입출력, 중복·손상 파일 처리 | 캡처 선택, 현재 보드 참조 |
| 기존 `Universe` / `Cell` 트리 | 입력 중재, 생명 계산, 전체 저장/복원, 화면 갱신 | 템플릿 파일 목록과 개별 명령의 좌표 집합 |

`BoardEditor` 이하 모델 테스트는 실제 `Neighborhood`/`Resident`로 실행하면서 JFrame이나 메뉴에 의존하지 않는다. UI 테스트만 EDT에서 이벤트와 Clock 의존성을 다룬다. 실제 게임 소스에는 테스트만을 위한 접근 메서드를 넣지 않았다.

## 3. 패턴을 어디에 적용했는가

| 패턴/원칙 | 실제 역할과 근거 | 구분 및 효과 |
|---|---|---|
| Command | `PlacePatternCommand.execute/undo`, `BoardEditor.history` | 캡처 확장에서 사용. 요청과 복원 좌표를 묶어 UI와 Undo를 분리한다. 별도 Command 인터페이스는 현재 명령 종류가 하나라 추가하지 않았다. |
| Memento | `Cell.createMemento`, `Cell.Memento`, `NeighborhoodState`, `Storable` | 원래 게임의 패턴을 캡처 읽기에 재사용한다. **현재 배치 Undo는 Memento를 사용하지 않는다.** |
| Composite | `Cell`을 구현한 `Resident`와 재귀 `Neighborhood` | 기존 구조를 재사용한다. 캡처는 내부 하위 셀 배열을 직접 순회하지 않고 공개 계약으로 조회·편집한다. |
| Adapter 역할 | `PatternCells`가 좌표·멱등 편집을 기존 `Cell.userClicked`에 연결 | 캡처 내부 경계다. 별도 Target 인터페이스를 갖춘 전형적 GoF 클래스 계층으로 과장하지 않는다. |
| Observer 방식 | `Consumer<EditKind>` → JPanel의 PropertyChange → 외부 구독자 | 완료된 편집만 전달해 통계 구현에 직접 의존하지 않는다. 기존 Clock/Publisher의 Observer와는 별도 연결이다. |
| SRP·관심사 분리 | 입력, 변경 이력, 값 객체, 저장, 보간을 나눔 | 오류를 해당 책임 단위에서 검증할 수 있다. |
| 불변 값 | 생성 시 배열 복사, 템플릿 내부 배열 미공개 | 호출자의 배열 변경이 저장·미리보기 내용을 몰래 바꾸지 못한다. |

이름이 `PatternTemplate`이라고 Template Method 패턴인 것은 아니다. `Mode` enum도 GoF State 패턴의 구현으로 세지 않는다. 색상 `CellColorPolicy`의 Strategy, 기존 Universe/Clock의 Singleton 등은 팀·기존 코드의 역할이며 캡처가 새로 도입한 공적으로 계산하지 않는다.

## 4. 설계 결정과 대안

### ADR-01 생존 모양만 캡처한다 (R-01, R-03)

- 문제: 색·나이·동결까지 복제하면 템플릿이 게임의 순간 상태와 결합한다.
- 선택: 선택 크기와 `boolean[][]`만 복사하고, 새 셀은 기존 편집 경로로 나이 0에서 시작한다.
- 대안: 색/나이를 포함한 스냅샷은 시점 복원에는 적합하지만 사용자의 모양 재사용 요구보다 넓다. 생존 셀의 최소 경계로 자르는 방식은 선택 여백과 기준점을 바꾼다.
- 비용: 정밀한 상태 복제는 제공하지 않는다. 세대/동결 복원은 별도 기능으로 설계해야 한다.
- 근거: `BoardEditor.capture`, `PatternTemplate`, `CellAgeColorTest`의 색·나이 독립 캡처 검사.

### ADR-02 공용 Cell API를 바꾸지 않는다 (R-03, R-08)

- 문제: `Cell`의 새 setter를 추가하면 Composite 모든 구현과 프리징·통계 브랜치가 함께 영향을 받는다.
- 선택: `PatternCells`에서 현재 생존 상태를 조회하고 달라질 때만 기존 `userClicked`를 호출한다. 논리 좌표 한 칸을 1픽셀로 취급해 기존 재귀 경로를 재사용한다.
- 대안: 공용 `setAliveAt`은 의도가 더 직접적이지만 변경 범위가 넓다. 내부 Resident를 직접 얻는 방법은 Composite 캡슐화를 깨뜨린다.
- 비용: UI 명칭의 메서드에 모델 편집을 의존하는 한계가 있다. 팀이 편집 API를 공통 정리한다면 이 어댑터가 교체 지점이다.
- 근거: `92ef1eb`, `PatternCellsTest`. 기존 Neighborhood의 재계산 경로를 유지한다.

### ADR-03 추가한 좌표만 Undo한다 (R-03, R-06)

- 문제: 전체 스냅샷 복원은 관련 없는 셀까지 건드리고 나이 보존에 불리하다.
- 선택: 죽음→생존으로 바뀐 좌표를 `LinkedHashSet<Point>`에 기록한다. Undo는 그 좌표만 죽인다. 공간 사용은 그 작업이 새로 만든 셀 수에 비례한다.
- 대안: 전체 Memento는 범용 복원에는 간단하지만 전체 보드 규모의 상태를 저장한다. 셀별 이전/이후 상태 쌍은 덮어쓰기 편집까지 지원할 때 더 적합하다.
- 비용: 세대 진행이나 외부 편집 후에는 좌표의 의미가 달라지므로 이력을 무효화한다. Redo와 범용 시간 여행을 지원하지 않는다.
- 근거: `ffbea9c`, `PatternHistoryTest`; 실제 성능 벤치마크를 한 주장은 아니다.

### ADR-04 누름 반복과 이동 보간을 분리한다 (R-04, R-05)

- 선택: press에서 즉시 배치, Swing Timer 80ms에서 현재 위치 재시도, drag에서 `PatternPath`로 중간 기준점을 열거한다. 모두 EDT에서 수행한다.
- 대안: drag 이벤트만 쓰면 정지한 채 누르는 요구를 표현하지 못한다. Timer만 쓰면 빠른 이동에서 간격이 생긴다. 템플릿 폭만큼 띄우는 방식은 겹치는 연속 찍기와 다른 동작이다.
- 보간: `steps=max(abs(dx),abs(dy))`로 두 끝점을 포함해 반올림한 정수 좌표를 방문한다. 한 칸 이하 간격을 보장하지만 브러시 두께나 물리 시간 기반 속도를 모델링하지 않는다.
- 비용: 80ms는 현재 조작 정책이지 정밀 시간 보장이 아니다. 같은 위치 재시도는 무변경으로 처리한다.
- 근거: `5ff5efb`, `9878edc`, `PatternPathTest`, `PatternToolsTest`.

### ADR-05 사용자 제스처를 Undo 한 건으로 묶는다 (R-06, R-09)

- 선택: `beginStroke` 이후 최초 유효 Command를 이력에 넣고 다음 명령의 추가 좌표를 합친다. `endStroke`에서 한 번 완료 알림을 보낸다.
- 대안: 각 찍기를 독립 이력으로 저장하면 짧은 드래그에도 Undo를 여러 번 해야 한다. 편집 전체를 한 건으로 묶으면 별도 클릭을 따로 복원할 수 없다.
- 비용: Undo 중 활성 드래그가 있다면 먼저 끝내므로 TEMPLATE 뒤 TEMPLATE_UNDO가 순서대로 발생할 수 있다. 빈 작업은 이력·알림이 없다.
- 근거: `9b1e8f0`, `PatternHistoryTest`, `PatternCompletionTest`.

### ADR-06 편집 종료와 복원을 분리한다 (R-07, R-08)

- 선택: Esc는 적용된 셀을 남기고 DRAW로 돌아간다. Undo가 셀 복원을 담당한다. 일반 DRAW에서 cancel은 즉시 반환한다.
- 대안: Esc에서 전체 드래그를 되돌리면 모드 종료와 데이터 복원을 한 키에 결합한다. 편집 전 재생 상태를 기억해 자동 재개할 수도 있지만, 사용자가 결과를 확인할 시간을 잃을 수 있다.
- 비용: 편집 종료 뒤 Go 메뉴에서 다시 시작해야 한다. 보드 외부 해제를 받는 전역 AWT 리스너는 활성 작업 때만 등록하고 종료 시 제거한다.
- 근거: `8bb9142`, `5c88c29`. 상태도는 enum과 `stamping`의 조합을 표현하며 새 State 클래스가 존재한다는 뜻이 아니다.

### ADR-07 제약은 모델에서, 알림은 완료 뒤 전달한다 (R-05, R-09)

- 선택: `Predicate<Rectangle>` guard를 BoardEditor가 모든 배치에서 검사한다. `Consumer<EditKind>`는 실제 변경 뒤 호출하고 UI가 PropertyChange로 외부에 전달한다.
- 대안: mousePressed에서만 검사하면 Timer/보간이 우회한다. Command가 FreezingManager·Statistics를 직접 호출하면 다른 기능의 변경에 결합한다.
- 비용: 문자열 속성 이름의 연결은 컴파일러가 모두 검사하지 못하므로 이벤트 이름·횟수·시점의 계약 검사가 필요하다. 외부 구독자는 콜백에서 보드를 재편집하지 않는 정책이 필요하다.
- 근거: `c9162b6`, `PatternCompletionTest`, `PatternToolsTest`. 실제 타 기능의 조립 상태는 별도 연계 문서에서 관리한다.

### ADR-08 저장 성공 후 목록을 갱신한다 (R-02)

- 선택: 임시 파일에 UTF-8 텍스트를 쓰고 목적 파일로 이동한 뒤 메모리 목록에 추가한다. 원자 이동을 지원하지 않으면 일반 이동으로 대체한다.
- 대안: 목록부터 갱신하면 저장 실패 뒤 재시작 시 항목이 사라진다. 데이터베이스는 이 규모의 단일 폴더 저장에 추가 운영 부담이 있다.
- 비용: 원자 이동 미지원 파일시스템까지 전원 장애 안전성을 보장하는 것은 아니다. 오류 파일은 경고로 격리하지만 자동 복구하지 않는다.
- 근거: `PatternLibrary.save/read`, `PatternFeatureTest.corruptFilesAndSaveFailure`.

## 5. 설명·검증의 한계

위의 판단은 현재 범위에 맞춘 설계 선택이며 모든 대안보다 항상 우수하다는 결론이 아니다. 대용량 성능·OS별 실제 마우스 입력·프로세스 강제 종료 중 저장은 별도 실험이 필요하다. 현재 모델·Swing·통합 회귀 검사와 실제 OS 수동 조작의 검증 범위를 구별한다.
