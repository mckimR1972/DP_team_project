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

## 검증 현황

### 완료한 검증

수정 당시 Java 17로 전체 Java 소스 14개를 컴파일하고, 임시 Java 검증 프로그램으로 다음 항목을 확인했다.

- `Resident`: 생존/사망 상태 각각에서 살아 있는 이웃 수 0~8개에 대한 다음 상태와 `transition()` 반환값.
- `Neighborhood`: 단일/중첩 셀 묶음의 빈 상태, 각 위치의 셀을 살리거나 다시 죽인 상태, `clear()` 후 상태.
- 스냅샷: `createMemento()` → `flush()` 경로의 좌표 중복 여부와 저장/복원 후 보드 상태 일치 여부.
- 세대 진행: 블록, 블링커, 글라이더가 각각 20세대 동안 별도로 계산한 기본 규칙의 결과와 일치하는지.

위 검증은 모두 통과했다. 기존 소스의 deprecated API 및 unchecked 연산에 대한 컴파일 경고는 남아 있다.

이 검증은 JUnit이 아닌 임시 Java 프로그램으로 수행했으며, 검증 프로그램은 저장소에 포함되어 있지 않다. `Universe.doStore()`의 파일 선택창을 포함한 실제 UI 저장 경로는 자동 실행하지 않았다. 따라서 스냅샷 검증 통과를 실제 UI 저장 경로 전체의 테스트 통과로 해석하지 않는다.

### 추가할 JUnit 테스트

현재 저장소에는 아래 JUnit 테스트가 아직 구현되어 있지 않다. 테스트 메서드 이름은 영어 `camelCase`로 작성하고, 검증 대상과 기대 동작을 표현한다. 실제 게임 메서드인 `transition()`, `isAlive()`, `doStore()`의 이름을 변경하는 것은 아니다.

| 테스트 메서드 이름(예정) | 검증 내용 | 수정 전/후 기대 결과 |
| --- | --- | --- |
| `transitionReturnsFalseWhenUnchanged` | 사망→사망, 생존→생존일 때 `false` 반환 | 실패 → 통과 |
| `transitionReturnsTrueWhenChanged` | 사망→생존, 생존→사망일 때 `true` 반환 | 실패 → 통과 |
| `isAliveReturnsFalseForEmptyNeighborhood` | 모든 하위 셀이 죽어 있으면 `false` 반환 | 실패 → 통과 |
| `isAliveReturnsFalseForEmptyNestedNeighborhood` | 중첩된 셀 묶음도 모두 죽어 있으면 `false` 반환 | 실패 → 통과 |
| `isAliveReturnsTrueWithLivingChild` | 하위 셀 중 하나라도 살아 있으면 `true` 반환 | 통과 → 통과; 정상 동작 유지 확인 |
| `saveWritesEachLiveCellOnce` | 실제 저장 경로로 기록한 파일에 살아 있는 셀의 좌표가 한 번씩만 포함됨 | 실패 → 통과 |

`transition()` 테스트는 최종 생존 상태뿐 아니라 반환값도 검사해야 원래 오류를 발견할 수 있다.

저장 테스트는 실제 저장 경로를 실행한 뒤 파일 안의 좌표 항목 개수와 중복 여부를 검사해야 한다. 저장 후 불러온 화면만 비교하면 중복 좌표가 있어도 같은 보드로 보일 수 있다. 또한 `createMemento()`만 검사하는 테스트는 기존 코드에서도 통과하므로, `doStore()`의 추가 `STORE` 호출 오류를 검출하지 못한다.

현재 `doStore()`는 파일 선택창과 파일 쓰기가 결합되어 있다. 코드 구조를 유지한다면 UI를 포함한 통합 테스트가 필요하며, 이후 파일 처리 책임을 분리할 때 실제 저장 처리를 JUnit에서 직접 호출할 수 있도록 구성할 수 있다.
