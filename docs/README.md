# 프로젝트 문서 안내

기능별 설계·검증 자료를 찾기 위한 목차다. 저장소에 강제된 공통 문서 양식은 없으며, 기존 문서는 기능 또는 문제별 Markdown 파일로 관리돼 왔다.

| 영역 | 현재 문서 | 성격 |
|---|---|---|
| 캡처·템플릿 | [캡처 문서 묶음](capture/README.md) | 기능 명세, 설계 결정, 연계, 테스트, 도식의 추적 시작점 |
| 캡처 사용법 | [capture-templates.md](capture-templates.md) | 사용법과 기존 설명 |
| 캡처 드래그 구현 이력 | [capture-drag-design.md](capture-drag-design.md) | PR #13 당시의 설계·시험 병합 기록 |
| 세대별 색상 | [feature-color-gen-changes.md](feature-color-gen-changes.md) | 변경 이유·대안·검증 |
| 화면 갱신 지연 | [color-gen-redraw-lag.md](color-gen-redraw-lag.md) | 원인 분석과 대안 비교 |
| 기본 오류 | [fix-life-game-changes.md](fix-life-game-changes.md) | 기존 코드 오류 수정과 회귀 검사 |
| 사운드 이펙트 | [sound-effects.md](sound-effects.md) | 기능 요약, 기존 코드와 비교, 패턴·대안, 테스트 |
| 사운드 기능 명세 | [sound-effects-spec.md](sound-effects-spec.md) | 유스케이스 명세, 요구사항, 적용·미적용 GoF 패턴, 요구사항별 테스트 |
| 사운드 UML | [sound-uml.md](sound-uml.md) | 클래스(전·후), 시퀀스, 상태 다이어그램, 시스템 시퀀스와 연산 계약 |
| 단일 진행 오류 | [fix-single-step-halt.md](fix-single-step-halt.md) | 실행 중 Tick이 멈추지 않던 원본 오류의 원인, 수정, 테스트 |
| Freezing (영역 고정) | [FREEZING.md](FREEZING.md) | 사용법, State 패턴, 다른 기능과 연결되는 부분, 추가한 패턴과 이유, 테스트 |

캡처 문서 묶음에서는 요구사항 → 설계 결정 → 코드 → 테스트 → 도식의 ID를 연결한다. 이는 캡처 자료의 정리 방식이며, 다른 팀원의 문서 형식을 일괄 변경하는 규칙은 아니다.

통계 문서는 해당 기능 PR과 함께 확인한다. 미병합 브랜치의 구현은 현재 dev에 존재하는 기능과 구분한다.
