"""
Adds lexical scoring to dense retrieval and measures what it buys.

Dense embeddings are strong on topic and weak on exact tokens. In this corpus
that is not an academic quibble: nearly every document has five siblings that
differ only by month or amount, so "Attijariwafa statement for janvier 2024"
retrieves a bank statement reliably and the *right* bank statement unreliably.

BM25 has the opposite bias — it is unbeatable on "janvier" and blind to
paraphrase. Fusing the two is the standard answer, and the numbers below say
whether it is worth the extra index on device.
"""

import math
import re
from collections import Counter


TOKEN = re.compile(r'\w+', re.UNICODE)


def tokenise(text: str) -> list[str]:
    return TOKEN.findall(text.lower())


class Bm25:
    """Textbook BM25 over the same chunks the dense index holds."""

    def __init__(self, documents: list[str], k1: float = 1.5, b: float = 0.75):
        self.k1, self.b = k1, b
        self.docs = [tokenise(d) for d in documents]
        self.lengths = [len(d) for d in self.docs]
        self.avg_length = sum(self.lengths) / max(len(self.docs), 1)
        self.term_frequencies = [Counter(d) for d in self.docs]

        document_frequency = Counter()
        for doc in self.docs:
            document_frequency.update(set(doc))

        total = len(self.docs)
        self.idf = {
            term: math.log(1 + (total - freq + 0.5) / (freq + 0.5))
            for term, freq in document_frequency.items()
        }

    def scores(self, query: str) -> list[float]:
        terms = tokenise(query)
        out = []
        for index, frequencies in enumerate(self.term_frequencies):
            length = self.lengths[index]
            total = 0.0
            for term in terms:
                if term not in frequencies:
                    continue
                tf = frequencies[term]
                denominator = tf + self.k1 * (1 - self.b + self.b * length / self.avg_length)
                total += self.idf.get(term, 0.0) * tf * (self.k1 + 1) / denominator
            out.append(total)
        return out


def reciprocal_rank_fusion(rankings: list[list[int]], k: int = 60) -> dict[int, float]:
    """
    Combines rankings by position rather than score.

    Deliberately not a weighted sum of the raw scores: BM25 is unbounded and
    cosine lives in [-1, 1], so blending them directly means inventing a scale
    factor and then tuning it against this corpus until it overfits. Rank
    fusion has one parameter and no units.
    """
    fused: dict[int, float] = {}
    for ranking in rankings:
        for position, item in enumerate(ranking):
            fused[item] = fused.get(item, 0.0) + 1.0 / (k + position + 1)
    return fused
