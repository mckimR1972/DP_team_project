# 통계·사운드 공통 이벤트 연결 변경

문서 갱신: 2026-11-18. dev 병합 커밋 `4a49d0a`와 현재 `feature/statistics` 작업본을 비교했다. 이 문서는 공통 이벤트 전환 내용과 검증 결과를 정리한다.

**사운드 패키지에서 변경한 파일은 SoundEffects.java 하나이며, Git diff 기준 22줄 추가·8줄 삭제다.** 아래에 모든 변경 지점과 실제 diff를 담았다. 공통 발행부인 Universe·BoardChangeEvent·BoardChangeListener도 수정했으며, 통계 내부의 집계 변경은 [statistics.md](statistics.md)에 별도로 정리했다.

## 변경 이유

같은 Universe의 완료 상태를 서로 다른 리스너와 발행 경로로 보내던 구조를 `BoardChangeListener` 하나로 통일했다. Universe가 같은 `BoardChangeEvent`를 등록된 Observer들에게 전달하고, Statistics와 SoundEffects가 필요한 정보를 선택하여 처리한다.

현재 상태와 탄생·사망을 한 이벤트에 담으며 전후 비교는 Universe에서 한 번 수행한다. 기존 사운드의 배열·좌표 목록·음 생성 규칙을 유지하면서 구독과 상태 입력을 연결했다.

## SoundEffects.java의 변경 지점 전체

대상 파일: [SoundEffects.java](../src/com/holub/life/sound/SoundEffects.java)

| 위치 | 변경 전 | 변경 후 | 이유 |
| --- | --- | --- | --- |
| import·클래스 선언 | `implements Universe.GenerationListener` | BoardChangeEvent·BoardChangeListener import 추가, `implements BoardChangeListener` | 통계와 같은 수신 계약 사용 |
| 필드 | 최신 판 상태 보관 필드 없음 | `private volatile boolean[][] latestCells` 추가 | EDT에서 받은 완성 상태를 재생선 스레드에 전달 |
| install()의 등록 | `universe.addGenerationListener(effects)` | `universe.addBoardChangeListener(effects)` | 공통 구독으로 전환하고 등록 즉시 INITIAL 수신 |
| 생성자·install()의 예약 | 생성자 끝에서 `this::playStep`을 예약 | 해당 예약을 생성자에서 제거하고 install()의 구독 직후 `effects::playStep` 예약 | 초기 스냅샷을 받은 뒤 재생선을 시작. 지연 0·주기 STEP_MS(150ms)는 유지 |
| 새 onBoardChanged() | 별도 메서드 없음 | 최신 배열 저장 → 종류 확인 → 탄생/사망 확인 → 기존 generationChanged 호출 | 모든 작업에서 상태를 갱신하고 변화가 있는 세대만 효과음 처리 |
| generationChanged()의 접근 범위 | public 외부 수신 메서드 | private 내부 처리 메서드 | 외부 수신은 onBoardChanged로 통일하고 기존 음 생성 본문 재사용 |
| generationChanged()의 판 크기 입력 | `mapper.notesFor(births, universe.widthInCells(), ...)` | `mapper.notesFor(births, latestCells.length, ...)` | 전달받은 스냅샷의 크기 사용. 본문에서 변경한 계산 입력은 이 한 곳 |
| playStep()의 상태 입력 | `boolean[][] cells = universe.liveCells()` | `boolean[][] cells = latestCells;`와 null이면 종료하는 검사 | 재생선 스레드가 실제 Cell 계층을 다시 읽지 않도록 연결 |
| boardDiedOut()의 상태 입력 | `countLive(universe.liveCells())` | `countLive(latestCells)` | 현재 이벤트의 완료 상태로 전멸 확인 |

`SoundEffects.install(universe)`의 호출 방법은 같으므로 Life의 설치 코드는 그대로다. Universe 참조는 재생선 표시 요청과 설정 창의 소유 창을 찾는 용도로 계속 사용한다.

## 등록과 재생선 시작 순서

기존에는 생성자가 재생선 예약까지 시작하고, install()에서 세대 리스너를 등록했다. 현재는 아래 순서다.

```java
SoundEffects effects = new SoundEffects(universe, synthesizer);
universe.addBoardChangeListener(effects); // 여기서 INITIAL을 동기 수신

effects.noteScheduler.scheduleAtFixedRate(
    effects::playStep, 0, STEP_MS, TimeUnit.MILLISECONDS);
effects.createMenus();
```

생성자에서는 여전히 MIDI 채널 연결, 스케줄러 생성, 악기 적용과 악기 변경 콜백을 구성한다. 이동한 것은 주기적 playStep 예약뿐이다. 구독 등록은 EDT에서 실행해야 하며 현재 Life의 초기화는 이미 EDT다.

## 새 수신 메서드와 반응 조건

추가한 수신 메서드의 전체 동작은 다음과 같다. 들여쓰기만 읽기 쉽게 정리했다.

```java
@Override
public void onBoardChanged(BoardChangeEvent event) {
    latestCells = event.copyAliveCells();
    if (event.kind() != BoardChangeEvent.Kind.GENERATION) return;

    List<Point> births = event.copyBirths();
    List<Point> deaths = event.copyDeaths();
    if (births.isEmpty() && deaths.isEmpty()) return;

    generationChanged(births, deaths);
}
```

| 이벤트·상황 | 최신 판 저장 | 기존 generationChanged 실행 |
| --- | --- | --- |
| INITIAL | 수행 | 생략 |
| EDIT·TEMPLATE·TEMPLATE_UNDO·CLEAR·LOAD | 수행 | 생략 |
| 탄생 또는 사망이 있는 GENERATION | 수행 | 실행 |
| 변화 없는 GENERATION | 수행 | 생략 |
| Sound Off 상태 | 수행 | 변화 있는 세대에서는 호출되지만 기존 enabled 검사에서 즉시 종료 |

Off 중에도 최신 상태를 받으므로 다시 On으로 바꾸면 재생선이 현재 배치를 사용한다. 변화 없는 세대는 기존 효과음 처리 전에 걸러 반복 감지·마무리 화음을 실행하지 않는다. 전멸은 사망 목록이 있는 실제 전멸 세대에 기존 조건대로 처리하며, 이후 빈 판의 세대마다 화음을 반복하지 않는다.

latestCells는 volatile 참조이며 이벤트가 반환한 배열을 저장한 뒤 수정하지 않는다. 다음 이벤트에서 참조 전체를 교체하고, playStep은 스텝 시작 때 읽은 참조 하나로 계산한다. volatile이 배열 원소의 임의 변경까지 보호하는 것은 아니므로, 저장한 배열을 직접 수정하지 않는 규칙을 유지한다.

재생선의 150ms 주기, 게임 정지 중 연주, 음 선택 계산은 유지한다. 편집·배치·Undo·Clear·Load의 완료 이벤트가 도착하면 이후 스텝은 해당 상태를 읽는다.

## 공통 발행부에서 바뀐 연결

사운드 전용으로 사용하던 아래 항목은 Universe에서 제거했다.

- `GenerationListener`
- `addGenerationListener()`
- `generationPublisher`
- `publishChanges(before)`

Universe의 기존 Clock.Listener.tick() 본문은 유지한다. 이전에는 생존 변화가 예상될 때만 before를 수집하고, 변화가 있으면 사운드 전용 publishChanges를 호출했다. 현재는 각 세대의 transition 전에 before를 수집하고, 완료 후 `notifyBoardChanged(GENERATION, before)`로 공통 이벤트를 발행한다. 안정된 판도 통계에 세대 완료를 알리고, 사운드가 빈 변화 목록을 보고 효과음을 생략한다.

탄생·사망 비교는 `createBoardChangeEvent(kind, before)`에서 진행 후 상태와 비교하여 List<Point>로 만든다. 행→열 순회와 Point(x=열,y=행)는 기존 사운드 발행과 같다. `addBoardChangeListener()`로 등록하고 `removeBoardChangeListener()`로 해제한다. 같은 발행에서는 같은 이벤트 인스턴스를 전달한다.

공개 `notifyBoardChanged(kind)`는 편집·복원 완료용이며, 이전 상태 없는 GENERATION 직접 호출은 거부한다. 세대 진행 요청은 Clock을 사용한다. 별도 advanceOneGeneration이나 기존 사운드 인터페이스를 감싸는 호환 경로는 없다.

## 전달 데이터와 기존 호출 변경

| 메서드 | 반환 정보 |
| --- | --- |
| kind() | INITIAL, GENERATION, EDIT, TEMPLATE, TEMPLATE_UNDO, CLEAR, LOAD |
| generation() | 작업 완료 시 세대 번호 |
| boardSize() | 정사각형 판 한 변의 셀 수 |
| copyAliveCells() | 현재 boolean[][], [행][열] |
| copyBirths() | 이번 세대에 태어난 List<Point>, x=열·y=행 |
| copyDeaths() | 이번 세대에 죽은 List<Point>, x=열·y=행 |

배열의 각 행, 목록과 Point를 복사하므로 수신자가 변경해도 다른 Observer에게 영향을 주지 않는다. 사운드에서 BitSet 변환을 수행할 필요는 없다. GENERATION은 한 세대의 실제 차이이고, 다른 종류의 변화 목록은 비어 있다.

기존 문서의 구독 인터페이스·등록 메서드·수신 메서드 이름은 위 표에 맞춰 바꾸면 된다. 테스트에서 `effects.generationChanged(births, deaths)`를 직접 호출했다면 이제 `effects.onBoardChanged(event)`를 호출한다. 예를 들어 이미 확보한 SoundEffects 인스턴스에 전달할 입력은 다음과 같다.

```java
boolean[][] cells = new boolean[64][64];
cells[10][20] = true;
BoardChangeEvent event = new BoardChangeEvent(
    BoardChangeEvent.Kind.GENERATION, 1, 64, cells,
    List.of(new Point(20, 10)), // 탄생: 현재 배열에서도 살아 있음
    List.of(new Point(19, 10))  // 사망: 현재 배열에서는 죽어 있음
);
effects.onBoardChanged(event);
```

이벤트 생성 시 전체 완료 상태와 변화 목록이 일치해야 한다. GENERATION 번호는 1 이상이고, 중복/범위 밖 좌표는 허용하지 않는다. 이 예시는 수신 로직 검사에 사용할 입력이며 실제 게임판을 진행시키는 코드는 아니다.

## 유지한 코드

사운드 패키지의 NoteMapper·RepeatTracker·SoundSettings·SoundSettingsDialog는 수정하지 않았다. SoundEffects 안에서도 applyInstruments·playEnding·strumDelay·updatePad·stopPad·play·playPercussion 본문은 그대로다.

generationChanged의 enabled 검사, 반복 감쇠, 전멸 분기, 최소 재생 간격, 생성음/대량 탄생/타악기 처리도 유지했다. 판 크기를 가져오는 입력만 최신 스냅샷 길이로 변경했다. 재생선의 상태 입력 변경은 위에 별도로 명시했다.

## SoundEffects.java 전체 실제 diff

아래는 기준 커밋 4a49d0a 대비 실제 변경 전체다. 파일 전체를 복사하지 않고 변경된 코드와 주변 문맥만 담았다.

<details>
<summary>22줄 추가·8줄 삭제 — 전체 diff 보기</summary>

```diff
diff --git a/src/com/holub/life/sound/SoundEffects.java b/src/com/holub/life/sound/SoundEffects.java
index 721ed72..4643318 100644
--- a/src/com/holub/life/sound/SoundEffects.java
+++ b/src/com/holub/life/sound/SoundEffects.java
@@ -15,6 +15,8 @@ import javax.sound.midi.Synthesizer;
 import javax.swing.SwingUtilities;
 
 import com.holub.life.Universe;
+import com.holub.life.BoardChangeEvent;
+import com.holub.life.BoardChangeListener;
 import com.holub.life.sound.SoundSettings.Feature;
 import com.holub.ui.MenuSite;
 
@@ -33,7 +35,7 @@ import com.holub.ui.MenuSite;
  * 각 기능은 {@link SoundSettings}에서 끄거나 악기를 바꿀 수 있다.
  * {@link #install}로 붙이며, 붙이지 않아도 게임은 똑같이 동작한다.
  */
-public final class SoundEffects implements Universe.GenerationListener
+public final class SoundEffects implements BoardChangeListener
 {
 	private static final int PAN_CONTROLLER     = 10;
 	private static final int EXPRESSION_CONTROLLER = 11;
@@ -64,6 +66,7 @@ public final class SoundEffects implements Universe.GenerationListener
 	private final RepeatTracker		repeats = new RepeatTracker();
 	private final ScheduledExecutorService noteScheduler;
 
+	private volatile boolean[][] latestCells; // 이벤트의 독립 복사본; 발행 후 수정하지 않는다
 	private volatile boolean enabled = true;
 	private volatile int	 chord	 = 0;
 	private long  lastPhraseNanos = 0;
@@ -80,7 +83,9 @@ public final class SoundEffects implements Universe.GenerationListener
 			synthesizer.open();
 
 			SoundEffects effects = new SoundEffects( universe, synthesizer );
-			universe.addGenerationListener( effects );
+			universe.addBoardChangeListener( effects ); // INITIAL을 먼저 받아 재생선 상태를 준비한다
+			effects.noteScheduler.scheduleAtFixedRate( effects::playStep, 0, STEP_MS,
+													  TimeUnit.MILLISECONDS );
 			effects.createMenus();
 		}
 		catch( MidiUnavailableException e )
@@ -114,8 +119,6 @@ public final class SoundEffects implements Universe.GenerationListener
 		(	() -> noteScheduler.execute( this::applyInstruments )
 		);
 
-		noteScheduler.scheduleAtFixedRate( this::playStep, 0, STEP_MS,
-										   TimeUnit.MILLISECONDS );
 	}
 
 	private void applyInstruments()
@@ -172,7 +175,8 @@ public final class SoundEffects implements Universe.GenerationListener
 			return;
 		}
 
-		boolean[][] cells  = universe.liveCells();
+		boolean[][] cells = latestCells;
+		if (cells == null) return;
 		int columnsPerStep = cells.length / STEPS_PER_SWEEP;
 		int firstColumn	   = step * columnsPerStep;
 
@@ -207,7 +211,17 @@ public final class SoundEffects implements Universe.GenerationListener
 		return count;
 	}
 
-	public void generationChanged( List<Point> births, List<Point> deaths )
+	/** 모든 작업은 재생선 상태를 갱신하고, 실제 세대 변화만 효과음을 낸다. */
+	@Override public void onBoardChanged(BoardChangeEvent event)
+	{	latestCells = event.copyAliveCells();
+		if (event.kind() != BoardChangeEvent.Kind.GENERATION) return;
+		List<Point> births = event.copyBirths();
+		List<Point> deaths = event.copyDeaths();
+		if (births.isEmpty() && deaths.isEmpty()) return;
+		generationChanged(births, deaths);
+	}
+
+	private void generationChanged( List<Point> births, List<Point> deaths )
 	{
 		if( !enabled )
 			return;
@@ -228,7 +242,7 @@ public final class SoundEffects implements Universe.GenerationListener
 		lastPhraseNanos = now;
 
 		List<NoteMapper.Note> notes = mapper.scaled
-		(	varied( mapper.notesFor( births, universe.widthInCells(),
+		(	varied( mapper.notesFor( births, latestCells.length,
 									 currentChord() ) ),
 			gain
 		);
@@ -253,7 +267,7 @@ public final class SoundEffects implements Universe.GenerationListener
 	}
 
 	private boolean boardDiedOut( List<Point> births )
-	{	return births.isEmpty() && countLive( universe.liveCells() ) == 0;
+	{	return births.isEmpty() && countLive( latestCells ) == 0;
 	}
 
 	private void playEnding()
```

</details>

## 검증과 담당자 확인

2026-11-17 실행한 전체 JUnit은 **266개 통과, 실패·오류·건너뜀 0**이다. 이번 2026-11-18 갱신은 문서만 수정했으며 테스트를 다시 실행하지 않았다.

[SoundEffectsIntegrationTest](../test/com/holub/life/SoundEffectsIntegrationTest.java) 15개는 실제 SoundEffects.install·Universe·Statistics를 연결하고, MIDI 장치·채널, 예약 실행, 메뉴 등록 UI를 대역으로 제어한다.

| 확인한 동작 | 검사 수 |
| --- | ---: |
| INITIAL 수신 후 재생선 시작 | 1 |
| 실제 블링커의 통계 집계와 생성음·타악기 예약 | 1 |
| 안정된 블록·빈 판에서 세대 효과음 생략 | 1 |
| INITIAL·EDIT·TEMPLATE·TEMPLATE_UNDO·CLEAR·LOAD의 상태 갱신 | 6 |
| Off 중 편집 후 On에서 최신 상태 사용 | 1 |
| 전멸 화음과 이후 빈 세대의 무음 | 1 |
| 스트럼·음 길이·타악기 예약 간격 유지 | 1 |
| 대량 탄생의 추가 악기 레이어 | 1 |
| 별도 사운드 스레드의 독립 스냅샷 사용 | 1 |
| MIDI 사용 불가 시 통계 진행 유지 | 1 |

6종류별 수신 처리, 생성음 간격, 대량 탄생 검사는 이벤트를 직접 구성한다. 실제 템플릿·Undo 등의 완료 경로는 기존 MergedFeaturesIntegrationTest에서 검사한다. 기존 음 선택·반복 감지·설정 JUnit 29개도 전체 실행에 포함되어 통과했다.

담당자는 변경된 구독 이름과 입력 계약을 문서·테스트에 반영하고, 실제 MIDI 장치에서 기존 음색·음량·재생선·On/Off·설정 변경이 의도대로 동작하는지 시연으로 확인하면 된다. 실제 장치 청음과 설정 창 수동 조작은 아직 검증하지 않았다.
