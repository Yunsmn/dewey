"""
What does a real category look like, in the shape of its own documents?

Learning categories from the user's folders needs a way to tell a real one from
a junk drawer. "Voiture" holding six car documents should become a category;
"Misc" holding six unrelated ones must not, or it swallows everything.

The signal is cohesion: how similar a folder's documents are to each other. This
measures it on folders we know the answer for — the corpus's own categories are
real, and a folder assembled from random categories is a junk drawer — and prints
the gap the threshold has to sit in.
"""

import argparse
import json
import random
from collections import defaultdict
from pathlib import Path

import numpy as np
from pypdf import PdfReader

from retrieval_bench import Encoder, chunk

CORPUS = Path('tools/corpus/corpus')
GROUND_TRUTH = Path('tools/corpus/ground_truth.json')


def cohesion(vectors: np.ndarray) -> float:
    """Mean pairwise cosine similarity, excluding each document with itself."""
    if len(vectors) < 2:
        return 1.0
    similarity = vectors @ vectors.T
    upper = similarity[np.triu_indices(len(vectors), k=1)]
    return float(upper.mean())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--model-dir', type=Path, required=True)
    parser.add_argument('--trials', type=int, default=40,
                        help='how many junk folders to assemble')
    args = parser.parse_args()

    encoder = Encoder(args.model_dir)
    truth = json.loads(GROUND_TRUTH.read_text())

    by_category = defaultdict(list)
    texts, owners = [], []
    for entry in truth:
        pieces = chunk('\n'.join(
            p.extract_text() or '' for p in PdfReader(str(CORPUS / entry['filename'])).pages
        ))[:2]
        texts.append(f'passage: {" ".join(pieces)}')
        owners.append(entry['category'])

    vectors = encoder.encode(texts)
    for vector, owner in zip(vectors, owners):
        by_category[owner].append(vector)

    print('REAL FOLDERS (corpus categories)')
    real = []
    for category, group in sorted(by_category.items()):
        if len(group) < 2:
            continue
        score = cohesion(np.array(group))
        real.append(score)
        print(f'  {category:18s} n={len(group):3d}  cohesion={score:.4f}')

    # A junk drawer: same size as a real folder, documents drawn from different
    # categories. This is the thing the threshold has to reject.
    print('\nJUNK DRAWERS (same sizes, mixed categories)')
    rng = random.Random(0)
    sizes = [len(g) for g in by_category.values() if len(g) >= 2]
    junk = []
    for _ in range(args.trials):
        size = rng.choice(sizes)
        picked = rng.sample(range(len(vectors)), min(size, len(vectors)))
        junk.append(cohesion(vectors[picked]))
    print(f'  n={len(junk)} folders  mean={np.mean(junk):.4f}  max={max(junk):.4f}')

    print(f'\nreal folders  : min {min(real):.4f}')
    print(f'junk drawers  : max {max(junk):.4f}')
    if max(junk) < min(real):
        print(f'separable — but only in ({max(junk):.4f}, {min(real):.4f}), '
              f'a gap of {min(real) - max(junk):.4f}')
    else:
        print('OVERLAP — cohesion alone cannot tell them apart')

    # Cohesion asks "is this folder a category". The question that actually
    # decides a filing is narrower: "does *this document* belong in *that*
    # folder". A junk drawer can produce one accidental close match; needing
    # several is far harder to hit by chance, so the score is the mean of the
    # top-k similarities rather than the single best.
    # An absolute similarity threshold is the wrong lens here, and measuring it
    # first was a mistake worth leaving recorded: everything in this corpus is a
    # short official document with dates and amounts, so a document's similarity
    # to the *wrong* folder routinely beats its similarity to the weakest case in
    # its own. Absolute scores do not separate.
    #
    # But the classifier never chooses by threshold. It takes the argmax across
    # categories and uses the threshold only to decide whether it is sure enough
    # to act. So the question that matters is whether the right folder ranks
    # first — leave-one-out, which is exactly the position a newly arrived
    # document is in.
    print('\n--- leave-one-out: does the right folder rank first? ---')
    for k in (1, 2, 3):
        correct = 0
        counted = 0
        margins = []
        for vector, owner in zip(vectors, owners):
            scored = {}
            for category, group in by_category.items():
                others = [g for g in group if not np.allclose(g, vector)]
                if len(others) < k:
                    continue
                sims = np.sort(np.array(others) @ vector)[::-1][:k]
                scored[category] = float(sims.mean())
            if owner not in scored:
                continue
            counted += 1
            ranked = sorted(scored.items(), key=lambda kv: -kv[1])
            if ranked[0][0] == owner:
                correct += 1
            if len(ranked) > 1:
                margins.append(ranked[0][1] - ranked[1][1])
        print(f'  k={k}: {correct}/{counted} = {correct / counted:.1%} '
              f'(mean margin {np.mean(margins):.4f})')


if __name__ == '__main__':
    main()
