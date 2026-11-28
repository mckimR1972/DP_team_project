# 프로젝트 문서 안내

기능별 설계·검증 자료를 찾기 위한 목차다. 저장소에 강제된 공통 문서 양식은 없으며, 기존 문서는 기능 또는 문제별 Markdown 파일로 관리돼 왔다.

| 영역 | 현재 문서 | 성격 |
|---|---|---|
| 전체 유스케이스 | [usecase-overview.png](usecase-overview.png) | 원본 유스케이스와 다섯 확장 기능의 유스케이스와 관계를 한 장에 그린 다이어그램 |
| 캡처 공유용 | [capture.md](capture/capture.md) | 팀 보고서용 기능 명세·GoF·유스케이스·UML·연산 계약·대표 테스트 |
| 캡처 상세 | [캡처 상세 문서](capture/README.md) | 설계 결정·통합·전체 테스트·도식·검증 근거 |
| 세대별 색상 | [color-gen/color-gen.md](color-gen/color-gen.md) | 기능 요약, 기존 코드와 비교, 패턴·대안, 테스트 |
| 화면 갱신 지연 | [color-gen.md 4.4절](color-gen/color-gen.md#44-판-전체를-다시-그리는-방법-화면-갱신-지연) | 원인 분석과 대안 비교 |
| 원본 대비 개선 | [original-improvements.md](original-improvements.md) | 원본 코드의 오류 수정, 구조·설계 개선, 원본 기능의 테스트 |
| 사운드 이펙트 | [sound-effects.md](sound-effects/sound-effects.md) | 기능 요약, 기존 코드와 비교, 패턴·대안, 테스트 |
| 사운드 기능 명세 | [sound-effects-spec.md](sound-effects/sound-effects-spec.md) | 유스케이스 명세, 요구사항, 적용·미적용 GoF 패턴, 요구사항별 테스트 |
| 사운드 UML | [sound-uml.md](sound-effects/sound-uml.md) | 클래스(전·후), 시퀀스, 상태 다이어그램, 시스템 시퀀스와 연산 계약 |
| 사운드 보고서용 정리 | [sound-report.md](sound-effects/sound-report.md) | 팀 보고서의 사운드 칸(목적, 변경 전후, 패턴, 대안, UML, 테스트)과 공통 장의 사운드 부분 |
| Freezing (영역 고정) | [freezing.md](freezing/freezing.md) | 기능 명세와 유스케이스, 바뀐 코드, UML 다이어그램, 패턴·설계 결정, 테스트 |
| 드래그 그리기 | [draw-cells-by-dragging.md](drag-drawing/draw-cells-by-dragging.md) | 유스케이스와 명세, 바뀐 코드, 다른 기능과 맞물린 부분, 테스트 |

캡처 문서 묶음에서는 요구사항 → 설계 결정 → 코드 → 테스트 → 도식의 ID를 연결한다. 이는 캡처 자료의 정리 방식이며, 다른 팀원의 문서 형식을 일괄 변경하는 규칙은 아니다.

통계 기능의 설계·구현·검증은 [통계 문서](statistics/statistics.md)에서 확인한다.
