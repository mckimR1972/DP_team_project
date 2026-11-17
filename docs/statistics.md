# 통계 기능 설계 및 구현 기록

현재 설계와 검증 요약을 먼저 정리하고, 상세 테스트와 과거 변경 기록은 접기 영역에서 관리한다.

## 현재 설계

### 구현 범위

Universe가 작업 완료 후 하나의 `BoardChangeEvent`를 발행하고, `Statistics`와 `SoundEffects`가 같은 `BoardChangeListener` 계약으로 구독한다. 통계는 **세대 번호·생존 수·생존 비율·이번 세대의 탄생·사망 수**를 계산하여 오른쪽 통계 탭에 표시한다.

기준은 dev 병합 커밋 `4a49d0a`다. 기존 Clock 리스너의 `tick()` 본문, 색상·캡처/템플릿·드래그·탭 배치를 유지하면서 공통 이벤트로 전환했다. 별도 `advanceOneGeneration()`은 없다. 프리즈와 색별 통계는 이번 구현에 포함하지 않는다.

### 클래스와 주요 메서드

| 클래스 | 역할과 메서드 |
| --- | --- |
| BoardChangeEvent | 작업 종류·세대·크기·현재 `boolean[][]`·탄생/사망 `List<Point>`를 담는 불변 이벤트. `kind()`·`generation()`·`boardSize()`·`copyAliveCells()`·`copyBirths()`·`copyDeaths()`로 조회한다. |
| BoardChangeListener | `onBoardChanged(event)`로 완료 상태를 받는 Observer 계약. Statistics와 SoundEffects가 구현한다. |
| Universe — 구독 | `addBoardChangeListener()`는 중복 등록을 막고 새 구독자에게만 INITIAL을 전달한다. `removeBoardChangeListener()`로 해제한다. |
| Universe — 상태와 발행 | `liveCells()`가 `isAliveAt()`으로 판을 한 번 읽는다. `createBoardChangeEvent(kind, before)`가 완료 상태와 탄생·사망을 구성하고, `notifyBoardChanged()`가 같은 이벤트 객체를 모든 구독자에게 동기 전달한다. |
| Statistics | `onBoardChanged()` → `calculate()` → Summary 콜백. 생존 수는 배열에서, 탄생·사망 수는 목록 크기에서 구한다. 이전 판·이전 세대·BitSet을 보관하거나 상태 차이를 재계산하지 않는다. `current()`는 최근 Summary를 Optional로 반환한다. |
| Statistics.Summary | 세대·생존 수·백분율·탄생·사망·`hasGenerationDelta`. 이벤트 수신마다 새 불변 결과를 만들며 패널과 계약은 유지한다. |
| StatisticsPanel | `showStatistics(summary)`로 값만 표시한다. 보드 조회·구독·계산을 하지 않는다. |
| SoundEffects | `install()`이 공통 리스너로 등록한다. `onBoardChanged()`는 최신 판을 갱신하고, 변화가 있는 GENERATION만 기존 내부 `generationChanged(List<Point>, List<Point>)`로 전달한다. |
| Life | `createSidebar()`로 패턴·통계 공용 탭 구성, `createContent()`로 배치, `connectStatistics()`로 계산·화면 연결. 기존 `SoundEffects.install()`을 유지한다. |
| Clock·Cell 계층 | dev의 Swing Timer와 `Clock.tick()`을 유지한다. Cell 계층의 기존 `isAliveAt(row, column)`을 재사용한다. |

### 데이터와 이벤트 계약

배열은 `[행][열]`, Point는 `x = 열, y = 행`이다. 이벤트 생성 시 배열의 각 행과 모든 Point를 복사하고, 조회 시에도 독립된 복사본을 반환한다. 한 Observer가 받은 배열·목록·Point를 바꿔도 다른 Observer나 이벤트는 바뀌지 않는다.

생성자는 null·음수 세대·잘못된 정사각형 크기·불완전한 행·범위 밖/중복 좌표를 거부한다. 탄생 좌표는 현재 살아 있어야 하고 사망 좌표는 현재 죽어 있어야 한다. GENERATION은 양수 세대이며 다른 종류는 탄생·사망 목록이 비어 있어야 한다.

| 작업 | 이벤트 | 탄생·사망 | 세대 |
| --- | --- | --- | --- |
| 새 구독자 | 해당 구독자에게만 INITIAL | 해당 없음 | 현재 값 |
| 자동·단일 진행 | GENERATION 한 번 | 실제 진행 전후 차이 | 1 증가 |
| 클릭·일반 드래그 | 실제 변경이 있는 마우스 이벤트 처리 후 EDIT | 해당 없음 | 유지 |
| 템플릿 배치·Undo | 완료 신호에서 TEMPLATE·TEMPLATE_UNDO | 해당 없음 | 유지 |
| Clear / Load 성공 | CLEAR / LOAD | 해당 없음 | 0 |
| 캡처 선택·무변경 배치·Load 취소/읽기 실패·Store | 없음 | 해당 없음 | 유지 |

일반 드래그는 각 mouseDragged 처리 완료 시, 템플릿은 한 배치 드래그 완료 시 알린다. PatternTools의 기존 PropertyChange 신호를 한 번 연결하며 패턴 코드는 변경하지 않았다. Load는 파일을 다 읽은 뒤 보드를 교체하고, 실패 시 보드·통계를 유지한다.

### 실행 흐름과 담당 경계

```text
Clock.tick() → Universe에 등록한 Clock.Listener.tick()
  → 편집 중이면 종료 → 패턴 Undo 이력 정리 → 다음 상태 계산
  → 진행 전 boolean[][] 수집 → 매 세대 transition() → 세대 증가
  → 진행 후 boolean[][] 수집 → 탄생·사망 List<Point> 계산
  → BoardChangeEvent 하나를 등록된 BoardChangeListener들에게 전달
      Statistics: 생존 수·비율·목록 크기 → Summary → StatisticsPanel
      SoundEffects: 최신 판 보관 → 변화 있는 GENERATION만 기존 음 생성
  → 기존 조건에 따라 게임판 repaint
```

- 전후 비교는 Universe가 한 번 수행한다. 이전 상태는 그 tick의 지역 변수이고, 이후 이벤트나 Statistics에 저장하지 않는다. 현재 상태 수집 결과를 비교와 이벤트 구성에 함께 사용한다.
- 공개 `notifyBoardChanged(kind)`는 편집·복원 완료용이다. 실제 진행 전 상태 없이 GENERATION을 호출하면 예외를 던진다. 세대 진행은 Clock을 통해 요청한다.
- 등록·해제·알림은 EDT에서 실행한다. EDT 밖 Clock.tick 요청은 기존 dev 코드가 EDT로 전달한다. 관찰자는 콜백에서 판을 변경하지 않는다.
- 같은 발행에서는 같은 이벤트 인스턴스를 전달한다. 발행 중 구독 변경은 다음 발행부터 반영된다. 신규 구독의 INITIAL은 기존 구독자를 다시 초기화하지 않는다.
- 안정된 블록·빈 판도 세대와 통계는 진행한다. Resident.transition()은 매 세대 호출하므로 생존 상태가 같아도 셀 나이가 증가한다. 순환 색상 또는 생존 변화가 있을 때만 기존 보드 repaint를 요청한다.

### 통계 계산과 표시

GENERATION은 **이벤트 자체에 해당 한 세대의 변화 결과**가 있으므로 `hasGenerationDelta = true`다. 목록이 비면 탄생·사망을 `0`으로 표시한다. 다른 종류는 `false`이며 변화량을 `—`로 표시한다. 모든 종류에서 세대·생존 수·비율은 최신 값으로 갱신한다.

Statistics가 처음 받는 이벤트가 GENERATION이거나 이전에 다른 세대/크기를 받았어도, 이번 이벤트가 담은 실제 한 세대의 결과를 사용한다. 누락된 여러 세대를 합산하는 뜻은 아니다. Undo 이력과 이전 판 비교 기준은 Statistics가 관리하지 않는다.

Life가 패널과 Statistics를 한 번 연결하면 INITIAL로 창 표시 전 값이 채워진다. 패널은 기존 258×512 선호 크기·여백 14px·제목 16pt를 유지한다. 보드는 512×512이고 오른쪽 공용 탭 전체 높이를 보드에 맞춘다. 패턴 탭을 떠날 때 진행 중 입력을 완료하고 Undo를 유지하며, 일반 그리기 상태에서는 탭 전환으로 Clock이 멈추지 않는다.

### 사운드 연결

`GenerationListener`·`addGenerationListener()`·`generationPublisher`·`publishChanges()`를 제거했다. 사운드의 `generationChanged()`는 외부 구독 메서드가 아닌 private 내부 음 생성 메서드로 유지한다.

`onBoardChanged()`는 사운드 Off 상태에서도 최신 배열 복사본을 저장한다. GENERATION이고 탄생 또는 사망이 있을 때만 기존 효과음 처리에 진입한다. INITIAL·편집·Undo·Clear·Load·변화 없는 세대는 세대 효과음이나 반복 감지·마무리 화음을 호출하지 않는다.

재생선은 `volatile boolean[][] latestCells`에서 완성된 스냅샷을 읽는다. 배열은 저장 후 수정하지 않고 다음 이벤트에서 참조 전체를 교체한다. 최초 구독의 INITIAL을 받은 뒤 150ms 재생선 예약을 시작한다. playStep·전멸 판단·판 크기는 이 스냅샷을 사용하며 사운드 스레드가 Universe의 셀 상태를 다시 읽지 않는다. 재생선 표시 요청은 기존 setPlayhead를 사용한다.

음 선택·악기·스트럼·반복 감쇠·전멸 화음·예약 간격과 NoteMapper·RepeatTracker·SoundSettings 코드는 유지했다. 자세한 변경점은 [통계·사운드 연결 변경 문서](sound-statistics-integration.md)에 정리했다.

### 설계 선택

| 선택 | 이유 |
| --- | --- |
| 공통 Observer 계약 | Universe가 한 완료 이벤트를 발행하고 Statistics·SoundEffects가 각자 필요한 정보와 종류를 선택한다. 두 기능은 서로를 참조하지 않는다. |
| boolean[][]와 List<Point> | 기존 사운드 입력을 유지하고 통계도 같은 데이터로 집계한다. BitSet과 목록 사이의 변환 경로를 두지 않는다. |
| Universe에서 실제 전후 비교 | Observer의 수신 이력에 의존하지 않고, 한 번 계산한 탄생·사망을 함께 사용한다. |
| 기존 tick 본문 유지 | 팀원이 사용하는 세대 진행 위치와 편집·나이·화면 처리를 유지한다. 별도 진입 메서드를 추가하지 않는다. |
| 불변 이벤트와 Summary | 관찰자 사이의 상태 간섭을 막고 통계 계산과 화면 표시를 분리한다. |
| EDT와 사운드 스냅샷 | 보드 변경·수집·발행은 EDT에서 순차 처리한다. 별도 사운드 스레드는 게시된 스냅샷만 읽어 진행 중인 보드를 읽지 않는다. |

## 검증 요약과 상세 테스트

2026-11-17, `4a49d0a` 이후 공통 이벤트 전환 작업본에서 **JUnit 266개 통과, 실패·오류·건너뜀 0**을 확인했다.

- 실행: `./gradlew test --no-daemon --console=plain`
- 결과: `BUILD SUCCESSFUL in 1m 24s`, 24개 클래스의 현재 JUnit XML과 실행 수를 대조했다.
- 환경: macOS / Gradle 9.5.1 / 실행 JDK 25.0.1 / 컴파일·테스트 toolchain JDK 17.0.19 / JUnit Jupiter 6.0.3
- 이전 242개에서 계약 변경에 맞춰 검사를 교체·확장했다. 총수 차이가 모두 새 테스트를 뜻하지는 않는다.

| 검사 | 실행 수 | 결과 |
| --- | ---: | --- |
| BoardChangeEventTest | 22 | 배열·Point 복사, 이벤트 계약 통과 |
| StatisticsTest | 22 | 현재 상태·공유 변화량 집계 통과 |
| UniverseEventsTest | 19 | 발행·구독·실제 보드 경로 통과 |
| ClockIntegrationTest | 6 | 자동·단일 진행·EDT 전달 통과 |
| StatisticsPanelTest + StatisticsPanelIntegrationTest | 25 | 표시·탭·연결 통과 |
| MergedFeaturesIntegrationTest | 12 | 기존 기능·통계 통합 통과 |
| SoundEffectsIntegrationTest — 신규 | 15 | 실제 사운드 구독·이벤트 처리·예약 통과 |
| dev의 기존 색상·패턴·드래그·사운드·회귀 검사 | 145 | 모두 통과 |
| 전체 | 266 | 실패·오류·건너뜀 0 |

기존 오류 수정 회귀 검사 32개는 dev 검사에 포함되어 통과했다. HTML 보고서는 `build/reports/tests/test/index.html`이며 빌드 산출물은 Git에 넣지 않는다.

실제 MIDI 장치 출력·청음·설정 창 수동 조작·별도 patternUiTest 데스크톱 검사는 실행하지 않았다. SoundEffects.install()은 MIDI·예약 실행·메뉴 UI를 대역으로 바꿔 실제 구독 코드를 실행했다. 음 생성 계산과 이벤트 처리는 실제 코드다.

<details>
<summary>공통 이벤트 22개·통계 22개 — 계약과 기대 결과</summary>

| 영역 | 검사 내용 |
| --- | --- |
| 현재 상태·좌표 | [행][열] 배열과 Point(x=열,y=행), 세대·크기·목록 유지 |
| 방어적 복사 | 원본/반환 배열의 행, 목록, Point를 바꿔도 이벤트와 다른 수신자 데이터 유지 |
| 입력 검증 | null·음수 세대·0세대 GENERATION·부적합한 크기·행·좌표·중복·현재 상태와 모순된 변화 거부 |
| 작업 종류 | 세대 외 6종류는 탄생·사망 목록을 허용하지 않음 |
| 생존 집계 | 빈 판 0%, 가득 찬 판 100%, 초기 생존 수·전달받은 세대 표시 |
| 공유 변화량 | 첫 GENERATION부터 전달받은 목록 사용, 블링커 2/2, 안정 상태 0/0, 전멸·탄생 집계 |
| 편집·복원 | 세대 외 6종류는 생존 표시를 갱신하고 변화량은 미계산. 다음 GENERATION은 자신의 목록 사용 |
| 수신 이력과 분리 | 이전 번호와 불연속이거나 크기가 달라도 현재 이벤트의 한 세대 변화량 사용 |
| 결과 보호 | 이벤트를 변경하지 않고 이벤트마다 Summary 한 번 전달, 이전 Summary 유지, null 입력 거부 |

StatisticsTest는 이벤트와 변화 목록을 직접 구성한다. 해당 단위 검사만으로 템플릿·Undo·Load의 실제 연동을 확인했다고 보지 않는다. 실제 작업 경로는 아래 통합 검사에서 확인한다.

</details>

<details>
<summary>Universe·Clock·화면·기존 기능 — 실제 연동 검사</summary>

UniverseEventsTest는 초기·중복·해제 구독, 동일 이벤트 공유, 좌표/목록, 편집·안정/빈 판·Load 성공/실패/취소·Clear·Store·EDT 규칙을 확인한다. GENERATION을 공개 편집 알림 API로 위조 발행할 수 없다는 검사 1개를 추가했다.

Clock의 6개 검사는 실제 자동 Timer·단일 진행·정지·속도 변경·메뉴 선택 중 진행 억제·EDT 밖 tick 전달을 확인한다. 화면 25개 검사는 기존 다섯 표시 항목, 초기값, 비율, 탭 전환·크기·구독 해제를 확인한다.

MergedFeaturesIntegrationTest의 12개 검사는 실제 보드·패턴·통계·탭을 연결한다.

| 실제 작업 | 기대 결과 |
| --- | --- |
| 블링커 한 세대 | 세대 1·생존 3·탄생/사망 각 2, 좌표 기록용 공통 Observer도 동일 결과 수신 |
| 안정된 블록 10세대 | 생존 4 유지·색상 파랑·통계 세대 증가, 탄생/사망 없음 |
| 전멸 후 빈 세대 | 최초 사망만 변화 목록에 포함, 이후 통계 세대 증가 |
| 일반 드래그 생성·삭제 | 실제 변경마다 완료 알림, 같은 칸 재통과는 중복 없음 |
| 템플릿 배치 | 편집 중 진행 차단, 드래그 완료 후 TEMPLATE 한 번 |
| 템플릿 Undo 후 진행 | 판과 통계 복원, 다음 세대의 실제 판에서 변화량 계산 |
| 범위 밖/무변경 배치·캡처 | 불필요한 알림·Undo 이력·세대 증가 없음 |
| 패턴↔통계 탭 | 입력 종료·Undo 유지, 일반 모드에서는 자동 진행 지속 |
| 배치 중 Clear·Load | 입력/이력 정리와 통계 갱신, 종료한 배치가 다시 셀을 만들지 않음 |

이 12개의 사운드 확인은 BoardChangeListener로 변경 목록을 기록하는 수신자를 사용한다. 실제 SoundEffects까지 연결하는 검사는 아래 15개다.

</details>

<details>
<summary>SoundEffectsIntegrationTest 15개 — 실제 사운드 객체와 통계 연결</summary>

실제 Universe·Clock·Statistics·SoundEffects.install()을 사용한다. MIDI 신시사이저/채널, ScheduledExecutorService, 메뉴 등록 UI만 대역이며 예약된 Runnable을 직접 실행해 MIDI 호출을 확인한다.

| 검사 | 실행 수 | 기대 결과 |
| --- | ---: | --- |
| 초기 상태와 시작 순서 | 1 | INITIAL 수신 뒤 재생선 시작, 즉시 실행되는 첫 스텝도 살아 있는 셀을 연주 |
| 실제 블링커 | 1 | 통계 탄생/사망 각 2와 생성음·타악기 예약 |
| 안정된 블록·빈 판 | 1 | 통계 세대는 증가, 세대 효과음/마무리 화음 예약 없음 |
| 세대 외 이벤트 | 6 | INITIAL·EDIT·TEMPLATE·TEMPLATE_UNDO·CLEAR·LOAD는 최신 재생선 상태만 갱신 |
| Off 중 편집 | 1 | 소리는 꺼져도 상태 수신, On 이후 최신 배치를 연주 |
| 전멸 | 1 | 3음 마무리 화음, 0/90/180ms 시작·900ms 길이 유지, 이후 빈 세대에서 반복 없음 |
| 생성음·타악기 예약 | 1 | 스트럼 25ms 간격과 0~15ms 흔들림, 음 길이 220ms, 타악기 지연 60ms 유지 |
| 대량 탄생 | 1 | 12개 탄생 입력에서 기존 추가 악기 레이어 유지 |
| 별도 스레드 스냅샷 | 1 | 원본/반환 배열 변경이나 실제 보드와 무관하게 수신한 완성 상태로 연주 |
| MIDI 사용 불가 | 1 | 사운드 설치 실패가 게임·통계 진행을 막지 않음 |

6종류별 처리와 생성음/대량 탄생 입력은 이벤트를 직접 구성한다. 템플릿·Undo 등 실제 기능의 완료 알림은 MergedFeaturesIntegrationTest에서 별도로 검증한다. 기존 NoteMapper·RepeatTracker·SoundSettings 29개 검사도 전체 실행에 포함한다.

</details>

## 변경 기록

<details>
<summary>2026-11-17 · 통계·사운드의 BoardChangeListener 통합</summary>

- 기준 커밋: 4a49d0a. 이 기록은 공통 이벤트 전환 변경을 설명한다.
- Universe의 기존 tick 본문에서 실제 전후 상태를 비교하고 하나의 공통 이벤트를 발행한다.
- GenerationListener와 사운드 전용 Publisher 경로를 제거했다. 기존 Clock 리스너는 유지한다.
- BoardChangeEvent를 boolean[][]·List<Point>로 통일하고 Statistics의 BitSet·이전 상태 비교를 제거했다.
- SoundEffects의 등록·수신·상태 입력만 연결하고 음 생성/예약 규칙을 유지했다. INITIAL 수신 뒤 재생선 예약을 시작한다.
- 이벤트/통계 계약 테스트를 갱신하고 실제 사운드 연결 검사 15개를 추가했다. 전체 266개가 통과했다.

</details>

<details>
<summary>2026-11-17 · 최신 dev 병합과 기존 기능·통계 연결 (4a49d0a)</summary>

통계 `4021231`에 dev `a480113`을 병합하고 `4a49d0a`로 로컬 커밋했다. 아래는 공통 이벤트 전환 전의 구현·검증 기록이다.

- 충돌 7개 파일을 해결했다. Cell·NeighborhoodTest의 같은 조회 계약은 하나로 유지하고, Neighborhood의 Load 캐시·화면 갱신 처리는 dev를 유지했다.
- Universe는 dev의 익명 Clock.Listener.tick() 본문을 유지하고 transition() 직후 generation 증가·GENERATION 알림을 추가했다. 기존 편집 보호, 매 세대 셀 나이 갱신, 조건부 보드 repaint, 사운드 알림을 보존했다.
- 별도 advanceOneGeneration()을 제거했다. 이를 직접 호출하던 UniverseEventsTest·StatisticsPanelIntegrationTest는 Clock.tick()으로 진행하도록 바꿨다. 제거된 메서드의 EDT 예외 검사만 삭제하고, Clock의 EDT 전달 및 자동·단일 진행 검사는 유지했다.
- 일반 드래그 완료와 기존 패턴 PropertyChange 신호를 통계에 연결했다. 사운드 구현과 통계 이벤트·계산·표시 클래스는 그대로 유지했다.
- Life에 실제 패턴·통계 공용 탭을 조립하고, 탭 전환으로 숨겨지는 패턴 입력을 종료한다.
- Clock은 dev의 구현으로 맞췄으며 EDT 밖 tick 거부 검사를 EDT 전달 검사로 변경했다.
- 기존 테스트 230개와 새 통합 테스트 12개가 모두 통과했다. 병합 커밋은 완료했으며 push는 하지 않았다.

</details>


<details>
<summary>2026-11-11 · 공통 이벤트와 기본 통계 계산 (2aeff06)</summary>

- 구현 및 검증일: 2026-11-11, 브랜치 feature/statistics
- 기준: c49ca39(dev의 기본 오류 수정 PR #1 병합)
- 구현 커밋: 2aeff06 — feat: add board change events and basic statistics

| 대상 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 게임판 완료 데이터 | 공통 전달 타입 없음 | `BoardChangeEvent`가 작업 종류·세대 번호·크기·현재 생존 상태를 전달 |
| 알림 수신 계약 | 통계용 수신 계약 없음 | `BoardChangeListener.onBoardChanged(event)` 추가 |
| 통계 계산 | 독립 집계 객체 없음 | `Statistics`가 이벤트만으로 계산 |
| 통계 검증 | 통계 테스트 없음 | 이벤트 13개·통계 23개의 JUnit 검사 추가 |
| 기존 게임 실행 | Universe·Clock·Cell 계층이 진행과 표시 담당 | 이 커밋에서는 기존 실행 코드 변경 없음 |

당시에는 Universe·Clock 실행 코드를 바꾸지 않고 이벤트·계산 테스트 36개를 추가했다. 기존 Gradle Wrapper·JUnit 설정으로 `./gradlew clean test --no-daemon --console=plain`을 실행해 통과했고 BUILD SUCCESSFUL과 XML을 대조했다. 실행 환경은 위 요약과 같으며 forkEvery 등 실행 설정은 변경하지 않았다. 이 커밋의 검증은 직접 생성한 이벤트에 한정되며 실제 게임판 연결은 이후 변경이다.

</details>

<details>
<summary>2026-11-11 · 실제 게임판 알림·세대 진행 연결</summary>

- 구현 및 검증일: 2026-11-11, 브랜치 feature/statistics
- 작업 기준: fddfcef. 아래는 해당 기준 이후 구현한 변경 내용이다.
- 재사용: 사운드 브랜치 6ff3e81의 Cell 계층 isAliveAt과 중첩 조회 테스트. 사운드 기능 전체를 합친 것은 아니다.

아래 표는 당시의 변경 기록이다. 2026-11-17 통합에서는 별도 advanceOneGeneration()을 제거하고 dev의 Clock.Listener.tick() 본문에 통계 알림을 연결했다.

| 기존 코드 | 당시 변경 후 | 달라진 동작 |
| --- | --- | --- |
| 공통 보드 알림 발행 없음 | Universe.add/removeBoardChangeListener, notifyBoardChanged | 등록 시 현재 상태 전달, 작업 완료 시 공통 이벤트 전달 |
| Cell.isAlive만 사용 | Cell.isAliveAt 및 Universe.captureAliveCells | 실제 개별 좌표를 읽어 전체 생존 상태 수집 |
| Universe 생성자 안 Clock.Listener.tick의 직접 계산 | Universe.advanceOneGeneration | 상태 반영·세대 증가·완료 알림의 순서 통일 |
| 클릭 처리의 userClicked → repaint | userClicked → EDIT 발행 → repaint | 편집 직후 생존 통계와 비교 기준 갱신 |
| Clear의 clear → repaint | clear → 세대 0 → CLEAR 발행 → repaint | 초기화된 보드와 세대 동시 반영 |
| doLoad에서 clear 후 파일 읽기 | 파일 읽기 성공 → clear·transfer → 세대 0 → LOAD 발행 | 취소·읽기 실패로 기존 보드가 지워지는 문제 방지 |
| Neighborhood.transfer의 로드 후 기존 활동 표시 | 로드한 모든 묶음의 amActive·activeEdges 무효화 | 다음 세대에서 빈 인접 묶음도 재계산 |
| java.util.TimerTask와 별도 스레드 | javax.swing.Timer | 자동 진행과 상태 수집을 EDT에서 실행 |
| getGraphics를 사용하는 refreshNow | repaint | 보드 갱신을 Swing 그리기 요청으로 통일 |

테스트에서 발견한 Load 경계 오류는 행 7의 열 7·8·9에 있는 블링커를 파일에서 복원한 뒤 확인했다.
수정 전 첫 진행에서 탄생이 기대값 2 대신 1로 계산됐고, 수정 후에는 행 6·7·8의 열 8이 살아 있는 정확한 배치와 탄생 2·사망 2를 확인했다.

신규 UniverseEventsTest 18개·ClockIntegrationTest 6개와 재사용한 조회 검사 1개를 추가했다. 초기 실행에서 발견한 문제를 수정한 뒤 전체 테스트를 재실행했으며 결과와 기대값은 위 검증 영역에 보존한다. Universe.refreshNow()의 직접 그리기는 repaint()로, Life.main()은 EDT에서 생성하도록 변경했다.

</details>

<details>
<summary>2026-11-11 · 문서 구조와 설계 설명 정리</summary>

- 2026-11-11: 단계별 파일 대신 docs/statistics.md에서 현재 설계와 변경 기록을 함께 관리하도록 정리했다. README는 작업 시작 시점 dev(c49ca39)의 내용으로 유지하고 이전 통계 링크를 제거했다.
- 신규 통계 테스트를 중심으로 검증 내용을 작성하고, 직접 만든 템플릿·Undo 이벤트와 실제 기능 연동을 구분했다.
- 통계 상태 수집·이전 상태 갱신의 일관성을 위한 Swing Timer 선택 이유와 대안을 문서화했다.
- 이번 정리에서는 중복된 현재 설계를 합치고 테스트 상세·변경 기록을 접기 영역으로 옮겼다. 테스트 입력·기대 결과와 변경 전후 표는 보존했다.
- 문서 정리 작업에서는 Java 소스·테스트·빌드 설정을 변경하거나 JUnit을 재실행하지 않았다. Markdown 구조·참조 경로·공백 오류와 기존 파일 보존 여부를 확인했다. 커밋과 push는 하지 않았다.

</details>

<details>
<summary>2026-11-16 · 통계 패널과 게임 시작 시 연결</summary>

- 작업 기준: feature/statistics의 e6a58db. 커밋·push 전 작업 내용이다.

| 대상 | 변경 전 | 변경 후 |
| --- | --- | --- |
| StatisticsPanel | 화면 클래스 없음 | Summary의 다섯 값을 표시하고 미계산 변화량을 `—`로 구분 |
| Life 생성자 | Universe만 가운데에 배치 | createContent()로 게임판·오른쪽 통계 탭 구성, 연결 후 pack·최소 크기 설정 |
| 통계 실행 연결 | 테스트에서만 Statistics 생성·등록 | connectStatistics()가 결과 콜백 연결·등록, INITIAL로 첫 표시 완성 |
| 구독 수명 | 실행 앱의 통계 구독 없음 | Life가 Statistics 참조를 보관하고 dispose()에서 해제 |
| 표시 검증 | 계산 결과와 이벤트만 검사 | 신규 JUnit 22개로 실제 JLabel·게임판 연결·배치 검사 |

Universe·Clock·Cell 계층·Statistics 계산·공통 이벤트 계약은 이번 작업에서 변경하지 않았다. 패널이 보드를 다시 조회하거나 자체 타이머로 갱신하는 방식 대신 기존 결과 콜백을 연결해 계산과 표시 책임을 분리했다. Life의 화면 배치와 연결을 package-private 메서드로 분리해 headless 검사에서도 앱과 동일한 코드를 사용한다.

전체 JUnit 115개 통과. 현재 화면 및 변경 상세는 위 설계·검증 영역에 정리했다. README와 빌드 설정은 수정하지 않았고 색별 통계·다른 담당 기능 연동은 후속 작업이다.

</details>

<details>
<summary>2026-11-17 · 캡처·프리즈 화면 규격 및 공용 탭 결합 준비</summary>

- 작업 기준: 앞서 작성한 통계 화면의 미커밋 변경. 현재 브랜치는 feature/statistics이며 이번에도 커밋·push는 하지 않았다.
- 참고 코드: capture-templates의 e7fec42와 freezing의 c95d0b5. 패널 외형과 창 배치만 맞췄으며 두 기능을 병합하지 않았다.

| 대상 | 변경 전 | 변경 후 |
| --- | --- | --- |
| StatisticsPanel | GridBagLayout, 제목 22pt, 여백 18~20px | BoxLayout, 선호 크기 258×512, 제목 16pt, 여백 14px |
| Life.createContent | (Universe, StatisticsPanel)을 받아 내부에서 탭 생성 | (Universe, JTabbedPane)을 받아 기존 패널·선택·리스너를 보존해 배치 |
| 창 배치 | 보드 주변 컨테이너, 12px 간격, 확대 가능 | CENTER에 보드·EAST에 탭, 간격 없음, 크기 변경 금지, 화면 중앙 배치 |
| 탭 크기 | 최소 폭 280px | 모든 탭 등록 후 자연 너비 재계산, 제목줄 포함 높이 512px |
| 레이아웃 검사 | 기본·확대 배치 2개 | 단독 배치·공용 탭 보존·숨김 상태 갱신·큰 값 배치 4개 |

공용 탭을 통계 쪽에서 다시 만들면 다른 기능의 탭이나 전환 리스너를 잃을 수 있어, 화면 조립 메서드가 완성된 탭을 받도록 변경했다. 기존 탭의 선호 너비가 먼저 고정된 경우도 고려해 전체 등록 후 재계산한다. 구독은 탭 선택과 분리해 한 번만 연결한다.

표시 검사에 규격 1개를 추가하고 배치 검사를 교체해 화면 관련 검사는 25개, 전체는 118개가 통과했다. 테스트용 패턴·프리즈 탭은 빈 패널이며 실제 기능 연동은 통합 후 확인한다. README·빌드 설정·게임 진행·통계 계산·이벤트 계약은 수정하지 않았다.

</details>
