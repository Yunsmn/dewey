"""Is the truncation caused by mixed-direction runs (Latin digits inside RTL)?"""

import unicodedata
from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from pypdf import PdfReader
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

OUT = Path('results/_probe')

# Same length, only difference is whether Latin digits appear mid-string.
NO_DIGITS = 'فاتورة الكهرباء لشهر يناير من المكتب الوطني للماء'
WITH_LATIN = 'فاتورة الكهرباء لشهر يناير 2024 من المكتب الوطني'
WITH_ARABIC_DIGITS = 'فاتورة الكهرباء لشهر يناير ٢٠٢٤ من المكتب الوطني'


def render(name: str, text: str) -> str:
    path = OUT / f'{name}.pdf'
    c = canvas.Canvas(str(path), pagesize=A4)
    c.setFont('NotoAr', 13)
    c.drawString(50, 760, get_display(arabic_reshaper.reshape(text)))
    c.save()
    return PdfReader(str(path)).pages[0].extract_text().strip()


def render_split_runs(name: str, text: str) -> str:
    """Draw each direction-run as its own text operation."""
    path = OUT / f'{name}.pdf'
    c = canvas.Canvas(str(path), pagesize=A4)
    c.setFont('NotoAr', 13)

    y = 760
    for part in text.split(' '):
        shaped = get_display(arabic_reshaper.reshape(part))
        c.drawString(50, y, shaped)
        y -= 18
    c.save()
    return PdfReader(str(path)).pages[0].extract_text()


def show(label: str, src: str, got: str) -> None:
    norm = unicodedata.normalize('NFKC', got)
    kept = len(got.replace('\n', ''))
    print(f'  {label}')
    print(f'    in  ({len(src)} ch) out ({kept} ch)  kept={kept / len(src):.0%}')
    print(f'    NFKC: {norm!r}')
    print(f'    exact: {norm == src}')
    print()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    pdfmetrics.registerFont(TTFont('NotoAr', '/usr/share/fonts/noto/NotoSansArabic-Regular.ttf'))

    print('=== single drawString ===\n')
    show('Arabic only, no digits   ', NO_DIGITS, render('d_none', NO_DIGITS))
    show('Arabic + Latin digits    ', WITH_LATIN, render('d_latin', WITH_LATIN))
    show('Arabic + Arabic-Indic dig', WITH_ARABIC_DIGITS, render('d_arabic', WITH_ARABIC_DIGITS))

    print('=== word-per-line (each run its own text op) ===\n')
    got = render_split_runs('d_split', WITH_LATIN)
    recovered = unicodedata.normalize('NFKC', got).split()
    expected = WITH_LATIN.split()
    print(f'    expected {len(expected)} tokens, recovered {len(recovered)}')
    print(f'    recovered: {recovered}')
    print(f'    all tokens present: {set(expected) == set(recovered)}')


if __name__ == '__main__':
    main()
