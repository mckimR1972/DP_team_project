# 타 기능과의 설계 접점

확인일: 2026-11-22. 기준 코드는 dev `838182c62e13322b46b2ffbaf60cc8fd3c5fd9ab`이다.
캡처 #13, 통계 #10, 프리징 #7은 모두 이 코드에 병합되어 있다. Universe의 실제 조립과 2026-11-21 실행한 통합 테스트를 기준으로 설명한다.

| 대상 | 현재 조립 | 검증 근거 |
|---|---|---|
| 캡처 | BoardEditor → PatternTools 완료 알림 | PatternCompletionTest, PatternToolsTest |
| 프리징 | Universe가 FreezingManager의 영역 검사를 배치 guard에 연결 | FreezingIntegrationTest 37회 |
| 통계·사운드 | Universe의 BoardChangeEvent를 각 구독자가 수신 | MergedFeaturesIntegrationTest 12회 및 별도 사운드 검사 |

## IF-01 일반 드래그와 입력 소유권 (R-04, R-07, R-08)

`Universe`의 일반 그리기 리스너는 `patternTools.isEditing()`이면 입력을 처리하지 않는다. `PatternTools`는 DRAW일 때 일반 왼쪽 입력을 처리하지 않는다. 따라서 같은 클릭이 일반 셀 토글과 템플릿 배치에 중복 적용되지 않는다.

일반 그리기는 첫 셀의 상태에 따라 그리기/지우기 방향을 정한다. 템플릿 배치는 살아 있는 칸만 추가한다. 한 칸짜리 템플릿도 지우개가 아니다. 두 입력의 목적이 달라 알고리즘을 무리하게 공통화하지 않았다.

현재 Life의 탭 변경은 숨겨진 도구의 cancel을 호출하며, **프리징 영역 선택 시작**은 캡처 cancel과 boardChanged를 호출해 Undo 이력까지 비운다. 탭 전환 자체가 항상 모든 이력을 비운다고 설명하면 코드와 다르다.

## IF-02 색상·나이와 좌표 편집 (R-01, R-03, R-06)

`PatternTemplate`에는 색·나이가 없고 생존 배열만 있다. `PatternCells.setAlive`는 현재 상태가 같으면 `userClicked`를 호출하지 않는다. 따라서 살아 있던 셀의 나이와 색이 유지되고 새 셀만 나이 0이 된다. Undo도 그 작업이 추가한 셀만 제거한다.

기존 `Resident`의 색상 Strategy를 캡처에서 교체하거나 계산하지 않는다. 전체 Memento를 clear/load하는 방식으로 돌아가면 기존 셀 나이 보존이 깨질 수 있다. 실제 렌더링 픽셀과 기존 셀 보존을 함께 검사한다.

## IF-03 시계·보드 변경·이력 (R-06, R-07, R-08)

편집 진입 시 Clock을 멈추고, Universe tick은 편집 중이면 즉시 반환한다. 단일 Tick 요청도 차단된다. UI 입력·반복 Timer·실제 시계 갱신은 EDT에서 처리하므로 배치 도중 세대 전이가 끼어들지 않는다.

일반 셀 변경·tick·Clear·Load 경로는 `boardChanged`로 오래된 이력을 무효화한다. 무효화 전에는 활성 작업을 끝낸다. 이후 Undo가 새 게임 상태를 과거 좌표로 훼손하지 않게 한다.

편집 종료는 자동 재생하지 않는다. 캡처 저장 취소, 편집 종료, Undo, 전체 Clear를 같은 의미로 섞지 않는다. 빈 DRAW 취소는 Clock에 영향을 주지 않는다.

## IF-04 프리징 제약 (R-05, R-06)

BoardEditor의 guard 기본값은 허용이며, 실제 게임의 Universe 생성 시 다음과 같이 FreezingManager에 연결한다.

```java
patternTools.setPlacementGuard(area -> freezingTools.manager().overlapsFrozen(area));
```

- 단위: `Rectangle(x,y,width,height)`의 셀 좌표. 픽셀 좌표가 아니다.
- 의미: **true가 차단**이다. 템플릿의 빈 여백도 포함한 사각형이 동결 영역과 겹치면 그 찍기를 거부한다.
- 위치: `BoardEditor.place`에서 검사해 press·Timer·보간·직접 모델 호출을 모두 막는다. `canPlace`는 미리보기에도 쓰인다.
- 드래그가 경계를 가로지르면 허용 위치만 추가한다. 실패한 찍기는 이력·완료 알림을 만들지 않는다.
- Undo 뒤에 동결된 셀을 손상하지 않도록, 프리징 영역 선택을 시작할 때 캡처 작업을 종료하고 기존 이력을 비우는 조립을 유지한다.

살아 있는 대상 좌표만 검사하면 공간 사용은 덜 제한되지만 #7의 영역 단위 정책과 달라진다. 정책을 바꾸려면 양쪽 명세·미리보기·테스트를 함께 바꿔야 한다. 이번 문서는 그런 변경을 결정하지 않는다.

현재 통합본은 Timer·보간·모델 guard와 Universe 연결을 모두 유지한다. 빈 여백 충돌, 동결 영역을 통과하는 드래그와 Undo, 영역 선택 시 이력 무효화를 FreezingIntegrationTest로 확인했다.

## IF-05 통계 완료 이벤트 (R-09)

현재 흐름은 `BoardEditor.EditKind` → PatternTools의 PropertyChange → Universe의 BoardChangeEvent → 통계·사운드 구독자다. Universe 생성 시 다음 연결을 한 번씩 등록한다.

```java
patternTools.controls().addPropertyChangeListener("TEMPLATE",
    event -> notifyBoardChanged(BoardChangeEvent.Kind.TEMPLATE));
patternTools.controls().addPropertyChangeListener("TEMPLATE_UNDO",
    event -> notifyBoardChanged(BoardChangeEvent.Kind.TEMPLATE_UNDO));
```

| 계약 | 이유 |
|---|---|
| 드래그 완료 뒤 실제 보드 상태를 관찰 | 중간 상태를 최종 통계로 잘못 읽는 문제 방지 |
| 한 작업당 TEMPLATE 1회, Undo당 TEMPLATE_UNDO 1회 | 타이머 횟수가 작업 수나 세대 수로 집계되는 문제 방지 |
| 무변경·거부·캡처에는 완료 이벤트 없음 | 보드 변경 없이 통계 이력이 늘어나는 문제 방지 |
| 세대 번호는 편집 때문에 증가하지 않음 | 자연 세대와 사용자 편집을 구분 |
| GENERATION만 births/deaths를 가짐 | 새 템플릿 셀을 자연 탄생·사망 통계로 오인하지 않음 |
| 구독자는 콜백에서 보드를 재편집하지 않음 | 재진입·중복 알림·관찰 순서 문제 방지 |

현재 `BoardChangeEvent`는 행렬을 `[row][column]`, Point를 `(column,row)`로 사용한다. 이벤트의 생존 배열과 좌표를 방어적으로 복사한다. 캡처 템플릿의 `[y][x]`와 동일한 방향인지 통합 검사로 확인한다.

`MergedFeaturesIntegrationTest`에는 `templateStrokePublishesOnlyAtCompletionAndBlocksGenerationProgress`, `templateUndoRestoresStatisticsAndNextGenerationUsesTheRestoredBoard`, `captureSelectionDoesNotChangeStatisticsOrAdvanceGenerations` 검사가 있다. 이 메서드들은 기준 코드의 2026-11-21 JUnit 실행에서 모두 통과했다.

## IF-06 사운드·전체 파일 저장 (R-02, R-08, R-09)

캡처는 사운드 구현을 직접 호출하지 않는다. SoundEffects는 BoardChangeEvent를 받으며 TEMPLATE와 TEMPLATE_UNDO를 자연 세대의 births/deaths로 연주하지 않는다. 편집은 재생선이 읽는 보드 스냅샷에는 반영된다. 음 매핑·반복 감쇠·통합 이벤트 검사는 통과했으며 실제 출력 장치의 청취 검증은 별도다.

`.lifepattern`은 부분 모양 재사용용이고 `.life`는 기존 보드 저장용이다. 템플릿 포맷에 프리징·통계 데이터를 몰래 추가하지 않는다. 새 필드나 버전이 필요하면 파일 호환성과 손상 파일 처리를 따로 설계한다.

## 통합 검사와 유지보수

IT-01~IT-06의 실제 검사 연결은 [테스트 계획](test-plan.md)에 있다. 공용 코드가 바뀌면 동결 경계 드래그 → Undo → 편집 종료 → 다음 세대 진행의 회귀를 확인한다. 리스너를 탭 전환마다 재등록하지 않으며, 탭 전환만으로 Undo 이력을 지우지 않는다. 프리징 영역 선택 시작은 이력을 지운다.
