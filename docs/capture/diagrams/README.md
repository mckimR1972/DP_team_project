# 캡처 도식 안내

캡처 도식은 이 폴더의 PlantUML `.puml` 원본과 같은 이름의 PNG 6쌍으로 관리한다. [manifest.json](manifest.json)은 요구사항·검사 연결을, [artifacts.json](artifacts.json)은 원본과 PNG의 해시·크기를 기록한다.

## 다시 생성하기

JDK 17+, Python 3.10+, PlantUML 1.2026.10 JAR과 Malgun Gothic 폰트를 사용한다. [공식 배포](https://github.com/plantuml/plantuml/releases/tag/v1.2026.10) JAR의 SHA-256은 다음과 같다.

```text
e9bc9e6b2eb715e8c0c929f3dcb5fc1923be13f37599b09c293c66d354e88686
```

저장소 루트에서 실행하며 JAR 경로는 설치 위치로 바꾼다.

```powershell
python docs/capture/tools/build_diagrams.py --jar C:\tools\plantuml-1.2026.10.jar --all
```

Smetana 내장 레이아웃으로 PNG 6개를 모두 생성한다. 표기 규칙과 검증 절차는 [문서와 그림 관리](../README.md#artifacts), 그림 설명은 [UML과 도식](../README.md#diagrams)에 있다.
