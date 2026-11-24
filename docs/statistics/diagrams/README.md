# 통계 다이어그램

[통계 설계 문서](../statistics.md)는 아래 PNG 파일을 참조한다. 다이어그램을 수정할 때는 원본을 먼저 고친 뒤 PNG를 다시 생성한다. 본문과 별도 원본에 같은 Mermaid 코드를 중복해서 관리하지 않는다.

| 다이어그램 | 수정용 원본 | 문서 삽입 이미지 |
| --- | --- | --- |
| 유스케이스 | [statistics-use-cases.puml](statistics-use-cases.puml) | [statistics-use-cases.png](statistics-use-cases.png) |
| 클래스 | [statistics-classes.mmd](statistics-classes.mmd) | [statistics-classes.png](statistics-classes.png) |
| 시퀀스 | [statistics-sequence.mmd](statistics-sequence.mmd) | [statistics-sequence.png](statistics-sequence.png) |

## 클래스·시퀀스 PNG 다시 생성

Mermaid CLI 11.12.0을 사용한다. 저장소 루트에서 다음 명령을 실행한다. 첫 실행에는 Node.js와 패키지 다운로드를 위한 네트워크가 필요하다. 프로젝트의 Gradle 설정은 변경하지 않는다.

```bash
npx --yes --package @mermaid-js/mermaid-cli@11.12.0 mmdc -i docs/statistics/diagrams/statistics-classes.mmd -o docs/statistics/diagrams/statistics-classes.png -c docs/statistics/diagrams/mermaid-config.json -b white -s 2 -w 1600
npx --yes --package @mermaid-js/mermaid-cli@11.12.0 mmdc -i docs/statistics/diagrams/statistics-sequence.mmd -o docs/statistics/diagrams/statistics-sequence.png -c docs/statistics/diagrams/mermaid-config.json -b white -s 2 -w 1600
```

[mermaid-config.json](mermaid-config.json)에 글꼴·색상·간격 설정을 보관한다. 렌더링 도구와 사용 가능한 한글 글꼴에 따라 배치가 조금 달라질 수 있으므로 생성 후 글자 잘림과 화살표 연결을 확인한다.

## 유스케이스 PNG 다시 생성

유스케이스는 기존 PlantUML 원본을 유지한다. PlantUML 실행 파일이 준비된 환경에서 다음 명령으로 PNG를 생성할 수 있다.

```bash
java -jar /path/to/plantuml.jar -tpng docs/statistics/diagrams/statistics-use-cases.puml
```
