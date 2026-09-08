"""
What does the classifier do with documents the generated corpus never contains?

The generated corpus only holds documents that belong somewhere, so a perfect
score on it says nothing about what a real Downloads folder does to the sort.
tools/corpus/real holds the awkward cases: papers pulled off arXiv, an RFC, a
scanned book.

**Read the verdict carefully — what this measures changed.** Papers were once
out-of-distribution, and the check was whether a similarity floor could hold
them out: it could, cleanly, papers topping out at 0.792 against a corpus
bottoming out at 0.821. Then `paper` became a category of its own, and those
same documents became things the classifier is *supposed* to recognise. A
prediction of `paper` here is now a hit, not a miss, and the two populations
overlapping on similarity is the expected consequence rather than a regression.

So the number to watch is the label, per document, printed below:

  - `paper` — correct. The category is doing its job.
  - anything else — a real misfile, and the thing this file exists to catch.
    A physics paper confidently filed under "Insurance" costs the user their
    trust in the whole feature.

What is *not* measured any more is genuine out-of-distribution behaviour. The
only document here that still belongs to nothing is the scanned 1918 government
report, and it has no text layer, so pypdf drops it before it reaches the
classifier at all. Judging "does the app know when to say I don't know" needs a
new sample of documents that belong to none of the categories.
"""

import argparse
import json
from pathlib import Path

import numpy as np
from pypdf import PdfReader

from classify_bench import PROTOTYPES
from retrieval_bench import Encoder, chunk


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--model-dir', type=Path, required=True)
    parser.add_argument('--real', type=Path, default=Path('tools/corpus/real'))
    args = parser.parse_args()

    encoder = Encoder(args.model_dir)

    labels = list(PROTOTYPES)
    texts, owners = [], []
    for label in labels:
        for text in PROTOTYPES[label]:
            texts.append(f'passage: {text}')
            owners.append(label)
    prototypes = encoder.encode(texts)
    owners = np.array(owners)

    # In-distribution baseline, for comparison.
    truth = json.loads(Path('tools/corpus/ground_truth.json').read_text())[:40]
    in_texts = []
    for entry in truth:
        pieces = chunk('\n'.join(
            p.extract_text() or ''
            for p in PdfReader(f"tools/corpus/corpus/{entry['filename']}").pages
        ))[:2]
        in_texts.append(f'passage: {" ".join(pieces)}')

    out_names, out_texts = [], []
    for path in sorted(args.real.glob('*.pdf')):
        try:
            text = '\n'.join(p.extract_text() or '' for p in PdfReader(str(path)).pages[:3])
        except Exception:
            continue
        pieces = chunk(text)[:2]
        if not pieces:
            continue
        out_names.append(path.name)
        out_texts.append(f'passage: {" ".join(pieces)}')

    def summarise(vectors):
        scores = vectors @ prototypes.T
        rows = []
        for row in scores:
            best = {label: row[owners == label].max() for label in labels}
            ranked = sorted(best.items(), key=lambda kv: -kv[1])
            rows.append((ranked[0][0], ranked[0][1], ranked[0][1] - ranked[1][1]))
        return rows

    inside = summarise(encoder.encode(in_texts))
    outside = summarise(encoder.encode(out_texts))

    print('IN-DISTRIBUTION (corpus documents)')
    print(f'  top similarity: mean {np.mean([r[1] for r in inside]):.4f}')
    print(f'  margin:         mean {np.mean([r[2] for r in inside]):.4f}')

    print('\nREAL DOWNLOADS (papers, RFCs, scans)')
    print(f'  top similarity: mean {np.mean([r[1] for r in outside]):.4f}')
    print(f'  margin:         mean {np.mean([r[2] for r in outside]):.4f}')
    for name, (label, top, margin) in zip(out_names, outside):
        print(f'    {name:42s} -> {label:16s} sim={top:.4f} margin={margin:.4f}')

    # The headline: how many of these landed on the label they should have.
    # Everything in tools/corpus/real is a paper, an RFC or a scanned book, and
    # the first two are what the `paper` category was added for.
    recognised = sum(1 for label, _, _ in outside if label == 'paper')
    misfiled = [(name, label) for name, (label, _, _) in zip(out_names, outside)
                if label != 'paper']
    print(f'\nrecognised as papers: {recognised}/{len(outside)}')
    for name, label in misfiled:
        print(f'  MISFILED {name} -> {label}')

    # Above this the sort acts; below it the document goes to review. Being
    # right but unsure is a mild annoyance, so it is reported rather than
    # treated as a failure.
    floor = 0.80
    unsure = [(name, top) for name, (_, top, _) in zip(out_names, outside) if top < floor]
    print(f'confident enough to file (sim >= {floor}): {len(outside) - len(unsure)}/{len(outside)}')
    for name, top in unsure:
        print(f'  to review {name} sim={top:.4f}')

    in_top = np.array([r[1] for r in inside])
    out_top = np.array([r[1] for r in outside])
    print(f'\ncorpus similarity:  min {in_top.min():.4f}')
    print(f'real similarity:    max {out_top.max():.4f}')
    print('These overlap by design now that papers have a category — see the '
          'note at the top of this file.')


if __name__ == '__main__':
    main()
