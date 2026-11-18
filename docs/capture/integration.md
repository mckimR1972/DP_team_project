# 타 기능과의 설계 접점

확인일: 2026-11-18. 현재 dev는 `a480113331b9c6541f03a920c4b37be07b6f1b16`이다. 아래 PR 상태는 확인 시점의 스냅샷이며, 실제 병합 때 최신 head를 다시 확인해야 한다.

| 대상 | 확인한 상태 | 문서에서의 의미 |
|---|---|---|
| 캡처 [#13](https://github.com/mckimR1972/DP_team_project/pull/13) | dev에 병합 | 현재 실행·시험 기준 |
| 프리징 [#7](https://github.com/mckimR1972/DP_team_project/pull/7) | 열림, `ee1e763c9ff7840ebffd9a86bd06721c7de5f8af` | 해당 브랜치 코드를 읽어 조립 계약 확인. 전체 병합 시험은 하지 않음 |
| 통계 [#10](https://github.com/mckimR1972/DP_team_project/pull/10) | 열림, `8eb1dfee832093b50432115c7d617b85f717d960` | 해당 브랜치에 캡처 알림 연결과 통합 검사 코드가 있음. 이번 dev 시험에는 포함되지 않음 |

PR #13 당시 기록의 통계 head `4021231`은 과거 기준이다. 현재 통계 브랜치에는 `Universe`가 TEMPLATE/TEMPLATE_UNDO를 구독하는 코드가 있으므로, 같은 리스너를 다시 추가하면 안 된다. [해당 Universe](https://github.com/mckimR1972/DP_team_project/blob/8eb1dfee832093b50432115c7d617b85f717d960/src/com/holub/life/Universe.java)

## IF-01 일반 드래그와 입력 소유권 (R-04, R-07, R-08)

`Universe`의 일반 그리기 리스너는 `patternTools.isEditing()`이면 입력을 처리하지 않는다. `PatternTools`는 DRAW일 때 일반 왼쪽 입력을 처리하지 않는다. 따라서 같은 클릭이 일반 셀 토글과 템플릿 배치에 중복 적용되지 않는다.

일반 그리기는 첫 셀의 상태에 따라 그리기/지우기 방향을 정한다. 템플릿 배치는 살아 있는 칸만 추가한다. 한 칸짜리 템플릿도 지우개가 아니다. 두 입력의 목적이 달라 알고리즘을 무리하게 공통화하지 않았다.

후속 도구 탭은 숨겨진 도구의 활성 드래그를 종료해야 한다. 프리징 PR의 탭 변경은 각 도구 cancel을 호출하며, **프리징 영역 선택 시작**은 캡처 cancel과 boardChanged를 호출해 Undo 이력까지 비운다. 탭 전환 자체가 항상 모든 이력을 비운다고 설명하면 코드와 다르다.

## IF-02 색상·나이와 좌표 편집 (R-01, R-03, R-06)

`PatternTemplate`에는 색·나이가 없고 생존 배열만 있다. `PatternCells.setAlive`는 현재 상태가 같으면 `userClicked`를 호출하지 않는다. 따라서 살아 있던 셀의 나이와 색이 유지되고 새 셀만 나이 0이 된다. Undo도 그 작업이 추가한 셀만 제거한다.

기존 `Resident`의 색상 Strategy를 캡처에서 교체하거나 계산하지 않는다. 전체 Memento를 clear/load하는 방식으로 돌아가면 기존 셀 나이 보존이 깨질 수 있다. 실제 렌더링 픽셀과 기존 셀 보존을 함께 검사한다.

## IF-03 시계·보드 변경·이력 (R-06, R-07, R-08)

편집 진입 시 Clock을 멈추고, Universe tick은 편집 중이면 즉시 반환한다. 단일 Tick 요청도 차단된다. UI 입력·반복 Timer·실제 시계 갱신은 EDT에서 처리하므로 배치 도중 세대 전이가 끼어들지 않는다.

일반 셀 변경·tick·Clear·Load 경로는 `boardChanged`로 오래된 이력을 무효화한다. 무효화 전에는 활성 작업을 끝낸다. 이후 Undo가 새 게임 상태를 과거 좌표로 훼손하지 않게 한다.

편집 종료는 자동 재생하지 않는다. 캡처 저장 취소, 편집 종료, Undo, 전체 Clear를 같은 의미로 섞지 않는다. 빈 DRAW 취소는 Clock에 영향을 주지 않는다.

## IF-04 프리징 제약 (R-05, R-06)

현재 dev의 `setPlacementGuard(Predicate<Rectangle>)`는 연결 지점이고 기본값은 항상 허용이다. 프리징 PR의 실제 조립은 다음과 같다.

```java
patternTools.setPlacementGuard(area -> freezingTools.manager().overlapsFrozen(area));
```

- 단위: `Rectangle(x,y,width,height)`의 셀 좌표. 픽셀 좌표가 아니다.
- 의미: **true가 차단**이다. 템플릿의 빈 여백도 포함한 사각형이 동결 영역과 겹치면 그 찍기를 거부한다.
- 위치: `BoardEditor.place`에서 검사해 press·Timer·보간·직접 모델 호출을 모두 막는다. `canPlace`는 미리보기에도 쓰인다.
- 드래그가 경계를 가로지르면 허용 위치만 추가한다. 실패한 찍기는 이력·완료 알림을 만들지 않는다.
- Undo 뒤에 동결된 셀을 손상하지 않도록, 프리징 영역 선택을 시작할 때 캡처 작업을 종료하고 기존 이력을 비우는 조립을 유지한다.

살아 있는 대상 좌표만 검사하면 공간 사용은 덜 제한되지만 #7의 영역 단위 정책과 달라진다. 정책을 바꾸려면 양쪽 명세·미리보기·테스트를 함께 바꿔야 한다. 이번 문서는 그런 변경을 결정하지 않는다.

PR #13 당시 시험 병합에서 `PatternTools` 충돌이 추가됐다는 기록이 있다. 후속 병합 시 캡처의 Timer·보간·모델 guard를 유지하면서 #7의 Universe 연결을 합쳐야 한다. 과거 충돌 파일 목록을 최신 head의 자동 병합 결과로 주장하지 않는다.

## IF-05 통계 완료 이벤트 (R-09)

현재 dev의 흐름은 `BoardEditor.EditKind` → `PatternTools.controls()`의 PropertyChange까지다. 통계 브랜치 `8eb1dfe`는 다음의 연결을 **이미 한 번씩** 등록한다.

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

PR #10의 `BoardChangeEvent`는 행렬을 `[row][column]`, Point를 `(column,row)`로 사용한다. 이벤트의 생존 배열과 좌표를 방어적으로 복사한다. 캡처 템플릿의 `[y][x]`와 동일한 방향인지 통합 검사로 확인한다.

통계 브랜치의 `MergedFeaturesIntegrationTest`에는 `templateStrokePublishesOnlyAtCompletionAndBlocksGenerationProgress`, `templateUndoRestoresStatisticsAndNextGenerationUsesTheRestoredBoard`, `captureSelectionDoesNotChangeStatisticsOrAdvanceGenerations` 검사가 있다. **소스 존재를 확인한 것이며 이 문서 작업에서 해당 브랜치의 통과를 새로 검증한 것은 아니다.**

## IF-06 사운드·전체 파일 저장 (R-02, R-08, R-09)

현재 캡처는 사운드 구현을 직접 호출하지 않는다. 통계 브랜치에서 완료 이벤트를 사운드와 공유하더라도 TEMPLATE를 자연 GENERATION의 births로 취급하지 않는 정책을 확인해야 한다. 현재 dev의 음높이·반복 감쇠·설정 검사는 캡처 통합 전후 회귀 기준이며 실제 출력 장치의 청취 검증을 대신하지 않는다.

`.lifepattern`은 부분 모양 재사용용이고 `.life`는 기존 보드 저장용이다. 템플릿 포맷에 프리징·통계 데이터를 몰래 추가하지 않는다. 새 필드나 버전이 필요하면 파일 호환성과 손상 파일 처리를 따로 설계한다.

## 후속 병합 검토 순서

캡처 #13은 이미 병합됐다. 예정된 프리징 → 통계의 상대 순서는 유지한다.

1. 프리징 병합에서 입력 소유권, 영역 guard, 탭 전환과 이력 무효화를 확인한다.
2. 통계 병합에서 완료 리스너의 중복 여부와 실제 이벤트 종류·횟수·세대 번호를 확인한다.
3. 통합된 dev에서 동결 경계 드래그 → Undo → 편집 종료 → 다음 세대 진행을 연속 수행하고 색·통계·사운드 회귀를 확인한다.
4. 최종 제출용 문서에서 각 PR의 head·실행 결과·미해결 항목을 갱신한다. 병합 충돌이 없다는 사실만으로 동작 호환성을 판정하지 않는다.
