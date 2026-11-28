# 통계 다이어그램

[통계 설계 문서](../statistics.md)는 아래 PNG 파일을 참조한다. 다이어그램을 수정할 때는 원본을 먼저 고친 뒤 PNG를 다시 생성한다. 본문에는 다이어그램 원본 코드를 중복해서 넣지 않는다.

| 다이어그램 | 수정용 원본 | 문서 삽입 이미지 |
| --- | --- | --- |
| 유스케이스 | [statistics-use-cases.puml](statistics-use-cases.puml) | [statistics-use-cases.png](statistics-use-cases.png) |
| 클래스 | [statistics-classes.puml](statistics-classes.puml) | [statistics-classes.png](statistics-classes.png) |
| 시퀀스 | [statistics-sequence.mmd](statistics-sequence.mmd) | [statistics-sequence.png](statistics-sequence.png) |
| 상태 | [statistics-state.puml](statistics-state.puml) | [statistics-state.png](statistics-state.png) |

유스케이스 그림은 전체 그림에서 통계 조회와 세대 진행의 관계를 발췌한다. 통계 조회 이름은 `View Statistics(E)`로 통일하고, 기본 통계와 색상 분포는 본문의 조회 항목으로 설명한다.

상태 그림은 Statistics의 최근 집계 결과를 기준으로 탄생·사망 표시 가능 여부의 전이를 나타낸다. 별도의 상태 클래스가 아니라 `currentSummary`와 `hasGenerationDelta` 값을 기준으로 구분한다.

## 유스케이스·클래스·상태 PNG 다시 생성

PlantUML 1.2026.10과 Java 21 이상을 사용한다. 다이어그램 렌더링용 Java 버전이며, 프로젝트의 Java toolchain 17 설정은 그대로 둔다. 실행 파일을 준비한 뒤 저장소 루트에서 다음 명령을 실행한다. `/path/to/plantuml-1.2026.10.jar`는 실제 파일 위치로 바꾼다.

```bash
java -Djava.awt.headless=true -jar /path/to/plantuml-1.2026.10.jar -charset UTF-8 -tpng docs/statistics/diagrams/statistics-use-cases.puml docs/statistics/diagrams/statistics-classes.puml docs/statistics/diagrams/statistics-state.puml
```

유스케이스와 클래스는 다른 기능의 문서와 같이 흑백과 표준 관계 표기를 사용한다. 유스케이스와 상태는 Smetana 배치 엔진을, 클래스는 ELK 배치 엔진을 사용한다.

## 시퀀스 PNG 다시 생성

Mermaid CLI 11.12.0을 사용한다. 저장소 루트에서 다음 명령을 실행한다. 첫 실행에는 Node.js와 패키지 다운로드를 위한 네트워크가 필요하다. 프로젝트의 Gradle 설정은 변경하지 않는다.

```bash
npx --yes --package @mermaid-js/mermaid-cli@11.12.0 mmdc -i docs/statistics/diagrams/statistics-sequence.mmd -o docs/statistics/diagrams/statistics-sequence.png -c docs/statistics/diagrams/mermaid-config.json -b white -s 2 -w 1600
```

[mermaid-config.json](mermaid-config.json)에 시퀀스의 글꼴·색상·간격 설정을 보관한다. 렌더링 도구와 사용 가능한 글꼴에 따라 배치가 조금 달라질 수 있으므로 생성 후 글자 잘림과 화살표 연결을 확인한다.
