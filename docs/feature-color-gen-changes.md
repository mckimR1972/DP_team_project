# feature/color-gen 코드 변경 내용 요약

- 브랜치: `feature/color-gen` (dev 커밋 `c49ca39`에서 분기)
- 기능: 세대에 따라 살아 있는 셀의 색이 바뀌고 순환한다. Color 메뉴로 켜고 끄며, 처음에는 꺼져 있다.
- 설계: 팀 설계 문서 「세대에 따른 셀 색 순환 기능」

이 브랜치에서 바뀐 코드를 변경 단위마다 기록한다. 변경마다 요약, 바꾼 이유, 변경 내용, 검증 결과를 적는다.

## 변경 목록

| 번호 | 변경 | 바뀌는 파일 | 상태 |
| --- | --- | --- | --- |
| 1 | 테스트 클래스마다 JVM 분리 | `build.gradle` | 반영 (2026-11-11) |
| 2 | 색 규칙 타입 3개 | `CellColorPolicy.java`, `FixedColorPolicy.java`, `CyclingColorPolicy.java` (새 파일) | 반영 (2026-11-11) |
| 3 | Resident의 나이 계산과 색 규칙 | `Resident.java` | 반영 (2026-11-12) |
| 4 | Universe의 틱 처리와 화면 갱신 | `Universe.java`, `Resident.java` | 반영 (2026-11-12) |
| 5 | Universe의 Color 메뉴 | `Universe.java`, `Resident.java` | 반영 (2026-11-12) |

번호는 설계 문서 6절 "구현 순서"의 번호와 같다.

## 1. 테스트 클래스마다 JVM 분리

### 요약

`build.gradle`의 테스트 설정에 `forkEvery = 1`을 넣어, 테스트 클래스마다 JVM을 새로 띄운다. Universe가 Singleton이라서, Universe를 테스트하는 클래스가 둘 이상이면 한 JVM 안에서는 먼저 실행된 클래스만 준비에 성공하기 때문이다. 게임 코드(`src/`)와 기존 테스트 코드는 바꾸지 않았다.

### 문제: Universe는 JVM에서 한 번만 만들어진다

```java
// Universe.java
private static final Universe theInstance = new Universe();   // JVM에서 한 번만 실행된다
public static Universe instance() { return theInstance; }      // 그 뒤로는 같은 객체를 돌려준다
```

- Universe 생성자는 메뉴(`MenuSite.addLine()`)와 시계 리스너(`Clock.instance().addClockListener()`)를 등록한다. 이 등록도 JVM에서 한 번만 일어난다.
- `UniverseStoreTest`는 창을 띄우지 않으려고, Universe가 만들어지는 순간 Mockito로 `MenuSite`와 `Clock`을 대신하고, 이때 등록되는 메뉴 콜백을 기록해 둔다(`@BeforeAll`).
- Gradle은 기본 설정에서 모든 테스트 클래스를 한 JVM에서 차례로 실행한다. 이런 방식으로 Universe를 준비하는 테스트 클래스가 둘이면, 먼저 실행된 클래스만 등록 순간을 기록한다. 나중 클래스가 `Universe.instance()`를 부르면 이미 만들어진 객체만 돌아오고 생성자가 다시 돌지 않으므로, 아무것도 기록되지 않는다.

```java
// UniverseStoreTest.java의 @BeforeAll (줄임)
try (MockedStatic<MenuSite> menus = mockStatic(MenuSite.class);
     MockedStatic<Clock> clocks = mockStatic(Clock.class)) {
    menus.when(() -> MenuSite.addLine(any(), eq("Grid"), anyString(), any(ActionListener.class)))
         .thenAnswer(invocation -> { gridActions.put(...); return null; });
    universe = Universe.instance();          // 처음 부를 때만 생성자가 돌고, 메뉴 콜백이 기록된다
}
assertNotNull(gridActions.get("Clear"));     // 기록이 없으면 여기서 실패한다
```

### 언제 문제가 되는가

- 지금 dev와 이 브랜치에는 Universe 테스트 클래스가 `UniverseStoreTest` 하나뿐이라 실패하지 않는다.
- 색 순환 기능은 블록만 있는 판에서도 색이 바뀌는지 확인하려고 Universe의 실제 틱 처리를 테스트한다. 이 `UniverseColorTest`는 변경 4\~5에서 추가할 예정이며, 같은 방법으로 Universe를 준비하므로 문제가 생긴다.
- 다른 기능 브랜치에서 같은 방법의 Universe 테스트를 추가해도 같은 문제가 생긴다.

### 확인한 결과

구현 예정인 색 기능 코드와 `UniverseColorTest`를 붙인 시험용 사본에서, 두 클래스의 실행 순서를 바꿔 가며 한 JVM으로 실행했다.

| 실행 순서 | 통과 | `@BeforeAll`에서 실패 | 실패 메시지 |
| --- | --- | --- | --- |
| UniverseColorTest → UniverseStoreTest | UniverseColorTest | UniverseStoreTest | `expected: not <null>`: Clear 메뉴 콜백이 기록되지 않음 |
| UniverseStoreTest → UniverseColorTest | UniverseStoreTest | UniverseColorTest | `Wanted but not invoked: clock.addClockListener(...)`: 시계 리스너 등록을 보지 못함 |

- 먼저 실행된 클래스만 통과했다. 실패한 클래스는 `@BeforeAll`에서 멈춰 테스트가 하나도 실행되지 않았고, 보고서에는 실패 1개로 나왔다.
- 실행 순서는 Gradle이 정하므로, 어느 쪽이 실패할지 테스트 코드로 정할 수 없다.
- 같은 사본에 `forkEvery = 1`을 넣으면 두 순서 모두에서 테스트 37개가 통과했다.

### 해결 방법과 대안

| 대안 | 방법 | 판단 |
| --- | --- | --- |
| 테스트 클래스마다 JVM 분리 | `build.gradle`에 `forkEvery = 1` 한 줄을 넣는다 | 선택 |
| Universe 준비 코드 공유 | 테스트 도우미 클래스 하나가 Universe를 한 번만 만들고, 메뉴 콜백과 시계 리스너를 보관한다. Universe를 쓰는 테스트 클래스는 모두 이 도우미를 쓴다 | 제외 |
| Universe 코드 변경 | Singleton 구조를 없애거나, 테스트에서 Universe를 다시 만들 수 있는 메서드를 넣는다 | 제외 |

**JVM 분리를 선택한 이유**

- 기존 `UniverseStoreTest`를 고치지 않는다.
- 다른 기능 브랜치에서 Universe 테스트를 추가해도 같은 설정으로 해결된다. 테스트를 쓰는 사람이 따로 지켜야 할 규칙이 없다.
- 테스트 클래스마다 JVM이 새로 뜨므로 static 상태가 테스트 클래스 사이로 넘어가지 않는다. Universe 객체와, 색 순환 기능에서 Resident에 두는 색 규칙이 여기에 해당한다.

**준비 코드 공유를 제외한 이유**

- `UniverseStoreTest`가 도우미를 쓰도록 고쳐야 한다.
- 모든 테스트 클래스가 Universe의 판 하나와 색 규칙을 함께 쓴다. 테스트마다 판을 비우고 색 규칙을 되돌려야 하고, 하나라도 빠뜨리면 다른 클래스의 결과가 바뀐다.
- Universe 테스트를 새로 쓰는 사람이 반드시 이 도우미를 써야 한다. 모르고 직접 준비하면 같은 문제가 다시 생긴다.

**Universe 코드 변경을 제외한 이유**

- Universe의 Singleton 구조는 원본 설계다. 테스트 때문에 바꾸면 이 기능의 범위를 넘어 기존 구조를 바꾸게 된다.
- Universe를 다시 만드는 메서드는 테스트에서만 쓰인다. 테스트만을 위한 코드가 실제 코드에 들어가게 된다.

**선택한 방법의 비용**

- 테스트 클래스마다 JVM을 새로 띄우므로 테스트 시간이 늘어난다. 이 브랜치(테스트 클래스 3개)에서 약 5초가 약 8초가 됐다. 테스트 클래스가 하나 늘 때마다 JVM 하나를 띄우는 시간(1\~2초)이 더 든다.
- `gradlew test`처럼 Gradle로 실행할 때만 적용된다. IDE 자체의 JUnit 실행기로 여러 테스트 클래스를 한 번에 실행하면 한 JVM에서 실행되어 같은 실패가 날 수 있다. 이때는 Gradle로 실행하거나 테스트 클래스를 하나씩 실행한다.

### 변경 내용

```diff
 tasks.withType(Test).configureEach {
     useJUnitPlatform()
     systemProperty 'java.awt.headless', 'true'
     // Static mocking replaces the file chooser, menus and clock in the Store test.
     jvmArgs "-javaagent:${configurations.mockitoAgent.asPath}"
+    // Universe is a singleton that registers its menus and clock listener only
+    // once per JVM, so every test class that captures them needs its own JVM.
+    forkEvery = 1
     testLogging {
```

### 검증

| 항목 | 바꾸기 전 | 바꾼 뒤 |
| --- | --- | --- |
| 테스트 결과 (`gradlew test`) | 32개 통과 | 32개 통과 |
| 테스트용 JVM 수 | 1개 | 3개 (테스트 클래스마다 1개) |
| 테스트 시간 (`gradlew test --rerun`, 번갈아 3회 실행 중 뒤의 2회) | 약 5초 | 약 8초 |

- 바꾼 뒤 `--warning-mode all`로 실행했을 때 사용 중단(deprecation) 경고는 없었다.
- 테스트용 JVM 수는 `--info` 출력에서 `Gradle Test Executor` 프로세스가 시작된 횟수로 셌다.
- 환경: Gradle 9.5.1(래퍼), JDK 17, JUnit Jupiter 6.0.3, Mockito 5.23.0, 2026-11-11.

## 2. 색 규칙 타입 3개

### 요약

색 규칙을 Strategy 패턴으로 분리하는 데 필요한 타입 3개를 추가했다. 이 변경에서는 게임 코드가 아직 새 타입을 쓰지 않으므로 게임 동작은 그대로다. 변경 3에서 Resident가, 변경 4와 5에서 Universe가 이 타입을 쓴다.

| 파일 | GoF Strategy에서의 역할 | 내용 |
| --- | --- | --- |
| `src/com/holub/life/CellColorPolicy.java` (새 파일) | Strategy | `colorOf(int age)`: 나이에 따른 살아 있는 셀의 색. `changesWithAge()`: 나이에 따라 색이 바뀌는 규칙인지 |
| `src/com/holub/life/FixedColorPolicy.java` (새 파일) | ConcreteStrategy | 나이와 상관없이 한 가지 색. 원본의 빨강을 유지할 때 쓴다 |
| `src/com/holub/life/CyclingColorPolicy.java` (새 파일) | ConcreteStrategy | period 세대마다 다음 색, 마지막 색 다음에는 처음 색 |
| `test/com/holub/life/CellColorPolicyTest.java` (새 파일) | 테스트 | 아래 테스트 5개 |

### 바꾼 이유

설계 문서 5절 결정 2(색을 정하는 구조)와 6절 "색 규칙 클래스의 세부 사항"을 따른다.

- 두 구현은 불변 객체다. `final` 클래스이고 필드는 `private final`이며, `CyclingColorPolicy`는 색 배열을 복사해 보관한다. 4,096개 셀이 규칙 객체 하나를 함께 쓰기 때문이다.
- 잘못된 값은 생성자에서 `IllegalArgumentException`으로 거부한다. 검사하지 않으면 그릴 때가 되어서야 예외가 나거나, null 색이 오류 없이 엉뚱한 색으로 칠해진다.
- `CyclingColorPolicy`는 색이 2개 이상이어야 한다. 색이 1개면 색이 바뀌지 않는데도 `changesWithAge()`가 true가 되기 때문이다.
- setter, getter, `equals()` 등은 쓰는 곳이 없어 만들지 않았다. 테스트는 게임이 쓰는 생성자, `colorOf()`, `changesWithAge()`만 쓴다.

### 테스트

| 테스트 메서드 | 확인 내용 | 실행 수 |
| --- | --- | --- |
| `fixedPolicyReturnsTheSameColorForEveryAge` | 나이 0, 9, 10, 30, 1000에서 모두 빨강 | 5 |
| `cyclingPolicyStepsEveryTenGenerationsAndWrapsAround` | 나이 0·9는 빨강, 10·19는 파랑, 20·29는 초록, 30은 다시 빨강, 1000은 파랑 | 8 |
| `onlyTheCyclingPolicyChangesWithAge` | 고정 색 규칙은 `changesWithAge()`가 false, 순환 규칙은 true | 1 |
| `cyclingPolicyKeepsItsOwnCopyOfTheColors` | 생성자에 넘긴 배열을 나중에 바꿔도 색이 그대로 | 1 |
| `policiesRejectInvalidArguments` | 고정 색 null, 간격 0, 간격 −10, 색 0개, 색 1개, 색 배열 null, null 색 포함에서 모두 `IllegalArgumentException` | 7 |

### 검증

| 항목 | 결과 |
| --- | --- |
| 전체 테스트 (`gradlew test --rerun`) | 54개 통과 (기존 32개, 새 22개), 실패 0 |
| 테스트용 JVM 수 | 4개 (테스트 클래스마다 1개) |
| 결함을 일부러 넣은 사본 | 색 배열을 복사하지 않게 하고 색 1개를 허용하게 바꾸자, `cyclingPolicyKeepsItsOwnCopyOfTheColors`와 "only one color" 경우 2개만 실패 |

- `--warning-mode all`로 실행했을 때 Gradle 사용 중단(deprecation) 경고는 없었다.
- 결함을 넣은 확인은 시험용 사본에서만 했고, 저장소의 코드는 바꾸지 않았다.
- 환경: Gradle 9.5.1(래퍼), JDK 17, JUnit Jupiter 6.0.3, 2026-11-11.

## 3. Resident의 나이 계산과 색 규칙

### 요약

셀마다 나이를 세고, 살아 있는 셀을 모든 셀이 함께 쓰는 색 규칙의 색으로 그리게 했다. 색 규칙의 처음 값은 원본 빨강의 고정 규칙이고 게임 안에 규칙을 바꾸는 곳이 아직 없으므로, 게임 화면은 dev와 같다.

| 파일 | GoF Strategy에서의 역할 | 내용 |
| --- | --- | --- |
| `src/com/holub/life/Resident.java` | Context | 모든 셀이 함께 쓰는 색 규칙 참조(static)와 `setColorPolicy()`, 셀마다의 `age`, `transition()`의 나이 계산, `redraw()`가 규칙에서 받는 색, `userClicked()`와 `transfer()` Load의 `age = 0` |
| `test/com/holub/life/CellAgeColorTest.java` (새 파일) | 테스트 | 아래 테스트 7개 |
| `test/com/holub/life/DefaultCellColorTest.java` (새 파일) | 테스트 | 아래 테스트 1개 |

### 바꾼 이유

설계 문서 5절 결정 1(나이를 세는 곳), 결정 3(모든 셀이 색 규칙을 함께 쓰는 방법), 결정 4(나이는 저장하지 않음)와 6절 "Resident와 Universe 변경의 세부 사항"을 따른다.

- 나이는 확정 단계인 `transition()`에서 센다. 확정 전후 모두 살아 있으면 1 늘리고, 아니면 0으로 만든다. `transition()`의 반환값(생사가 바뀌었는가)은 그대로다.
- 색 규칙 참조는 Resident 클래스에 하나만 둔다(static). 그래서 `new Resident()`, `create()`, 판을 만드는 코드는 그대로다.
- `setColorPolicy()`는 같은 패키지에서만 부를 수 있고 null을 거부한다. 부르는 곳이 같은 패키지의 Universe와 테스트뿐이기 때문이다.
- 색 규칙 참조에는 아직 `volatile`을 붙이지 않았다. 지금은 규칙을 바꾸는 쪽과 읽는 쪽이 모두 판을 그리는 스레드다. 시계 스레드가 규칙을 읽기 시작하는 변경 4에서 붙인다.
- 나이를 0으로 되돌리는 곳은 셀이 살아나는 길마다 한 곳씩이다. 태어날 때는 `transition()`의 계산, 클릭은 `userClicked()`, 불러오기는 `transfer()`의 Load다. `clear()`는 dev 그대로 둔다. 죽은 셀의 나이는 그리지도 저장하지도 않고, 다시 살아날 때 위 세 곳 중 하나가 0으로 덮어쓰기 때문이다.
- `setColorPolicy()`는 변경 5에서 Color 메뉴가 부르기 전까지 테스트만 부른다. 테스트만을 위한 메서드가 아니라, 변경 5의 Color 메뉴가 쓸 메서드를 먼저 넣은 것이다.

이 변경 뒤의 동작은 다음과 같다.

- 처음 값이 원본 빨강의 고정 규칙이므로 화면은 dev와 같다.
- Universe는 아직 dev처럼 생사가 바뀔 셀이 있는 틱에만 `transition()`을 부른다. 그래서 판 전체가 멈추면 나이도 멈춘다. 색이 고정이라 화면에는 드러나지 않으며, 변경 4에서 매 틱 부르게 바꾼다.

### 변경 내용

```diff
@@ -25,4 +25,22 @@ public final class Resident implements Cell
 
+	/** The color rule that every live cell is drawn with. It starts as
+	 *  the original fixed red, so the board looks the same as before
+	 *  until another rule is chosen.
+	 */
+	private static CellColorPolicy colorPolicy = new FixedColorPolicy( LIVE_COLOR );
+
+	/** Make every cell use the given color rule from now on. Call this
+	 *  on the thread that draws the board (the Swing event thread in the
+	 *  game).
+	 *  @throws IllegalArgumentException if policy is null.
+	 */
+	static void setColorPolicy( CellColorPolicy policy )
+	{	if( policy == null )
+			throw new IllegalArgumentException( "null color policy" );
+		colorPolicy = policy;
+	}
+
 	private boolean amAlive 	= false;
 	private boolean willBeAlive	= false;
+	private int		age			= 0;	// generations this cell has been alive; 0 when born
 
@@ -81,2 +99,3 @@ public final class Resident implements Cell
 	{	boolean changed = amAlive != willBeAlive;
+		age = (amAlive && willBeAlive) ? age + 1 : 0;	// survivors grow older; others start over
 		amAlive = willBeAlive;
@@ -87,3 +106,3 @@ public final class Resident implements Cell
     {   g = g.create();
-		g.setColor(amAlive ? LIVE_COLOR : DEAD_COLOR );
+		g.setColor(amAlive ? colorPolicy.colorOf(age) : DEAD_COLOR );
 		g.fillRect(here.x+1, here.y+1, here.width-1, here.height-1);
@@ -102,2 +121,3 @@ public final class Resident implements Cell
 	{	amAlive = !amAlive;
+		age = 0;				// a cell revived by a click starts over
 	}
@@ -117,3 +137,4 @@ public final class Resident implements Cell
 		if( doLoad )
-		{	if( amAlive = willBeAlive = memento.isAlive(upperLeft) )
+		{	age = 0;							// ages are not saved
+			if( amAlive = willBeAlive = memento.isAlive(upperLeft) )
 				return true;
```

### 테스트

| 테스트 클래스 | 테스트 메서드 | 확인 내용 | 실행 수 |
| --- | --- | --- | --- |
| `CellAgeColorTest` | `stillLifeChangesColorEveryTenGenerationsAndWrapsAround` | 2×2 블록이 9틱까지 빨강, 10틱에 파랑, 20틱에 초록, 30틱에 다시 빨강 | 1 |
| `CellAgeColorTest` | `survivorAgesNewbornStartsAtZeroAndRebornCellStartsOver` | 간격이 1인 규칙에서 블링커의 살아남은 칸은 나이가 1, 2로 늘고, 태어난 칸과 다시 태어난 칸은 나이 0, 죽은 칸은 죽은 칸 색 | 1 |
| `CellAgeColorTest` | `cellRevivedByClickStartsAtAgeZero` | 초록이 된 블록의 한 칸을 두 번 눌러 죽였다 살리면 그 칸만 빨강 | 1 |
| `CellAgeColorTest` | `storeThenLoadKeepsCellsAndRestartsColor` | 초록이 된 블록을 Store → Load하면 같은 네 칸이 빨강으로 복원되고, 10틱 뒤 파랑 | 1 |
| `CellAgeColorTest` | `cellClickedAfterClearStartsRed` | Clear 뒤 살아 있는 셀 0개, 다시 클릭한 칸은 빨강 | 1 |
| `CellAgeColorTest` | `lifeRuleIsUnchanged` | 무작위 판 32개(설계 문서의 실험과 같은 무늬 4종 × 시드 8개) × 100세대가 매 세대 Life 규칙을 직접 계산한 결과와 같음 | 1 |
| `CellAgeColorTest` | `setColorPolicyRejectsNullAndKeepsThePreviousPolicy` | null이면 `IllegalArgumentException`, 그 뒤에도 이전 규칙으로 그림 | 1 |
| `DefaultCellColorTest` | `liveCellsStayRedWhenNoColorPolicyIsChosen` | 색 규칙을 한 번도 정하지 않은 판의 블록이 25세대 동안 빨강 | 1 |

- 색은 게임이 쓰는 `redraw()`로 판을 이미지에 그린 뒤 셀 한가운데 픽셀에서 읽는다. 살아 있는 셀은 Grid > Store와 같은 `transfer()` 경로로 읽는다. 게임 코드에 테스트용 조회 메서드는 넣지 않았다.
- `CellAgeColorTest`는 테스트마다 색 규칙을 원래의 고정 빨강으로 되돌린다(`@AfterEach`). 그래서 Resident의 처음 값은 규칙을 한 번도 바꾸지 않는 `DefaultCellColorTest`에서 확인한다. Gradle이 테스트 클래스마다 JVM을 새로 띄우므로(변경 1) 다른 테스트가 바꾼 규칙이 남아 있지 않다.

### 검증

| 항목 | 결과 |
| --- | --- |
| 전체 테스트 (`gradlew test --rerun`) | 62개 통과 (기존 32개, `CellColorPolicyTest` 22개, 새 8개), 실패 0 |
| 테스트용 JVM 수 | 6개 (테스트 클래스마다 1개) |
| 새 테스트 시간 | `CellAgeColorTest` 약 2.5초(그중 `lifeRuleIsUnchanged` 약 1.9초), `DefaultCellColorTest` 약 0.3초 |

추가한 줄마다 결함을 하나씩 넣은 사본에서 전체 테스트를 돌린 결과다. 8가지 결함 모두 실패하는 테스트가 하나 이상 있었고, 기존 테스트 32개와 `CellColorPolicyTest`는 어느 결함에서도 실패하지 않았다.

| 바꾼 줄 | 넣은 결함 | 실패한 테스트 |
| --- | --- | --- |
| 색 규칙의 처음 값 | 고정 빨강 대신 순환 규칙으로 시작 | `liveCellsStayRedWhenNoColorPolicyIsChosen` |
| `setColorPolicy()`의 null 검사 | 검사를 지움 | `setColorPolicyRejectsNullAndKeepsThePreviousPolicy` |
| `setColorPolicy()`의 대입 | 대입을 지움(규칙이 바뀌지 않음) | `lifeRuleIsUnchanged`를 뺀 `CellAgeColorTest` 6개 |
| `transition()`의 나이 계산 | 줄을 지움(나이가 늘지 않음) | `lifeRuleIsUnchanged`를 뺀 `CellAgeColorTest` 6개 |
| `transition()`의 나이 계산 | 새로 태어난 셀이 나이 1부터 시작 | `survivorAgesNewbornStartsAtZeroAndRebornCellStartsOver` |
| `redraw()`의 색 | 규칙 대신 원래 빨강으로 그림 | `lifeRuleIsUnchanged`를 뺀 `CellAgeColorTest` 6개 |
| `userClicked()`의 `age = 0` | 줄을 지움 | `cellRevivedByClickStartsAtAgeZero`, `cellClickedAfterClearStartsRed` |
| `transfer()` Load의 `age = 0` | 줄을 지움 | `storeThenLoadKeepsCellsAndRestartsColor` |

`lifeRuleIsUnchanged`는 특정 줄이 아니라 생사 규칙이 그대로인지를 지키는 테스트다. 위 결함은 모두 색에만 영향을 주므로 이 테스트는 통과했다.

- `--warning-mode all`로 실행했을 때 Gradle 사용 중단(deprecation) 경고는 없었다.
- 결함을 넣은 확인은 시험용 사본에서만 했고, 저장소의 코드는 바꾸지 않았다.
- 환경: Gradle 9.5.1(래퍼), JDK 17, JUnit Jupiter 6.0.3, Mockito 5.23.0, 2026-11-12.

## 4. Universe의 틱 처리와 화면 갱신

### 요약

Universe가 매 틱 `transition()`을 불러, 생사가 바뀌지 않는 틱에도 셀의 나이가 늘게 했다. 지금 색 규칙이 나이에 따라 색을 바꾸면(`Resident.colorChangesWithAge()`가 true) 매 틱 판 전체를 다시 그리고, 아니면 dev처럼 바뀐 곳만 다시 그린다. 게임 안에서 색 순환을 켜는 Color 메뉴는 아직 없으므로 화면은 dev와 같다.

| 파일 | GoF Strategy에서의 역할 | 내용 |
| --- | --- | --- |
| `src/com/holub/life/Universe.java` | Client | 시계 리스너의 틱 처리: 매 틱 `transition()`을 부르고, `Resident.colorChangesWithAge()`로 다시 그릴 범위를 정한다. `refreshNow()`가 판 전체를 그릴지를 인자로 받는다 |
| `src/com/holub/life/Resident.java` | Context | `colorChangesWithAge()` 추가(같은 패키지 전용), 색 규칙 참조에 `volatile` |
| `test/com/holub/life/UniverseColorTest.java` (새 파일) | 테스트 | 아래 테스트 2개 |
| `test/com/holub/life/CellAgeColorTest.java` | 테스트 | 아래 테스트 1개 추가 |

### 바꾼 이유

설계 문서 5절 결정 1(나이를 세는 곳)과 6절 "Resident와 Universe 변경의 세부 사항"을 따른다.

- dev의 틱 처리는 생사가 바뀔 셀이 있을 때만 `transition()`을 부르고, 생사가 바뀐 블록만 다시 그린다. 그러면 2×2 블록처럼 멈춘 패턴은 나이가 늘지 않고 다시 그려지지도 않아서, 색 순환을 켜도 색이 바뀌지 않는다(설계 문서 4절).
- 그래서 매 틱 `transition()`을 부르고, 색이 나이에 따라 바뀌는 규칙일 때만 판 전체를 다시 그린다. 고정 색일 때는 dev와 같은 방식으로 갱신한다.
- 다시 그릴 범위는 Universe가 따로 저장하지 않고, 매 틱 `Resident.colorChangesWithAge()`로 지금 규칙에 묻는다. 값을 하나만 두므로 색 규칙과 다시 그리는 방식이 어긋날 수 없다.
- 틱 처리는 시계 스레드에서 돈다. 이제 다른 스레드가 색 규칙 참조를 읽으므로 `volatile`을 붙였다.
- 매 틱 `transition()`을 불러도 생사와 화면은 dev와 같다. 설계 문서 3절의 실험(판 100개 × 300틱, 150틱에 Store → Load)에서 색 순환을 끈 판은 매 세대 살아 있는 셀과 매 틱 화면이 dev와 같았다.

이 변경 뒤의 동작은 다음과 같다.

- 게임 안에서 색 순환을 켤 방법이 아직 없어 화면은 dev와 같다. Color 메뉴는 변경 5에서 넣는다.
- 판이 멈춰 있어도 셀의 나이는 매 틱 는다. 색이 고정이라 화면에는 드러나지 않는다.

### 변경 내용

```diff
// Universe.java
@@ -144,10 +144,21 @@ public class Universe extends JPanel
 			{	public void tick()
-				{	if( outermostCell.figureNextState
+				{	outermostCell.figureNextState
 						   ( Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,
 							 Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY
-						   )
-					  )
-					{	if( outermostCell.transition() )
-							refreshNow();
-					}
+						   );
+
+					// Transition on every tick, even when no cell will be
+					// born or die, so that the cells of a stable pattern
+					// still grow one generation older.
+
+					boolean changed = outermostCell.transition();
+
+					// A color that depends on age can change on any tick,
+					// so then every cell is redrawn. Otherwise only the
+					// parts of the board that changed are redrawn, as before.
+
+					if( Resident.colorChangesWithAge() )
+						refreshNow( true );
+					else if( changed )
+						refreshNow( false );
 				}
@@ -231,5 +242,7 @@ public class Universe extends JPanel
 	 *  have to be refreshed.
+	 *  @param drawAll true to redraw every cell, even the
+	 *  			ones that did not change.
 	 */
 
-	private void refreshNow()
+	private void refreshNow( final boolean drawAll )
 	{	SwingUtilities.invokeLater
@@ -245,3 +258,3 @@ public class Universe extends JPanel
 						panelBounds.y = 0;
-						outermostCell.redraw(g, panelBounds, false); //{=Universe.redraw2}
+						outermostCell.redraw(g, panelBounds, drawAll); //{=Universe.redraw2}
 					}

// Resident.java
@@ -27,9 +27,9 @@ public final class Resident implements Cell
 	 *  the original fixed red, so the board looks the same as before
-	 *  until another rule is chosen.
+	 *  until another rule is chosen. Universe reads it on the clock
+	 *  thread (through colorChangesWithAge()) while it is changed on
+	 *  another thread, so it is volatile.
 	 */
-	private static CellColorPolicy colorPolicy = new FixedColorPolicy( LIVE_COLOR );
+	private static volatile CellColorPolicy colorPolicy = new FixedColorPolicy( LIVE_COLOR );
 
-	/** Make every cell use the given color rule from now on. Call this
-	 *  on the thread that draws the board (the Swing event thread in the
-	 *  game).
+	/** Make every cell use the given color rule from now on.
 	 *  @throws IllegalArgumentException if policy is null.
@@ -42,2 +42,9 @@ public final class Resident implements Cell
 
+	/** Return true if a cell's color can change just because the cell
+	 *  grows older. Universe then redraws every cell on every tick.
+	 */
+	static boolean colorChangesWithAge()
+	{	return colorPolicy.changesWithAge();
+	}
+
 	private boolean amAlive 	= false;
```

### 테스트

| 테스트 클래스 | 테스트 메서드 | 확인 내용 | 실행 수 |
| --- | --- | --- | --- |
| `UniverseColorTest` | `stillLifeStaysRedWhileCyclingIsOff` | 고정 색 규칙에서 Universe의 실제 틱으로 30틱을 보내도 블록이 빨강 | 1 |
| `UniverseColorTest` | `stillLifeCyclesThroughColorsWhenNothingElseChanges` | 순환 규칙에서 블록만 있는 판이 10틱에 파랑, 20틱에 초록, 30틱에 빨강 | 1 |
| `CellAgeColorTest` | `colorChangesWithAgeFollowsTheCurrentPolicy` | 순환 규칙이면 `Resident.colorChangesWithAge()`가 true, 고정 규칙이면 false | 1 |

- UniverseColorTest는 UniverseStoreTest와 같은 방법으로 창 없이 실제 Universe를 만든다. Mockito로 `MenuSite`와 `Clock`을 대신해서 메뉴 콜백과 Universe가 시계에 등록한 리스너를 받아 두고, 그 리스너의 `tick()`을 불러 틱을 보낸다. 색은 `Universe.paint()`로 이미지에 그려 읽는다.
- 순환 테스트는 `Resident.setColorPolicy()`로 규칙을 직접 넣는다. 그래서 이 테스트는 메뉴가 아니라 Universe의 틱 처리를 확인한다. 메뉴로 규칙을 바꾸는 부분은 변경 5에서 따로 시험한다.

### 검증

| 항목 | 결과 |
| --- | --- |
| 전체 테스트 (`gradlew test --rerun`) | 65개 통과 (기존 32개, `CellColorPolicyTest` 22개, `CellAgeColorTest` 8개, `DefaultCellColorTest` 1개, `UniverseColorTest` 2개), 실패 0 |
| 테스트용 JVM 수 | 7개 (테스트 클래스마다 1개) |

바꾼 줄마다 결함을 하나씩 넣은 사본에서 전체 테스트를 돌린 결과다.

| 바꾼 줄 | 넣은 결함 | 실패한 테스트 |
| --- | --- | --- |
| 매 틱 `transition()` 호출 | dev처럼 생사가 바뀔 셀이 있을 때만 부름 | `stillLifeCyclesThroughColorsWhenNothingElseChanges` |
| `colorChangesWithAge()` | 늘 false를 돌려줌 | `colorChangesWithAgeFollowsTheCurrentPolicy` |
| `refreshNow( true )` | `refreshNow( false )`로 바꿈 | 없음. 창이 없는 테스트에서는 그리지 않으므로 아래 실제 창 확인으로 대신함 |
| 색 규칙 참조의 `volatile` | 지움 | 없음. 스레드 사이에 값이 보이는지는 테스트로 재현할 수 없음 |

`refreshNow(true)`가 판 전체를 다시 그리는지는 창이 없는 테스트에서 확인할 수 없다(`getGraphics()`가 null). 그래서 시험용 사본으로 실제 게임 창을 띄워 확인했다. `Resident.setColorPolicy()`로 순환 규칙을 넣고, 마우스 이벤트로 2×2 블록을 놓은 뒤 `Clock.instance().tick()`으로 틱을 보냈다. `paint()`는 부르지 않았고, 화면에 실제로 그려진 픽셀을 `java.awt.Robot`으로 읽었다.

| 시점 | 이 변경의 빌드 | 대조: `refreshNow( true )`를 `refreshNow( false )`로 바꾼 사본 |
| --- | --- | --- |
| 블록을 놓은 직후 | 빨강 | 빨강 |
| 9틱 | 빨강 | 빨강 |
| 10틱 | 파랑 | 빨강 |
| 20틱 | 초록 | 빨강 |
| 30틱 | 빨강 | 빨강 |

대조 사본에서는 멈춘 블록이 다시 그려지지 않아 화면이 빨강에 머물렀다. 이 변경의 빌드에서는 틱마다 판 전체를 다시 그려 나이에 맞는 색이 나왔다.

- `--warning-mode all`로 실행했을 때 Gradle 사용 중단(deprecation) 경고는 없었다.
- 결함을 넣은 확인과 실제 창 확인은 시험용 사본에서만 했고, 저장소의 코드는 바꾸지 않았다.
- 환경: Gradle 9.5.1(래퍼), JDK 17, JUnit Jupiter 6.0.3, Mockito 5.23.0, Windows 11, 2026-11-12.

## 5. Universe의 Color 메뉴

### 요약

Grid, Go 메뉴 뒤에 Color 메뉴를 추가했다. Single Color를 고르면 원본 빨강의 고정 규칙으로, Cycle by Generation을 고르면 10세대마다 빨강 → 파랑 → 초록으로 도는 규칙으로 바뀐다. 처음에는 원본 빨강이므로 프로그램을 켠 직후의 화면은 dev와 같다. 이 변경으로 기능이 완성된다.

| 파일 | GoF Strategy에서의 역할 | 내용 |
| --- | --- | --- |
| `src/com/holub/life/Universe.java` | Client | Color 메뉴 항목 2개. 고른 항목에 맞는 규칙 객체(ConcreteStrategy)를 만들어 `useColorPolicy()`로 Resident에 넘기고 판을 바로 다시 그린다 |
| `src/com/holub/life/Resident.java` | Context | 원본 상수 `LIVE_COLOR`의 `private`을 떼어 Universe가 원본 빨강으로 쓰게 함 |
| `test/com/holub/life/UniverseColorTest.java` | 테스트 | 아래 테스트 2개 추가 |

### 바꾼 이유

설계 문서 1절(기능 정의)과 6절 "Resident와 Universe 변경의 세부 사항"의 "색 전환을 둘 곳", "메뉴 이름과 선택 표시", "원본 빨강을 둘 곳"을 따른다.

- 색 전환은 원본의 Grid, Go 메뉴처럼 `MenuSite.addLine()`으로 등록하는 Color 메뉴에 둔다. 다른 팀원 기능이 쓰는 창 오른쪽 패널은 아직 dev에 없다. 그 UI가 dev에 들어오면 팀과 이야기해 위치를 다시 정한다. 규칙을 바꾸는 코드는 `useColorPolicy()` 호출 하나라서 옮기기 쉽다.
- 메뉴는 시계 리스너를 등록한 다음에 추가한다. 시계가 처음 만들어질 때 Go 메뉴가 생기므로, 그 뒤에 추가해야 Color 메뉴가 Go 뒤에 온다.
- 지금 고른 항목은 메뉴에 표시하지 않는다. `MenuSite.addLine()`은 일반 메뉴 항목만 만들고, 원본의 Go 메뉴도 지금 속도를 표시하지 않는다.
- 원본 빨강은 Resident의 원본 상수 `LIVE_COLOR` 한 곳에만 둔다. 처음 값, Single Color, 순환의 첫 색이 모두 이 상수를 쓰므로 세 곳이 어긋날 수 없다.
- `useColorPolicy()`는 규칙을 바꾼 뒤 `repaint()`로 판을 바로 다시 그린다. 시계가 멈춰 있어도 고른 규칙의 색이 바로 보이게 하기 위해서다.
- 다시 그릴 범위는 변경 4에서 Universe가 매 틱 `Resident.colorChangesWithAge()`로 묻게 했으므로, 메뉴에서는 규칙만 바꾸면 된다.

### 변경 내용

```diff
// Resident.java
@@ -22,3 +22,3 @@ public final class Resident implements Cell
 	private static final Color BORDER_COLOR = Colors.DARK_YELLOW;
-	private static final Color LIVE_COLOR 	= Color.RED;
+	static final Color LIVE_COLOR			= Color.RED;	// Universe's Color menu uses it too
 	private static final Color DEAD_COLOR   = Colors.LIGHT_YELLOW;

// Universe.java
@@ -166,2 +166,35 @@ public class Universe extends JPanel
 		);
+
+		// The Color menu is added after the clock subscription above,
+		// which creates the Go menu, so that it comes after Grid and Go.
+
+		MenuSite.addLine
+		(	this, "Color", "Single Color",
+			new ActionListener()
+			{	public void actionPerformed(ActionEvent e)
+				{	useColorPolicy( new FixedColorPolicy( Resident.LIVE_COLOR ) );
+				}
+			}
+		);
+
+		MenuSite.addLine
+		(	this, "Color", "Cycle by Generation",
+			new ActionListener()
+			{	public void actionPerformed(ActionEvent e)
+				{	// The original red, then a blue and a green that stay easy
+					// to tell apart, also with red-green color blindness.
+
+					useColorPolicy( new CyclingColorPolicy
+						( 10, Resident.LIVE_COLOR, new Color(0x0072B2), new Color(0x009E73) ) );
+				}
+			}
+		);
+	}
+
+	/** Make every cell use the given color rule from now on, and
+	 *  redraw the board with it right away.
+	 */
+	private void useColorPolicy( CellColorPolicy policy )
+	{	Resident.setColorPolicy( policy );
+		repaint();
 	}
```

### 테스트

| 테스트 클래스 | 테스트 메서드 | 확인 내용 | 실행 수 |
| --- | --- | --- | --- |
| `UniverseColorTest` | `ageIsCountedWhileCyclingIsOffSoSwitchingOnShowsTheRightColor` | 원본 규칙으로 25틱을 보낸 뒤 Color > Cycle by Generation을 고르면 틱 없이 바로 초록 | 1 |
| `UniverseColorTest` | `singleColorBringsBackTheOriginalRed` | 순환으로 20틱이 지나 초록인 블록이 Color > Single Color를 고르면 바로 빨강, 10틱 뒤에도 빨강 | 1 |

- 두 테스트는 Universe가 실제로 등록한 메뉴 콜백을 불러 항목을 고른다(변경 4의 UniverseColorTest와 같은 준비 방법).
- 테스트 준비 단계(`@BeforeEach`, `@AfterEach`)는 메뉴를 거치지 않고 `Resident.setColorPolicy()`로 규칙을 되돌린다. 메뉴가 고장 났을 때 메뉴 테스트만 실패하게 하기 위해서다.

### 검증

| 항목 | 결과 |
| --- | --- |
| 전체 테스트 (`gradlew test --rerun`) | 67개 통과 (기존 32개, `CellColorPolicyTest` 22개, `CellAgeColorTest` 8개, `DefaultCellColorTest` 1개, `UniverseColorTest` 4개), 실패 0 |
| 테스트용 JVM 수 | 7개 (테스트 클래스마다 1개) |

바꾼 줄마다 결함을 하나씩 넣은 사본에서 전체 테스트를 돌린 결과다.

| 바꾼 줄 | 넣은 결함 | 실패한 테스트 |
| --- | --- | --- |
| Single Color 항목의 규칙 | 원본 빨강 대신 파랑 | `singleColorBringsBackTheOriginalRed` |
| Cycle by Generation 항목의 색 순서 | 파랑과 초록의 순서를 바꿈 | `ageIsCountedWhileCyclingIsOffSoSwitchingOnShowsTheRightColor`, `singleColorBringsBackTheOriginalRed` |
| Cycle by Generation 항목의 간격 | 10 대신 20 | `ageIsCountedWhileCyclingIsOffSoSwitchingOnShowsTheRightColor`, `singleColorBringsBackTheOriginalRed` |
| `useColorPolicy()`의 `Resident.setColorPolicy()` | 호출을 지움 | `ageIsCountedWhileCyclingIsOffSoSwitchingOnShowsTheRightColor`, `singleColorBringsBackTheOriginalRed` |
| `useColorPolicy()`의 `repaint()` | 호출을 지움 | 없음. 아래 실제 창 확인으로 대신함 |

메뉴를 고른 순간 판을 다시 그리는지(`repaint()`)와 Color 메뉴가 Go 뒤에 생기는지는 JUnit으로 확인할 수 없다. 테스트는 색을 읽을 때 직접 `paint()`를 부르고, 시계를 대신 넣어 Go 메뉴가 생기지 않기 때문이다. 그래서 시험용 사본으로 실제 게임 창을 띄워 확인했다. 마우스 이벤트로 2×2 블록을 놓고 `Clock.instance().tick()`으로 25틱을 보낸 뒤, 시계를 멈춘 채 메뉴 항목을 골랐다. `paint()`는 부르지 않았고, 화면에 실제로 그려진 픽셀을 `java.awt.Robot`으로 읽었다.

| 확인 | 이 변경의 빌드 | 대조: `useColorPolicy()`에서 `repaint()`를 지운 사본 |
| --- | --- | --- |
| 메뉴 순서 | Grid, Go, Color (Color: Single Color, Cycle by Generation) | 같음 |
| 25틱 뒤, 아무것도 고르기 전 | 빨강 | 빨강 |
| Cycle by Generation을 고른 직후 | 초록 | 빨강 |
| Single Color를 고른 직후 | 빨강 | 빨강 |

대조 사본에서는 메뉴를 골라도 다음 틱까지 판이 다시 그려지지 않아, 나이 25인 블록이 빨강에 머물렀다.

- `--warning-mode all`로 실행했을 때 Gradle 사용 중단(deprecation) 경고는 없었다.
- 결함을 넣은 확인과 실제 창 확인은 시험용 사본에서만 했고, 저장소의 코드는 바꾸지 않았다.
- 환경: Gradle 9.5.1(래퍼), JDK 17, JUnit Jupiter 6.0.3, Mockito 5.23.0, Windows 11, 2026-11-12.
