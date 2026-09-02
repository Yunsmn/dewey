"""
Downloads real PDFs from the open web to stress the extraction pipeline.

The generated corpus has one weakness it cannot fix: we wrote it, so every
document has a clean text layer, well-behaved fonts and a valid xref table.
Real PDFs are where extraction actually breaks — image-only scans with no text
at all, CID-encoded fonts that extract as mojibake, malformed cross-reference
tables, files that are a PDF header wrapped around a JPEG.

So this pulls a deliberate mix:

  arxiv    research papers — real text layers, heavy fonts, multi-column
  ietf     RFCs — plain, long, predictable; the easy baseline
  archive  scanned public-domain books — no text layer at all, so the OCR
           fallback is exercised for the first time on genuine scans

Everything lands with the sort of filename real archives contain, because a
demo folder of tidy names is not the folder anyone actually has.

Licensing: downloads are for local testing and are never committed. The
directory is gitignored. arXiv papers carry their authors' licences and
archive.org items here are public domain; neither is redistributed by us.

Usage:
    python tools/corpus/fetch_real_pdfs.py --out tools/corpus/real --count 30
"""

import argparse
import random
import re
import sys
import time
import urllib.request
from pathlib import Path

USER_AGENT = 'dewey-corpus-builder/0.1 (student project; contact via repo)'
# archive.org returns 503 under even modest bursts, so downloads are paced.
REQUEST_GAP_SECONDS = 2.5

# arXiv listing IDs spread across fields so the text is not all one subject.
ARXIV_IDS = [
    '1706.03762', '1810.04805', '2005.14165', '1512.03385', '1409.1556',
    '2010.11929', '1412.6980', '1502.03167', '1406.2661', '1301.3781',
    '2103.00020', '1908.10084', '2201.11903', '1907.11692', '2104.08691',
]

IETF_RFCS = [8259, 7540, 2616, 3986, 6749, 7231, 8446, 5321]

# Public-domain scans: image-only PDFs, which is the point.
ARCHIVE_ITEMS = [
    'reportofcommissi00unit', 'annualreportofse1918unit', 'cu31924031223846',
    'bulletinofunited1921unit', 'reportofsecretar1919unit',
]

FILENAME_PATTERNS = [
    'Scan_{yyyymmdd}_{nnn}.pdf', 'IMG_{nnnn}.pdf', 'document ({n}).pdf',
    'DOC_{nnnnnn}.pdf', '{yyyymmdd}_{hhmiss}.pdf', 'Untitled ({n}).pdf',
    'Nouveau document {n}.pdf', 'file-{nnnnnn}.pdf', 'download ({n}).pdf',
    'WhatsApp Doc {yyyy}-{mm}-{dd} at {hh}.{mi}.{ss}.pdf',
]


def messy_name(rng: random.Random, used: set[str]) -> str:
    while True:
        pattern = rng.choice(FILENAME_PATTERNS)
        name = (
            pattern
            .replace('{yyyymmdd}', f'{rng.randint(2021, 2025)}{rng.randint(1, 12):02d}{rng.randint(1, 28):02d}')
            .replace('{yyyy}', str(rng.randint(2021, 2025)))
            .replace('{mm}', f'{rng.randint(1, 12):02d}')
            .replace('{dd}', f'{rng.randint(1, 28):02d}')
            .replace('{hhmiss}', f'{rng.randint(0, 23):02d}{rng.randint(0, 59):02d}{rng.randint(0, 59):02d}')
            .replace('{hh}', f'{rng.randint(0, 23):02d}')
            .replace('{mi}', f'{rng.randint(0, 59):02d}')
            .replace('{ss}', f'{rng.randint(0, 59):02d}')
            .replace('{nnnnnn}', str(rng.randint(100000, 999999)))
            .replace('{nnnn}', str(rng.randint(1000, 9999)))
            .replace('{nnn}', f'{rng.randint(1, 999):03d}')
            .replace('{n}', str(rng.randint(1, 12)))
        )
        if name not in used:
            used.add(name)
            return name


def sources() -> list[tuple[str, str]]:
    """(kind, url) pairs, interleaved so a partial run still gets a mix."""
    items = (
        [('arxiv', f'https://arxiv.org/pdf/{i}') for i in ARXIV_IDS]
        # rfc-editor serves PDFs under /pdfrfc/, not /rfc/ — the obvious path 404s.
        + [('ietf', f'https://www.rfc-editor.org/pdfrfc/rfc{n}.txt.pdf') for n in IETF_RFCS]
        + [('archive', f'https://archive.org/download/{i}/{i}.pdf') for i in ARCHIVE_ITEMS]
    )
    by_kind: dict[str, list] = {}
    for kind, url in items:
        by_kind.setdefault(kind, []).append((kind, url))

    interleaved = []
    while any(by_kind.values()):
        for kind in list(by_kind):
            if by_kind[kind]:
                interleaved.append(by_kind[kind].pop(0))
    return interleaved


def fetch(url: str, destination: Path) -> tuple[bool, str]:
    request = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read()
    except Exception as error:
        return False, str(error)[:80]

    # A rate-limit or error page saved as .pdf would be a confusing failure
    # much later, in the extractor, so reject anything that is not a PDF here.
    if not payload.startswith(b'%PDF'):
        return False, f'not a PDF ({len(payload)} bytes)'

    destination.write_bytes(payload)
    return True, f'{len(payload) // 1024} KB'


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=Path('tools/corpus/real'))
    parser.add_argument('--count', type=int, default=25)
    parser.add_argument('--seed', type=int, default=7)
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(args.seed)
    used: set[str] = set()

    manifest_lines = []
    got = 0

    for kind, url in sources():
        if got >= args.count:
            break
        name = messy_name(rng, used)
        ok, detail = fetch(url, args.out / name)
        status = 'ok  ' if ok else 'FAIL'
        print(f'{status} {kind:8s} {name:45s} {detail}')
        if ok:
            got += 1
            manifest_lines.append(f'{name}\t{kind}\t{url}')
        # Be a good citizen with public archives that cost someone money.
        time.sleep(REQUEST_GAP_SECONDS)

    # Provenance matters: these are other people's documents, and six months
    # from now nobody will remember where a file called IMG_4471.pdf came from.
    (args.out / 'SOURCES.tsv').write_text(
        'filename\tsource\turl\n' + '\n'.join(manifest_lines) + '\n'
    )
    print(f'\n{got} PDFs in {args.out}, provenance in {args.out}/SOURCES.tsv')
    return 0 if got else 1


if __name__ == '__main__':
    sys.exit(main())
