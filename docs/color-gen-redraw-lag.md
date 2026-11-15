# 색 순환 중 화면 갱신 지연 문제와 해결 방안

- 브랜치: `feature/color-gen`
- 상태: 원인을 확인했고 해결 방안 A로 방향을 정했다. 코드에는 아직 반영하지 않았다 (2026-11-15).
- 관련 문서: 설계 문서 「세대에 따른 셀 색 순환 기능」 5절 결정 1, 7절, 8절 / 변경 기록 [feature-color-gen-changes.md](feature-color-gen-changes.md)

## 1. 문제 상황

### 증상

Color > Cycle by Generation을 켜고 Go > Fast나 Medium으로 돌리면 셀 클릭과 메뉴 선택이 늦게 반영되거나 창이 멈춘 것처럼 된다. 오래 돌릴수록 더 늦어진다. Go > Halt도 늦게 먹고, 멈춘 뒤에도 한동안 클릭과 메뉴가 듣지 않는다.

### 재현 방법

1. 게임을 실행하고 2×2 블록을 몇 개 놓는다.
2. Color > Cycle by Generation을 고른다.
3. Go > Fast를 고르고 몇 초 뒤에 셀을 클릭하거나 메뉴를 연다.

같은 판에서 Color > Single Color로 돌리면 지연이 없다.

### 측정 결과

실제 게임 창을 띄우고 메뉴로 색 규칙과 속도를 고른 뒤 6초 동안 돌렸다. 그동안 0.1초마다 Swing 이벤트 큐에 빈 작업을 넣고, 그 작업이 실제로 실행될 때까지 걸린 시간을 쟀다. 셀 클릭과 메뉴 선택도 같은 큐에서 차례를 기다리므로, 이 시간만큼 반영이 늦어진다.

"멈춘 뒤"는 시계를 멈춘 직후 넣은 작업이 실행되기까지 걸린 시간이다. 이미 쌓인 작업을 다 처리하는 데 걸린 시간과 같다. 실제로 Halt를 누르면 그 클릭도 큐 뒤에서 기다리므로 이보다 늦게 멈춘다.

- 패턴 판: 2×2 블록 6개와 블링커 2개
- 무작위 판: 64×64 전체에서 30%의 셀을 무작위로 살린 판

| 코드 | 판 | 속도 | 마지막 1초 지연 | 최대 지연 | 멈춘 뒤 |
| --- | --- | --- | --- | --- | --- |
| 지금 코드, 색 순환 켬 | 패턴 | Fast | 14.1초 | 15.4초 | 15.8초 |
| 지금 코드, 색 순환 켬 | 패턴 | Medium | 4.5초 | 4.7초 | 4.8초 |
| 지금 코드, 색 순환 켬 | 무작위 | Fast | 20.5초 | 21.8초 | 22.1초 |
| 지금 코드, 색 순환 켬 | 무작위 | Medium | 5.9초 | 6.2초 | 6.4초 |
| 지금 코드, 색 순환 끔 | 패턴 | Fast | 0 ms | 6 ms | 7 ms |
| 지금 코드, 색 순환 끔 | 무작위 | Fast | 6.6초 | 7.0초 | 7.1초 |
| dev | 패턴 | Fast | 0 ms | 8 ms | 5 ms |
| dev | 무작위 | Fast | 6.5초 | 6.8초 | 6.8초 |
| dev | 무작위 | Medium | 2.2초 | 2.2초 | 2.2초 |

- 색 순환을 켜면 패턴 몇 개만 있는 판에서도 지연이 생기고, 돌리는 동안 계속 늘어난다.
- 색 순환을 끄면 dev와 같다.
- 판 대부분이 계속 바뀌는 무작위 판에서는 dev도 밀린다. 원본에서 온 한계이고 5절에 따로 적었다.

판 전체를 한 번 그리는 데 걸린 시간 (위 측정마다 15번 잰 중앙값의 범위):

| 그리는 방법 | 시간 |
| --- | --- |
| `getGraphics()`로 화면에 바로 그리기 (`refreshNow()`의 방법) | 75~143 ms |
| Swing 이중 버퍼를 거쳐 그리기 (`paint()`의 방법) | 1.7~4.5 ms |

## 2. 원인

1. 시계는 `java.util.Timer` 스레드에서 `scheduleAtFixedRate`로 30 ms(Fast)나 70 ms(Medium)마다 틱을 보낸다.
2. 색 순환이 켜져 있으면 Universe의 틱 처리가 매 틱 `refreshNow( true )`를 부른다.
3. `refreshNow()`는 그리기 작업을 `SwingUtilities.invokeLater()`로 이벤트 큐에 넣고 바로 돌아온다. 앞에 넣은 작업이 끝났는지는 보지 않는다.
4. 그 작업은 `getGraphics()`로 얻은 화면에 4,096칸을 하나씩 바로 그린다. 칸마다 `fillRect()` 한 번과 `drawLine()` 두 번을 부르고, 한 장면에 75~143 ms가 걸린다.
5. 작업이 들어오는 간격(30 ms, 70 ms)보다 처리 시간이 길어서 큐에 그리기 작업이 계속 쌓인다. 한 장면에 75 ms가 걸리면 1초에 13장쯤 그릴 수 있는데, Fast는 1초에 33번쯤 요청한다.
6. 셀 클릭, 메뉴 선택, Halt도 같은 큐의 맨 뒤에 줄을 서므로 앞에 쌓인 그리기가 끝나야 처리된다.

색 순환이 꺼져 있으면 `refreshNow( false )`가 활성 블록만 그린다. 판의 일부만 움직이면 한 장면이 짧아서 작업이 쌓이지 않는다. 판 대부분이 계속 바뀌면 거의 판 전체를 바로 그리게 되어 dev에서도 같은 일이 생긴다.

### 설계와 테스트에서 놓친 이유

- 설계 문서 5절 결정 1의 그리기 시간(색 순환 켬 2.0~2.3 ms)은 화면 대신 이미지에 그려서 잰 값이다(설계 문서 8절). 화면에 바로 그리는 비용은 그보다 30배 넘게 컸다.
- JUnit 테스트는 창이 없어서 `getGraphics()`가 null이고, `refreshNow()`가 아무것도 그리지 않는다.
- 설계 문서 7절의 실제 창 확인은 틱을 하나씩 보내고 기다리면서 색만 봤다. 시계를 계속 돌리지 않았으므로 작업이 쌓이지 않았다.

## 3. 해결 방안 A: 색 순환 중에는 repaint()로 다시 그리기를 요청

### 바꾸는 코드

`Universe.java` 한 파일만 바꾼다. 색 순환이 켜져 있으면 `refreshNow( true )` 대신 `repaint()`를 부르고, `refreshNow()`는 dev의 원래 모양(인자 없음)으로 되돌린다.

```diff
@@ -150,11 +150,12 @@ public class Universe extends JPanel
 					// 바뀌는 셀이 없어도 매 틱 불러야 멈춘 패턴도 나이를 먹는다
 					boolean changed = outermostCell.transition();
 
-					// 나이로 색이 바뀌는 규칙이면 판 전체를, 아니면 바뀐 곳만 다시 그린다
+					// 나이로 색이 바뀌면 판 전체를, 아니면 바뀐 곳만 다시 그린다.
+					// 판 전체는 repaint()로 요청해야 밀린 요청이 하나로 합쳐진다
 					if( Resident.colorChangesWithAge() )
-						refreshNow( true );
+						repaint();
 					else if( changed )
-						refreshNow( false );
+						refreshNow();
 				}
 			}
 		);
@@ -260,10 +261,9 @@ public class Universe extends JPanel
 	 *  the redraw() method on a given <code>Cell</code>
 	 *  does nothing if the <code>Cell</code> doesn't
 	 *  have to be refreshed.
-	 *  @param drawAll true면 바뀌지 않은 셀까지 모두 다시 그린다
 	 */
 
-	private void refreshNow( final boolean drawAll )
+	private void refreshNow()
 	{	SwingUtilities.invokeLater
 		(	new Runnable()
 			{	public void run()
@@ -275,7 +275,7 @@ public class Universe extends JPanel
 						Rectangle panelBounds = getBounds();
 						panelBounds.x = 0;
 						panelBounds.y = 0;
-						outermostCell.redraw(g, panelBounds, drawAll); //{=Universe.redraw2}
+						outermostCell.redraw(g, panelBounds, false); //{=Universe.redraw2}
 					}
 					finally
 					{	g.dispose();
```

바꾼 뒤의 `refreshNow()`는 Javadoc까지 dev와 같다. Resident, Neighborhood, Clock과 색 규칙 클래스는 바뀌지 않는다.

### 동작

| 단계 | 하는 일 |
| --- | --- |
| 틱 (시계 스레드) | `repaint()`는 바로 그리지 않고, Universe를 다시 그려야 한다는 표시만 Swing의 RepaintManager에 남긴다. 아직 처리되지 않은 표시가 있으면 새 요청은 거기에 합쳐진다. 그래서 큐에는 판 전체 그리기가 많아야 하나만 기다린다 |
| 그리기 (이벤트 스레드) | 차례가 오면 Swing이 `paint()`를 부른다. `paint()`는 원래부터 `redraw( g, panelBounds, true )`로 판 전체를 그린다. 이중 버퍼에 그린 뒤 한 번에 화면으로 옮기므로 한 장면이 1.7~4.5 ms다 |
| 색 순환을 끈 경우 | `refreshNow()`가 dev와 같은 코드로 활성 블록만 그린다 |

- `repaint()`는 Swing에서 어느 스레드에서 불러도 되는 메서드라 시계 스레드에서 불러도 된다. 셀 클릭(`mousePressed()`), Grid > Clear, Color 메뉴(`useColorPolicy()`)도 이미 `repaint()`로 다시 그린다.
- 그리는 도중에 `transition()`이 돌면 그 블록을 건너뛰는 것(`Neighborhood.redraw()`의 `readingPermitted` 확인)은 지금과 같다.
- 활성 블록의 파란 테두리도 `paint()`가 같은 `redraw()`로 그리므로 지금과 같다.

### 시험용 사본으로 확인한 결과

위 코드를 지금 코드의 사본에 넣고 1절과 같은 방법으로 쟀다.

| 판 | 색 순환 | 속도 | 마지막 1초 지연 | 최대 지연 | 멈춘 뒤 |
| --- | --- | --- | --- | --- | --- |
| 패턴 | 켬 | Fast | 0 ms | 4 ms | 8 ms |
| 패턴 | 켬 | Medium | 0 ms | 8 ms | 7 ms |
| 무작위 | 켬 | Fast | 0 ms | 8 ms | 5 ms |
| 무작위 | 켬 | Medium | 0 ms | 4 ms | 5 ms |
| 패턴 | 끔 | Fast | 0 ms | 6 ms | 7 ms |
| 무작위 | 끔 | Fast | 9.9초 | 10.2초 | 10.2초 |

- 색 순환을 켜면 판과 속도에 상관없이 지연이 10 ms 안쪽이다.
- 색 순환을 끈 무작위 판은 dev와 같은 코드라 dev처럼 밀린다(5절).
- 화면 색: 같은 사본을 실제 창에서 돌리며 화면 픽셀을 읽었다. 블록은 9틱에 빨강, 10틱에 파랑, 20틱에 초록, 30틱에 빨강이었다. `repaint()`를 지운 대조 사본에서는 계속 빨강이었다. 그래서 이 확인으로 `repaint()`가 빠졌는지 가려낼 수 있다.

### 비용과 주의할 점

- 이벤트 스레드가 바쁘면 Swing이 요청을 합치므로 중간 세대의 화면이 보이지 않을 수 있다. 세대 계산은 매 틱 그대로 한다. 한 장면(2~3 ms)이 틱 간격보다 훨씬 짧아서 보통은 틱마다 한 장면씩 그려진다.
- `refreshNow()`의 인자가 없어지므로 Universe에서 dev와 달라지는 부분이 줄어든다.
- 통계 기능 문서처럼 시계를 Swing 타이머로 바꾸더라도 이 방법은 그대로 쓸 수 있다.

### 확인 계획

- JUnit (`UniverseColorTest`): 테스트에서 RepaintManager를 바꿔 끼워 Universe의 `repaint()` 요청을 센다. 창이 없는 환경에서도 셀 수 있는 것을 확인했다.
  - 색 순환을 켠 판에 멈춘 패턴만 두고 틱을 보내면 매 틱 `repaint()`를 요청한다. 지금 코드는 `refreshNow( true )`를 쓰므로 이 테스트에서 실패한다.
  - 색 순환을 끈 판에 멈춘 패턴만 두면 틱이 `repaint()`를 요청하지 않는다.
- 실제 창: 1절의 지연 측정과 위의 화면 색 확인을 구현한 코드로 다시 한다.
- 테스트 이름과 개수는 구현 전에 정한다.

### 함께 고칠 문서

- 설계 문서: 5절 결정 1(그리기 비용), 6절의 바뀌는 파일 표와 Universe 코드, 7절 확인 방법, 8절 한계와 측정 환경, 9절 세대 진행 순서
- 변경 기록: 7번 항목으로 추가

## 4. 다른 방안과 비교

| 방안 | 내용 | 측정 또는 판단 | 결과 |
| --- | --- | --- | --- |
| A. 색 순환 중 `repaint()` | 3절 | 지연 10 ms 안쪽. 한 장면 2~3 ms | 선택 |
| B. `refreshNow( true )` 요청 합치기 | 이미 기다리는 갱신이 있으면 새 요청을 버리고, 그 갱신이 판 전체를 그리게 한다 | 지연 최대 0.34~0.48초, 멈춘 뒤 0.15~0.25초. 화면에 바로 그리는 비용(한 장면 120~145 ms)이 남아 화면이 초당 7~8장만 바뀐다. `refreshNow()`에 필드 2개와 요청을 합치는 코드가 더 들어간다 | 제외 |
| C. 색이 바뀐 블록만 다시 그리기 | 셀의 색이 바뀐 블록에 표시를 남겨 그 블록만 그린다 | 원본의 Neighborhood와 Cell을 고쳐야 한다. 무작위 판에서는 거의 매 틱 어딘가의 색이 바뀌어 판 전체에 가까운 그리기가 남는다 | 제외 |

## 5. 이 문서의 범위 밖: 색 순환을 끈 상태의 지연

색 순환을 끈 상태에서도 판 대부분이 계속 바뀌면 dev처럼 밀린다(Fast 6.5초, Medium 2.2초). 원인은 같다. 활성 블록을 화면에 바로 그리는 시간이 틱 간격보다 길다. 이 지연은 원본에서 온 것이다. A는 원본 동작을 그대로 두려고 이 경로를 바꾸지 않는다. 꺼진 상태에도 같은 방법을 쓰면 없어지지만 원본 동작이 바뀌므로 팀이 따로 정한다.

## 6. 측정 환경

- Windows 11, 화면 1920×1080(배율 100%), 실행 JDK 21.0.12, 2026-11-15
- 측정 프로그램과 해결 방안 사본은 저장소 밖의 시험용 사본에서만 돌렸고, 저장소 코드는 바꾸지 않았다.
- 시간은 PC마다 다르다. 지연이 생기는지는 한 장면을 그리는 시간이 틱 간격보다 긴지로 갈린다.
