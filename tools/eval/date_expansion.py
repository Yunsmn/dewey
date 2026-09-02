"""
Makes documents findable by how people say dates, not how the file prints them.

A quarter of this corpus prints `2023-08-14` and nothing else, while the person
looking for it will type "the Electroplanet receipt from aout". Neither an
embedding nor BM25 can bridge that, because the word is not in the document.

So it gets put there. At index time every date is expanded into the forms a
trilingual user might reach for — French, English and Arabic month names, plus
the bare year — and appended to the text that gets indexed. The document is not
modified; only what we index is.

This is the cheap half of the date problem. The expensive half — deciding
whether 03/04/2024 is March or April — belongs in the extraction layer, where a
wrong answer costs a wrong due date rather than a slightly worse search.
"""

import re

MONTHS_FR = ['janvier', 'fevrier', 'mars', 'avril', 'mai', 'juin',
             'juillet', 'aout', 'septembre', 'octobre', 'novembre', 'decembre']
MONTHS_EN = ['January', 'February', 'March', 'April', 'May', 'June',
             'July', 'August', 'September', 'October', 'November', 'December']
# Moroccan Arabic month names, which differ from the Levantine set.
MONTHS_AR = ['يناير', 'فبراير', 'مارس', 'أبريل', 'ماي', 'يونيو',
             'يوليوز', 'غشت', 'شتنبر', 'أكتوبر', 'نونبر', 'دجنبر']

ISO_DATE = re.compile(r'\b(\d{4})-(\d{2})-(\d{2})\b')
SLASH_DATE = re.compile(r'\b(\d{2})/(\d{2})/(\d{4})\b')


def expansions(text: str) -> list[str]:
    """Every spoken form of every date in the text, deduplicated, order kept."""
    found: list[str] = []
    seen: set[str] = set()

    def add(year: str, month: int) -> None:
        if not 1 <= month <= 12:
            return
        for name in (MONTHS_FR[month - 1], MONTHS_EN[month - 1], MONTHS_AR[month - 1]):
            phrase = f'{name} {year}'
            if phrase not in seen:
                seen.add(phrase)
                found.append(phrase)

    for year, month, _ in ISO_DATE.findall(text):
        add(year, int(month))

    # Day-first, because this corpus is Moroccan. An ambiguous 03/04/2024 is
    # read as April; see the module docstring on why that call lives here.
    for day, month, year in SLASH_DATE.findall(text):
        add(year, int(month))

    return found


def augment(text: str) -> str:
    extra = expansions(text)
    return f'{text}\n{" ".join(extra)}' if extra else text
