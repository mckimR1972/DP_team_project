# 캡처 도식 원본과 PDF

원본은 PlantUML `.puml`이다. 각 그림은 같은 이름의 **개별 한 페이지 PDF**로 만들고 내용 단위로 함께 커밋한다. SVG도 함께 보관해 발표 자료에서 확대·편집할 수 있다. 도식 ID와 요구/검사 연결은 `manifest.json`이 관리한다.

현재 dev의 실제 구현은 일반 실선으로, 미병합 PR 연결은 해당 그림의 범례에 따라 점선과 상태 표기로 구별한다. `Mode`의 파생 상태와 실제 Java enum 값을 혼동하지 않는다.

## 다시 생성하기

JDK 17+, Python의 `reportlab`, `pypdf`, PlantUML 1.2026.10 JAR, 한글 TTF 폰트가 필요하다. 도구 바이너리는 저장소에 넣지 않는다. [공식 배포](https://github.com/plantuml/plantuml/releases/tag/v1.2026.10)에서 받은 JAR의 SHA-256:

```text
e9bc9e6b2eb715e8c0c929f3dcb5fc1923be13f37599b09c293c66d354e88686
```

저장소 루트에서 실행한다. `C:\tools\plantuml-1.2026.10.jar`는 자신의 설치 경로로 바꾼다. Windows 기본 폰트는 Malgun Gothic이며 다른 OS에서는 한글 TTF 경로를 지정한다.

```powershell
python docs/capture/tools/build_diagrams.py --jar C:\tools\plantuml-1.2026.10.jar --id D-01
python docs/capture/tools/build_diagrams.py --jar C:\tools\plantuml-1.2026.10.jar --all
python docs/capture/tools/build_diagrams.py --atlas
```

Smetana 내장 레이아웃을 사용하므로 별도 Graphviz 설치가 필요 없다. 내용은 로컬에서 렌더링하며 원본을 외부 서버에 보내지 않는다. [PlantUML의 Smetana 설명](https://plantuml.com/smetana02)

각 PDF는 A4 가로, 한글 폰트가 포함된 제목·설명, 고해상도 도식으로 구성한다. 원본 수정 → 해당 PDF/SVG 재생성 → 페이지 검토 → manifest 연결 확인 → 커밋 순서를 따른다. 합본은 개별 PDF를 묶으므로 개별 파일과 내용이 다르지 않다.
