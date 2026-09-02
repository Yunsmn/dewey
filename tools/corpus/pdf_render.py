"""
Renders a document's lines to a PDF with a real text layer.

Layout is deliberately plain — the experiment measures retrieval, not design —
but direction handling is exact, because that is what decides whether the text
survives extraction.
"""

from dataclasses import dataclass
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

from arabic_text import contains_arabic, shape_for_display, split_direction_runs

FONT_DIR = '/usr/share/fonts/noto'
FONT_LATIN = 'NotoLatin'
FONT_ARABIC = 'NotoArabic'

PAGE_WIDTH, PAGE_HEIGHT = A4
MARGIN_LEFT = 56
MARGIN_RIGHT = 56
MARGIN_TOP = 56
MARGIN_BOTTOM = 64

BODY_SIZE = 10.5
HEADING_SIZE = 15
LINE_HEIGHT = 15.5
HEADING_GAP = 10

_fonts_registered = False


def register_fonts() -> None:
    global _fonts_registered
    if _fonts_registered:
        return

    pdfmetrics.registerFont(TTFont(FONT_LATIN, f'{FONT_DIR}/NotoSans-Regular.ttf'))
    pdfmetrics.registerFont(TTFont(FONT_ARABIC, f'{FONT_DIR}/NotoSansArabic-Regular.ttf'))
    _fonts_registered = True


@dataclass(frozen=True)
class Line:
    text: str
    heading: bool = False


def _font_for(text: str) -> str:
    return FONT_ARABIC if contains_arabic(text) else FONT_LATIN


def _wrap(text: str, size: float, max_width: float) -> list[str]:
    """Greedy word wrap, measured in the font the line will actually use."""
    font = _font_for(text)
    words = text.split()
    if not words:
        return ['']

    lines: list[str] = []
    current: list[str] = []

    for word in words:
        candidate = ' '.join([*current, word])
        if current and pdfmetrics.stringWidth(candidate, font, size) > max_width:
            lines.append(' '.join(current))
            current = [word]
        else:
            current.append(word)

    if current:
        lines.append(' '.join(current))

    return lines


def _draw_runs(c: canvas.Canvas, text: str, y: float, size: float) -> None:
    """
    Draw one visual line, each direction run as its own text operation.

    Runs are emitted in logical order. An RTL line is laid out from the right
    margin leftwards, which is both correct typography and what keeps extraction
    order equal to reading order.
    """
    runs = [(run.strip(), rtl) for run, rtl in split_direction_runs(text)]
    runs = [(run, rtl) for run, rtl in runs if run]
    if not runs:
        return

    # Runs are stripped and re-separated by an explicit gap. Leaving the original
    # whitespace attached puts the space on the wrong side of a direction change,
    # which renders as "بمدينةTanger".
    gap = pdfmetrics.stringWidth(' ', FONT_LATIN, size)

    if contains_arabic(text):
        cursor = PAGE_WIDTH - MARGIN_RIGHT
        for index, (run_text, run_rtl) in enumerate(runs):
            font = FONT_ARABIC if run_rtl else FONT_LATIN
            drawn = shape_for_display(run_text) if run_rtl else run_text
            width = pdfmetrics.stringWidth(drawn, font, size)
            if index:
                cursor -= gap
            c.setFont(font, size)
            c.drawString(cursor - width, y, drawn)
            cursor -= width
        return

    cursor = MARGIN_LEFT
    for index, (run_text, run_rtl) in enumerate(runs):
        font = FONT_ARABIC if run_rtl else FONT_LATIN
        drawn = shape_for_display(run_text) if run_rtl else run_text
        if index:
            cursor += gap
        c.setFont(font, size)
        c.drawString(cursor, y, drawn)
        cursor += pdfmetrics.stringWidth(drawn, font, size)


def render(path: Path, lines: list[Line]) -> int:
    """Writes the PDF and returns the page count."""
    register_fonts()
    path.parent.mkdir(parents=True, exist_ok=True)

    c = canvas.Canvas(str(path), pagesize=A4)
    max_width = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
    y = PAGE_HEIGHT - MARGIN_TOP
    pages = 1

    for line in lines:
        size = HEADING_SIZE if line.heading else BODY_SIZE

        for wrapped in _wrap(line.text, size, max_width):
            if y < MARGIN_BOTTOM:
                c.showPage()
                pages += 1
                y = PAGE_HEIGHT - MARGIN_TOP

            if wrapped:
                _draw_runs(c, wrapped, y, size)
            y -= LINE_HEIGHT if not line.heading else LINE_HEIGHT + HEADING_GAP

    c.save()
    return pages
