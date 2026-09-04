"""
Pure-Python mirror of app/src/main/kotlin/app/dewey/extract/*.kt.

Mirrors app.dewey.extract.FieldExtractor, AmountExtractor, VendorExtractor and
DateFieldExtractor exactly - same label lists, same regexes, same tie-break
rules, same two-tier due/issue date split. If these drift, extract_bench.py
stops describing the app, the same rule retrieval_bench.py and
classify_bench.py already follow for Chunker.kt and CategoryPrototypes.kt.

The point of a Python mirror rather than benchmarking the Kotlin directly: the
Kotlin runs on a phone and this machine must not start a JVM (see the harness
note at the top of the task). The measured numbers are only honest if the
rules are identical, so read this file next to the Kotlin, not instead of it.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from date_expansion import MONTHS_AR, MONTHS_EN, MONTHS_FR

# ------------------------------------------------------------------- windows

def non_blank_lines(text: str) -> list[str]:
    return [line.strip() for line in text.split('\n') if line.strip()]


def forward_window(lines: list[str], start: int, size: int = 3) -> str:
    """
    [start] joined with up to `size` - 1 following lines, so a value is found
    even when the renderer split it onto a separate line from its label.

    This is not a corpus artifact to shrug off: every Arabic document in the
    test corpus renders "label : value" as two or three *separate* extracted
    lines ("المبلغ الإجمالي المستحق :" / "666.01" / "درهم", each its own
    line) because pdf_render.py draws each direction run as its own text
    operation for Arabic/Latin mixed content - see its module docstring.
    French and English happen to keep label and value on one line, so a
    window of size 1 already covers them.

    The window only ever looks *forward* from a line already confirmed to
    anchor a label or a candidate - never backward, and never built before a
    label is found. An earlier version joined windows first and searched
    labels across the join, and a long wrapped French sentence ("... 5000
    dirhams, payable d'avance avant le cinq de\\nchaque mois... ARTICLE 4 -
    DEPOT DE GARANTIE...") was enough to make the deposit two lines later
    read as if it were on the rent's own line, which is exactly the
    corruption invoice2data-style label matching is supposed to avoid.
    """
    return ' '.join(lines[start:start + size])


# --------------------------------------------------------------------- dates

_ISO = re.compile(r'\b(\d{4})-(\d{1,2})-(\d{1,2})\b')
_SLASH = re.compile(r'\b(\d{1,2})/(\d{1,2})/(\d{4})\b')
_DOT = re.compile(r'\b(\d{1,2})\.(\d{1,2})\.(\d{4})\b')

_MONTH_LOOKUP = {
    name.lower(): index + 1
    for months in (MONTHS_FR, MONTHS_EN, MONTHS_AR)
    for index, name in enumerate(months)
}
_SPELLED = re.compile(
    r'\b(\d{1,2})\s+(' + '|'.join(re.escape(name) for name in _MONTH_LOOKUP) + r')\s+(\d{4})',
    re.IGNORECASE,
)


def _to_date(year: int, month: int | None, day: int | None) -> str | None:
    """ISO string on success, mirroring LocalDate.of()'s validation - no crash."""
    if month is None or day is None:
        return None
    try:
        import datetime
        return datetime.date(year, month, day).isoformat()
    except ValueError:
        return None


def first_date(line: str) -> str | None:
    m = _ISO.search(line)
    if m:
        year, month, day = m.groups()
        d = _to_date(int(year), int(month), int(day))
        if d:
            return d

    m = _SLASH.search(line)
    if m:
        day, month, year = m.groups()
        d = _to_date(int(year), int(month), int(day))
        if d:
            return d

    m = _DOT.search(line)
    if m:
        day, month, year = m.groups()
        d = _to_date(int(year), int(month), int(day))
        if d:
            return d

    m = _SPELLED.search(line)
    if m:
        day, month_name, year = m.groups()
        month = _MONTH_LOOKUP.get(month_name.lower())
        d = _to_date(int(year), month, int(day))
        if d:
            return d

    return None


_DUE_LABELS = [
    "date limite de paiement", "date d'echeance", "date d'échéance",
    "echeance de paiement", "échéance de paiement",
    "آخر أجل للأداء", "تاريخ الاستحقاق",
    "due date", "payment due",
]

_ISSUE_LABELS = [
    "date d'achat", "date de consultation", "date de delivrance", "date de délivrance",
    "date d'effet", "date de depot", "date de dépôt", "solde final",
    "fait a", "fait à",
    "date d'emission", "date d'émission", "date d'edition", "date d'édition",
    "emise le", "émise le",
    "تاريخ الفحص", "تاريخ تسليم النسخة", "بتاريخ",
    "date of purchase", "issued on", "date issued",
    'date of issue',
    'fait le',
    'تاريخ الإصدار',
]

_PERIOD_LINE = ["periode", "période"]
_RANGE_START = re.compile(r'\bdu\b', re.IGNORECASE)


@dataclass(frozen=True)
class DocumentDates:
    issue_date: str | None = None
    due_date: str | None = None


def extract_dates(text: str) -> DocumentDates:
    lines = non_blank_lines(text)
    issue_date = None
    due_date = None
    range_fallback = None

    for i, line in enumerate(lines):
        # The label is matched on this single line only - never a joined
        # window - so a label found here is never actually a different
        # field's label bleeding in from a neighbour. Only once a line is
        # confirmed to anchor a label does the search widen forward, to
        # reach a value the renderer put on the next line or two.
        folded = line.lower()

        if due_date is None and any(label.lower() in folded for label in _DUE_LABELS):
            due_date = first_date(forward_window(lines, i)) or due_date
            continue

        if issue_date is None and any(label.lower() in folded for label in _ISSUE_LABELS):
            issue_date = first_date(forward_window(lines, i)) or issue_date
            continue

        if (range_fallback is None
                and any(word in folded for word in _PERIOD_LINE)
                and _RANGE_START.search(line)):
            range_fallback = first_date(forward_window(lines, i)) or range_fallback

    return DocumentDates(issue_date or range_fallback, due_date)


# -------------------------------------------------------------------- amount

def parse_amount(raw: str) -> float | None:
    """Mirrors AmountParsing.parse(): decimal mark is whichever separator is
    rightmost; the other, if any, is thousands grouping and gets dropped."""
    text = raw.strip()
    if not text:
        return None

    negative = text.startswith('-')
    if negative:
        text = text[1:]
    if not text:
        return None

    for ch in (' ', ' ', ' '):
        text = text.replace(ch, '')

    last_comma = text.rfind(',')
    last_dot = text.rfind('.')

    if last_comma == -1 and last_dot == -1:
        normalised = text
    elif last_comma != -1 and last_dot != -1:
        normalised = text.replace('.', '').replace(',', '.') if last_comma > last_dot \
            else text.replace(',', '')
    elif last_comma != -1:
        normalised = text.replace(',', '.')
    else:
        normalised = text

    try:
        value = float(normalised)
    except ValueError:
        return None
    return -value if negative else value


# Requires at least one thousands group (`+`) so plain "281.26" or "15023.26"
# fall straight to the second alternative instead of the two fighting.
_NUMBER = r'-?\d{1,3}(?:[   ,.]\d{3})+(?:[.,]\d+)?|-?\d+(?:[.,]\d+)?'
_CURRENCY = r'(?:\bMAD\b|\bDH\b|\bdirhams?\b|درهم|دراهم)'
_NUMBER_THEN_CURRENCY = re.compile(f'({_NUMBER})\\s*({_CURRENCY})', re.IGNORECASE)
_CURRENCY_THEN_NUMBER = re.compile(f'({_CURRENCY})\\s*({_NUMBER})', re.IGNORECASE)

_POSITIVE_PHRASES = [
    "total a payer", "total à payer", "total a regler", "total à régler",
    "montant total regle", "montant total réglé", "montant total",
    "total amount paid", "solde final", "prime annuelle",
    "salaire mensuel brut", "gratification mensuelle",
    "impot sur le revenu du", "impôt sur le revenu du", "loyer mensuel",
    "المبلغ الإجمالي المستحق", "الوجيبة الشهرية", "الوجيبة الكرائية",
]

_NEGATIVE_PHRASES = [
    "depot de garantie", "dépôt de garantie", "garantie", "franchise",
    "subtotal", "total hors taxe", "value added tax", "tva",
    "consommation hors forfait", "abonnement", "revenus fonciers",
    "revenus salariaux", "deductions", "déductions", "solde initial",
    "الضمانة",
]


@dataclass(frozen=True)
class AmountResult:
    value: float
    currency: str


def _phrase_score(text: str) -> int | None:
    """1, -1, or None (no phrase found) - never 0, so callers can tell
    "no signal here" apart from "neutral signal found here"."""
    folded = text.lower()
    if any(p.lower() in folded for p in _POSITIVE_PHRASES):
        return 1
    if any(p.lower() in folded for p in _NEGATIVE_PHRASES):
        return -1
    return None


def _score(lines: list[str], start: int, pair_span: int, back: int = 2) -> int:
    """
    A candidate's score comes from whichever context actually names it.

    First choice is the candidate's own line(s) - the pair-window it was
    found in - since a label naming the value right next to it (rent,
    deposit, subtotal, grand total) is a stronger and more local signal than
    anything further away, and is what keeps a rent line and a deposit line
    two paragraphs apart from contaminating each other even when both
    mention a total-shaped phrase somewhere nearby. Only when that line has
    no phrase at all does the search widen backward - never forward - to the
    line or two before it, which is where an Arabic label sits relative to
    its value once the two have been split onto separate lines by the
    renderer (see forward_window's docstring). Backward, not forward: a
    label never follows the value it names in this corpus.
    """
    own = _phrase_score(' '.join(lines[start:start + pair_span]))
    if own is not None:
        return own
    behind = _phrase_score(' '.join(lines[max(0, start - back):start + 1]))
    return behind if behind is not None else 0


def extract_amount(text: str) -> AmountResult | None:
    lines = non_blank_lines(text)
    best = None
    best_score = -10**9

    for i in range(len(lines)):
        window = forward_window(lines, i, size=2)
        candidates = []
        for m in _NUMBER_THEN_CURRENCY.finditer(window):
            candidates.append((m.group(1), m.group(2)))
        for m in _CURRENCY_THEN_NUMBER.finditer(window):
            candidates.append((m.group(2), m.group(1)))
        if not candidates:
            continue

        score = _score(lines, i, pair_span=2)
        for raw_number, currency_token in candidates:
            value = parse_amount(raw_number)
            if value is None:
                continue
            if score >= best_score:
                best_score = score
                best = (value, currency_token)

    if best is None or best_score < 0:
        return None
    return AmountResult(best[0], 'MAD')


# -------------------------------------------------------------------- vendor

_GENERIC_MASTHEADS = {
    "royaume du maroc - ministere de l'interieur",
    "royaume du maroc - ministère de l'intérieur",
    "المملكة المغربية - وزارة الداخلية",
}


def extract_vendor(text: str) -> str | None:
    lines = [line.strip() for line in text.split('\n') if line.strip()]
    if not lines:
        return None

    candidate = lines[0]
    if candidate.lower() in _GENERIC_MASTHEADS and len(lines) > 1:
        candidate = lines[1]

    return candidate or None


# ------------------------------------------------------------------- top level

@dataclass(frozen=True)
class ExtractedFields:
    vendor: str | None = None
    amount: float | None = None
    currency: str | None = None
    issue_date: str | None = None
    due_date: str | None = None


def extract(text: str) -> ExtractedFields:
    if not text.strip():
        return ExtractedFields()

    # See FieldExtractor.kt: shaped Arabic glyphs extract as presentation-form
    # codepoints, and NFKC is what makes the Arabic label lists match at all.
    normalised = unicodedata.normalize('NFKC', text)

    amount = extract_amount(normalised)
    dates = extract_dates(normalised)

    return ExtractedFields(
        vendor=extract_vendor(normalised),
        amount=amount.value if amount else None,
        currency=amount.currency if amount else None,
        issue_date=dates.issue_date,
        due_date=dates.due_date,
    )
