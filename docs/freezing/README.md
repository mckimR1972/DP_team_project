# Freezing 문서 묶음

Freezing(영역 고정) 기능의 명세와 설계 설명을 주제별로 나눈 문서다. 기능 전체의 통합 기록과 테스트 결과는 기존 [../FREEZING.md](../FREEZING.md)에 있고, 이 폴더는 그 내용을 과제 문서 항목에 맞춰 다시 정리한 것이다.

- 코드 기준: dev `838182c` (PR #7 병합 후, main `4dc39e6`과 같은 트리)
- 구현 PR: [#7 Feature/freezing](https://github.com/mckimR1972/DP_team_project/pull/7)

| 문서 | 내용 |
| --- | --- |
| [usecase-and-summary.md](usecase-and-summary.md) | 유스케이스 명세 5개(ID, 기능 이름, 액터, 목표, 진입·트리거, 기본 흐름, 대안·예외, 사전·사후 조건), 확장된 기능과 설계 개선 요약 |
| [changes.md](changes.md) | 상세 변경 내용, 기존 설계·코드와의 비교 |
| [design.md](design.md) | 설계 패턴 후보 표(장단점), 설계 결정과 이유·과정, 클래스·시퀀스·상태 다이어그램 |

`changes.md`와 `design.md`는 각각 별도 PR로 올라온다. 다이어그램은 Mermaid로 문서 안에 적었으며 GitHub에서 그림으로 보인다.
