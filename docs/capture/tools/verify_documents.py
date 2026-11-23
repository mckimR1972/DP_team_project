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

from pypdf import PdfReader
import pypdfium2 as pdfium

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
    usecases = ['Capture Template E', 'Place Template E', 'Place Template Continuously E',
                'Undo Placement E', 'Finish Template Editing E']
    diagram = (ROOT / 'diagrams/01-use-cases.puml').read_text(encoding='utf-8')
    for number, name in enumerate(usecases, 1):
        label = f'E-{number:02} {name}'
        require(all(re.search(r'^#{3,4} ' + re.escape(label) + r'$', text, re.M) for text in texts),
                f'Use-case name differs between documents: {label}')
        require(f'usecase "{label}"' in diagram, f'Use-case diagram label differs: {label}')
    extended = diagram.split('rectangle "추가 시스템 기능"', 1)[1]
    require(all(label.endswith(' E') for label in re.findall(r'usecase "([^"\n]+)"', extended)),
            'An extended use-case name lacks its trailing E')
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
        png = (ROOT / 'diagrams' / (item['slug'] + '.png')).read_bytes()
        require(png[:8] == b'\x89PNG\r\n\x1a\n', f'Invalid PNG: {item["id"]}')
        png_size = struct.unpack('>II', png[16:24])
        svg = ET.parse(ROOT / 'diagrams/svg' / (item['slug'] + '.svg')).getroot()
        svg_size = tuple(float(x) for x in svg.attrib['viewBox'].split()[2:])
        # PlantUML rounds raster/vector bounds differently by up to three pixels.
        require(all(abs(p - s) <= 4 for p, s in zip(png_size, svg_size)),
                f'PNG is cropped or differs from SVG bounds: {item["id"]}')

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
    for relative, expected in artifacts['sha256'].items():
        path = ROOT / relative
        require(digest(path, binary=path.suffix in ['.pdf', '.png']) == expected, f'Stale artifact hash: {relative}')
    atlas_path = ROOT / 'diagrams/capture-diagrams.pdf'
    reader = PdfReader(atlas_path)
    require(len(reader.pages) == len(items) + 1 == artifacts['pdf_pages'], 'Atlas page count differs')
    require('캡처' in reader.pages[0].extract_text(), 'Missing Korean title text')
    pdf_output = BUILD / 'pdf-review'
    pdf_output.mkdir(parents=True, exist_ok=True)
    with pdfium.PdfDocument(atlas_path) as atlas:
        atlas[0].render(scale=1.5).to_pil().save(pdf_output / '00-cover.png')
        for page_number, item in enumerate(items, 1):
            pdf_path = ROOT / 'diagrams/pdf' / (item['slug'] + '.pdf')
            standalone = PdfReader(pdf_path)
            require(len(standalone.pages) == 1, f'Expected one page: {item["id"]}')
            require(item['id'] in standalone.pages[0].extract_text(), f'Missing diagram ID: {item["id"]}')
            with pdfium.PdfDocument(pdf_path) as individual:
                a = atlas[page_number].render(scale=1.5).to_pil()
                b = individual[0].render(scale=1.5).to_pil()
                require(a.size == b.size and a.tobytes() == b.tobytes(), f'Atlas differs: {item["id"]}')
                a.save(pdf_output / (item['slug'] + '.png'))

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
                  inline_diagrams=12, browser=browser, pdf_pages=7, matching_pdf_pages=6,
                  artifact_hashes=len(artifacts['sha256']),
                  document_sha256={p.relative_to(REPO).as_posix(): digest(p) for p in capture_docs})
    output = BUILD / 'verification.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'PASS: {links} local links ({link_anchors} anchors); 6 matching contracts; 384 recorded JUnit + 17 Swing checks; '
          '12 rendered diagrams; 6 matching PDF pages + cover; artifact hashes; unchanged source baseline')
    print(output)


if __name__ == '__main__':
    main()
