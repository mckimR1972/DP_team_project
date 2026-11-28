# 캡처 도식 안내

캡처 도식은 이 폴더의 PlantUML `.puml` 원본과 같은 이름의 PNG 10쌍으로 관리한다.

## 다시 생성하기

JDK 17+, PlantUML 1.2026.10 JAR과 Malgun Gothic 폰트를 사용한다. [공식 배포](https://github.com/plantuml/plantuml/releases/tag/v1.2026.10) JAR의 SHA-256은 다음과 같다.

```text
e9bc9e6b2eb715e8c0c929f3dcb5fc1923be13f37599b09c293c66d354e88686
```

저장소 루트에서 실행하며 JAR 경로는 설치 위치로 바꾼다.

```powershell
java "-Djava.awt.headless=true" "-DPLANTUML_LIMIT_SIZE=8192" -jar /path/to/plantuml-1.2026.10.jar -charset UTF-8 -tpng "docs/capture/diagrams/0*.puml" "docs/capture/diagrams/report/*.puml"
```

Smetana 내장 레이아웃으로 모든 PNG를 생성한다. 표기 규칙은 [문서와 그림 관리](../README.md#artifacts), 그림 설명은 [UML과 도식](../README.md#diagrams)에 있다.
