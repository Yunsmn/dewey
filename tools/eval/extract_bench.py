"""
Measures whether rule-based field extraction is accurate enough to ship.

The brief calls this a cloud job. It is rule-based instead, for the same
reason app.dewey.classify.DocumentClassifier is: reading invoice2data's
approach - a small, per-phrase vocabulary tried in priority order, not a
model - teaches more about real layout chaos than reaching for a model would,
and its failures are legible (a missing label) rather than opaque (a wrong
model output). This measures precision and recall per field against
tools/corpus/ground_truth.json's 100 documents, so the claim can be checked
rather than taken on faith.

Two fields need a scoring rule of their own, because a strict string-equals
check would be dishonest about what "correct" means for them:

  vendor    Ground truth is a short organisation name; the extractor returns
            a raw letterhead line, which is often that name plus a suffix
            ("Lydec - Distribution Eau et Electricite"). Scored as correct on
            case-insensitive containment either direction, which is the same
            check used while designing the extractor (see the module comment
            in VendorExtractor.kt) - not a metric invented after the fact to
            flatter the result.

  date      Ground truth's "date" field is a document's issue date - except
            for utility bills and telecom bills, where the corpus's own
            metadata records a day that is never printed anywhere in the
            document (only the *due* date is printed - see the templates).
            Scoring the extractor's issue date against that field would
            therefore score those categories as failures for extracting
            nothing, when extracting nothing is the honest answer. So "date"
            counts as correct if EITHER the extracted issue date OR due date
            matches, and the due-date rate for bill categories is reported
            on its own beneath the table, since that is what a bills
            dashboard actually consumes and ground truth has no separate
            field to check it against.
"""

import json
from pathlib import Path

from extract_fields import extract
from pypdf import PdfReader

CORPUS = Path('tools/corpus/corpus')
GROUND_TRUTH = Path('tools/corpus/ground_truth.json')


def read_pdf(path: Path) -> str:
    return '\n'.join(page.extract_text() or '' for page in PdfReader(str(path)).pages)


def vendor_matches(extracted: str | None, truth: str | None) -> bool:
    if not extracted or not truth:
        return extracted == truth
    e, t = extracted.casefold(), truth.casefold()
    return e in t or t in e


def amount_matches(extracted: float | None, truth: float | None) -> bool:
    if extracted is None or truth is None:
        return extracted == truth
    return abs(extracted - truth) < 0.01


def main() -> None:
    truth = json.loads(GROUND_TRUTH.read_text())

    fields = ['vendor', 'amount', 'currency', 'date']
    # true positive / false positive / false negative, per field. A "positive"
    # is ground truth having a value for that field on that document.
    counts = {f: {'tp': 0, 'fp': 0, 'fn': 0, 'tn': 0} for f in fields}

    due_date_hits = 0
    due_date_total = 0
    date_findable_hits = 0
    date_findable_total = 0
    mistakes = {f: [] for f in fields}

    for entry in truth:
        text = read_pdf(CORPUS / entry['filename'])
        result = extract(text)

        checks = {
            'vendor': (vendor_matches(result.vendor, entry['organisation']), result.vendor, entry['organisation']),
            'amount': (amount_matches(result.amount, entry['amount']), result.amount, entry['amount']),
            'currency': (result.currency == entry['currency'], result.currency, entry['currency']),
            'date': (
                entry['date'] in (result.issue_date, result.due_date),
                (result.issue_date, result.due_date),
                entry['date'],
            ),
        }

        for field, (correct, got, want) in checks.items():
            has_truth = want is not None
            has_result = got is not None and got != (None, None)
            c = counts[field]
            if has_truth and correct:
                c['tp'] += 1
            elif has_truth and not correct:
                c['fn'] += 1
                if has_result:
                    c['fp'] += 1
                mistakes[field].append((entry['filename'], entry['category'], want, got))
            elif not has_truth and has_result:
                c['fp'] += 1
                mistakes[field].append((entry['filename'], entry['category'], want, got))
            else:
                c['tn'] += 1

        # Bill categories: what a bills dashboard actually reads. No ground
        # truth field exists for this - see the module docstring - so it is
        # reported as its own rate, not folded into the "date" precision
        # above.
        if entry['category'] in ('utility_bill', 'misc'):
            due_date_total += 1
            if result.due_date is not None:
                due_date_hits += 1

        # utility_bill and misc (telecom) print only a due date - their
        # ground truth "date" is never on the page at all, by construction
        # (see templates.py: utility_bill_fr's issue day is a bare literal
        # never rendered into any Line). university prints no date whatsoever.
        # 37 of the 100 documents fall in these three categories, so a raw
        # "date" recall number is dominated by an admitted non-goal rather
        # than by extractor mistakes - see date_findable below for the split.
        if entry['category'] not in ('utility_bill', 'misc', 'university'):
            date_findable_total += 1
            if entry['date'] in (result.issue_date, result.due_date):
                date_findable_hits += 1

    print(f'{len(truth)} documents\n')
    print(f'{"field":10s} {"precision":>10s} {"recall":>8s} {"tp":>5s} {"fp":>5s} {"fn":>5s} {"tn":>5s}')
    for field in fields:
        c = counts[field]
        precision = c['tp'] / (c['tp'] + c['fp']) if (c['tp'] + c['fp']) else float('nan')
        recall = c['tp'] / (c['tp'] + c['fn']) if (c['tp'] + c['fn']) else float('nan')
        print(f'{field:10s} {precision:>10.1%} {recall:>8.1%} {c["tp"]:>5d} {c["fp"]:>5d} {c["fn"]:>5d} {c["tn"]:>5d}')

    print(f'\ndate, restricted to the {date_findable_total} documents where ground truth\'s date is '
          f'actually printed somewhere in the text: {date_findable_hits}/{date_findable_total} '
          f'({date_findable_hits / date_findable_total:.1%})')
    print(f'utility_bill/misc due date found: {due_date_hits}/{due_date_total} '
          f'({due_date_hits / due_date_total:.1%}) - not checked against ground truth '
          f'(no such field exists); see module docstring')

    for field in fields:
        if mistakes[field]:
            print(f'\n{len(mistakes[field])} {field} mistakes:')
            for filename, category, want, got in mistakes[field][:20]:
                print(f'  {filename:38s} {category:16s} want={want!r:30} got={got!r}')


if __name__ == '__main__':
    main()
