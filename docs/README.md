# 프로젝트 문서 안내

기능별 설계·검증 자료를 찾기 위한 목차다. 저장소에 강제된 공통 문서 양식은 없으며, 기존 문서는 기능 또는 문제별 Markdown 파일로 관리돼 왔다.

| 영역 | 현재 문서 | 성격 |
|---|---|---|
| 캡처 공유용 | [capture.md](capture/capture.md) | 팀 보고서용 기능 명세·GoF·유스케이스·UML·연산 계약·대표 테스트 |
| 캡처 상세 | [캡처 상세 문서](capture/README.md) | 설계 결정·통합·전체 테스트·도식·검증 근거 |
| 세대별 색상 | [feature-color-gen-changes.md](color-gen/feature-color-gen-changes.md) | 변경 이유·대안·검증 |
| 화면 갱신 지연 | [color-gen-redraw-lag.md](color-gen/color-gen-redraw-lag.md) | 원인 분석과 대안 비교 |
| 기본 오류 | [fix-life-game-changes.md](fixes/fix-life-game-changes.md) | 기존 코드 오류 수정과 회귀 검사 |
| 사운드 이펙트 | [sound-effects.md](sound-effects/sound-effects.md) | 기능 요약, 기존 코드와 비교, 패턴·대안, 테스트 |
| 단일 진행 오류 | [fix-single-step-halt.md](fixes/fix-single-step-halt.md) | 실행 중 Tick이 멈추지 않던 원본 오류의 원인, 수정, 테스트 |
| Freezing (영역 고정) | [FREEZING.md](freezing/FREEZING.md) | 사용법, State 패턴, 다른 기능과 연결되는 부분, 추가한 패턴과 이유, 테스트 |

캡처 문서 묶음에서는 요구사항 → 설계 결정 → 코드 → 테스트 → 도식의 ID를 연결한다. 이는 캡처 자료의 정리 방식이며, 다른 팀원의 문서 형식을 일괄 변경하는 규칙은 아니다.

통계 문서는 해당 기능 PR과 함께 확인한다. 미병합 브랜치의 구현은 현재 dev에 존재하는 기능과 구분한다.
