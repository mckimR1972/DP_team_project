# DP_team_project
설계 패턴 팀프로젝트, life game  기능 확장

## 기본 오류 수정

소스 코드 수정 커밋: `d3f4478` (`fix: correct cell state reporting and remove duplicate save`)

이번 변경은 아래 세 가지 오류를 수정한다. 게임의 생존 규칙과 기존 메서드 이름은 유지한다.

| 대상 | 수정 전 | 수정 후 |
| --- | --- | --- |
| `Resident.transition()` | 상태가 유지되면 `true`, 달라지면 `false` 반환 | 상태가 달라지면 `true`, 유지되면 `false` 반환 |
| `Universe.doStore()` | 살아 있는 셀의 좌표를 같은 스냅샷에 두 번 수집 | 스냅샷 생성 시 한 번만 수집 |
| `Neighborhood.isAlive()` | 하위 셀 상태와 관계없이 항상 `true` 반환 | 하위 셀 중 하나라도 살아 있으면 `true`, 모두 죽어 있으면 `false` 반환 |

### 1. Resident.transition(): 상태 변화 여부 반환값 수정

`Cell.transition()`의 계약은 상태가 바뀌었을 때 `true`를 반환하는 것이다. 기존 코드는 현재 상태와 다음 상태가 같은지를 나타내는 `isStable()`을 그대로 반환하여 결과가 반대였다.

수정 전:

```java
public boolean transition() {
    boolean changed = isStable();
    amAlive = willBeAlive;
    return changed;
}
```

수정 후:

```java
public boolean transition() {
    boolean changed = amAlive != willBeAlive;
    amAlive = willBeAlive;
    return changed;
}
```

현재 상태를 덮어쓰기 전에 차이를 계산한다. 생존/사망 상태를 갱신하는 처리 자체는 기존에도 수행됐으며, 이번 수정은 상위 `Neighborhood`에 전달하는 변화 여부를 바로잡는다.

| 현재 상태 → 다음 상태 | 수정 전 반환값 | 수정 후 반환값 |
| --- | --- | --- |
| 사망 → 사망 | `true` | `false` |
| 생존 → 생존 | `true` | `false` |
| 사망 → 생존 | `false` | `true` |
| 생존 → 사망 | `false` | `true` |

### 2. Universe.doStore(): 중복 상태 수집 제거

`Neighborhood.createMemento()`는 내부에서 이미 `transfer(..., Cell.STORE)`를 호출하여 살아 있는 셀의 좌표를 수집한다. `doStore()`에서 이를 다시 호출하면 같은 좌표가 두 번 추가된다.

수정 전의 저장 처리:

```java
Storable memento = outermostCell.createMemento();
outermostCell.transfer(memento, new Point(0, 0), Cell.STORE);
memento.flush(out);
```

수정 후의 저장 처리:

```java
Storable memento = outermostCell.createMemento();
memento.flush(out);
```

살아 있는 셀이 하나라면 저장되는 좌표 항목이 기존 2개에서 1개로 줄어든다. `flush(out)`은 수집된 스냅샷을 출력 스트림에 기록하므로 유지한다. 불러오기에서 사용하는 `transfer(..., Cell.LOAD)`는 스냅샷을 실제 보드에 적용하는 별도 작업이므로 유지한다.

### 3. Neighborhood.isAlive(): 하위 셀의 생존 여부 반영

`Cell.isAlive()`의 계약에 따라 자신 또는 하위 셀 중 하나라도 살아 있는지 판단해야 한다. 기존 구현은 빈 보드도 살아 있다고 반환했다.

수정 전:

```java
public boolean isAlive() {
    return true;
}
```

수정 후:

```java
public boolean isAlive() {
    for (int row = 0; row < gridSize; ++row)
        for (int column = 0; column < gridSize; ++column)
            if (grid[row][column].isAlive())
                return true;
    return false;
}
```

하위 객체가 `Resident`이면 단일 셀의 상태를 확인하고, `Neighborhood`이면 그 내부를 재귀적으로 확인한다. 따라서 중첩된 셀 묶음에서도 같은 기준으로 동작한다.

## JUnit 테스트

### 실행 방법

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

### 테스트 목적과 기대 결과

입력별 실행 횟수를 포함하여 총 32개를 검사한다. 테스트 메서드는 영어 이름으로 작성한다.

| 테스트 클래스 / 메서드 | 준비 및 실행 | 기대 결과 | 현재 코드 실행 결과 |
| --- | --- | --- | --- |
| `ResidentTest.transitionReturnsFalseWhenUnchanged` | 죽은 셀의 이웃 수가 3개가 아닌 경우, 살아 있는 셀의 이웃 수가 2·3개인 경우 | `transition()`이 `false`를 반환하고 생존 상태 유지 | 10개 모두 통과 |
| `ResidentTest.transitionReturnsTrueWhenChanged` | 죽은 셀의 이웃 수가 3개인 경우, 살아 있는 셀의 이웃 수가 2·3개가 아닌 경우 | `transition()`이 `true`를 반환하고 생존 상태 변경 | 8개 모두 통과 |
| `NeighborhoodTest.isAliveReturnsFalseForEmptyNeighborhood` / `isAliveReturnsFalseForEmptyNestedNeighborhood` | 단일/중첩 셀 묶음을 빈 상태로 생성 | `isAlive()`가 `false` 반환 | 2개 모두 통과 |
| `NeighborhoodTest.isAliveReturnsTrueWithLivingChild` / `isAliveReturnsTrueWithLivingNestedChild` | 단일/중첩 셀 묶음의 모서리와 내부 위치에 셀 하나를 살림 | 위치와 중첩 여부에 관계없이 `true` 반환 | 6개 모두 통과 |
| `NeighborhoodTest.isAliveReturnsFalseAfterLastLivingChildDies` | 마지막 생존 셀을 클릭하여 죽임 | `false` 반환 | 통과 |
| `NeighborhoodTest.isAliveReturnsTrueWhileAnotherChildRemainsAlive` | 서로 다른 위치의 두 셀 중 하나만 죽임 | 다른 셀이 살아 있으므로 `true` 반환 | 통과 |
| `NeighborhoodTest.isAliveReturnsFalseAfterClear` | 살아 있는 셀이 있는 보드에 `clear()` 호출 | `false` 반환 | 통과 |
| `UniverseStoreTest.saveWritesEachLiveCellOnce` | 실제 Store 동작으로 살아 있는 셀 1개 / 4개를 파일에 저장 | 좌표 항목이 각각 1개 / 4개이고 실제 생존 좌표와 일치 | 2개 모두 통과 |
| `UniverseStoreTest.saveWritesNoCoordinatesForEmptyBoard` | 빈 보드를 실제 Store 동작으로 저장 | 저장된 좌표 항목이 0개 | 통과 |

`ResidentTest`는 살아 있는 이웃 수 0~8개와 현재 생존/사망 상태를 조합한 18개 경우를 확인한다. 다음 상태를 계산하는 동안 현재 상태가 유지되는지, `transition()`의 반환값과 적용된 생존 상태가 올바른지를 검사한다.

`UniverseStoreTest`는 등록된 실제 Store 메뉴 콜백을 실행하여 `Universe.doStore()`가 만든 파일을 읽는다. `createMemento()`만 별도로 호출하는 테스트가 아니므로 `doStore()`의 중복 `STORE` 호출을 검출할 수 있다. 파일의 좌표 항목 개수와 좌표 집합을 함께 검사하여 중복뿐 아니라 잘못된 좌표 저장도 확인한다.

저장 테스트에서는 창을 띄우지 않도록 Mockito로 메뉴 등록, 파일 선택 결과, 시계만 대체한다. 실제 보드, 마우스 입력 처리, Store 콜백, `doStore()`, 스냅샷 및 파일 쓰기는 원래 코드를 실행한다. Swing 작업과 해당 테스트 대체 객체의 사용은 이벤트 처리 스레드(EDT)에서 수행하고, 테스트마다 보드를 초기화한다. 파일 선택창 자체의 화면 동작은 이 테스트 범위에 포함하지 않는다.

### 수정 전후 실행 결과

2026-10-31에 Gradle 9.5.1 / JDK 17 환경에서 같은 JUnit 테스트를 다음 두 소스 버전에 다시 실행했다. 아래 표는 두 버전을 모두 Gradle로 실행한 결과이다.

- 수정 전: 원본 소스 커밋 `cdcedf3`의 별도 복사본에 동일한 테스트와 Gradle 설정을 적용.
- 수정 후: 오류 수정 커밋 `d3f4478`의 소스. 이번 Gradle 전환에서 게임 소스와 JUnit 테스트 코드를 변경하지 않음.

| 테스트 클래스 | 검사 수 | 수정 전 통과 / 실패 | 수정 후 통과 / 실패 |
| --- | ---: | ---: | ---: |
| `ResidentTest` | 18 | 0 / 18 | 18 / 0 |
| `NeighborhoodTest` | 11 | 7 / 4 | 11 / 0 |
| `UniverseStoreTest` | 3 | 1 / 2 | 3 / 0 |
| **합계** | **32** | **8 / 24** | **32 / 0** |

수정 전에는 다음과 같은 실패가 발생했다.

- 상태가 유지되는 셀에서 기대값 `false` 대신 `true`, 상태가 바뀌는 셀에서 기대값 `true` 대신 `false` 반환.
- 빈 셀 묶음, 마지막 생존 셀을 죽인 보드, `clear()`한 보드에서 기대값 `false` 대신 `true` 반환.
- 셀 1개 저장 시 좌표 항목이 기대값 1개 대신 2개, 셀 4개 저장 시 기대값 4개 대신 8개 기록.

수정 후에는 모든 결과가 기대값과 일치했다. 두 실행 모두 테스트 실행 오류와 건너뛴 검사는 0개였으며, 수정 전 실패는 위 동작의 검증 실패였다. 기존에도 정상 동작하던 8개 검사는 수정 후에도 통과했다.

수정 후 코드에서 `./gradlew clean test --no-daemon --console=plain`을 실행하여 `BUILD SUCCESSFUL`을 확인했다. Gradle XML 보고서의 집계 결과는 **32개 실행, 실패 0개, 오류 0개, 건너뜀 0개**이다.

도구 사용 안내: [JUnit 공식 문서](https://docs.junit.org/current/user-guide/), [Gradle 테스트 공식 문서](https://docs.gradle.org/current/userguide/java_testing.html), [Gradle Wrapper 공식 문서](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
