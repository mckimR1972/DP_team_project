# 영역 캡처와 템플릿 배치 기능

Allen Holub의 Java Swing 라이프게임에 **영역 캡처와 템플릿 재배치**를 추가한 브랜치다.
최신 `dev@6db01c9`의 기본 오류 수정·색상 기능·Gradle/JUnit 환경을 병합했다.

실행 방법과 테스트 실행 명령은 저장소의 [README](../README.md)에 있다.

## 사용

1. 보드의 셀을 클릭해 살아 있는 셀을 그린다. 보드는 64 x 64다.
2. **영역 캡처**를 누르고 보드에서 드래그한다. 역방향 드래그도 되고, 바깥으로 드래그하면 보드 끝에 맞춰진다.
3. **선택 영역 저장**을 누르고 이름을 입력한다. 선택한 사각형의 크기와 빈 여백도 유지된다.
4. 저장 직후 보드를 클릭하면 캡처한 패턴이 배치된다. 목록에서 다른 템플릿을 고른 뒤 **선택한 템플릿 배치**를 눌러도 된다.
5. 클릭한 칸이 패턴의 왼쪽 위가 된다. 초록 미리보기는 배치 가능, 빨강은 보드 바깥이다. 클릭을 반복하면 여러 번 배치한다.
6. **배치 되돌리기** 또는 `Ctrl+Z`로 최근 배치를 되돌린다. 최대 100회까지 보관한다.
7. **Esc**, 오른쪽 클릭, **편집 끝내기**로 일반 그리기 모드로 돌아간다. **Go > Slow / Medium / Fast**로 진행하고 **Halt**로 멈춘다. **Tick (Single Step)**은 한 세대만 진행한다.

캡처·배치 모드에서는 시뮬레이션이 멈춘다. 편집을 끝낸 뒤 Go 메뉴에서 다시 시작한다.
템플릿의 살아 있는 셀만 추가하므로 빈 칸이 기존 셀을 지우지 않는다.
패턴이 보드 밖으로 나가면 전체 배치를 거부한다. 빈 영역과 중복 이름은 저장하지 않는다.
일반 셀 클릭, 세대 진행, Grid > Clear / Load 이후에는 오래된 배치 이력을 비운다.
기존 **Grid > Store / Load**는 전체 보드를 `.life` 파일로 저장하고 불러오는 별도 기능이다.

기본 템플릿은 Glider, Blinker, Block, Toad, Beacon이다.
사용자가 저장한 패턴은 저장소의 `templates/*.lifepattern`에 남고, 다음 실행 때 자동으로 읽는다.
`templates/`와 `build/`는 Git에서 제외된다. 새 템플릿 파일은 UTF-8 텍스트이며 `O`는 살아 있음, `.`은 빈 칸이다.
테스트나 별도 보관 폴더가 필요하면 `java "-Dlife.templates=경로" -jar build\HolubLife.jar`로 지정할 수 있다.

## 구조와 설계 패턴

| 클래스 | 역할 |
| --- | --- |
| `Life` | Swing 창, 메뉴, 보드와 패턴 패널 조립 |
| `Universe` | 이벤트를 Cell 트리로 전달하는 기존 Mediator / Singleton |
| `Cell`, `Neighborhood`, `Resident` | 보드와 단일 셀의 공통 동작을 제공하는 기존 Composite |
| `Clock`, `Publisher` | Observer 방식의 세대 갱신 알림 |
| `Cell.Memento`, `NeighborhoodState`, `Storable` | 기존 상태 저장·복원 계약 |
| `PatternTools` | 선택·배치 모드, 마우스 입력, 미리보기, 버튼 |
| `PatternTemplate` | 이름·크기·상대 셀 좌표를 가진 불변 패턴 값 |
| `PatternLibrary` | 기본 패턴 목록 및 사용자 파일 저장·불러오기 |
| `BoardEditor` | 영역 캡처, 범위 검사, 배치 이력 관리 |
| `PlacePatternCommand` | **Command**: 배치를 실행하고 **Memento**로 배치 전 상태 복원 |

```text
Life -> Universe -> Cell (Neighborhood -> Neighborhood -> Resident)
          ^          ^
          | tick     | createMemento / transfer
        Clock       BoardEditor -> PlacePatternCommand
          |              ^                 |
       Publisher         |             이전 상태 보관
                     PatternTools
                         |
                  PatternTemplate <-> PatternLibrary -> templates/*.lifepattern
```

이번 확장은 기존 Memento를 재사용하고 배치를 작은 Command 객체로 분리했다.
PatternTemplate은 재사용할 데이터이며, 템플릿 기능을 위해 Template Method 상속 구조를 추가하지 않았다.
선택 모드는 작은 enum으로 처리한다.

상태 일관성을 위해 초기 UI 생성과 Clock 갱신을 Swing 이벤트 스레드로 통일했다.
기존 `Resident.transition()`의 변경 여부 반환을 바로잡고, 상태 복원 직후 모든 Neighborhood가 다음 세대를 다시 계산하도록 했다.
이 변경은 템플릿 배치 후 블록 경계에서 생겨야 할 셀을 누락하지 않게 한다.

## 검증

핵심 검증: 영역 좌표·여백, 비파괴 캡처, 병합 배치, 중첩·중복 배치, 되돌리기,
경계 거부, 저장 후 재로딩, 손상 파일 격리, 저장 실패 시 목록 유지, 불변 데이터.
글라이더 40세대와 임의 보드 360세대를 독립적인 Life 계산 코드와 비교한다.

Swing 검증: 실제 버튼과 이벤트를 통한 배치, 역방향 드래그, 저장 대화상자,
저장 패턴 재배치, Ctrl+Z 동작, Esc 이후 그리기, 한 세대 진행, 경계 드래그와 취소.
UI 렌더링 PNG는 `build/life-selection.png`, `build/life-placement.png`에 생성된다.
2026-11-17 통합 검증: JUnit 70개(실패·오류·건너뜀 0), 캡처 Swing 검사 14개, JAR 빌드 통과.

## 타 기능 연계 상태

템플릿은 이름·크기·셀의 생존 형태만 저장한다. 색·나이·프리징 상태는 저장하지 않는다.
이 브랜치는 dev와 캡처 기능을 검증하는 범위이며, 팀 기능 전체의 통합 완료를 뜻하지 않는다.

- **색상:** 캡처는 색상·나이를 저장하지 않고 살아 있는 셀 배치만 저장한다. 새로 배치된 셀은 나이 0부터 시작한다. 배치·Undo는 생존 상태가 달라지는 셀만 편집하므로 기존 셀의 나이·색상은 유지한다. 매 세대 transition과 색상 갱신도 보존한다.
- **통계:** 배치·Undo 완료 후 `TEMPLATE` / `TEMPLATE_UNDO` 알림을 연결해야 한다. 실패·취소·무변경 배치는 성공 알림을 보내지 않는다.
- **드래그:** 일반 그리기와 캡처·배치·프리징 선택의 입력을 분리하고, 일반 편집 후 Undo 이력 무효화와 완료 알림을 연결해야 한다.
- **사운드:** 세대 이벤트와 공통 보드 완료 이벤트의 관계를 맞춰야 한다. 실제 사운드 로컬 프로토타입과의 통합은 검증하지 않았다.
- **프리징:** PR #7은 원본 캡처 커밋 `cd2fdb1`을 포함한다. 이 캡처 PR의 병합 뒤에는 해당 PR의 diff와 충돌을 다시 확인해야 한다. 이 브랜치에 프리징 기능을 가져오지는 않았다.
