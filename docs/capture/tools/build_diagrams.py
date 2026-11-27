"""Render all PlantUML sources as PNG and record their input/output hashes.

Python standard library only. Rebuilding all diagrams keeps the shared theme
and every PNG in sync.
"""
from pathlib import Path
import argparse
import hashlib
import json
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DIAGRAMS = ROOT / 'diagrams'
JAR_SHA256 = 'e9bc9e6b2eb715e8c0c929f3dcb5fc1923be13f37599b09c293c66d354e88686'
PNG_LIMIT = 8192


def digest(path):
    data = path.read_bytes() if path.suffix == '.png' else path.read_text(encoding='utf-8').encode('utf-8')
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--all', action='store_true', help='Rebuild all diagrams (the default)')
    args = parser.parse_args()
    if not args.jar.is_file() or hashlib.sha256(args.jar.read_bytes()).hexdigest() != JAR_SHA256:
        parser.error('Use the pinned PlantUML 1.2026.10 JAR with the documented SHA-256')
    meta = json.loads((DIAGRAMS / 'manifest.json').read_text(encoding='utf-8'))
    dimensions = {}
    files = [DIAGRAMS / 'manifest.json', DIAGRAMS / 'theme.puml', Path(__file__)]
    slugs = [item['slug'] for item in meta['diagrams']] + meta['readme_only']
    for slug in slugs:
        source = DIAGRAMS / (slug + '.puml')
        command = ['java', '-Djava.awt.headless=true', f'-DPLANTUML_LIMIT_SIZE={PNG_LIMIT}',
                   '-jar', str(args.jar.resolve()), '-charset', 'UTF-8', '-failfast2',
                   '-tpng', '-o', str(source.parent), str(source)]
        subprocess.run(command, check=True)
        png = source.with_suffix('.png')
        data = png.read_bytes()
        if data[:8] != b'\x89PNG\r\n\x1a\n':
            raise RuntimeError(f'Invalid PNG: {png.name}')
        width, height = struct.unpack('>II', data[16:24])
        if not (0 < width < PNG_LIMIT and 0 < height < PNG_LIMIT):
            raise RuntimeError(f'PNG may be clipped at the renderer limit: {png.name}')
        dimensions[png.relative_to(ROOT).as_posix()] = [width, height]
        files.extend([source, png])
        print(f"Rendered {slug}: {png.name} ({width} x {height})")
    artifacts = {
        'source_commit': meta['source_commit'], 'date': meta['date'],
        'format': 'png', 'renderer': 'PlantUML 1.2026.10',
        'renderer_sha256': JAR_SHA256, 'png_limit': PNG_LIMIT,
        'text_hash_policy': 'UTF-8 with LF newlines',
        'png_dimensions': dimensions,
        'sha256': {path.relative_to(ROOT).as_posix(): digest(path) for path in files},
    }
    (DIAGRAMS / 'artifacts.json').write_text(
        json.dumps(artifacts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()
