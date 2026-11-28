# 유스케이스와 명세·요구사항 대응표

전체 유스케이스 다이어그램([usecase-overview.png](usecase-overview.png))의 확장 유스케이스(이름 끝에 `(E)`)마다, 유스케이스 명세가 있는 곳과 그 유스케이스에 대응하는 요구사항이 있는 곳을 모았다. 요구사항 ID는 각 기능 문서가 쓰는 ID를 그대로 옮겼다.

## 1. 읽는 법

- **명세**: 유스케이스의 사전 조건, 기본 흐름, 대체 흐름을 적은 곳이다.
- **요구사항**: 기능·비기능 요구사항 표가 있는 곳이다.
- **대응 요구사항**: 그 유스케이스 명세의 "연관 요구사항"(또는 요구사항 표의 유스케이스 열)에 적힌 ID다.
- 요구사항 ID의 이름은 기능 문서마다 다르다. 같은 `FR-1`이라도 문서가 다르면 다른 요구사항이다.

| 기능 | 기능 요구사항 ID | 비기능 요구사항 ID |
| --- | --- | --- |
| 드래그 그리기, 나이 기반 색상 표시, Freezing | `FR-n` | `NFR-n` |
| 패턴 캡처·템플릿 배치 | `R-nn` (요구사항과 수용 조건을 한 표에 적음) | |
| 음향 효과 | `Fn` | `Nn` |
| 게임 통계 조회 | ID 없음 (계산·표시 규칙과 갱신 시점을 표로 적음) | |

## 2. 대응표

| ID | 유스케이스 | 명세 | 요구사항 | 대응 요구사항 |
| --- | --- | --- | --- | --- |
| UC-08 | Draw Cells by Dragging(E) | [draw-cells-by-dragging.md](drag-drawing/draw-cells-by-dragging.md) 1절 | [draw-cells-by-dragging.md](drag-drawing/draw-cells-by-dragging.md) 2절 | FR-1 ~ FR-5, NFR-1 ~ NFR-4 |
| UC-09 | Switch Color Mode(E) | [color-gen.md](color-gen/color-gen.md) 1.2절 | [color-gen.md](color-gen/color-gen.md) 1.4절 | FR-1, FR-2, FR-5 |
| UC-10 | Recolor Cells by Age(E) | [color-gen.md](color-gen/color-gen.md) 1.2절 | [color-gen.md](color-gen/color-gen.md) 1.4절 | FR-5, FR-6, FR-9, NFR-4 |
| UC-11 | View Statistics(E) | [statistics.md](statistics/statistics.md) 1.4절 | [statistics.md](statistics/statistics.md) 1.2절(계산·표시 규칙), 1.3절(갱신 시점과 예외 상황) | (ID 없음) |
| UC-12 | Install Freezing(E) | [freezing.md](freezing/freezing.md) 1.2절 | [freezing.md](freezing/freezing.md) 1.4절 | FR-1, FR-2, FR-3, FR-4, FR-7, FR-10, NFR-4 |
| UC-13 | Release Freezing(E) | [freezing.md](freezing/freezing.md) 1.2절 | [freezing.md](freezing/freezing.md) 1.4절 | FR-6, FR-9 |
| UC-14 | Protect Frozen Region(E) | [freezing.md](freezing/freezing.md) 1.2절 | [freezing.md](freezing/freezing.md) 1.4절 | FR-8 |
| UC-15 | Keep Region Frozen(E) | [freezing.md](freezing/freezing.md) 1.2절 | [freezing.md](freezing/freezing.md) 1.4절 | FR-4, FR-5, FR-7, NFR-1, NFR-2 |
| UC-16 | Capture Template(E) | [capture.md](capture/capture.md) 2절 (E-01) | [capture.md](capture/capture.md) 1절 | R-01, R-02, R-07, R-08 |
| UC-17 | Undo Placement(E) | [capture.md](capture/capture.md) 2절 (E-03) | [capture.md](capture/capture.md) 1절 | R-06, R-08, R-09 |
| UC-18 | Place Template(E) | [capture.md](capture/capture.md) 2절 (E-02) | [capture.md](capture/capture.md) 1절 | R-03 ~ R-09 |
| UC-19 | Toggle Sound(E) | [sound-effects-spec.md](sound-effects/sound-effects-spec.md) 2.2절 (UC-S2) | [sound-effects-spec.md](sound-effects/sound-effects-spec.md) 3.1절 | F7, F8, F9, F10, F12 |
| UC-20 | Configure Sound(E) | [sound-effects-spec.md](sound-effects/sound-effects-spec.md) 2.2절 (UC-S3) | [sound-effects-spec.md](sound-effects/sound-effects-spec.md) 3.1절 | F11 |
| UC-21 | Hear Generation Sound(E) | [sound-effects-spec.md](sound-effects/sound-effects-spec.md) 2.2절 (UC-S1) | [sound-effects-spec.md](sound-effects/sound-effects-spec.md) 3.1절 | F1 ~ F6, F9, F12 |

## 3. 참고

- **원본 유스케이스(UC-01 ~ UC-07)**: Edit Cells, Run / Halt Game, Step One Generation, Clear Board, Store / Load Board, Exit Game, Advance Generation은 목표와 메인 성공 시나리오가 원본과 같으므로 따로 명세하지 않았다. 확장이 끼어드는 확장 지점은 전체 유스케이스 다이어그램에 적었고, 확장 기능 때문에 결과가 달라진 부분은 각 기능 문서의 연산 계약에 사후 조건으로 적었다.
- **괄호 안의 ID**: 캡처의 E-01 ~ E-03과 음향의 UC-S1 ~ UC-S3은 각 기능 문서 안에서 쓰는 유스케이스 ID다. 위 표의 UC-08 ~ UC-21과 같은 유스케이스를 가리킨다.
- **비기능 요구사항**: 각 문서의 비기능 요구사항 가운데 확장 유스케이스에 묶이지 않는 것(예: 음향의 N1 ~ N5, Freezing의 NFR-3, NFR-5, NFR-6)은 표에 적지 않았다. 각 문서의 요구사항 절에 있다.
