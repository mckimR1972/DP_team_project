# 캡처 테스트 계획과 실행 기록

기준: dev `838182c` / 문서 확인일: 2026-11-22 (Asia/Seoul). 최신 기능 검증 실행일은 2026-11-21이다. 상세 입력·기대값과 선택 이유는 [테스트 케이스](test-cases.md)에 있다.

## 1. 왜 이런 검사가 필요한가

캡처는 단순히 그림을 옮기는 기능처럼 보이지만 좌표계, 기존 셀의 나이, 파일 실패, 마우스 이벤트 순서, Undo 이력, 세대 진행을 동시에 다룬다. 화면에 모양이 보이는 것만으로는 기존 셀을 지웠는지·나이를 초기화했는지·이력을 여러 번 넣었는지 판단하기 어렵다.

따라서 순수한 결과 상태를 먼저 검사하고, 입력 연결과 실제 Swing 조립을 단계적으로 확인한다. 정상 입력만 반복하지 않고 경계값·무변경·실패·중단·타 기능 전환을 포함한다. 테스트 수를 늘리는 것이 아니라 관찰 가능한 실패 위험을 나누는 것이 목적이다.

## 2. 계층별 전략

| 계층 | 실제 대상·격리 | 검증 목적 | 한계 |
|---|---|---|---|
| 모델 | 실제 Neighborhood/Resident + BoardEditor/Command | 좌표, 비파괴 캡처, 변경 집합, 경계·guard, 이력·콜백 | 실제 OS 입력·메뉴 조립은 확인하지 않음 |
| 값·저장 | PatternTemplate/Library + 임시 파일 | 불변성, 형식 검증, 재로딩, 실패 후 목록 일관성 | 전원 장애·모든 파일시스템 장애를 재현하지 않음 |
| 계산 회귀 | 실제 셀 트리와 독립 boolean 배열 Life 계산 비교 | 배치 후 하위 Neighborhood 경계의 생명 규칙·캐시 문제 탐지 | 전수 증명이 아니며 고정 표본에 대한 회귀 검사 |
| headless Swing JUnit | 실제 패널/버튼/마우스 이벤트/Timer, Clock만 Mockito | 입력 소유권, 반복, 보간, 취소, 종료 리스너, 알림, 색 픽셀 | 실제 창 관리자 포커스 이동이나 물리 마우스 조작을 모두 대체하지 않음 |
| 데스크톱 Swing 실행 | 실제 JFrame/Universe와 저장 대화상자 | 조립, 저장·재배치, 단축키, 편집 중 Tick 차단, 드래그 전체 Undo | 이벤트는 프로그램에서 주입한다. 사람의 사용성 평가가 아님 |
| 기존 전체 회귀 | 색상·셀·이웃·Store·일반 드래그·사운드 테스트 | 캡처 개선이 다른 기능을 깨뜨리지 않았는지 확인 | 실제 Freezing/Statistics 통합을 포함하며 OS 수동 조작은 별도 |

모델을 UI에서 분리했기 때문에 검사 대부분이 화면 없이 실행된다. Singleton Universe를 쓰는 클래스는 Gradle `forkEvery=1`로 서로 다른 JVM에서 실행한다. PatternTools 테스트는 EDT에서 생성·입력·검증하고, 임시 템플릿 폴더와 색상 정책·모의 Clock을 정리한다.

## 3. 재현 절차와 통과 조건

저장소 루트, JDK 17 환경에서 실행한다. 처음에는 Wrapper와 의존성 다운로드가 필요하다.

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat patternUiTest --console=plain
```

한 번에 실행하려면 `test patternUiTest`를 지정한다. 두 번째 작업은 화면이 있는 환경이 필요하다. 재검증 시 이전 결과를 재사용하지 않으려면 `--rerun-tasks`를 붙인다.

- JUnit: 실패·오류·건너뜀 0을 기준으로 한다. 파라미터화 테스트는 입력별 실행 수로 집계한다.
- Swing: `PASS 17 Swing integration checks...` 메시지와 프로세스 정상 종료를 확인한다. 이는 JUnit 17개가 아니라 별도 실행기의 check 17개다.
- XML: `build/test-results/test/TEST-*.xml`, HTML: `build/reports/tests/test/index.html`.
- 결과 이미지: `build/life-selection.png`, `life-placement.png`, `life-template-drag.png`, `life-template-drag-undo.png`.
- 보드 상태 assertion과 화면을 함께 확인한다. 렌더링 이미지 한 장만으로 모든 요구가 통과했다고 판정하지 않는다.
- 일반 코드·다른 기능이 바뀌면 전체 회귀를 실행한다. 문장 수정만으로 같은 테스트를 반복 실행할 필요는 없으며 문서 링크·기준·그림 일치 여부를 검증한다.

## 4. 최신 실행 증거

실행일: 2026-11-21 (Asia/Seoul). 명령: `.\gradlew.bat test patternUiTest --no-daemon --console=plain`.
환경: Windows, Temurin JDK 17.0.21.1+2, Gradle 9.5.1, JUnit Jupiter 6.0.3, Mockito 5.23.0.
문서 정리 중 게임 소스·테스트·빌드 설정이 이 기준과 동일함을 확인했다. 같은 코드의 결과를 보존하며 새 실행으로 재표기하지 않는다.

| 결과 | 수치/상태 |
|---|---|
| 전체 JUnit | **384**, 실패 0, 오류 0, 건너뜀 0 |
| Pattern 이름의 JUnit | **39**: Feature 10 + Cells 2 + History 4 + Path 9 + Tools 12 + Completion 2 |
| 실제 프리징 통합 클래스 | FreezingIntegrationTest **37** (384에 포함) |
| 색상·캡처·통계·사운드 통합 클래스 | MergedFeaturesIntegrationTest **12** (384에 포함) |
| 별도 Swing 실행기 | **17 checks 통과** (JUnit 수에 포함하지 않음) |

[최신 결과 JSON](evidence/2026-11-21/results.json), [전체 콘솔 기록](evidence/2026-11-21/verification.log), [원본 JUnit XML](evidence/2026-11-21/xml)에 수치·메서드·검증값을 보관한다.
[2026-11-18의 145회 기록](evidence/2026-11-18/results.json)은 당시 코드 `a480113`의 과거 증거다. 두 실행 수를 합산하지 않는다.

| 같은 실행의 화면 | 확인할 내용 |
|---|---|
| [영역 선택](evidence/2026-11-21/life-selection.png) | 역방향 선택과 저장 활성화 |
| [저장·재배치](evidence/2026-11-21/life-placement.png) | 저장한 모양의 재배치 |
| [연속 배치](evidence/2026-11-21/life-template-drag.png) | 이동 경로의 추가 셀 |
| [드래그 Undo](evidence/2026-11-21/life-template-drag-undo.png) | 이전 셀을 유지하며 한 작업 복원 |

## 5. 완료된 통합 검사

다음 메서드는 위 384회 실행에 포함돼 통과했다. IT는 시나리오 묶음이며 별도의 실행 수가 아니다.

| ID | 실제 검사 | 검증 내용 |
|---|---|---|
| IT-01 | `FreezingIntegrationTest.aTemplateWhoseEmptyMarginAloneOverlapsIsRejectedWhole` | 빈 여백만 동결 영역에 겹쳐도 전체 배치·이력·알림 거부 |
| IT-02 | `FreezingIntegrationTest.aHeldTemplateStrokeSkipsEveryStampThatWouldTouchTheFrozenArea` | 동결 구간을 통과하는 드래그에서 허용 구간만 배치하고 Undo로 복원 |
| IT-03 | `FreezingIntegrationTest.startingAnAreaSelectionDuringAHeldStrokeEndsTheStroke` | 숨겨진 드래그 종료, 프리징 선택 시작 시 이력 무효화 |
| IT-04 | `MergedFeaturesIntegrationTest.templateStrokePublishesOnlyAtCompletionAndBlocksGenerationProgress`, `unchangedOrOutsideTemplatePlacementsDoNotDuplicateNotificationsOrHistory` | 완료 이벤트 한 번, 무변경·거부 알림 없음, 생존 수 일치·세대 번호 유지 |
| IT-05 | `MergedFeaturesIntegrationTest.templateUndoRestoresStatisticsAndNextGenerationUsesTheRestoredBoard` | Undo 뒤 통계 복원, 다음 세대의 탄생·사망과 사운드 전달 |
| IT-06 | `MergedFeaturesIntegrationTest.clearEndsHeldStampingAndLeavesStatisticsEmpty`, `loadEndsPatternEditingAndNotifiesBothObserversOnTheNextActualGeneration`; `UniverseColorTest.placementAndUndoPreserveExistingCellAge`; `UniverseStoreTest` | Clear/Load 뒤 편집·이력 종료, Store 회귀, 기존 셀 나이·색 보존 |

수동 추가 확인 범위: 실제 마우스로 창 밖에서 놓기, Alt-Tab, 창 닫기·재표시, 화면 배율 변경, 장시간 누르기. 자동 이벤트 주입만으로 모든 OS 입력·청취·장기 성능을 검증했다고 주장하지 않는다.
