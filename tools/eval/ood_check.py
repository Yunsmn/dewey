"""
What does the classifier do with a document that is none of its categories?

The generated corpus only contains documents that belong somewhere, so a perfect
score on it says nothing about the case that actually fills a review queue: a
research paper, a manual, an RFC — something a person downloaded that is not a
bill, a contract or a receipt.

If those come back with a low margin, the review queue is doing real work. If
they come back confident and wrong, the sort silently files a physics paper under
"Insurance" and the user loses trust in one screen.
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

    print('\nOUT-OF-DISTRIBUTION (real papers, RFCs, scans)')
    print(f'  top similarity: mean {np.mean([r[1] for r in outside]):.4f}')
    print(f'  margin:         mean {np.mean([r[2] for r in outside]):.4f}')
    for name, (label, top, margin) in zip(out_names, outside):
        print(f'    {name:42s} -> {label:16s} sim={top:.4f} margin={margin:.4f}')

    # Can a single similarity threshold separate them? That is what decides
    # whether "I don't know" is expressible at all.
    in_top = np.array([r[1] for r in inside])
    out_top = np.array([r[1] for r in outside])
    print(f'\nin-distribution  similarity: min {in_top.min():.4f}')
    print(f'out-of-distribution similarity: max {out_top.max():.4f}')
    print('separable by similarity alone' if out_top.max() < in_top.min()
          else 'OVERLAP — a similarity floor alone cannot separate them')


if __name__ == '__main__':
    main()
