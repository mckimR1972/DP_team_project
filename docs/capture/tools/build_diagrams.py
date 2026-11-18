"""Render tracked PlantUML sources locally and build one-page PDFs / an atlas."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.utils import ImageReader
from pypdf import PdfReader, PdfWriter

ROOT = Path(__file__).resolve().parents[1]
DIAGRAMS = ROOT / 'diagrams'
REPO = ROOT.parents[1]
BUILD = REPO / 'build' / 'capture-docs'
WIDTH, HEIGHT = landscape(A4)
JAR_SHA256 = 'e9bc9e6b2eb715e8c0c929f3dcb5fc1923be13f37599b09c293c66d354e88686'


def wrapped(c, text, x, y, width, size=10, leading=15):
    c.setFont('Korean', size)
    line = ''
    for char in text:
        if pdfmetrics.stringWidth(line + char, 'Korean', size) > width:
            c.drawString(x, y, line)
            y -= leading
            line = char
        else:
            line += char
    if line:
        c.drawString(x, y, line)
    return y - leading


def page_header(c, number, title, meta, page_size=None):
    WIDTH, HEIGHT = page_size or landscape(A4)
    c.setFillColor(HexColor('#37728A'))
    c.setFont('Korean', 9)
    c.drawString(32, HEIGHT - 28, f'LIFE GAME / CAPTURE DESIGN / {number}')
    c.setFillColor(HexColor('#17364A'))
    c.setFont('Korean', 22)
    c.drawString(32, HEIGHT - 57, title)
    c.setStrokeColor(HexColor('#BDD0DA'))
    c.line(32, HEIGHT - 70, WIDTH - 32, HEIGHT - 70)
    c.setFont('Korean', 8)
    c.setFillColor(HexColor('#567080'))
    c.drawString(32, 19, f"기준 dev {meta['source_commit'][:7]}  |  {meta['date']}  |  문서 버전 {meta['version']}")
    c.drawRightString(WIDTH - 32, 19, number)


def render_diagram(item, meta, jar):
    WIDTH, HEIGHT = A4 if item.get('orientation') == 'portrait' else landscape(A4)
    source = DIAGRAMS / (item['slug'] + '.puml')
    for extension in ('svg', 'png'):
        target = DIAGRAMS / 'svg' if extension == 'svg' else BUILD
        target.mkdir(parents=True, exist_ok=True)
        command = ['java', '-Djava.awt.headless=true', '-jar', str(jar),
                   '-charset', 'UTF-8', '-failfast2', '-t' + extension,
                   '-o', str(target), str(source)]
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace')
        if result.returncode:
            raise RuntimeError(result.stderr + result.stdout)
    png = BUILD / (item['slug'] + '.png')
    pdf_dir = DIAGRAMS / 'pdf'
    pdf_dir.mkdir(exist_ok=True)
    destination = pdf_dir / (item['slug'] + '.pdf')
    c = canvas.Canvas(str(destination), pagesize=(WIDTH, HEIGHT), invariant=1)
    c.setTitle(item['id'] + ' ' + item['title'])
    c.setAuthor('DP_team_project / capture documentation')
    page_header(c, item['id'], item['title'], meta, (WIDTH, HEIGHT))
    image = ImageReader(str(png))
    iw, ih = image.getSize()
    box_x, box_y, box_w, box_h = 32, 136, WIDTH - 64, HEIGHT - 222
    factor = min(box_w / iw, box_h / ih)
    c.drawImage(image, box_x + (box_w - iw * factor) / 2,
                box_y + (box_h - ih * factor) / 2, iw * factor, ih * factor, mask='auto')
    c.setFillColor(HexColor('#17364A'))
    y = wrapped(c, item['summary'], 32, 119, WIDTH - 64, 10, 14)
    y = wrapped(c, item['note'], 32, y - 2, WIDTH - 64, 9, 13)
    c.setFillColor(HexColor('#567080'))
    wrapped(c, item['requirements'] + '  |  ' + item['tests'], 32, 64, WIDTH - 64, 8, 11)
    wrapped(c, '근거: ' + item['document'] + ' / ' + item['code'], 32, 49, WIDTH - 64, 8, 11)
    c.showPage()
    c.save()
    if len(PdfReader(destination).pages) != 1:
        raise RuntimeError('Each diagram must have one PDF page')
    print(f"Rendered {item['id']}: {destination.relative_to(REPO)}")


def build_atlas(meta):
    BUILD.mkdir(parents=True, exist_ok=True)
    cover = BUILD / 'atlas-cover.pdf'
    c = canvas.Canvas(str(cover), pagesize=(WIDTH, HEIGHT), invariant=1)
    c.setTitle('캡처·템플릿 설계 도식 모음')
    page_header(c, 'INDEX', '캡처·템플릿 설계 도식 모음', meta)
    c.setFillColor(HexColor('#17364A'))
    wrapped(c, '기능 요구 → 객체 책임 → 실행 흐름 → 입력 상태 → 타 기능 계약', 32, HEIGHT - 100, WIDTH - 64, 13, 19)
    y = HEIGHT - 150
    for page, item in enumerate(meta['diagrams'], 2):
        c.setFillColor(HexColor('#17364A'))
        c.setFont('Korean', 13)
        c.drawString(32, y, f"{item['id']}  {item['title']}")
        c.drawRightString(WIDTH - 32, y, f'{page}쪽')
        c.setFillColor(HexColor('#567080'))
        wrapped(c, item['requirements'] + ' / ' + item['tests'], 32, y - 19, WIDTH - 64, 9, 13)
        y -= 49
    c.setFillColor(HexColor('#17364A'))
    wrapped(c, 'D-06의 점선은 미병합 PR의 연결이다. 현재 dev의 실제 구현과 구분해 읽는다.', 32, 82, WIDTH - 64, 10, 15)
    wrapped(c, '각 그림의 원본·개별 PDF·코드·테스트 연결은 diagrams/manifest.json과 traceability.md에서 관리한다.', 32, 60, WIDTH - 64, 9, 13)
    c.showPage()
    c.save()
    writer = PdfWriter()
    writer.append(cover, outline_item='목차와 기준')
    for item in meta['diagrams']:
        writer.append(DIAGRAMS / 'pdf' / (item['slug'] + '.pdf'), outline_item=item['id'] + ' ' + item['title'])
    writer.add_metadata({'/Title': '캡처·템플릿 설계 도식 모음', '/Author': 'DP_team_project / capture documentation'})
    output = DIAGRAMS / 'capture-diagrams.pdf'
    with output.open('wb') as stream:
        writer.write(stream)
    print(f'Atlas: {len(writer.pages)} pages')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--font', type=Path, default=Path(os.environ.get('WINDIR', 'C:/Windows')) / 'Fonts/malgun.ttf')
    parser.add_argument('--id')
    parser.add_argument('--all', action='store_true')
    parser.add_argument('--atlas', action='store_true')
    args = parser.parse_args()
    meta = json.loads((DIAGRAMS / 'manifest.json').read_text(encoding='utf-8'))
    pdfmetrics.registerFont(TTFont('Korean', str(args.font)))
    if not (args.id or args.all or args.atlas):
        parser.error('Choose --id, --all, or --atlas')
    if args.id or args.all:
        if not args.jar or not args.jar.is_file():
            parser.error('--jar must point to PlantUML 1.2026.10')
        if hashlib.sha256(args.jar.read_bytes()).hexdigest() != JAR_SHA256:
            parser.error('PlantUML jar checksum differs from the pinned release')
        items = [x for x in meta['diagrams'] if args.all or x['id'] == args.id]
        if not items:
            parser.error('Unknown diagram ID')
        for item in items:
            render_diagram(item, meta, args.jar.resolve())
    if args.atlas:
        build_atlas(meta)


if __name__ == '__main__':
    main()
