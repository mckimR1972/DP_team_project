# 유스케이스와 시스템 시퀀스 다이어그램 대응표

확장 유스케이스마다 그 유스케이스를 그린 시스템 시퀀스 다이어그램(SSD) 그림과, 그림을 설명하는 절, 그림에 나오는 시스템 연산을 모았다. 유스케이스 ID는 [유스케이스와 명세·요구사항 대응표](usecase-traceability.md)와 같다.

통계(UC-11 View Statistics(E))와 드래그 그리기(UC-08 Draw Cells by Dragging(E))는 아직 SSD가 없어 이 표에서 뺐다.

## 1. 읽는 법

- **SSD 그림**: 각 기능 폴더의 png다. 같은 이름의 `.puml`이 원본이다.
- **설명**: 그림을 넣고 그린 규칙과 시스템 연산 표를 적은 절이다.
- **시스템 연산**: 그림에서 사용자가 시스템(`:LifeGame`)에 보내는 연산이다. 각 연산의 사후 조건은 같은 문서의 연산 계약 절에 있다.
- 한 그림이 여러 유스케이스를 함께 그린 경우(사운드)는 "그림에서 해당하는 부분"에 어느 구간인지 적었다.

## 2. 대응표

| ID | 유스케이스 | SSD 그림 | 설명 | 시스템 연산 | 그림에서 해당하는 부분 |
| --- | --- | --- | --- | --- | --- |
| UC-09 | Switch Color Mode(E) | [ssd-turn-on.png](color-gen/diagrams/ssd-turn-on.png), [ssd-turn-off.png](color-gen/diagrams/ssd-turn-off.png) | [color-gen.md](color-gen/color-gen.md) 3.1절 | `turnOnColorCycling()`, `turnOffColorCycling()` | 전체. 순환 모드를 고르는 메인 성공 시나리오와, 한 가지 색 모드를 고르는 대체 시나리오 1a를 한 장씩 그렸다 |
| UC-10 | Recolor Cells by Age(E) | [ssd-step.png](color-gen/diagrams/ssd-step.png) | [color-gen.md](color-gen/color-gen.md) 3.1절 | `stepOneGeneration()` | 전체. 색 순환이 켜져 있을 때 Step One Generation의 결과로 나이에 맞는 색을 받는 흐름이다 |
| UC-12 | Install Freezing(E) | [ssd-install.png](freezing/diagram/ssd-install.png) | [freezing.md](freezing/freezing.md) 3.1절 | `setFreezingDuration(n)`, `startRegionSelection()`, `selectRegion(start, end)`, `installFreezing()`, `cancelRegionSelection()` | 전체 |
| UC-13 | Release Freezing(E) | [ssd-release.png](freezing/diagram/ssd-release.png) | [freezing.md](freezing/freezing.md) 3.1절 | `releaseFreezing(region)`, `releaseAllFreezing()`, `clearBoard()`, `loadBoard(file)` | 전체. 뒤의 두 연산은 Clear Board, Store / Load Board를 확장하는 경우다 |
| UC-14 | Protect Frozen Region(E) | [ssd-protect.png](freezing/diagram/ssd-protect.png) | [freezing.md](freezing/freezing.md) 3.1절 | `editCell(row, column)`, `placeTemplate(origin)` | 전체. Edit Cells와 Place Template(E)를 확장하는 두 경우다 |
| UC-15 | Keep Region Frozen(E) | [ssd-keep-frozen.png](freezing/diagram/ssd-keep-frozen.png) | [freezing.md](freezing/freezing.md) 3.1절 | `stepOneGeneration()` | 전체. Step One Generation 안에서 일어나고, 게임이 돌 때 세대마다 같은 결과가 나오는 부분은 loop로 그렸다 |
| UC-16 | Capture Template(E) | [07-ssd-capture.png](capture/diagrams/report/07-ssd-capture.png) | [capture.md](capture/capture.md) 5절 "시스템 시퀀스 다이어그램" | `selectCaptureArea(start, end)`, `saveTemplate(name)`, `finishTemplateEditing()` | 전체 |
| UC-17 | Undo Placement(E) | [09-ssd-undo.png](capture/diagrams/report/09-ssd-undo.png) | [capture.md](capture/capture.md) 5절 "시스템 시퀀스 다이어그램" | `undoPlacement()` | 전체 |
| UC-18 | Place Template(E) | [08-ssd-place.png](capture/diagrams/report/08-ssd-place.png) | [capture.md](capture/capture.md) 5절 "시스템 시퀀스 다이어그램" | `placeTemplate(origin)`, `finishPlacementStroke()`, `finishTemplateEditing()` | 전체 |
| UC-19 | Toggle Sound(E) | [ssd.png](sound-effects/diagram/ssd.png) | [sound-uml.md](sound-effects/sound-uml.md) 4.1절 | `selectSoundOff()`, `selectSoundOn()` | 그림 뒷부분. 소리를 끄면 재생선과 패드가 멈추고, 다시 켜면 재생선이 돈다 |
| UC-20 | Configure Sound(E) | [ssd.png](sound-effects/diagram/ssd.png) | [sound-uml.md](sound-effects/sound-uml.md) 4.1절 | `openSoundSettings()`, `setFeature(feature, on)`, `setInstrument(feature, instrument)` | 그림 가운데. 설정 창을 열어 항목을 끄고 악기를 바꾼다 |
| UC-21 | Hear Generation Sound(E) | [ssd.png](sound-effects/diagram/ssd.png) | [sound-uml.md](sound-effects/sound-uml.md) 4.1절 | `tick()` | 그림 앞부분의 "틱 간격마다" loop. 세대마다 생성 화음과 소멸 타악기를 듣는다 |

## 3. 참고

- **기능마다 SSD를 나눈 방식이 다르다.** 색 순환은 시나리오마다, Freezing과 캡처·템플릿은 유스케이스마다 한 장씩 그렸다. 사운드는 세 유스케이스를 이어지는 한 시나리오(소리를 듣고, 설정을 바꾸고, 껐다 켜기)로 한 장에 그렸다.
- **사운드 그림의 다른 연산**: 같은 그림의 `startUp()`, `editCell()`, `sweepStep()`, `selectSpeed()`, `halt()`는 프로그램 실행, 셀 편집, 재생선 타이머, 게임 실행과 정지를 보이려고 함께 그린 연산이다. 재생선 소리는 따로 유스케이스로 두지 않았다([sound-effects-spec.md](sound-effects/sound-effects-spec.md) 2.1절).
- **원본 연산이 다시 나오는 경우**: `stepOneGeneration()`, `clearBoard()`, `loadBoard(file)`, `editCell(row, column)`은 원본(또는 다른 기능)의 연산이다. 확장 유스케이스가 그 연산의 결과를 바꾸므로 해당 기능의 SSD에 다시 그렸다.
