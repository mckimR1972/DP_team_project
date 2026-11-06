# DP_team_project

Allen Holub의 Java Swing 라이프게임에 **영역 캡처와 템플릿 재배치**를 추가한 로컬 작업본입니다.
기준 브랜치: `dev`, 기준 커밋: `cdcedf3` (`Add Holub Life game source`).

## 실행

Windows에서 이 폴더의 **`run.cmd`를 더블클릭**하면 컴파일 후 게임이 열립니다.
JDK 17 이상과 PATH에 등록된 `java`, `javac`, `jar`가 필요합니다. 작업 PC에서는 JDK 17.0.6으로 확인했습니다.
외부 라이브러리나 Maven/Gradle 설치는 필요하지 않습니다.

터미널에서는 저장소 폴더를 연 뒤 다음과 같이 실행합니다.

```powershell
.\run.cmd
```

빌드한 JAR만 실행하려면 다음 명령을 사용합니다. 사용자 템플릿 경로가 일정하도록 저장소 폴더에서 실행하세요.

```powershell
java -jar build\HolubLife.jar
```

게임을 종료한 뒤 다시 빌드하세요. Windows에서는 실행 중인 JAR 파일을 교체할 수 없습니다.

## 사용

1. 보드의 셀을 클릭해 살아 있는 셀을 그립니다. 보드는 64 x 64입니다.
2. **영역 캡처**를 누르고 보드에서 드래그합니다. 역방향 드래그도 가능하며, 바깥으로 드래그하면 보드 끝에 맞춰집니다.
3. **선택 영역 저장**을 누르고 이름을 입력합니다. 선택한 사각형의 크기와 빈 여백도 유지됩니다.
4. 저장 직후 보드를 클릭하면 캡처한 패턴이 배치됩니다. 목록에서 다른 템플릿을 고른 뒤 **선택한 템플릿 배치**를 눌러도 됩니다.
5. 클릭한 칸이 패턴의 왼쪽 위가 됩니다. 초록 미리보기는 배치 가능, 빨강은 보드 바깥입니다. 클릭을 반복하면 여러 번 배치합니다.
6. **배치 되돌리기** 또는 `Ctrl+Z`로 최근 배치를 되돌립니다. 최대 100회까지 보관합니다.
7. **Esc**, 오른쪽 클릭, **편집 끝내기**로 일반 그리기 모드로 돌아갑니다. **Go > Slow / Medium / Fast**로 진행하고 **Halt**로 멈춥니다. **Tick (Single Step)**은 한 세대만 진행합니다.

캡처·배치 모드에서는 시뮬레이션이 멈춥니다. 편집을 끝낸 뒤 Go 메뉴에서 다시 시작하세요.
템플릿의 살아 있는 셀만 추가하므로 빈 칸이 기존 셀을 지우지 않습니다.
패턴이 보드 밖으로 나가면 전체 배치를 거부합니다. 빈 영역과 중복 이름은 저장하지 않습니다.
일반 셀 클릭, 세대 진행, Grid > Clear / Load 이후에는 오래된 배치 이력을 비웁니다.
기존 **Grid > Store / Load**는 전체 보드를 `.life` 파일로 저장하고 불러오는 별도 기능입니다.

기본 템플릿은 Glider, Blinker, Block, Toad, Beacon입니다.
사용자가 저장한 패턴은 저장소의 `templates/*.lifepattern`에 남으며 다음 실행 때 자동으로 읽습니다.
`templates/`와 `build/`는 Git에서 제외됩니다. 새 템플릿 파일은 UTF-8 텍스트이며 `O`는 살아 있음, `.`은 빈 칸입니다.
테스트나 별도 보관 폴더가 필요하면 `java "-Dlife.templates=경로" -jar build\HolubLife.jar`로 지정할 수 있습니다.

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

이번 확장은 기존 Memento를 재사용하고 배치를 작은 Command 객체로 분리했습니다.
PatternTemplate은 재사용할 데이터이며, 템플릿 기능을 위해 Template Method 상속 구조를 추가하지 않았습니다.
선택 모드는 작은 enum으로 처리합니다.

상태 일관성을 위해 초기 UI 생성과 Clock 갱신을 Swing 이벤트 스레드로 통일했습니다.
기존 `Resident.transition()`의 변경 여부 반환을 바로잡고, 상태 복원 직후 모든 Neighborhood가 다음 세대를 다시 계산하도록 했습니다.
이 변경은 템플릿 배치 후 블록 경계에서 생겨야 할 셀을 누락하지 않게 합니다.

## 검증

```powershell
# 빌드 + 핵심 동작 테스트 10개
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Test

# 위 테스트 + 실제 Swing 컴포넌트 통합 검사 14개
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -UiTest
```

핵심 검증: 영역 좌표·여백, 비파괴 캡처, 병합 배치, 중첩·중복 배치, 되돌리기,
경계 거부, 저장 후 재로딩, 손상 파일 격리, 저장 실패 시 목록 유지, 불변 데이터.
글라이더 40세대와 임의 보드 360세대를 독립적인 Life 계산 코드와 비교합니다.

Swing 검증: 실제 버튼과 이벤트를 통한 배치, 역방향 드래그, 저장 대화상자,
저장 패턴 재배치, Ctrl+Z 동작, Esc 이후 그리기, 한 세대 진행, 경계 드래그와 취소.
UI 렌더링 PNG는 `build/life-selection.png`, `build/life-placement.png`에 생성됩니다.
원본 코드의 deprecated API 및 raw collection 경고는 남아 있으며 컴파일 오류는 아닙니다.
