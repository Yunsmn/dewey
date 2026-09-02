"""
Generates the test corpus and its answer key.

Two design decisions worth knowing:

*Filenames carry no information.* Real archives are full of `Scan_20240312_004.pdf`
and `IMG_2891.pdf` — that is precisely why people cannot find anything. Naming
files descriptively here would let retrieval cheat and would measure nothing.

*Every document is round-trip verified.* Text is extracted back out of the
finished PDF and compared against the source. Arabic in particular can lose
content at direction boundaries, and a document whose text did not survive would
silently corrupt the language breakdown.

Usage:
    python generate_corpus.py --sample 8     # a spread for review
    python generate_corpus.py                # the full corpus
"""

import argparse
import json
import random
from collections import Counter
from dataclasses import asdict
from pathlib import Path

from pypdf import PdfReader

import templates as T
from arabic_text import normalise_extracted, tokens_for_comparison
from pdf_render import render

CORPUS_DIR = Path('corpus')
GROUND_TRUTH = Path('ground_truth.json')
SEED = 20260817

# A document is usable if this fraction of its source tokens survive extraction.
MIN_TOKEN_RECALL = 0.92

FILENAME_PATTERNS = [
    'Scan_{yyyymmdd}_{nnn}.pdf',
    'IMG_{nnnn}.pdf',
    'document ({n}).pdf',
    'DOC_{nnnnnn}.pdf',
    'CamScanner {mm}-{dd}-{yyyy} {hh}.{mi}.pdf',
    '{yyyymmdd}_{hhmiss}.pdf',
    'Untitled ({n}).pdf',
    'Nouveau document {n}.pdf',
    'WhatsApp Doc {yyyy}-{mm}-{dd} at {hh}.{mi}.{ss}.pdf',
    'file-{nnnnnn}.pdf',
    'photo_{nnnn}@{yyyymmdd}.pdf',
]


def make_filename(rng: random.Random, used: set[str]) -> str:
    """Deliberately uninformative, in the style real archives actually contain."""
    while True:
        pattern = rng.choice(FILENAME_PATTERNS)
        year = rng.randint(2021, 2025)
        month = rng.randint(1, 12)
        day = rng.randint(1, 28)
        name = (
            pattern.replace('{yyyymmdd}', f'{year}{month:02d}{day:02d}')
            .replace('{yyyy}', str(year))
            .replace('{mm}', f'{month:02d}')
            .replace('{dd}', f'{day:02d}')
            .replace('{hhmiss}', f'{rng.randint(0, 23):02d}{rng.randint(0, 59):02d}{rng.randint(0, 59):02d}')
            .replace('{hh}', f'{rng.randint(0, 23):02d}')
            .replace('{mi}', f'{rng.randint(0, 59):02d}')
            .replace('{ss}', f'{rng.randint(0, 59):02d}')
            .replace('{nnnnnn}', f'{rng.randint(100000, 999999)}')
            .replace('{nnnn}', f'{rng.randint(1000, 9999)}')
            .replace('{nnn}', f'{rng.randint(1, 999):03d}')
            .replace('{n}', str(rng.randint(1, 12)))
        )
        if name not in used:
            used.add(name)
            return name


def build_plan(rng: random.Random) -> list[T.GeneratedDoc]:
    """
    The corpus, built to be confusable on purpose.

    Consecutive months from one provider, recurring vendors, two letters from the
    same clinic — near-duplicates are where retrieval actually gets tested.
    """
    docs: list[T.GeneratedDoc] = []

    # 14 utility bills: consecutive months from the same providers, plus Arabic ONEE.
    lydec_months = [(2023, m) for m in range(1, 7)]          # 6 consecutive, near-identical
    redal_months = [(2024, m) for m in (2, 3, 4)]
    for year, month in lydec_months:
        docs.append(T.utility_bill_fr(rng, 'Lydec', year, month))
    for year, month in redal_months:
        docs.append(T.utility_bill_fr(rng, 'Redal', year, month))
    docs.append(T.utility_bill_fr(rng, 'Amendis', 2024, 9))
    for year, month in [(2023, 1), (2023, 2), (2024, 5), (2024, 11)]:
        docs.append(T.utility_bill_ar(rng, year, month))

    # 8 bank statements: same bank, consecutive months — the hardest case.
    for month in range(1, 7):
        docs.append(T.bank_statement(rng, 'Attijariwafa Bank', 2024, month))
    docs.append(T.bank_statement(rng, 'CIH Bank', 2023, 11))
    docs.append(T.bank_statement(rng, 'Bank of Africa', 2022, 4))

    # 6 rental contracts: different cities, landlords, years.
    docs.append(T.rental_contract(rng, 2021, 'Casablanca', 'fr'))
    docs.append(T.rental_contract(rng, 2023, 'Rabat', 'fr'))
    docs.append(T.rental_contract(rng, 2024, 'Marrakech', 'fr'))
    docs.append(T.rental_contract(rng, 2022, 'Benguerir', 'fr'))
    docs.append(T.rental_contract(rng, 2023, 'Tanger', 'ar'))
    docs.append(T.rental_contract(rng, 2025, 'Casablanca', 'ar'))

    # 16 invoices: ~5 recurring vendors spread across months, mixed fr/en.
    for index in range(16):
        vendor = T.VENDORS[index % len(T.VENDORS)]
        year = 2022 + (index % 4)
        month = 1 + (index * 7) % 12
        language = 'en' if index % 3 == 0 else 'fr'
        docs.append(T.vendor_invoice(rng, vendor, year, month, language))

    # 8 medical: two from the same clinic, different dates.
    docs.append(T.medical_letter(rng, 'Clinique Al Madina', 2023, 4, 'fr'))
    docs.append(T.medical_letter(rng, 'Clinique Al Madina', 2024, 1, 'fr'))
    docs.append(T.medical_letter(rng, 'Centre Medical Anfa', 2022, 9, 'fr'))
    docs.append(T.medical_letter(rng, 'Centre Medical Anfa', 2024, 6, 'fr'))
    docs.append(T.medical_letter(rng, 'Clinique Al Madina', 2023, 11, 'ar'))
    docs.append(T.medical_letter(rng, 'Clinique Al Madina', 2025, 2, 'ar'))
    docs.append(T.medical_letter(rng, 'Centre Medical Anfa', 2021, 7, 'fr'))
    docs.append(T.medical_letter(rng, 'Centre Medical Anfa', 2025, 3, 'ar'))

    # 8 university documents.
    for year, kind in [(2021, 'transcript'), (2022, 'transcript'), (2023, 'transcript'),
                       (2024, 'transcript')]:
        docs.append(T.university_doc(rng, 'Universite Mohammed VI Polytechnique', year, kind))
    docs.append(T.university_doc(rng, 'Universite Mohammed VI Polytechnique', 2023, 'enrollment'))
    docs.append(T.university_doc(rng, 'Universite Mohammed V', 2022, 'enrollment'))
    docs.append(T.university_doc(rng, 'Al Akhawayn University', 2024, 'transcript'))
    docs.append(T.university_doc(rng, 'Universite Mohammed V', 2025, 'enrollment'))

    # 5 insurance policies with overlapping renewal dates.
    docs.append(T.insurance_policy(rng, 'Wafa Assurance', 'auto', 2023))
    docs.append(T.insurance_policy(rng, 'AXA Assurance Maroc', 'health', 2023))
    docs.append(T.insurance_policy(rng, 'RMA Watanya', 'home', 2024))
    docs.append(T.insurance_policy(rng, 'Wafa Assurance', 'auto', 2024))
    docs.append(T.insurance_policy(rng, 'Sanlam Maroc', 'health', 2025))

    # 4 employment documents.
    docs.append(T.employment_doc(rng, 2023, 'internship'))
    docs.append(T.employment_doc(rng, 2024, 'internship'))
    docs.append(T.employment_doc(rng, 2024, 'attestation'))
    docs.append(T.employment_doc(rng, 2022, 'attestation'))

    # 5 tax forms across years.
    for year in (2020, 2021, 2022, 2023, 2024):
        docs.append(T.tax_form(rng, year))

    # 6 warranties.
    for year in (2021, 2022, 2023, 2023, 2024, 2025):
        docs.append(T.warranty(rng, year))

    # 5 admin/ID documents.
    for year in (2022, 2023, 2024, 2024, 2025):
        docs.append(T.admin_doc(rng, year))

    # 15 misc long-tail: telecom bills add realistic noise near utility bills.
    for index in range(15):
        operator = T.TELECOMS[index % len(T.TELECOMS)]
        docs.append(T.telecom_bill(rng, operator, 2022 + (index % 4), 1 + (index * 5) % 12))

    return docs


def source_text(doc: T.GeneratedDoc) -> str:
    return '\n'.join(line.text for line in doc.lines)


def extract(path: Path) -> str:
    reader = PdfReader(str(path))
    return '\n'.join(page.extract_text() or '' for page in reader.pages)


def verify(doc: T.GeneratedDoc, path: Path) -> dict:
    """Did the text survive the PDF round-trip?"""
    extracted = normalise_extracted(extract(path))
    want = tokens_for_comparison(source_text(doc))
    got = tokens_for_comparison(extracted)

    recall = len(want & got) / len(want) if want else 0.0

    # Case-insensitive: templates render headings in upper case, and Arabic has
    # no case at all, so folding is the only comparison that means anything.
    haystack = extracted.casefold()
    missing_key = [term for term in doc.key_terms if term.casefold() not in haystack]

    return {
        'token_recall': round(recall, 4),
        'missing_key_terms': missing_key,
        'extracted_chars': len(extracted),
        'usable': recall >= MIN_TOKEN_RECALL and not missing_key,
    }


def sample_spread(docs: list[T.GeneratedDoc]) -> list[T.GeneratedDoc]:
    """One document per (category, language) pair, for review."""
    seen: set[tuple[str, str]] = set()
    picked: list[T.GeneratedDoc] = []
    for doc in docs:
        key = (doc.category, doc.language)
        if key not in seen:
            seen.add(key)
            picked.append(doc)
    return picked


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--sample', type=int, default=0,
                        help='generate only N documents, spread across categories')
    args = parser.parse_args()

    rng = random.Random(SEED)
    docs = build_plan(rng)

    if args.sample:
        docs = sample_spread(docs)[: args.sample]
        print(f'SAMPLE MODE — {len(docs)} documents\n')
    else:
        CORPUS_DIR.mkdir(parents=True, exist_ok=True)
        for existing in CORPUS_DIR.glob('*.pdf'):
            existing.unlink()

    used: set[str] = set()
    manifest = []
    failures = []

    for doc in docs:
        filename = make_filename(rng, used)
        path = CORPUS_DIR / filename
        pages = render(path, doc.lines)
        check = verify(doc, path)

        record = {
            'filename': filename,
            **{k: v for k, v in asdict(doc).items() if k != 'lines'},
            'pages': pages,
            'source_chars': len(source_text(doc)),
            'roundtrip': check,
        }
        manifest.append(record)

        if not check['usable']:
            failures.append(record)

        if args.sample:
            flag = 'OK ' if check['usable'] else 'BAD'
            print(f'{flag} {filename:44s} {doc.category:16s} {doc.language:5s} '
                  f'{pages}p recall={check["token_recall"]:.2f}')
            if check['missing_key_terms']:
                print(f'      missing key terms: {check["missing_key_terms"]}')

    print()
    print(f'documents      : {len(manifest)}')
    print(f'languages      : {dict(Counter(d["language"] for d in manifest))}')
    print(f'categories     : {dict(Counter(d["category"] for d in manifest))}')
    print(f'pages total    : {sum(d["pages"] for d in manifest)}')
    recalls = [d['roundtrip']['token_recall'] for d in manifest]
    print(f'token recall   : min={min(recalls):.3f} mean={sum(recalls) / len(recalls):.3f}')
    print(f'unusable docs  : {len(failures)}')

    for bad in failures:
        print(f'  FAIL {bad["filename"]} ({bad["category"]}/{bad["language"]}) '
              f'recall={bad["roundtrip"]["token_recall"]:.2f} '
              f'missing={bad["roundtrip"]["missing_key_terms"]}')

    if not args.sample:
        GROUND_TRUTH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf8')
        print(f'\nwrote {GROUND_TRUTH}')


if __name__ == '__main__':
    main()
