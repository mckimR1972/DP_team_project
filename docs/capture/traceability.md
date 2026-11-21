# 요구·설계·코드·검사·그림 추적표

기준: dev `838182c` / 2026-11-22. [도식 합본](diagrams/capture-diagrams.pdf)은 표지·목차 1쪽과 D-01~D-06의 6쪽으로 구성한다. 각 개별 PDF는 한 페이지이며 합본에서도 같은 그림을 사용한다.

## 1. 도식 목차

| ID / 합본 쪽 | 도식 | 편집 원본 | 개별 PDF | 주요 연결 |
|---|---|---|---|---|
| D-01 / 2 | 유스케이스 | [PlantUML](diagrams/01-use-cases.puml) | [PDF](diagrams/pdf/01-use-cases.pdf) | E-01~E-05, R-01~R-09 |
| D-02 / 3 | 클래스 책임·협력 | [PlantUML](diagrams/02-classes.puml) | [PDF](diagrams/pdf/02-classes.pdf) | ADR-01~ADR-08 |
| D-03 / 4 | 캡처·저장 시퀀스 | [PlantUML](diagrams/03-capture-save.puml) | [PDF](diagrams/pdf/03-capture-save.pdf) | E-01, R-01,R-02, T-01,T-02,T-05~T-07 |
| D-04 / 5 | 연속 배치·Undo 시퀀스 | [PlantUML](diagrams/04-stroke-undo.puml) | [PDF](diagrams/pdf/04-stroke-undo.pdf) | E-02~E-04, ADR-03~ADR-05,ADR-07 |
| D-05 / 6 | 편집·입력 작업 상태 | [PlantUML](diagrams/05-edit-states.puml) | [PDF](diagrams/pdf/05-edit-states.pdf) | E-05, ADR-06, T-18~T-22 |
| D-06 / 7 | 타 기능 설계 접점 | [PlantUML](diagrams/06-integration.puml) | [PDF](diagrams/pdf/06-integration.pdf) | IF-01~IF-06, IT-01~IT-06 |

SVG는 [diagrams/svg](diagrams/svg)에 같은 이름으로 저장한다. 원본·테마·개별 PDF·SVG의 내용 해시는 [artifacts.json](diagrams/artifacts.json)에 있다. 생성 방법은 [도식 안내](diagrams/README.md)에 있다.

## 2. 요구사항에서 근거 찾기

| 요구 | 설계 결정 | 구현의 주요 진입점 | 대표 검사 | 도식 |
|---|---|---|---|---|
| R-01 생존 모양 캡처 | ADR-01 | BoardEditor.capture / PatternTemplate | T-01,T-02,T-04 | D-01,D-03 |
| R-02 검증·저장 | ADR-08 | PatternLibrary.save/read | T-05~T-07 | D-02,D-03 |
| R-03 비파괴 배치 | ADR-01~ADR-03 | PatternCells.setAlive / PlacePatternCommand.execute | T-03,T-09,T-24 | D-02,D-04 |
| R-04 반복·보간 | ADR-04 | PatternTools의 mouse/Timer / PatternPath.visit | T-13,T-16~T-18 | D-04,D-05 |
| R-05 배치 제약 | ADR-07 | BoardEditor.fits/canPlace/place | T-04,T-15,T-21,T-23 | D-04,D-06 |
| R-06 Undo | ADR-03,ADR-05 | BoardEditor.beginStroke/endStroke/undo | T-10~T-12,T-19 | D-02,D-04 |
| R-07 종료·취소 | ADR-06 | PatternTools.stopStamping/cancel | T-20~T-22 | D-05 |
| R-08 기존 기능 보존 | ADR-02,ADR-06 | Universe tick/setCell/Clear/Load | T-08,T-22,T-24,T-25 + 기존 회귀 | D-02,D-05,D-06 |
| R-09 완료 알림 | ADR-05,ADR-07 | BoardEditor.onEdit → JPanel PropertyChange | T-14,T-23 | D-04,D-06 |

소스는 [캡처 패키지](../../src/com/holub/life), 검사 메서드는 [케이스 표](test-cases.md)에서 찾는다. 행 번호는 코드 이동에 따라 바뀌므로 클래스·메서드 이름과 기준 커밋으로 추적한다.

## 3. 과제 제출물과의 연결

과제 안내 PDF의 1~2쪽을 읽고 아래 산출물에 대응시켰다. 원문은 사용자가 제공한 `DP Term Project 2026 Life Game - updated-4.pdf`이며, 원문 자체는 이 저장소에 재배포하지 않는다.

| 과제 요구 | 현재 자료 | 최종 제출 때 남은 일 |
|---|---|---|
| 확장 기능 E 표기 유스케이스 | feature-spec, D-01 | 팀 전체 유스케이스와 중복·액터 정리 |
| 변경 전후·패턴·선택 이유·대안 | design의 구현 이력, ADR-01~08, D-02~05 | 담당자가 실제 결정과 구현을 설명할 수 있게 검토 |
| 테스트 목적·코드·결과·기존 기능 유지 | test-plan, test-cases, evidence 결과/이미지 | 통합된 기준의 IT 검사 결과 확인과 팀 최종본 반영 |
| GitHub 활용 1~3쪽·팀원별 기여 자료 | 코드 PR #9/#13, 문서 브랜치의 단위 커밋, 추적표 | 팀 전체 기여 표와 진행 이력 화면 취합. 커밋 수를 품질 점수로 환산하지 않음 |
| 프로젝트 문서 PDF 50쪽 이내 | 이 Markdown 묶음과 그림 6개 | 팀 문서에 필요한 내용을 편집·통합하고 전체 페이지 수 관리 |
| 발표·영상에서 설계와 테스트 설명 | 각 그림의 코드/검사 연결 | 팀 발표용 PDF·영상은 별도 제작. 이 도식 PDF 자체가 최종 발표물은 아님 |

## 4. 이번 문서 커밋의 단위

1. 기능 명세와 E 유스케이스
2. 설계 발전 과정과 ADR
3. 타 기능 접점과 미병합 PR 상태
4. 테스트 케이스 선정 이유와 실제 실행 증거
5. D-01 유스케이스 원본·개별 PDF
6. D-02 클래스 원본·개별 PDF
7. D-03 캡처 저장 시퀀스 원본·개별 PDF
8. D-04 연속 배치/Undo 시퀀스 원본·개별 PDF
9. D-05 상태도 원본·개별 PDF
10. D-06 타 기능 접점 원본·개별 PDF
11. 합본·전체 목차·상호 참조와 최종 가독성 검증

개별 커밋은 완결된 설명 또는 도식을 추가하는 단위다. 임의의 시간 간격이나 문장 수를 만들기 위해 분할한 것이 아니다. 커밋 hash는 파일 안에 자기 자신을 기록하지 않고 `git log -- docs/capture`에서 확인한다.

## 5. 문서를 갱신할 때

1. 변경된 기능의 R/ADR/IF와 해당 코드 기준을 갱신한다.
2. 테스트 목적·절차·기대값을 확인하고 필요 시 실제 검사를 수행한다. 새 결과를 과거 실행 기록에 덮어쓰지 않는다.
3. 영향받은 `.puml`과 manifest를 수정하고 개별 PDF/SVG를 생성·검토한다.
4. 합본을 다시 만들고 `verify_documents.py`로 로컬 링크·파일 해시·PDF 페이지·합본 일치를 검사한다.
5. 변경 이유와 검증 범위를 적어 커밋한다. 팀 최종 문서에 반영한 위치도 추적표에 추가한다.
