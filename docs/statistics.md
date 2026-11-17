# 통계 기능 설계 및 구현 기록

현재 설계와 검증 요약을 먼저 정리하고, 상세 테스트와 변경 기록은 접기 영역에서 관리한다.

## 현재 설계

### 구현 범위

Universe가 게임판의 작업 완료 상태를 발행하고 Statistics가 받아 **세대 번호·생존 셀 수·생존 비율·세대별 탄생·사망 수**를 계산한다. 자동 진행·단일 진행·클릭·일반 드래그·템플릿 배치·배치 Undo·Clear·Load를 연결했다. Life의 오른쪽 공용 탭에 실제 패턴 도구와 통계 패널을 함께 배치했다.

통계 `4021231`에 dev `a480113`을 병합한 작업본을 검증했다. 색상·캡처/템플릿·일반 드래그·사운드를 보존했으며 **공통 이벤트 전환은 아직 하지 않았다.** 통계는 기존 BitSet·BoardChangeListener, 사운드는 기존 GenerationListener와 음 생성 코드를 유지한다.

아직 구현하거나 연결하지 않은 내용:

- **후속 통합:** 프리즈는 이번 dev에 포함되지 않았다. 통계·사운드의 구독 인터페이스 및 자료형 통일은 별도 후속 작업이다.
- **색별 통계:** 이벤트에 나이·색상 정보가 없어 색별 집계·파이 차트는 미구현이다. 프리즈된 생존 셀도 나이가 증가한다는 정책은 색상·프리즈 통합 시 확인한다.

### 클래스와 주요 메서드

| 클래스 | 주요 메서드·데이터와 역할 |
| --- | --- |
| BoardChangeEvent | kind()·generation()·boardSize()로 작업 종류·세대·크기를 조회하고, copyAliveCells()로 생존 상태의 독립된 BitSet을 반환한다. |
| BoardChangeListener | onBoardChanged(event): 작업 완료 상태를 받는 Observer 계약이다. |
| Statistics | 생성자에서 결과 콜백을 받는다. onBoardChanged()가 calculate()로 계산한 뒤 비교 기준과 결과를 갱신하고 콜백을 호출한다. current()는 최근 결과를 Optional로 반환하며 첫 이벤트 전에는 비어 있다. |
| Statistics.Summary | 불변 결과: generation(전달받은 세대), livingCells(생존 수), livingRatio(백분율 0~100), births·deaths(탄생·사망), hasGenerationDelta(변화량 계산 여부). |
| StatisticsPanel | 생성자는 항목과 초기 `—` 표시를 구성한다. showStatistics(summary)는 다섯 항목을 갱신하고, private formatPercentage()는 비율을 소수점 둘째 자리로 표시한다. 보드 조회·통계 계산·구독은 하지 않는다. |
| Life — 화면과 연결 | createSidebar()가 실제 패턴·통계 탭을 구성한다. createContent()는 보드와 공용 탭을 배치하고, connectStatistics()는 패널 콜백에 연결한 Statistics를 등록한다. SoundEffects.install()도 유지한다. dispose()에서 통계 구독을 해제한다. |
| Universe — 구독 | addBoardChangeListener()는 중복 등록을 막고 새 구독자에게만 INITIAL을 전달한다. removeBoardChangeListener()는 없는 구독자를 해제해도 안전하다. |
| Universe — 진행 | 생성자에서 등록한 Clock.Listener.tick()은 편집 중이면 종료한다. 진행 시 패턴 Undo 이력을 정리하고 상태 계산·매 세대 transition·세대 증가·통계 알림을 수행한다. 생존 변화가 있을 때 기존 사운드 알림을 전달하며, 색상 순환 중이거나 생존 상태가 바뀌면 보드를 repaint한다. |
| Universe — 발행 | captureAliveCells()로 상태를 수집하고 createBoardChangeEvent()로 이벤트를 만든다. notifyBoardChanged(kind)는 같은 이벤트를 구독자들에게 전달하며 보드·세대 번호를 바꾸지 않는다. |
| Universe — 불러오기 | doLoad()는 파일을 다 읽은 후 보드를 교체한다. 취소·읽기 실패는 기존 상태를 유지한다. |
| Cell 계층 | isAliveAt(row, column)으로 셀 좌표를 조회한다. Neighborhood는 좌표를 하위 Cell로 변환·위임하고 Resident는 자신의 상태를, DUMMY는 false를 반환한다. |
| Clock·Life | Clock의 Swing Timer가 Clock.tick()을 호출하고 Universe에 등록된 Clock.Listener.tick()으로 연결된다. Life.main()은 invokeLater(Life::new)로 EDT에서 앱을 구성한다. |

생존 좌표는 `row * boardSize + column` 비트로 표현한다. BoardChangeEvent는 입력·조회 시 BitSet을 복사하며 Cell 객체나 이전 상태를 노출하지 않는다. null·음수 세대·잘못된 보드 크기·범위 밖 비트는 거부한다.

isAliveAt 구현과 중첩 조회 테스트는 사운드 브랜치의 `6ff3e81`을 재사용했다. 유효한 셀 행·열을 사용하며 Resident의 조회 좌표는 (0,0)이다. 별도로 Neighborhood.transfer(..., LOAD)의 계산 캐시를 무효화해 로드 후 빈 인접 묶음도 다시 계산하도록 수정했다.

### 실행과 연동 규칙

```text
Clock.tick() → Universe 생성자에 등록한 Clock.Listener.tick()
  → 상태 계산·반영 → 세대 증가 → notifyBoardChanged(GENERATION)
  → isAliveAt으로 전체 상태 수집 → BoardChangeEvent
  → Statistics 계산·비교 기준 갱신 → Summary 콜백 → StatisticsPanel.showStatistics
```

| 작업 | 알림 | 세대 번호 |
| --- | --- | --- |
| 새 구독자 등록 | 해당 구독자에게만 INITIAL | 현재 값 |
| 자동 진행·단일 진행 | 완료 후 GENERATION 한 번 | 1 증가 |
| 보드 안의 왼쪽 클릭·일반 드래그 | 실제 변경이 있는 마우스 이벤트 처리 후 EDIT 한 번 | 유지 |
| Clear | 초기화 후 CLEAR 한 번 | 0 |
| Load 성공 | 상태 적용 후 LOAD 한 번 | 0 |
| Load 취소·읽기 IOException / Store | 없음 | 유지 |
| 템플릿 배치·Undo | 완료된 드래그당 TEMPLATE 한 번, 실제 Undo 후 TEMPLATE_UNDO 한 번 | 유지 |

저장 형식에 세대 번호가 없어 Load 성공 시 0으로 시작한다. Load 성공·Store는 Clock을 멈추고, 일반 그리기 모드의 Clear는 자동 진행을 멈추지 않는다. 진행 중인 템플릿 드래그를 Clear로 끝내면 TEMPLATE로 그 작업을 먼저 확정한 뒤 CLEAR로 초기화한다. 무변경·거부된 배치, 캡처 선택에는 상태 변경 알림이 없다.

- **EDT 실행:** Universe의 등록·해제·notifyBoardChanged는 EDT 밖에서 호출하면 IllegalStateException을 던진다. 세대 진행은 Clock.tick()을 통해 요청하며, dev의 Clock 구현이 EDT 밖 요청을 invokeLater로 전달한 뒤 Universe의 리스너 본문을 실행한다. startTicking·stop은 UI의 EDT 경로에서 호출하며, dev처럼 별도의 EDT 거부 검사는 없다.
- **연동 방법:** PatternTools의 기존 TEMPLATE·TEMPLATE_UNDO PropertyChange 완료 신호를 Universe 생성 시 한 번 연결한다. 한 템플릿 드래그가 끝난 뒤 상태를 읽고 통계에 전달한다. 편집 중에는 세대 진행을 막는다. 콜백은 짧게 처리하고 게임판을 다시 변경하지 않는다.
- **구독 관리:** 일반 발행은 구독자가 없으면 수집을 생략한다. 발행 시작 시 구독자 목록을 복사하므로 콜백 중 해제는 다음 발행부터 반영된다.
- **타이머 동작:** dev의 Swing Timer 구현을 유지한다. 양수 간격이면 이전 Timer를 멈추고 새 Timer를 시작하며, 첫 자동 tick도 지정 간격 뒤 실행한다. 0 이하는 정지다. 메뉴 선택 중에는 tick을 건너뛰며 세대는 실제 완료 횟수로 센다.

### 통계 계산 규칙

1. 모든 이벤트에서 생존 수·비율과 전달받은 세대 번호를 갱신한다. 통계 전용 타이머는 없다.
2. GENERATION이고 이전 상태가 있으며, 보드 크기가 같고 세대 번호가 정확히 1 증가했을 때만 탄생·사망을 계산한다. 탄생은 현재 생존 집합에서 이전 집합을 뺀 수, 사망은 그 반대다. 계산에는 복사본을 사용한다.
3. 계산하지 못한 경우 hasGenerationDelta는 false이며 births·deaths의 0은 미계산 값이다. 화면은 `—` 등으로 표시한다. 실제 변화가 없는 세대는 true와 0으로 구분한다.
4. 모든 이벤트의 현재 상태를 다음 비교 기준으로 교체한다. 편집 후 다음 세대도 새 기준으로 비교하며, 세대 누락·역행·중복이나 크기 변경 시 변화량을 계산하지 않고 기준을 다시 잡는다. 직전 상태 하나만 보관하므로 Undo 이력은 아니다.

### 통계 화면과 시작 시 연결

Life는 `MenuSite.establish()` → Universe 확보 → 패널·공용 탭 구성 → `connectStatistics()` → `SoundEffects.install()` → `pack()` → 창 표시 순서로 실행한다. 통계 등록 시 INITIAL이 즉시 전달되며, 등록 때문에 세대나 보드를 변경하지 않는다.

| 표시 항목 | Summary 값 | 표시 규칙 |
| --- | --- | --- |
| 현재 세대 / 살아 있는 셀 | generation / livingCells | 전달받은 정수 그대로 표시 |
| 생존 비율 | livingRatio | 이미 백분율이므로 다시 100을 곱하지 않음. `Locale.ROOT`로 `0.00%` 형식 표시 |
| 이번 세대 탄생 / 사망 | births / deaths | hasGenerationDelta가 true이면 숫자, false이면 `—`로 교체 |

- 첫 결과 전에는 다섯 값 모두 `—`다. 빈 보드의 INITIAL은 세대 0·생존 0·비율 0.00%·변화량 `—`, 실제 변화 없는 세대는 변화량 `0`으로 구분한다.
- 패널 생성·갱신은 EDT에서 실행한다. showStatistics()는 EDT 밖 요청과 null 결과를 거부한다. 기존 동기 알림을 사용하므로 추가 Timer·invokeLater·상태 이력은 없다.
- 패널은 캡처·프리즈와 같은 선호 크기 258×512, 사방 여백 14px, 제목 16pt다. BoxLayout으로 세로 배치하고, 각 통계 행은 항목명·숫자를 좌우 정렬한다.
- Life.createSidebar()는 `패턴 캡처`와 `통계` 탭을 구성한다. createContent()는 Universe를 CENTER·공용 탭을 EAST에 간격 없이 배치한다. 모든 탭 등록 후 선호 너비를 재계산하고, 탭 제목줄을 포함한 전체 높이를 보드의 512px로 맞춘다.
- 창은 `setResizable(false)` → `pack()` → `setLocationRelativeTo(null)`로 구성한다. 기본 게임판 512×512를 유지하며, 이전 확대·여백 배치는 사용하지 않는다.
- 패널은 탭이나 JFrame을 소유하지 않는다. 다른 탭을 선택해도 구독·비교 기준을 유지하며 최신 결과를 표시한다. connectStatistics()와 createContent()는 같은 패키지에서만 사용하고, 테스트도 실제 연결·배치 메서드를 재사용한다.

패턴 탭을 떠날 때 finishPatternEditing()으로 활성 입력을 종료한다. 진행 중인 드래그는 완료 알림을 한 번 보내고 Undo 가능 상태를 유지한다. 일반 그리기 모드에서는 cancel()이 아무 작업도 하지 않으므로 탭 전환이 시계를 멈추지 않는다. 탭을 열 때마다 Statistics나 패턴 완료 리스너를 다시 등록하지 않는다.

### 설계 선택

| 선택 | 이유 |
| --- | --- |
| Observer | Universe는 Subject, BoardChangeListener는 수신 계약, Statistics는 Observer다. Universe가 통계·사운드의 구체적인 구현을 알 필요가 없다. |
| 현재 상태 전달·계산과 표시 분리 | Statistics가 비교 기준과 집계를 담당하고 불변 Summary를 패널에 전달한다. Universe는 통계 패널을 모르며 Statistics도 Swing에 의존하지 않는다. 패널을 별도 BoardChangeListener로 만들지 않아 계산과 보드 조회가 중복되지 않는다. |
| 불변 이벤트·결과 | 관찰자나 화면이 받은 데이터를 바꿔 다른 관찰자의 상태를 훼손하지 못하게 한다. |
| 공통 isAliveAt 재사용 | 통계 전용 셀 조회 메서드는 추가하지 않는다. 이번 병합에서는 기존 통계 BitSet 수집과 사운드 배열 조회를 유지한다. 두 발행 경로의 통일은 후속 작업이다. |
| dev의 Clock.Listener.tick 본문 유지 | 기존 편집 보호·상태 계산·나이 갱신·화면·사운드 처리 순서를 보존하고, transition() 직후 세대 증가와 통계 알림만 추가한다. 별도 세대 진행 메서드 없이 자동 진행과 단일 진행이 같은 리스너를 사용한다. |
| LinkedHashSet 구독 관리 | 통계 리스너의 중복 등록 방지·해제·명확한 타입을 지원한다. 사운드의 기존 Publisher는 이번 병합에서 유지한다. |

**Swing Timer 선택 이유:** 통계는 세대 진행뿐 아니라 클릭·Clear 등의 완료 시에도 갱신된다. 게임판 전체를 읽는 중 상태가 바뀌거나 Statistics의 이전 상태 갱신이 겹치지 않도록 변경·수집·동기 알림을 EDT에서 순차 처리하고, Clock의 자동 진행도 여기에 맞췄다. 이벤트 복사는 수집 완료 후의 값을 보호하므로 수집 과정의 일관성도 필요하다. 기존 Timer에 EDT 전달이나 공통 잠금을 추가하는 대안도 있지만, 현재 Swing 구조에서는 Swing Timer가 간단하다. Statistics 자체는 타이머에 의존하지 않는다. 모든 연동 기능이 EDT 규칙을 지켜야 하며, 계산이 길어지면 UI 응답이 늦어질 수 있다. [Swing Timer 공식 문서](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/Timer.html)

## 검증 요약과 상세 테스트

2026-11-17, 통계 `4021231`과 dev `a480113`의 병합 작업본에서 dev의 Clock.Listener.tick() 본문을 유지하도록 수정한 뒤 전체 검사를 다시 실행했다. **JUnit 242개 통과, 실패·오류·건너뜀 0**을 확인했다. 새 통합 검사 12개를 중심으로 아래에 기록한다. 공통 이벤트 전환은 이 검증에 포함되지 않는다.

- 실행: `./gradlew test --no-daemon --console=plain`
- 환경: macOS, Gradle 9.5.1 / Gradle 실행 JDK 25.0.1 / 컴파일·테스트 toolchain Amazon Corretto JDK 17.0.19 / JUnit Jupiter 6.0.3
- 이번 재실행은 `BUILD SUCCESSFUL in 1m 15s`로 완료했다. 현재 클래스 이름에 대응하는 JUnit XML 23개의 실행 수를 대조했다. 파라미터별 실행을 포함하며, 이름이 중복된 오래된 XML 복사본은 집계하지 않았다.
- dev의 클래스별 JVM 격리 설정(`forkEvery = 1`)을 유지한다. 통계 테스트는 사용자 저장 템플릿 대신 빈 임시 폴더를 사용한다.

| 검사 구분 | 실행 수 | 결과 |
| --- | ---: | --- |
| MergedFeaturesIntegrationTest — 이번 병합 연결 검사 | 12 | 모두 통과 |
| 기존 통계·화면·시계 연결 검사 6개 클래스 | 85 | 모두 통과 |
| dev의 기존 색상·패턴·드래그·사운드·회귀 검사 | 145 | 모두 통과 |
| 전체 | 242 | 실패·오류·건너뜀 0 |

기존 오류 수정 회귀 검사 32개도 dev 검사에 포함되어 모두 통과했다.

**검증 범위:** 신규 통합 검사는 실제 Universe·Clock·PatternTools·BoardEditor·Statistics·StatisticsPanel과 Life의 실제 공용 탭 구성 메서드를 사용한다. 사운드는 기존 GenerationListener를 구현한 기록용 수신자로 전달 좌표·호출 횟수를 확인하고, 기존 NoteMapper·RepeatTracker·SoundSettings JUnit도 함께 실행했다. 실제 MIDI 출력과 SoundEffects.install()을 통한 장치 연결, JFrame 수동 조작, 별도 `patternUiTest` 데스크톱 검사는 이번에 실행하지 않았다.

StatisticsTest는 이벤트를, StatisticsPanelTest는 Summary를 직접 구성하는 단위 검사다. 기존 StatisticsPanelIntegrationTest의 일부 공용 탭 검사는 빈 패널을 사용하며, 이번 MergedFeaturesIntegrationTest는 실제 패턴 패널과 편집 경로를 연결한다. 색상별 통계와 프리즈는 검증 범위 밖이다.

<details>
<summary>병합 통합 검사 12개 — 실제 동작과 기대 결과</summary>

`MergedFeaturesIntegrationTest`는 실제 메뉴·마우스·Swing Timer 경로를 실행한다. 사운드 수신자는 MIDI 장치를 열지 않으며 실제 Universe가 계산한 좌표를 기록한다.

| 검사 | 기대 결과 |
| --- | --- |
| 한 번의 단일 tick과 블링커 | 세대 1, 생존 3, 탄생·사망 각 2. 기존 사운드 리스너도 정확한 좌표를 EDT에서 한 번 수신 |
| 안정된 블록과 색상 순환 | 10세대 동안 생존 4·탄생/사망 0 유지, 셀은 파랑으로 변경, 세대 사운드 알림 없음 |
| 전멸 후 빈 보드 진행 | 최초 사망 좌표만 사운드에 한 번 전달, 통계 세대는 계속 증가 |
| 일반 드래그 생성·삭제 | 마우스 이벤트마다 완료된 상태 한 번 전달, 같은 위치 재통과는 알림 없음 |
| 템플릿 드래그 완료 | 누르는 동안 세대 진행·통계 알림 없음, 해제 후 TEMPLATE 한 번과 생존 수 5 표시 |
| 템플릿 Undo와 다음 세대 | 드래그 한 건을 Undo하여 이전 생존 수 복원, 다음 블링커 세대의 탄생·사망 각 2 |
| 무변경·범위 밖 배치 | 알림·Undo 이력 중복 없음 |
| 캡처 영역 선택 | 판·통계·세대·사운드 알림 변경 없음 |
| 실제 패턴·통계 탭 전환 | 진행 중 드래그를 한 번 확정하고 Undo 유지, 이후 세대 진행 가능, 보드 512px 유지 |
| 편집 중이 아닌 탭 전환 | 실제 자동 Clock이 계속 진행 |
| 드래그 도중 Clear | 드래그 확정 후 CLEAR, 통계 초기화·Undo 비활성화, 배치 Timer가 이후 셀을 다시 만들지 않음 |
| 패턴 편집 중 Load | 편집 종료·이력 제거·세대 0, 다음 세대에 통계와 사운드가 동일한 탄생·사망 수신 |

</details>

<details>
<summary>StatisticsPanelTest 12개 — 표시 규칙·공통 규격과 기대 결과</summary>

Summary를 직접 전달하고 실제 JLabel 텍스트를 읽는다. 패널에 보관된 별도 결과를 검사하거나 통계 계산을 다시 실행하는 검사가 아니다. 컴포넌트의 생성·갱신·조회는 EDT에서 수행한다.

| 검사 내용 | 입력 및 기대 결과 | 실행 수 |
| --- | --- | ---: |
| 공통 패널 규격 | 선호 크기 258×512·여백 14px·굵은 제목 16pt | 1 |
| 첫 결과 전 | 다섯 값 모두 `—` | 1 |
| 기본 결과와 long 세대 | 세대 3000000000·생존 120·2.93%·탄생 8·사망 5가 그대로 표시 | 1 |
| 비율 형식 | 0 → 0.00%, 100 → 100.00%, 12.3456 → 12.35%, 0.0244140625 → 0.02% | 4 |
| 미계산 변화량 | 이전 탄생·사망 숫자를 `—`로 교체 | 1 |
| 실제 변화 없음 | hasGenerationDelta=true인 탄생·사망 0을 `0`으로 표시 | 1 |
| 연속 결과 | 세대·생존 수·비율·탄생·사망을 모두 최신 값으로 교체 | 1 |
| 잘못된 갱신 요청 | EDT 밖 호출 거부 / null 결과 거부 및 기존 표시 유지 | 2 |
| 합계 | | 12 |

</details>

<details>
<summary>StatisticsPanelIntegrationTest 13개 — 실제 게임판·표시·탭 결합</summary>

기존 LifeTestSupport와 실제 Life.connectStatistics()를 사용한다. 창 메뉴 등록 및 Load 파일 선택 UI만 대체하며 게임판, 계산, 타이머, 파일 복원과 JLabel 갱신은 실제 코드다. 각 검사 후 Clock 정지·리스너 해제·보드 초기화를 수행한다. StatisticsPanelTestSupport는 테스트 전용 컴포넌트 조회 도우미이며 앱에는 포함되지 않는다.

| 검사 내용 | 기대 결과 | 실행 수 |
| --- | --- | ---: |
| 시작 연결 | 등록 직후 세대 0·생존 0·0.00%·탄생/사망 `—` | 1 |
| 진행 중인 보드에 연결 | 안정 블록 2세대 상태를 유지해 표시, 다음 세대는 3·생존 4·변화량 0 | 1 |
| 정지 상태 편집 | 고립 셀 클릭 → 1·0.02%, 세대 진행 → 사망 1, 재편집 → 변화량 `—` | 1 |
| 실제 단일 진행 메뉴 | 블링커 1세대·생존 3·0.07%·탄생 2·사망 2 | 1 |
| 안정된 블록 | 1~3세대 증가, 생존 4·0.10%·탄생/사망 0 | 1 |
| Clear 메뉴 | 모든 현재값 초기화, 변화량 `—` | 1 |
| 실제 파일 Load | 복원 후 세대 0·생존 3·변화량 `—`, 다음 세대는 탄생 2·사망 2 | 1 |
| 구독 해제 | 이후 클릭·세대 진행에도 기존 표시 유지 | 1 |
| 자동 Swing Timer | 3세대 표시 시 정지, 생존 4·변화량 0. 완료는 CountDownLatch로 대기 | 1 |
| 통계 단독 배치 | 외부에서 만든 탭을 전달, 게임판 512×512와 탭 전체 높이 일치, 간격·겹침·라벨 잘림 없음 | 1 |
| 공용 탭 보존·너비 재계산 | 빈 패널 두 개 뒤에 통계 추가, 기존 패널·선택·리스너 유지, 이전 고정 폭 80px을 새 내용에 맞게 재계산 | 1 |
| 숨겨진 통계·탭 전환 | 다른 탭에서 블링커 2세대 진행 → 통계 탭에 생존 3·탄생 2·사망 2 표시, 결과·비교 기준 유지, 재전환 후 3세대 정상 표시 | 1 |
| 큰 숫자·안내문 배치 | 직접 전달한 세대 3000000000·생존/탄생/사망 4096·100.00%와 안내문이 좁은 패널 안에서 잘리지 않음 | 1 |
| 합계 | | 13 |

</details>

<details>
<summary>이벤트·통계 단위 테스트 36개 — 입력과 기대 결과</summary>

StatisticsTest는 테스트에서 지정한 좌표로 BitSet과 BoardChangeEvent를 만들어 onBoardChanged()에 직접 전달한다. **템플릿 배치·Undo를 실제로 실행한 검사가 아니다.** EDIT·CLEAR·LOAD도 이 클래스에서는 직접 만든 이벤트이며 실제 메뉴·파일 처리를 실행하지 않는다.

블링커 모양·세대 번호도 직접 지정한다. 일부 입력은 집합 차이 계산을 확인하기 위한 상태이며 Life 규칙이나 세대 증가 자체를 실행하지 않는다. Universe 생성이나 정적 메서드 대체 없이 통계 계산을 검증한다.

#### BoardChangeEventTest: 13개 검사

| 검사 내용 | 입력 및 기대 결과 | 실행 수 |
| --- | --- | ---: |
| 전달 정보 보존 | 종류 TEMPLATE, 42세대, 크기 3, 마지막 유효 셀 (2,2)를 그대로 보관 | 1 |
| 초기 빈 상태 허용 | 0세대의 1×1 빈 보드를 정상 생성 | 1 |
| 원본 데이터 변경 방어 | 생성 후 입력 BitSet을 바꿔도 이벤트의 상태 유지 | 1 |
| 반환 데이터 변경 방어 | 조회한 복사본을 바꿔도 이벤트와 다른 조회 결과 유지 | 1 |
| 종류 누락 거부 | null 종류 입력 시 NullPointerException | 1 |
| 생존 데이터 누락 거부 | null BitSet 입력 시 NullPointerException | 1 |
| 음수 세대 거부 | -1, Long.MIN_VALUE 입력 시 IllegalArgumentException | 2 |
| 잘못된 보드 크기 거부 | 0, -1, 46341, Integer.MAX_VALUE 입력 시 IllegalArgumentException | 4 |
| 범위 밖 생존 셀 거부 | 3×3 보드에서 유효 범위 0~8 밖인 9번 비트 입력 시 IllegalArgumentException | 1 |
| 합계 | | 13 |

#### StatisticsTest: 23개 검사

아래 상태와 이벤트는 모두 테스트 코드에서 직접 구성한다. 탄생·사망 미계산은 `hasGenerationDelta == false`로 구분한다.

| 검사 내용 | 입력 및 기대 결과 | 실행 수 |
| --- | --- | ---: |
| 첫 이벤트 수신 전 | 결과가 비어 있고 결과 콜백도 호출되지 않음 | 1 |
| 기본 집계 | 4×4에서 생존 4개, 전달 세대 12 → 생존 4, 비율 25%, 세대 12, 변화량 미계산 | 1 |
| 빈 상태 집계 | 생존 0, 비율 0%, 변화량 미계산 | 1 |
| 전체 생존 상태 집계 | 2×2에서 생존 4개 → 비율 100% | 1 |
| 비교 기준 없는 GENERATION | 첫 수신은 변화량 미계산, 다음 연속 세대부터 비교 | 1 |
| 블링커 모양의 두 상태 비교 | 5×5의 가로 3개와 세로 3개를 전달 → 생존 3, 비율 12%, 탄생 2, 사망 2 | 1 |
| 배치가 같은 연속 세대 | 전달 세대 번호 반영, 탄생·사망 0, 계산 여부 true | 1 |
| 생존 상태에서 빈 상태로 변경 | 생존 3개 → 0개 입력 시 탄생 0, 사망 3 | 1 |
| 빈 상태에서 생존 상태로 변경 | 생존 0개 → 3개 입력 시 탄생 3, 사망 0 | 1 |
| EDIT·TEMPLATE·TEMPLATE_UNDO 이벤트 | 종류별로 변화량 미계산 및 기준 교체 확인, 다음 GENERATION에서 새 기준으로 탄생 1·사망 1 계산 | 3 |
| CLEAR 이벤트 | 전달한 빈 상태와 세대 번호 반영, 다음 GENERATION에서 새 기준 사용 | 1 |
| LOAD 이벤트 | 전달한 상태와 세대 번호 반영, 다음 GENERATION에서 새 기준 사용 | 1 |
| INITIAL 재수신 | 새 상태와 세대 번호로 기존 비교 기준 교체 | 1 |
| 여러 세대 이벤트 연속 수신 | 반복 집계 결과가 맞고, 기존 이벤트의 생존 데이터도 유지됨 | 1 |
| 세대 번호 불연속 | 9세대 이후 8·9·12세대 각각에 대해 변화량 미계산, 기준 교체 후 연속 세대부터 정상 비교 | 3 |
| 보드 크기 변경 | 3×3 → 4×4에서 비율 갱신·변화량 미계산, 이후 같은 크기의 연속 세대부터 비교 | 1 |
| 결과 전달 및 불변성 | 이벤트 3개에 결과 3번 전달, 최근 결과 조회 가능, 이전 결과 값 유지 | 1 |
| 결과 콜백 누락 거부 | 생성자에 null 콜백 입력 시 NullPointerException | 1 |
| 이벤트 누락 거부 | null 이벤트 입력 시 NullPointerException, 결과는 발행하지 않음 | 1 |
| 합계 | | 23 |

</details>

<details>
<summary>UniverseEventsTest 18개 — 실제 게임판 연결</summary>

LifeTestSupport는 테스트 사이에 실제 Universe·Clock Singleton을 함께 사용하되 매번 정지·초기화하고 구독을 해제하도록 준비한다.
메뉴 등록 UI, 파일 선택 창, 오류 알림 창만 대체한다. 실제 Clock, Cell, Statistics, 직렬화·파일 처리는 대체하지 않는다.
기존 UniverseStoreTest도 같은 준비 코드를 사용하도록 바꿨으며 저장 중복 검사의 입력과 기대 결과는 유지했다.
Gradle 실행 설정이나 forkEvery는 바꾸지 않았다.

아래 18개 검사는 모두 통과했다.

| 검사 내용 | 기대 결과 |
| --- | --- |
| 최초 등록 | EDT에서 INITIAL 한 번, 세대 0, 크기 64, 빈 생존 상태 |
| 중복 등록·새 구독자 | 중복 초기 알림 없음, 새 구독이 기존 구독자를 초기화하지 않음, 발행마다 한 번씩 수신 |
| 해제·재등록 | 해제 후 알림 없음, 재등록 시 진행된 현재 세대로 INITIAL 수신 |
| 클릭 좌표와 토글 | 비대칭 좌표·보드 경계 좌표를 row × 64 + column으로 저장, 다시 클릭하면 사망 상태 반영 |
| 무효 마우스 입력 | 보드 밖 클릭과 오른쪽 버튼은 상태 변경·알림 없음 |
| 실제 블링커 진행 | 하위 묶음 경계를 가로질러 정확한 가로·세로 배치 교대, 진행마다 탄생 2·사망 2 |
| 안정된 블록 | 동일 배치로 3세대 진행, 매번 번호 증가·탄생 0·사망 0 |
| 빈 보드 | 빈 상태에서도 진행마다 번호 증가·완료 알림 |
| 진행 후 실제 편집 | 고립 셀 추가 직후 비교 기준 갱신, 다음 실제 세대에서 사망 1 |
| Clear | 빈 상태·세대 0을 한 번 발행, 다음 진행은 1세대 |
| 실제 파일 Load | 복원 상태를 한 번 발행, 세대 0, 다음 세대의 경계 탄생·사망과 정확한 좌표 확인 |
| 파일 읽기 오류 | 직렬화 헤더가 잘못된 파일에서 오류 알림, 기존 보드·세대·통계 유지, 완료 이벤트 없음 |
| Load 취소 | 기존 보드 유지, 완료 이벤트와 오류 창 없음 |
| Store | 파일 출력 성공, 세대 유지, 보드 알림 없음 |
| 이벤트 공유·상태 보호 | 관찰자들은 같은 이벤트를 받지만 복사본 변경으로 다른 관찰자나 과거 이벤트를 훼손하지 못함 |
| 발행 중 해제 | 현재 발행은 일관된 구독자 목록을 사용하고 다음 발행부터 해제 반영 |
| EDT 밖 요청 | 등록·해제·명시적 알림 호출을 IllegalStateException으로 거부. Clock.tick()의 EDT 전달은 ClockIntegrationTest에서 검증 |
| 필수 인자 누락 | null 리스너·작업 종류를 거부 |

</details>

<details>
<summary>ClockIntegrationTest 6개·isAliveAt 조회 1개 — 실행과 기대 결과</summary>

아래 6개 검사는 모두 통과했다.

| 검사 내용 | 기대 결과 |
| --- | --- |
| 실제 단일 진행 메뉴 콜백 | Clock.tick() → Universe의 Clock.Listener.tick()을 거쳐 EDT에서 1세대 알림 |
| 자동 Swing Timer | 실제 타이머가 세대 1·2·3을 EDT에서 순서대로 발행하고 정지 후 추가 진행 없음 |
| 첫 알림 전 Halt | 대기 중인 자동 진행이 취소되어 세대 알림 없음 |
| 실행 속도 교체 | 새 간격으로 진행하고 이전 예약이 남아 추가 세대를 만들지 않음 |
| 메뉴 선택 중 tick | 선택 중에는 진행하지 않고 선택 해제 후 다음 tick에서 진행 |
| EDT 밖 tick 요청 | EDT에 예약되어 세대 알림이 한 번 발생 |

자동 타이머 검사는 CountDownLatch와 EDT의 일회성 지연 표식으로 완료·정지를 확인하며, EDT를 Thread.sleep으로 막지 않는다.
재사용한 isAliveAt 검사는 4×4 중첩 보드의 (3,2)를 살리고 (2,3)은 죽어 있음을 확인해 행·열 및 중첩 좌표 변환을 검증한다.
이 기존 시계·조회 검사에는 템플릿·Undo·프리즈·색상·사운드 연동이나 패널 표시가 포함되지 않는다. 패널 연결 검증은 위 신규 StatisticsPanelIntegrationTest에 정리했다.

</details>

HTML 보고서: `build/reports/tests/test/index.html`. 빌드 산출물과 보고서는 Git에 포함하지 않는다.

## 변경 기록

<details>
<summary>2026-11-17 · 최신 dev 병합과 기존 기능·통계 연결</summary>

통계 `4021231`에 dev `a480113`을 병합했다. 아래는 커밋 전 작업본의 구현·검증 기록이다.

- 충돌 7개 파일을 해결했다. Cell·NeighborhoodTest의 같은 조회 계약은 하나로 유지하고, Neighborhood의 Load 캐시·화면 갱신 처리는 dev를 유지했다.
- Universe는 dev의 익명 Clock.Listener.tick() 본문을 유지하고 transition() 직후 generation 증가·GENERATION 알림을 추가했다. 기존 편집 보호, 매 세대 셀 나이 갱신, 조건부 보드 repaint, 사운드 알림을 보존했다.
- 별도 advanceOneGeneration()을 제거했다. 이를 직접 호출하던 UniverseEventsTest·StatisticsPanelIntegrationTest는 Clock.tick()으로 진행하도록 바꿨다. 제거된 메서드의 EDT 예외 검사만 삭제하고, Clock의 EDT 전달 및 자동·단일 진행 검사는 유지했다.
- 일반 드래그 완료와 기존 패턴 PropertyChange 신호를 통계에 연결했다. 사운드 구현과 통계 이벤트·계산·표시 클래스는 그대로 유지했다.
- Life에 실제 패턴·통계 공용 탭을 조립하고, 탭 전환으로 숨겨지는 패턴 입력을 종료한다.
- Clock은 dev의 구현으로 맞췄으며 EDT 밖 tick 거부 검사를 EDT 전달 검사로 변경했다.
- 기존 테스트 230개와 새 통합 테스트 12개가 모두 통과했다. 코드 병합과 충돌 해결은 완료했으나 머지 커밋·push는 하지 않았다.

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
