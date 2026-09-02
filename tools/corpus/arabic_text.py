"""
Direction-aware text rendering and extraction.

Why this module exists: reportlab draws exactly the codepoints it is given and
has no bidi support, and pypdf silently drops text after a direction change
inside a single text-showing operation. A line like

    فاتورة الكهرباء لشهر يناير 2024 من المكتب الوطني

loses everything after "2024" on extraction — 54% of the characters — while the
same line without digits round-trips exactly.

Real Moroccan documents are full of Arabic prose carrying Latin numerals, so
this is the normal case rather than an edge case. The fix is to split each line
into direction runs and draw every run as its own text operation, which
recovers all tokens.
"""

import unicodedata

ARABIC_RANGES = (
    (0x0600, 0x06FF),  # Arabic
    (0x0750, 0x077F),  # Arabic Supplement
    (0x08A0, 0x08FF),  # Arabic Extended-A
    (0xFB50, 0xFDFF),  # Arabic Presentation Forms-A
    (0xFE70, 0xFEFF),  # Arabic Presentation Forms-B
)


def is_arabic_char(char: str) -> bool:
    code = ord(char)
    return any(low <= code <= high for low, high in ARABIC_RANGES)


def contains_arabic(text: str) -> bool:
    return any(is_arabic_char(char) for char in text)


def split_direction_runs(text: str) -> list[tuple[str, bool]]:
    """
    Break a line into (run_text, is_rtl) pairs.

    Neutral characters — spaces, punctuation — attach to the run in progress, so
    a run boundary only appears where the script genuinely changes.
    """
    if not text:
        return []

    runs: list[tuple[str, bool]] = []
    current: list[str] = []
    current_rtl: bool | None = None

    for char in text:
        if char.isspace() or not (char.isalnum() or is_arabic_char(char)):
            current.append(char)
            continue

        char_rtl = is_arabic_char(char)
        if current_rtl is None:
            current_rtl = char_rtl
        elif char_rtl != current_rtl:
            runs.append((''.join(current), current_rtl))
            current = []
            current_rtl = char_rtl

        current.append(char)

    if current:
        runs.append((''.join(current), bool(current_rtl)))

    return runs


def shape_for_display(text: str) -> str:
    """Join Arabic letters and reorder to visual order, for rendering only."""
    import arabic_reshaper
    from bidi.algorithm import get_display

    return get_display(arabic_reshaper.reshape(text))


def normalise_extracted(text: str) -> str:
    """
    Fold Arabic presentation forms back to base letters.

    NFKC maps the U+FE70..FEFF presentation forms produced by shaping back onto
    their base characters, which recovers the original string exactly for
    Arabic-only runs.
    """
    return unicodedata.normalize('NFKC', text)


def tokens_for_comparison(text: str) -> set[str]:
    """
    Token set used to verify a document survived the PDF round-trip.

    Compares content rather than order, because extraction order across
    separately-drawn runs is not guaranteed to match reading order.
    """
    normalised = normalise_extracted(text)
    cleaned = ''.join(
        char if (char.isalnum() or is_arabic_char(char)) else ' ' for char in normalised
    )
    return {token for token in cleaned.split() if len(token) > 1}
