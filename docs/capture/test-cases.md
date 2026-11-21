# 테스트 케이스와 선정 근거

기준: dev `838182c`. T 번호는 설명을 위한 **시나리오 묶음**이며 JUnit 실행 개수와 같지 않다. 아래는 모두 현재 테스트 소스에 대응한다. 실행 상태는 [계획·결과](test-plan.md)를 따른다.

소스 위치: [test/com/holub/life](../../test/com/holub/life). 표의 `클래스.메서드`는 그대로 검색할 수 있는 이름이다.

## 모델·저장·규칙 검사

| ID / 요구 | 실제 테스트 | 입력·절차 → 기대값 | 왜 필요한가 |
|---|---|---|---|
| T-01 / R-01 | `PatternFeatureTest.capturePreservesOffsetsAndMargins` | (3,4)의 Glider를 (2,3,5,5)로 캡처 → 5×5, 생존 5개, 상대 좌표와 원본 보존 | 최소 경계로 잘라내거나 x/y를 뒤바꾸는 오류 탐지 |
| T-02 / R-01,R-03 | `CellAgeColorTest.captureCopiesOnlyLivingCellsRegardlessOfColorAndAge` | 나이·색이 다른 시점의 같은 모양 캡처 → 같은 생존 모양 | 사용자 요구인 색 독립성을 별도로 증명 |
| T-03 / R-03,R-06 | `PatternFeatureTest.repeatPlacementAndUndo`, `transparentOverlapAndNoOp` | 원본 옆에 복사·중첩·같은 위치 반복 후 Undo → 원본/빈칸 밑 셀 보존, 무변경 이력 없음 | 배치를 토글 또는 덮어쓰기로 구현하는 실수 탐지 |
| T-04 / R-01,R-05 | `PatternFeatureTest.boundsAndEmptyCapture` | 음수·초과·빈 캡처 거부, 16×16 보드 (13,13)의 3×3 허용 | 경계에서 일부만 찍히는 오류와 끝 칸의 off-by-one 탐지 |
| T-05 / R-02 | `PatternFeatureTest.templateValidation` | 생성 뒤 입력 배열 수정, 불규칙 행·잘못된 문자·줄바꿈 이름 → 복사 보존 또는 거부 | 템플릿이 외부 배열과 공유돼 변하거나 포맷이 깨지는 문제 방지 |
| T-06 / R-02 | `PatternFeatureTest.saveReloadAndDuplicateNames` | 임시 폴더 저장 후 Library 재생성 → 이름·크기·셀 동일, 중복 거부 | 메모리 목록만 성공하고 실제 재시작은 실패하는 문제 탐지 |
| T-07 / R-02 | `PatternFeatureTest.corruptFilesAndSaveFailure` | 정상+손상 파일, 폴더 자리에 일반 파일 → 정상 항목 유지·경고 1개, 실패 시 목록 불변 | 외부 파일 오류를 정상 자료 손상으로 전파하지 않기 위해 |
| T-08 / R-08 | `PatternFeatureTest.gliderAcrossNeighborhoods`, `randomLifeComparison` | Glider 40세대, seed 1972의 12개 16×16 표본×30세대 → 독립 배열 알고리즘과 셀별 일치 | 편집 후 Composite 경계·캐시 문제는 정적인 화면만으로 발견하기 어려움 |
| T-09 / R-03,R-05 | `PatternCellsTest.editsUseColumnRowCoordinatesAndAreIdempotent`, `invalidCoordinatesCannotEditBoard` | Point(7,8) → row8/col7만 변경; 동일 상태·잘못된 좌표 → 무변경/거부 | 어댑터의 좌표 순서와 toggle 우회 조건을 검증 |
| T-10 / R-06 | `PatternHistoryTest.overlappingCommandsUndoOnlyTheirOwnAdditions` | 겹치는 두 배치를 순서대로 Undo → 각 작업의 추가분만 제거 | 여러 작업의 공유 셀을 잘못 지우지 않게 하기 위해 |
| T-11 / R-06 | `PatternHistoryTest.historyKeepsOnlyTheLastHundredChangedPlacements` | 101개 유효 작업+150회 무변경, 100회 Undo → 가장 오래된 작업만 남음 | 무변경 시도가 이력을 밀어내는 문제와 제한 경계 검사 |
| T-12 / R-06,R-08 | `PatternHistoryTest.aStrokeIsOneCommandEvenWithRepeatedAndOverlappingStamps`, `emptyStrokeAndInvalidationDoNotLeaveAnUndoCommand`; `PatternFeatureTest.historyInvalidation` | 중복 포함 제스처·빈 제스처·clearHistory → 한 번 복원/빈 이력/과거 복원 금지 | 사용자 작업 단위와 내부 찍기 횟수의 차이를 검증 |
| T-13 / R-04 | `PatternPathTest.visitsEveryStepWithoutGaps` | 가로·세로·대각선·역방향·경사·동일점의 9입력 → 양끝 포함, max거리+1개, 인접 차≤1 | 이벤트가 건너뛴 구간을 보완하되 방향에 따라 끊기지 않게 하기 위해 |
| T-14 / R-09 | `PatternCompletionTest.observersSeeCompletedStateAndNoEventsForNoOps` | 작업 중/완료/Undo/빈 Undo/무변경 → 완료 시 실제 상태 관찰, TEMPLATE·UNDO 각각 1회 | 화면 재그리기와 모델 완료 시점을 혼동하지 않게 하기 위해 |
| T-15 / R-05,R-09 | `PatternCompletionTest.modelRejectsGuardedRectanglesIncludingTransparentMargins` | 템플릿 빈 여백만 금지 사각형과 겹침 → 보드·이력·이벤트 불변 | UI에서만 검사하거나 실제 생존 칸만 검사하는 우회 방지 |

## 입력·색상·통합 검사

아래 T-16~T-24는 `PatternToolsTest`다. Clock만 모의 객체이며 패널·보드·Timer는 실제 객체다. 대부분 이벤트를 EDT에서 주입한다.

| ID / 요구 | 실제 메서드 | 입력·절차 → 기대값 | 왜 필요한가 |
|---|---|---|---|
| T-16 / R-03,R-04 | `dotAndLargerTemplatesStampWithoutErasing` | 한 칸·다중 칸 템플릿 press/drag/release, 단순 hover → 누른 경로만 추가, 기존 셀 보존 | 일반 셀 드래그와 템플릿 조작의 입력 분기 확인 |
| T-17 / R-04 | `fastDragFillsIntermediateAnchorsInBothDirections` | 큰 가로 이동 뒤 반대 대각 이동 → 중간 기준점 전부 생존 | 순수 보간 함수가 실제 입력 경로에도 연결됐는지 확인 |
| T-18 / R-04,R-06 | `holdingStillDoesNotToggleOrCreateExtraUndoEntries` | 누르고 260ms 동안 실제 Swing Timer 처리 후 해제·Undo → 한 번에 제거, 추가 이력 없음 | 정지한 채 누름이 토글·중복 Undo를 만들지 않게 하기 위해. 정확한 Timer 호출 횟수를 보장하는 검사는 아님 |
| T-19 / R-06 | `oneUndoRevertsOneWholeDragButKeepsPreviousClick` | 앞선 클릭 뒤 중첩 드래그, Undo → 드래그만 제거, 앞선 클릭 유지 | 모델의 묶음 정책이 UI 버튼에서 지켜지는지 확인 |
| T-20 / R-07 | `outsideReleaseStopsStampingAndRemovesGlobalListener`, `focusLossStopsHeldGesture` | 보드 밖 좌표 해제/포커스 리스너 호출 후 drag → 추가 없음, 리스너 제거 | 버튼 놓침으로 계속 찍히거나 전역 리스너가 누적되는 문제 탐지 |
| T-21 / R-05,R-07 | `invalidLargeStampLeavesBoardUntouchedAndCanReturnInside` | 끝 칸에 2×2 거부, 바깥 경유 후 내부 복귀 → 부분 배치·바깥 구간 연결 없음 | 범위 검사와 보간 기준점 초기화가 함께 맞아야 함 |
| T-22 / R-07,R-08 | `cancelInDrawModeDoesNotStopSimulation`, `cancelEndsStrokeButKeepsItUndoable`, `captureAndExternalBoardChangesDoNotLeaveAStaleStroke` | DRAW 취소·활성 작업 Esc·boardChanged·캡처 전환 → 시계 부작용 없음/작업 보존/이력 종료/원본 불변 | 종료·Undo·캡처의 의미가 서로 섞이는 문제 방지 |
| T-23 / R-05,R-09 | `guardAppliesToEveryStampAndCompletionEventsFireOnce` | x4~5 금지 영역을 가로질러 드래그, 해제, 거부/무변경, Undo → 금지 칸 불변·완료별 1회 | 반복·보간·PropertyChange를 실제 UI 경로에서 검증 |
| T-24 / R-03,R-06,R-08 | `dragAndUndoPreserveTheAgeColorsOfExistingCells` | 10세대 생존 블록(파랑)에 빨강 새 셀을 드래그 추가 후 Undo → 기존 파랑 유지, 새 셀 제거 | 생존 배열이 같아도 나이를 초기화하는 회귀를 픽셀로 탐지 |
| T-25 / R-01~R-08 | `PatternUiTest.main` (별도 실행기) | 실제 Universe에서 기본 배치·역방향 선택·대화상자 저장·재로딩·Ctrl+Z·Esc·단일 Tick·드래그 → 17 checks | 객체 단위 검사가 통과해도 잘못된 리스너/레이아웃 조립이 있을 수 있음 |

## 기존 기능 보존과 추적

`UniverseColorTest.placementAndUndoPreserveExistingCellAge`는 실제 Universe의 색상 보존 경로를 추가로 확인한다. `UniverseStoreTest`는 일반 드래그·지우기·보드 밖 처리·Store·playhead를, `ResidentTest`와 `NeighborhoodTest`는 생명 규칙과 셀 조회를, `sound` 패키지 검사는 음 매핑·반복 감쇠·설정을 담당한다. 이들은 팀의 기존 회귀 증거이며 캡처 신규 테스트로 재명명하지 않는다.

| 요구 | 대표 검사 | 설계 결정 |
|---|---|---|
| R-01 | T-01,T-02,T-04,T-25 | ADR-01 |
| R-02 | T-05~T-07,T-25 | ADR-08 |
| R-03 | T-02,T-03,T-09,T-16,T-24 | ADR-01~ADR-03 |
| R-04 | T-13,T-16~T-18 | ADR-04 |
| R-05 | T-04,T-09,T-15,T-21,T-23 | ADR-07 |
| R-06 | T-03,T-10~T-12,T-18,T-19,T-24 | ADR-03,ADR-05 |
| R-07 | T-20~T-22,T-25 | ADR-06 |
| R-08 | T-08,T-12,T-22,T-24,T-25와 기존 회귀 | ADR-02,ADR-06 |
| R-09 | T-14,T-15,T-23 | ADR-05,ADR-07 |

T-15/T-23은 주입한 Predicate 계약 검사다. 실제 프리징 관리자를 사용한 IT-01/IT-02와 같지 않다. 실제 프리징·통계 통합 검사는 별도의 IT 항목에 연결하며 현재 전체 384회 실행에 포함된다.
