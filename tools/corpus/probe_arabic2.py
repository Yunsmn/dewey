"""Isolate the two variables separately: truncation, then ordering."""

import unicodedata
from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from pypdf import PdfReader
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

FONT = '/usr/share/fonts/noto/NotoSansArabic-Regular.ttf'
OUT = Path('results/_probe')

SHORT = 'فاتورة الكهرباء'                      # 15 chars, definitely fits
LONG = 'فاتورة الكهرباء لشهر يناير 2024 من المكتب الوطني'


def roundtrip(name: str, text: str, *, shape: bool, use_textobject: bool) -> str:
    path = OUT / f'{name}.pdf'
    drawn = get_display(arabic_reshaper.reshape(text)) if shape else text

    c = canvas.Canvas(str(path), pagesize=A4)
    c.setFont('NotoAr', 13)
    if use_textobject:
        t = c.beginText(60, 760)
        t.textLine(drawn)
        c.drawText(t)
    else:
        c.drawString(60, 760, drawn)
    c.save()

    return PdfReader(str(path)).pages[0].extract_text().strip()


def report(label: str, source: str, got: str) -> None:
    norm = unicodedata.normalize('NFKC', got)
    print(f'  {label}')
    print(f'    in  ({len(source):2d} ch): {source!r}')
    print(f'    out ({len(got):2d} ch): {got!r}')
    print(f'    NFKC          : {norm!r}')
    print(f'    NFKC == source: {norm == source}')
    print(f'    NFKC reversed == source: {norm[::-1] == source}')
    print()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    pdfmetrics.registerFont(TTFont('NotoAr', FONT))

    print('=== TEST 1: short string, does length survive? ===\n')
    report('shaped+bidi, drawString', SHORT, roundtrip('t1a', SHORT, shape=True, use_textobject=False))
    report('logical, drawString', SHORT, roundtrip('t1b', SHORT, shape=False, use_textobject=False))

    print('=== TEST 2: longer string ===\n')
    report('shaped+bidi, drawString', LONG, roundtrip('t2a', LONG, shape=True, use_textobject=False))
    report('logical, drawString', LONG, roundtrip('t2b', LONG, shape=False, use_textobject=False))

    print('=== TEST 3: textobject instead of drawString ===\n')
    report('shaped+bidi, textLine', LONG, roundtrip('t3a', LONG, shape=True, use_textobject=True))
    report('logical, textLine', LONG, roundtrip('t3b', LONG, shape=False, use_textobject=True))

    print('=== TEST 4: latin control (is truncation Arabic-specific?) ===\n')
    latin = 'Facture electricite janvier 2024 Office National'
    pdfmetrics.registerFont(TTFont('NotoLat', '/usr/share/fonts/noto/NotoSans-Regular.ttf'))
    path = OUT / 't4.pdf'
    c = canvas.Canvas(str(path), pagesize=A4)
    c.setFont('NotoLat', 13)
    c.drawString(60, 760, latin)
    c.save()
    report('latin, drawString', latin, PdfReader(str(path)).pages[0].extract_text().strip())


if __name__ == '__main__':
    main()
