# 확장 기능별 유스케이스 정리

전체 시스템 유스케이스 다이어그램([usecase-overview.png](usecase-overview.png))의 유스케이스를 기능별로 나눈 것이다. 각 기능의 유스케이스 다이어그램은 여기서 자기 줄에 있는 것만 떼어 그린다.

## 1. 전체 구성

- 원본 유스케이스 7개: Edit Cells, Run / Halt Game, Step One Generation, Advance Generation, Clear Board, Store / Load Board, Exit Game
- 확장 유스케이스 14개: 이름 끝에 `(E)`가 붙는다. 14개 모두 담당 기능이 정해져 있다.
- 관계: «include» 2개, «extend» 9개

## 2. 기능별 유스케이스

| 기능 | 자기 유스케이스 | 맞물리는 원본 유스케이스 | 관계 |
| --- | --- | --- | --- |
| 색 순환 | `Switch Color Mode(E)`, `Recolor Cells by Age(E)` | Advance Generation | `Recolor Cells by Age(E)`가 "세대 확정 후"에 «extend» |
| 캡처·템플릿 | `Capture Template(E)`, `Place Template(E)`, `Undo Placement(E)` | 없음 | 자기 쪽에서 나가는 관계는 없다. `Place Template(E)`에 확장 지점 "배치 전"만 둔다 |
| Freezing | `Install Freezing(E)`, `Release Freezing(E)`, `Keep Region Frozen(E)`, `Protect Frozen Region(E)` | Advance Generation, Edit Cells, Clear Board, Store / Load Board | «extend» 5개 (3절) |
| 통계 | `View Statistics(E)` | Advance Generation | "세대 확정 후"에 «extend» |
| 사운드 | `Toggle Sound(E)`, `Configure Sound(E)`, `Hear Generation Sound(E)` | Advance Generation | `Hear Generation Sound(E)`가 "세대 확정 후"에 «extend» |
| 드래그 그리기 | `Draw Cells by Dragging(E)` | Edit Cells | "끌기"에 «extend» |

## 3. «extend» 관계 9개

| 기능 | 확장하는 유스케이스 | 확장 대상 | 조건 | 확장 지점 |
| --- | --- | --- | --- | --- |
| 색 순환 | `Recolor Cells by Age(E)` | Advance Generation | 색 순환이 켜져 있음 | 세대 확정 후 |
| 통계 | `View Statistics(E)` | Advance Generation | 통계 구독 중 | 세대 확정 후 |
| 사운드 | `Hear Generation Sound(E)` | Advance Generation | 소리가 켜져 있음 | 세대 확정 후 |
| Freezing | `Keep Region Frozen(E)` | Advance Generation | 고정 영역이 있음 | 다음 세대 계산 후, 세대 확정 후 |
| Freezing | `Protect Frozen Region(E)` | Edit Cells | 고른 셀이 고정 영역 안에 있음 | 셀 변경 전 |
| Freezing | `Protect Frozen Region(E)` | `Place Template(E)` | 템플릿이 고정 영역과 겹침 | 배치 전 |
| Freezing | `Release Freezing(E)` | Clear Board | 고정 영역이 있음 | 판을 비운 후 |
| Freezing | `Release Freezing(E)` | Store / Load Board | 불러오기에 성공했고 고정 영역이 있음 | 불러온 후 |
| 드래그 그리기 | `Draw Cells by Dragging(E)` | Edit Cells | 버튼을 누른 채 끎 | 끌기 |

«include»는 원본 유스케이스 사이에만 있다. Run / Halt Game과 Step One Generation이 Advance Generation을 «include»한다. 세대 진행에 붙는 네 기능(색 순환, Freezing, 통계, 사운드)의 그림에는 이 두 관계도 같이 그린다.

## 4. 사용자와의 연결

| 구분 | 유스케이스 |
| --- | --- |
| 사용자와 직접 이어진다 | `Switch Color Mode(E)`, `Capture Template(E)`, `Place Template(E)`, `Undo Placement(E)`, `Install Freezing(E)`, `Release Freezing(E)`, `View Statistics(E)`, `Toggle Sound(E)`, `Configure Sound(E)` |
| 사용자와 이어지지 않고 «extend»로만 붙는다 | `Recolor Cells by Age(E)`, `Keep Region Frozen(E)`, `Protect Frozen Region(E)`, `Hear Generation Sound(E)`, `Draw Cells by Dragging(E)` |

## 5. 기능별 그림으로 떼어 갈 때 정할 것

1. **`Protect Frozen Region(E)` → `Place Template(E)`.** Freezing과 캡처·템플릿에 걸친 관계다. 기능별 그림에는 다른 기능을 그리지 않는 것이 기준이므로, 확장하는 쪽인 Freezing 그림에 `Place Template(E)`를 이름만 두고 관계를 그리고, 캡처·템플릿 그림에는 확장 지점 "배치 전"만 적는 것을 권한다.
2. **`Draw Cells by Dragging(E)`의 담당(정함).** 드래그 그리기 기능의 유스케이스로 정했다. 유스케이스 다이어그램, 명세, 요구사항, 테스트는 [드래그 그리기 문서](drag-drawing/draw-cells-by-dragging.md)에 있다. 색 순환 명세와 사운드 명세는 같은 이름을 그대로 쓴다.
3. **합친 유스케이스의 이름.** `Switch Color Mode(E)`, `View Statistics(E)`, `Keep Region Frozen(E)`, `Protect Frozen Region(E)`는 이번에 새로 붙인 이름이다.
4. **통계의 조건 문구.** 지금은 "통계 구독 중"으로 적었다.
5. **그리지 않은 관계.** 판이 바뀔 때마다 통계가 갱신되는 것과, 캡처·배치·영역 선택 때 시계가 멈추는 것은 그림에 넣지 않았다. 각 기능의 명세 본문에 적는다.
