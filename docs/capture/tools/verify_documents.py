"""Validate local Markdown, recorded test evidence and diagrams.

Run verify_markdown.cjs first. This verifies recorded evidence; it does not run JUnit.
"""
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import html
import json
import re
import struct
import subprocess
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
BUILD = REPO / 'build/capture-docs'
LINK = re.compile(r'!?\[[^\]]*\]\((<[^>]+>|[^\s)]+)(?:\s+"[^"]*")?\)')


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def digest(path, binary=False):
    data = path.read_bytes() if binary else path.read_text(encoding='utf-8').encode('utf-8')
    return hashlib.sha256(data).hexdigest()


def prose(text):
    return re.sub(r'^(`{3,}|~{3,}).*?^\1\s*$', '', text, flags=re.M | re.S)


def anchors(text):
    body = prose(text)
    ids = set(re.findall(r'<a\s+(?:id|name)=["\']([^"\']+)', body))
    seen = Counter()
    for heading in re.findall(r'^#{1,6}\s+(.+?)\s*#*$', body, re.M):
        heading = re.sub(r'!?\[([^\]]+)\]\([^)]*\)', r'\1', heading)
        heading = html.unescape(re.sub(r'<[^>]*>', '', heading)).lower()
        slug = re.sub(r'[^\w -]', '', heading).replace(' ', '-')
        ids.add(slug + (f'-{seen[slug]}' if seen[slug] else ''))
        seen[slug] += 1
    return ids


def local_links(documents):
    count = anchor_count = 0
    for path in documents:
        body = path.read_text(encoding='utf-8')
        require(not re.search(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', body), f'Control character: {path}')
        for target in LINK.findall(prose(body)):
            url = urlsplit(target.strip('<>'))
            if url.scheme or url.netloc:
                continue
            dest = (path.parent / unquote(url.path)).resolve() if url.path else path
            require(dest.exists(), f'Broken local link: {path.name} -> {target}')
            count += 1
            if url.fragment and dest.suffix.lower() == '.md':
                require(unquote(url.fragment) in anchors(dest.read_text(encoding='utf-8')),
                        f'Broken Markdown anchor: {path.name} -> {target}')
                anchor_count += 1
    return count, anchor_count


def contracts(text):
    return {key: re.findall(r'^\|.*\|$', body, re.M) for key, body in
            re.findall(r'^### (OC-\d\d)[^\n]*\n(.*?)(?=^### |^## |\Z)', text, re.M | re.S)}


def uml_conventions(diagram, classes, theme):
    names = ['Capture Template(E)', 'Place Template(E)', 'Place Template Continuously(E)',
             'Undo Placement(E)', 'Finish Template Editing(E)']
    labels = re.findall(r'^\s*usecase (?:\w+ as )?"([^"\n]+)', diagram, re.M)
    require(len(labels) == 6 and set(labels) == {'Edit Cells', *names},
            'Use-case scope/names differ from the capture convention')
    require(re.findall(r'^\s*rectangle "([^"]+)"', diagram, re.M) == ['Life Game'],
            'Use-case diagram must have one system boundary')
    require('actor "게임 사용자"' in diagram and not re.search(r'#[0-9A-Fa-f]{6}', diagram),
            'Use the UML actor and monochrome use cases')
    require('Place Template(E)\n--\nextension points\n반복 배치"' in diagram
            and 'Place <.. Stroke : <<extend>>' in diagram
            and 'note top on link\n  condition: {' in diagram
            and 'extension point: 반복 배치' in diagram,
            'Missing extension point or linked condition')
    require('skinparam monochrome true' in theme and 'hide circle' in theme,
            'Shared theme must be monochrome with class circles hidden')
    declarations = re.findall(r'^(?:class|interface) .*$', classes, re.M)
    require(all('<<' not in line or line.startswith('interface Cell <<interface>>')
                for line in declarations), 'Move class role stereotypes to prose')
    require('interface Cell <<interface>>' in classes, 'Cell interface marker missing')
    require('Neighborhood "1" *-- "64" Cell' in classes and 'o--' not in classes,
            'Neighborhood composition/multiplicity differs')
    for line in classes.splitlines():
        if '-->' in line or '*--' in line:
            require(re.fullmatch(r'\w+ "[^"]+" (?:-->|\*--) "[^"]+" \w+(?: : \w+)?', line),
                    'Association/composition lacks multiplicity: ' + line)
        if '..>' in line:
            require(re.fullmatch(r'\w+ \.\.> \w+ : <<(?:use|create)>>', line),
                    'Dependency needs a use/create label: ' + line)
    require('{static} ~visit(' in classes, 'Static operation must be underlined')
    require(not re.search(r'\b(?:PR\s*#\d+|[a-f0-9]{7,40})\b', classes),
            'Keep development history outside the class diagram')

    members = 0
    for kind, cls, body in re.findall(
            r'^(class|interface) (\w+)(?: <<interface>>)? \{\n(.*?)^\}', classes, re.M | re.S):
        source = (REPO / 'src/com/holub/life' / (cls + '.java')).read_text(encoding='utf-8')
        source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
        source = re.sub(r'\bfinal\s+', '', source).replace('java.util.function.', '')
        source = re.sub(r'\s+', '', source)
        for member in body.splitlines():
            member = member.strip()
            static = member.startswith('{static} ')
            if static:
                member = member[len('{static} '):]
            method = re.fullmatch(r'([+~#-])(\w+)\((.*)\) : (.+)', member)
            field = re.fullmatch(r'([+~#-])(\w+) : (.+)', member)
            require(method or field, 'Use one fully typed member per line: ' + member)
            symbol, name = (method or field).group(1, 2)
            access = {'+': 'public', '~': '', '#': 'protected', '-': 'private'}[symbol]
            if kind == 'interface':
                require(symbol == '+', 'Interface operations must be public')
                access = ''
            if method:
                params = []
                for param in method[3].split(',') if method[3] else []:
                    pair = re.fullmatch(r'\s*(\w+)\s*:\s*(.+?)\s*', param)
                    require(pair, 'Missing parameter type: ' + member)
                    params.append(pair[2] + pair[1])
                signature = access + ('static' if static else '') + method[4] + name + '(' + ','.join(params) + ')'
                require(re.sub(r'\s+', '', signature) in source,
                        f'Java operation signature differs: {cls}.{name}')
            else:
                signature = access + ('static' if static else '') + field[3] + name
                require(re.search(re.escape(re.sub(r'\s+', '', signature)) + r'[=;]', source),
                        f'Java field declaration differs: {cls}.{name}')
            members += 1
    require(members > 0, 'No class members checked')
    return members


def main():
    manifest = json.loads((ROOT / 'diagrams/manifest.json').read_text(encoding='utf-8'))
    items = manifest['diagrams']
    require(len(items) == 6 and len({d['id'] for d in items}) == 6, 'Expected six distinct diagrams')
    baseline = manifest['source_commit']
    diff = subprocess.run(['git', '-c', 'safe.directory=' + REPO.as_posix(),
                           'diff', '--quiet', baseline, '--', 'src', 'test', 'build.gradle'], cwd=REPO)
    require(diff.returncode == 0, 'Code/test baseline differs; refresh evidence and documentation')
    documents = sorted((REPO / 'docs').rglob('*.md')) + [REPO / 'README.md']
    links, link_anchors = local_links(documents)
    capture_docs = [ROOT / 'capture.md', ROOT / 'README.md']
    texts = [p.read_text(encoding='utf-8') for p in capture_docs]
    require(len(contracts(texts[0])) == 6 and contracts(texts[0]) == contracts(texts[1]),
            'Shared/detailed operation contracts disagree')
    usecases = ['Capture Template(E)', 'Place Template(E)', 'Place Template Continuously(E)',
                'Undo Placement(E)', 'Finish Template Editing(E)']
    diagram = (ROOT / 'diagrams/01-use-cases.puml').read_text(encoding='utf-8')
    typed_members = uml_conventions(
        diagram, (ROOT / 'diagrams/02-classes.puml').read_text(encoding='utf-8'),
        (ROOT / 'diagrams/theme.puml').read_text(encoding='utf-8'))
    for number, name in enumerate(usecases, 1):
        label = f'E-{number:02} {name}'
        require(all(re.search(r'^#{3,4} ' + re.escape(label) + r'$', text, re.M) for text in texts),
                f'Use-case name differs between documents: {label}')
    expected_images = {(ROOT / 'diagrams' / (d['slug'] + '.png')).resolve() for d in items}
    for path, body in zip(capture_docs, texts):
        for snippet in re.findall(r'```java\n(.*?)```', body, re.S):
            normalized = re.sub(r'\s+', '', snippet)
            require(any(normalized in re.sub(r'\s+', '', source.read_text(encoding='utf-8'))
                        for source in (REPO / 'src').rglob('*.java')), 'Quoted Java snippet differs from source')
        images = re.findall(r'!\[[^\]]*\]\(([^)]+)\)', body)
        require(len(images) == 6 and {(path.parent / p).resolve() for p in images} == expected_images,
                f'Inline diagram set differs: {path}')
        for cls, symbol in re.findall(r'\b([A-Z][A-Za-z0-9]+)\.([a-z][A-Za-z0-9_]*)', body):
            if symbol == 'java':
                continue
            candidates = list((REPO / 'src').rglob(cls + '.java')) + list((REPO / 'test').rglob(cls + '.java'))
            if candidates:
                require(any(re.search(r'\b' + re.escape(symbol) + r'\b', c.read_text(encoding='utf-8')) for c in candidates),
                        f'Missing code symbol: {cls}.{symbol}')
    for item in items:
        path, fragment = item['document'].split('#')
        require(fragment in anchors((ROOT / path).read_text(encoding='utf-8')), f'Manifest link: {item["id"]}')

    evidence_path = ROOT / manifest['evidence']
    evidence = json.loads(evidence_path.read_text(encoding='utf-8'))
    require(evidence['source_commit'] == baseline, 'Test evidence baseline differs')
    keys = ['tests', 'failures', 'errors', 'skipped']
    totals = {k: sum(s[k] for s in evidence['suites']) for k in keys}
    require(totals == evidence['totals'] == dict(tests=384, failures=0, errors=0, skipped=0), 'Test totals disagree')
    require(len(evidence['suites']) == 30, 'Expected 30 recorded JUnit suites')
    require(sum(s['tests'] for s in evidence['suites'] if s['name'].rsplit('.', 1)[-1].startswith('Pattern')) == 39,
            'Pattern suite subtotal differs')
    require(len(list((evidence_path.parent / 'xml').glob('*.xml'))) == len(evidence['suites']), 'Extra/missing XML')
    for suite in evidence['suites']:
        path = evidence_path.parent / suite['xml_file']
        require(digest(path, binary=True) == suite['xml_sha256'], f'XML hash differs: {path.name}')
        xml = ET.parse(path).getroot()
        require(xml.attrib['name'] == suite['name'], f'Wrong XML suite: {path.name}')
        require({k: int(xml.attrib[k]) for k in keys} == {k: suite[k] for k in keys}, f'XML totals: {path.name}')
        cases = xml.findall('testcase')
        require(len(cases) == suite['tests'] and Counter(c.get('name') for c in cases) == Counter(suite['cases']),
                f'XML case list differs: {path.name}')
        require(not any(c.find(tag) is not None for c in cases for tag in ['failure', 'error', 'skipped']),
                f'Non-passing XML case: {path.name}')
    selected = re.findall(r'`([A-Za-z]+Test)\.([a-zA-Z0-9_]+)`', texts[0])
    require(len(selected) == 8, 'Expected eight representative JUnit methods')
    for cls, method in selected:
        require(any(s['name'].endswith('.' + cls) and method + '()' in s['cases'] for s in evidence['suites']),
                f'Selected JUnit method absent in evidence: {cls}.{method}')
    require(len(evidence['screenshots']) == 5 and len(evidence.get('raw_screenshots', [])) == 4,
            'Expected a test summary and four dated Swing screens with raw originals')
    require(all(s['date'] == evidence['date'] and s['source_commit'] == baseline
                for s in evidence['screenshots']), 'Screenshot date/commit differs')
    for screenshot in evidence['screenshots'] + evidence.get('raw_screenshots', []):
        require(digest(evidence_path.parent / screenshot['file'], binary=True) == screenshot['sha256'], 'Screenshot changed')
    swing = evidence['swing']
    log = evidence_path.parent / swing['log_file']
    require(digest(log, binary=True) == swing['log_sha256'], 'Test log changed')
    require(swing['checks'] == 17 and swing['status'] == 'passed' and
            'PASS 17 Swing integration checks' in log.read_text(encoding='utf-8'), 'Swing evidence differs')

    artifacts = json.loads((ROOT / 'diagrams/artifacts.json').read_text(encoding='utf-8'))
    require(artifacts['source_commit'] == baseline, 'Diagram baseline differs')
    require(artifacts['format'] == 'png' and artifacts['date'] == manifest['date'],
            'Diagram format/date differs')
    diagram_dir = ROOT / 'diagrams'
    require(not list(diagram_dir.rglob('*.svg')) and not list(diagram_dir.rglob('*.pdf')),
            'Removed diagram formats are still present')
    require({p.resolve() for p in diagram_dir.glob('*.png')} == expected_images,
            'Expected exactly the six diagram PNGs')
    expected_artifacts = {'diagrams/manifest.json', 'diagrams/theme.puml', 'tools/build_diagrams.py'}
    for item in items:
        expected_artifacts.update(f'diagrams/{item["slug"]}{ext}' for ext in ['.puml', '.png'])
        relative = 'diagrams/' + item['slug'] + '.png'
        png = (ROOT / relative).read_bytes()
        require(png[:8] == b'\x89PNG\r\n\x1a\n', 'Invalid PNG: ' + relative)
        dimensions = struct.unpack('>II', png[16:24])
        require(list(dimensions) == artifacts['png_dimensions'][relative]
                and all(0 < size < artifacts['png_limit'] for size in dimensions),
                'PNG size differs or hits the renderer limit: ' + relative)
    require(set(artifacts['sha256']) == expected_artifacts, 'Artifact inventory differs')
    for relative, expected in artifacts['sha256'].items():
        path = ROOT / relative
        require(digest(path, binary=path.suffix == '.png') == expected, f'Stale artifact hash: {relative}')

    browser_path = BUILD / 'preview/verification.json'
    require(browser_path.exists(), 'Run node docs/capture/tools/verify_markdown.cjs first')
    browser = json.loads(browser_path.read_text(encoding='utf-8'))
    require(len(browser['results']) == 2, 'Expected two browser-checked Markdown documents')
    require({r['document'] for r in browser['results']} == {p.relative_to(REPO).as_posix() for p in capture_docs},
            'Wrong browser document set')
    for result in browser['results']:
        require(digest(REPO / result['document']) == result['sourceSha256'], 'Markdown preview is stale; re-render')
        require(len(result['images']) == 6 and all(i['loaded'] and i['visible'] and i['display'][0] >= 500 for i in result['images'])
                and not result['missingAnchors'] and not result['horizontalOverflow'] and not result['errors'],
                'Browser rendering check failed')
    report = dict(status='passed', checked_at=datetime.now(timezone.utc).isoformat(), source_commit=baseline,
                  markdown_documents=len(documents), local_links=links, markdown_anchor_links=link_anchors,
                  matching_operation_contracts=6, matching_usecase_names=5, selected_junit_methods=len(selected),
                  recorded_junit=totals, recorded_junit_suites=30, recorded_swing_checks=swing['checks'],
                  test_execution_date=evidence['date'], note='Recorded test evidence verified; JUnit was not rerun by this tool.',
                  inline_diagrams=12, browser=browser, png_diagrams=6, typed_uml_members=typed_members,
                  artifact_hashes=len(artifacts['sha256']),
                  document_sha256={p.relative_to(REPO).as_posix(): digest(p) for p in capture_docs})
    output = BUILD / 'verification.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'PASS: {links} local links ({link_anchors} anchors); 6 matching contracts; 384 recorded JUnit + 17 Swing checks; '
          f'12 rendered diagrams; {typed_members} typed UML members; PNG artifact hashes; unchanged source baseline')
    print(output)


if __name__ == '__main__':
    main()
