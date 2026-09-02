"""
Validity probe: does Arabic text survive the PDF round-trip?

Rendering Arabic legibly requires reshaping (joining letters into presentation
forms) and bidi reordering (logical -> visual). reportlab then draws exactly
those codepoints, so pypdf may extract presentation forms in reversed order.

If that happens, embedding the extracted text measures a quirk of this
generator rather than retrieval quality. This probe measures the damage and
tests whether normalisation recovers the original string.
"""

import unicodedata
from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from pypdf import PdfReader
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

FONT_PATH = '/usr/share/fonts/noto/NotoSansArabic-Regular.ttf'
OUT = Path('results/_probe')

SOURCE = 'فاتورة الكهرباء لشهر يناير 2024 من المكتب الوطني للكهرباء'


def normalise(text: str) -> str:
    """Fold Arabic presentation forms back to base letters."""
    return unicodedata.normalize('NFKC', text)


def write_pdf(path: Path, text: str, font: str) -> None:
    c = canvas.Canvas(str(path), pagesize=A4)
    c.setFont(font, 14)
    c.drawString(60, 760, text)
    c.save()


def extract(path: Path) -> str:
    return PdfReader(str(path)).pages[0].extract_text().strip()


def similarity(a: str, b: str) -> float:
    """Crude character-multiset overlap — enough to see if content survived."""
    from collections import Counter
    ca, cb = Counter(a.replace(' ', '')), Counter(b.replace(' ', ''))
    if not ca:
        return 0.0
    overlap = sum((ca & cb).values())
    return overlap / sum(ca.values())


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    pdfmetrics.registerFont(TTFont('NotoAr', FONT_PATH))

    print(f'SOURCE (logical order, base letters):\n  {SOURCE!r}\n')

    # Arm A: the "pretty" path — reshaped + bidi, as you must do to render.
    shaped = get_display(arabic_reshaper.reshape(SOURCE))
    write_pdf(OUT / 'a_shaped.pdf', shaped, 'NotoAr')
    a_raw = extract(OUT / 'a_shaped.pdf')
    a_norm = normalise(a_raw)
    a_norm_rev = normalise(a_raw)[::-1]

    print('ARM A — reshaped + bidi (renders correctly on screen)')
    print(f'  extracted raw : {a_raw!r}')
    print(f'  NFKC          : {a_norm!r}')
    print(f'  NFKC reversed : {a_norm_rev!r}')
    print(f'  char overlap vs source, raw      : {similarity(SOURCE, a_raw):.2f}')
    print(f'  char overlap vs source, NFKC     : {similarity(SOURCE, a_norm):.2f}')
    print(f'  exact match after NFKC+reverse   : {a_norm_rev == SOURCE}')
    print()

    # Arm B: logical order, no reshaping — renders badly, extracts cleanly?
    write_pdf(OUT / 'b_logical.pdf', SOURCE, 'NotoAr')
    b_raw = extract(OUT / 'b_logical.pdf')

    print('ARM B — raw logical order (renders disconnected/wrong on screen)')
    print(f'  extracted raw : {b_raw!r}')
    print(f'  exact match   : {b_raw == SOURCE}')
    print(f'  char overlap  : {similarity(SOURCE, b_raw):.2f}')
    print()

    print('Verdict inputs:')
    print(f'  Arm A recoverable : {a_norm_rev == SOURCE or similarity(SOURCE, a_norm) > 0.95}')
    print(f'  Arm B faithful    : {b_raw == SOURCE}')


if __name__ == '__main__':
    main()
