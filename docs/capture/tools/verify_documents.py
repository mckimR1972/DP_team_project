"""Check documentation links, source symbols, artifact hashes and PDF identity."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
from urllib.parse import unquote

from pypdf import PdfReader
import pypdfium2 as pdfium

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    manifest = json.loads((ROOT / 'diagrams/manifest.json').read_text(encoding='utf-8'))
    items = manifest['diagrams']
    require(len(items) == 6 and len({d['id'] for d in items}) == 6, 'Expected six distinct diagrams')
    baseline = manifest['source_commit']
    diff = subprocess.run(['git', '-c', 'safe.directory=' + REPO.as_posix(),
                           'diff', '--quiet', baseline, '--', 'src', 'test', 'build.gradle'], cwd=REPO)
    require(diff.returncode == 0, 'Code/test baseline differs; refresh evidence and documentation')

    documents = list(ROOT.rglob('*.md')) + [REPO / 'docs/README.md', REPO / 'README.md']
    checked_links = 0
    for path in documents:
        text = path.read_text(encoding='utf-8')
        require(not re.search(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', text), f'Control character: {path}')
        for target in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)', text):
            target = target.strip('<>').split('#', 1)[0]
            if not target or re.match(r'[a-z]+:', target):
                continue
            target = unquote(target)
            require((path.parent / target).exists(), f'Broken local link: {path.name} -> {target}')
            checked_links += 1
        # Verify qualified Java symbols in this bundle, without claiming a semantic code check.
        if path.is_relative_to(ROOT):
            for cls, symbol in re.findall(r'\b([A-Z][A-Za-z0-9]+)\.([a-z][A-Za-z0-9_]*)', text):
                if symbol == 'java':
                    continue
                candidates = list((REPO / 'src').rglob(cls + '.java')) + list((REPO / 'test').rglob(cls + '.java'))
                if candidates:
                    require(any(re.search(r'\b' + re.escape(symbol) + r'\b', c.read_text(encoding='utf-8')) for c in candidates),
                            f'Missing code symbol: {cls}.{symbol}')

    evidence = json.loads((ROOT / 'evidence/2026-11-18/results.json').read_text(encoding='utf-8'))
    require(evidence['source_commit'] == baseline, 'Test evidence baseline differs')
    totals = {k: sum(s[k] for s in evidence['suites']) for k in ['tests', 'failures', 'errors', 'skipped']}
    require(totals == evidence['totals'] == {'tests': 145, 'failures': 0, 'errors': 0, 'skipped': 0}, 'Test totals disagree')
    for screenshot in evidence['screenshots']:
        path = ROOT / 'evidence/2026-11-18' / screenshot['file']
        require(hashlib.sha256(path.read_bytes()).hexdigest() == screenshot['sha256'], 'Evidence screenshot changed')

    artifacts = json.loads((ROOT / 'diagrams/artifacts.json').read_text(encoding='utf-8'))
    for relative, expected in artifacts['sha256'].items():
        path = ROOT / relative
        data = path.read_bytes() if path.suffix == '.pdf' else path.read_text(encoding='utf-8').replace('\r\n', '\n').encode('utf-8')
        require(hashlib.sha256(data).hexdigest() == expected, f'Stale artifact hash: {relative}')

    atlas_path = ROOT / 'diagrams/capture-diagrams.pdf'
    reader = PdfReader(atlas_path)
    require(len(reader.pages) == len(items) + 1 == artifacts['pdf_pages'], 'Atlas page count differs')
    require('캡처' in reader.pages[0].extract_text(), 'Missing Korean title text')
    with pdfium.PdfDocument(atlas_path) as atlas:
        for page_number, item in enumerate(items, 1):
            pdf_path = ROOT / 'diagrams/pdf' / (item['slug'] + '.pdf')
            standalone = PdfReader(pdf_path)
            require(len(standalone.pages) == 1, f"Expected one page: {item['id']}")
            require(item['id'] in standalone.pages[0].extract_text(), f"Missing diagram ID: {item['id']}")
            with pdfium.PdfDocument(pdf_path) as individual:
                a = atlas[page_number].render(scale=1).to_pil()
                b = individual[0].render(scale=1).to_pil()
                require(a.size == b.size and a.tobytes() == b.tobytes(), f"Atlas differs: {item['id']}")
    print(f'PASS: {checked_links} local links; source baseline; test evidence; six one-page PDFs; seven-page atlas; identical diagram pages; artifact hashes')


if __name__ == '__main__':
    main()
