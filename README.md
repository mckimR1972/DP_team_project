# DP_team_project

설계 패턴 팀 프로젝트: Holub Life Game 기능 확장

## Life Game 실행 방법

JDK 17이 필요하다. 저장소 루트(`build.gradle`이 있는 폴더)에서 실행한다.

1. 소스를 컴파일한다. 첫 실행에는 Gradle Wrapper가 필요한 파일을 받으므로 인터넷 연결이 필요하다.

   macOS / Linux:

   ```sh
   ./gradlew classes
   ```

   Windows PowerShell:

   ```powershell
   .\gradlew.bat classes
   ```

2. `BUILD SUCCESSFUL`이 나오면 게임을 실행한다.

   ```sh
   java -cp build/classes/java/main com.holub.life.Life
   ```

Windows에서는 저장소 폴더의 `run.cmd`를 더블클릭하거나 터미널에서 실행해도 된다. `build.ps1`로 컴파일하고 `build\HolubLife.jar`를 만든 뒤 게임을 연다. PATH에 `java`, `javac`, `jar`가 있어야 하고, 외부 라이브러리나 Gradle 설치는 필요 없다.

```powershell
.\run.cmd
```

이미 만든 JAR만 실행하려면 저장소 폴더에서 다음을 실행한다. 사용자 템플릿(`templates/`)을 저장소 폴더 기준으로 읽기 때문이다.

```powershell
java -jar build\HolubLife.jar
```

Windows에서는 실행 중인 JAR를 바꿀 수 없으므로, 다시 빌드하기 전에 게임을 닫는다.

## JUnit 테스트 실행

JDK 17을 준비한 뒤, 저장소 루트(`build.gradle`이 있는 폴더)에서 실행한다.

macOS / Linux:

```sh
./gradlew clean test
```

Windows PowerShell:

```powershell
.\gradlew.bat clean test
```

Gradle Wrapper가 지정된 Gradle과 테스트 라이브러리를 받으므로 첫 실행에는 인터넷 연결이 필요하다. IDE에서는 저장소를 Gradle 프로젝트로 열고 테스트 클래스를 실행할 수 있다. Java Toolchain 설정으로 컴파일과 테스트에 JDK 17을 사용한다.

- 실행 환경: JDK 17, Gradle 9.5.1, JUnit Jupiter 6.0.3, Mockito 5.23.0.
- 게임 소스: `src/`, 테스트 소스: `test/`.
- HTML 테스트 보고서: `build/reports/tests/test/index.html`. 테스트 실행 후 브라우저로 열어 전체 결과와 각 테스트의 결과를 확인할 수 있다.
- XML 테스트 결과: `build/test-results/test/`.
- 빌드 결과인 `build/`와 Gradle 작업 캐시인 `.gradle/`은 Git에 포함하지 않는다.
- 캡처·템플릿 기능의 Swing 통합 검사는 데스크톱 화면이 있어야 해서 일반 테스트와 따로 실행한다.

  ```powershell
  # 게임 JAR 빌드 + JUnit 테스트
  powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Test

  # 위 테스트 + 실제 Swing 컴포넌트 통합 검사 17개
  powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -UiTest

  # Swing 통합 검사만 실행
  .\gradlew.bat patternUiTest
  ```

- 컴파일할 때 원본 코드의 deprecated API와 raw collection 경고가 나오지만 오류는 아니다.
- Gradle은 테스트 클래스마다 JVM을 따로 띄운다(`forkEvery = 1`). Universe가 Singleton이라, Universe를 쓰는 테스트 클래스끼리 한 JVM에서 돌면 충돌하기 때문이다. IDE의 JUnit 실행기로 여러 테스트 클래스를 한 번에 돌리면 Universe 테스트가 실패할 수 있으니, 이때는 Gradle로 실행한다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/fixes/fix-life-game-changes.md](docs/fixes/fix-life-game-changes.md) | 기본 오류 3가지 수정, 회귀 테스트 32개와 수정 전후 결과 |
| [docs/color-gen/color-gen.md](docs/color-gen/color-gen.md) | 세대에 따른 셀 색 순환 기능: 요구사항과 유스케이스, 바뀐 코드, 패턴, 설계 결정과 대안, 테스트 |
| [docs/color-gen/feature-color-gen-changes.md](docs/color-gen/feature-color-gen-changes.md) | 세대에 따른 셀 색 순환 기능의 변경 기록 |
| [docs/color-gen/color-gen-redraw-lag.md](docs/color-gen/color-gen-redraw-lag.md) | 색 순환 중 화면 갱신 지연: 문제 상황, 원인, 해결 방안 |
| [docs/capture/capture-templates.md](docs/capture/capture-templates.md) | 영역 캡처와 템플릿 배치 기능: 사용법, 구조와 설계 패턴, 검증, 다른 기능과의 연계 |
| [docs/capture/README.md](docs/capture/README.md) | 캡처 기능 명세·설계 결정·타 기능 접점·테스트 케이스·개별/합본 도식 PDF와 추적표 |
| [docs/fixes/fix-single-step-halt.md](docs/fixes/fix-single-step-halt.md) | 실행 중 Tick(Single Step)이 멈추지 않는 문제: 원인, 수정, 다른 방법, 수정 전후 테스트 |
| [docs/freezing/FREEZING.md](docs/freezing/FREEZING.md) | Freezing(영역 고정) 기능: 사용법, State 패턴, 다른 기능과 연결되는 부분, 추가한 패턴과 이유, 검증 |

## GitHub 사용 규칙 (팀 공통)

### 브랜치

| 브랜치 | 용도 | 규칙 |
| --- | --- | --- |
| `main` | 최종 제출용 | 중간에 한 번씩만 dev를 합쳐 갱신한다. 직접 커밋하거나 push하지 않는다 |
| `dev` | 통합 브랜치 | 직접 커밋하거나 push하지 않고, PR로만 합친다 |
| `feature/…`, `fix/…`, `docs/…` | 각자의 작업 | 최신 dev에서 만든다. 이름은 영어 소문자와 하이픈으로 쓴다(예: `feature/color-gen`) |

### 작업 순서

1. 최신 dev에서 작업 브랜치를 만든다.

   ```sh
   git switch dev
   git pull
   git switch -c feature/color-gen
   ```

2. 작업을 조각으로 나눠, 조각마다 테스트를 통과시킨 뒤 커밋한다.

   ```sh
   git add src/com/holub/life/Universe.java test/color-gen/com/holub/life/UniverseColorTest.java
   git commit -m "feat: Color 메뉴 추가"
   ```

3. 작업 브랜치를 push하고 GitHub에서 PR을 연다. base는 `dev`, compare는 작업 브랜치다.

   ```sh
   git push -u origin feature/color-gen
   ```

4. 리뷰를 받은 뒤 dev에 병합한다.

### 커밋

- 한 커밋에는 한 가지 변경만 담는다. 큰 기능은 조각으로 나눠 조각마다 커밋한다.
- 커밋하기 전에 테스트가 모두 통과하는지 확인한다.
- 메시지는 `feat:`, `fix:`, `test:`, `docs:`, `build:` 같은 머리말 뒤에 무엇을 바꿨는지 적는다.
- `git add .` 대신 바꾼 파일을 직접 적는다. 관계없는 수정이나 개인 파일이 섞이지 않게 하기 위해서다.
- push하기 전에 브랜치 이름(`git branch --show-current`)과 커밋의 작성자·메시지(`git log -1`)를 확인한다.
- Windows에서 `git add` 때 나오는 `LF will be replaced by CRLF` 경고는 줄 끝 문자를 바꾼다는 안내라서 무시해도 된다.

### PR

- 본문에 무엇을 왜 바꿨는지, 테스트 결과, 기존 기능에 미치는 영향을 적는다.
- PR을 올리기 전에 `src/`에 테스트에서만 쓰는 메서드나 필드가 없는지 확인한다(아래 "코드").
- 다른 기능과 같은 파일(예: `Universe.java`, `Resident.java`)을 고쳤다면 본문에 적어, 병합할 때 생길 충돌을 미리 알린다.
- 다른 사람의 작업 브랜치에 push하지 않는다. 공유 브랜치(`main`, `dev`)에는 force push하지 않는다.

### 코드

- **JUnit 테스트에서만 쓰이는 메서드나 필드를 실제 게임 코드(`src/`)에 절대 넣지 않는다.** 테스트는 게임이 실제로 쓰는 메서드만으로 검증하고, 테스트 도우미 코드는 `test/`에만 둔다. 그렇게 검증하기 어렵다면 테스트용 코드를 넣지 말고, 설계 문제로 보고 팀과 먼저 논의한다.
- 기존 Life Game 기능은 원본과 똑같이 동작해야 한다. 새 기능은 사용자가 켰을 때만 동작하게 하고, 기본 상태는 원본과 같게 둔다.

### 올리지 않는 파일

- `build/`, `.gradle/`, `*.class`, 사용자가 저장한 템플릿(`templates/`)은 `.gitignore`로 제외되어 있다.
- 개인 메모나 개인 도구의 설정 파일은 `.gitignore`가 아니라 각자 `.git/info/exclude`에 적어 제외한다. `.gitignore`는 팀 전체가 함께 쓰는 파일이기 때문이다.
