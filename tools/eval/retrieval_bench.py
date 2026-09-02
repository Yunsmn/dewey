"""
Measures whether the on-device retrieval design actually finds the right file.

The app cannot be the first place this is tested. Chunk size, the e5 query/passage
prefixes and the choice of encoder are all decisions that are cheap to change here
and expensive to change once they are Kotlin, an ONNX asset and a populated
database on a phone.

Queries are the ground-truth descriptions: "Lydec electricity and water bill for
janvier 2023, Casablanca" should return the file that actually is that bill. The
corpus is built to be confusable, so six consecutive Lydec months compete for
every Lydec query — which is the point.
"""

import argparse
import json
import re
from pathlib import Path

import numpy as np
import onnxruntime as ort

from date_expansion import augment
from hybrid import Bm25, reciprocal_rank_fusion
from pypdf import PdfReader
from tokenizers import Tokenizer

CORPUS = Path('tools/corpus/corpus')
GROUND_TRUTH = Path('tools/corpus/ground_truth.json')

# Mirrors app/src/main/kotlin/app/dewey/index/Chunker.kt. If these drift, the
# benchmark stops describing the app.
TARGET_SIZE = 900
OVERLAP = 150
MAX_TOKENS = 512


def chunk(text: str) -> list[str]:
    text = re.sub(r'\s+', ' ', text).strip()
    if not text:
        return []
    if len(text) <= TARGET_SIZE:
        return [text]

    chunks, start = [], 0
    while start < len(text):
        hard_end = min(start + TARGET_SIZE, len(text))
        if hard_end == len(text):
            end = hard_end
        else:
            floor = max(start + TARGET_SIZE - 250, start + 1)
            end = hard_end
            for i in range(hard_end - 1, floor - 1, -1):
                if text[i] in '.!?۔؟\n':
                    end = i + 1
                    break
            else:
                for i in range(hard_end - 1, floor - 1, -1):
                    if text[i] == ' ':
                        end = i + 1
                        break
        piece = text[start:end].strip()
        if piece:
            chunks.append(piece)
        if end >= len(text):
            break
        start = max(end - OVERLAP, start + 1)
    return chunks


class Encoder:
    def __init__(self, model_dir: Path):
        self.tokenizer = Tokenizer.from_file(str(model_dir / 'tokenizer.json'))
        self.tokenizer.enable_truncation(MAX_TOKENS)
        self.session = ort.InferenceSession(
            str(model_dir / 'model_quantized.onnx'),
            providers=['CPUExecutionProvider'],
        )
        self.inputs = {i.name for i in self.session.get_inputs()}

    def encode(self, texts: list[str], batch: int = 16) -> np.ndarray:
        out = []
        for start in range(0, len(texts), batch):
            out.append(self._encode_batch(texts[start:start + batch]))
        return np.vstack(out)

    def _encode_batch(self, texts: list[str]) -> np.ndarray:
        encoded = self.tokenizer.encode_batch(texts)
        width = max(len(e.ids) for e in encoded)

        ids = np.zeros((len(encoded), width), dtype=np.int64)
        mask = np.zeros((len(encoded), width), dtype=np.int64)
        for row, item in enumerate(encoded):
            ids[row, :len(item.ids)] = item.ids
            mask[row, :len(item.attention_mask)] = item.attention_mask

        feed = {'input_ids': ids, 'attention_mask': mask}
        if 'token_type_ids' in self.inputs:
            feed['token_type_ids'] = np.zeros_like(ids)

        hidden = self.session.run(None, feed)[0]

        # Mean pooling over real tokens only. Averaging padding in is a classic
        # way to make every long document look alike.
        expanded = mask[..., None].astype(np.float32)
        pooled = (hidden * expanded).sum(axis=1) / np.clip(expanded.sum(axis=1), 1e-9, None)
        return pooled / np.clip(np.linalg.norm(pooled, axis=1, keepdims=True), 1e-9, None)


def read_pdf(path: Path) -> str:
    return '\n'.join(page.extract_text() or '' for page in PdfReader(str(path)).pages)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--model-dir', type=Path, required=True)
    parser.add_argument('--limit', type=int, default=0, help='only the first N documents')
    parser.add_argument('--hybrid', action='store_true', help='fuse dense with BM25')
    parser.add_argument('--dates', action='store_true', help='expand dates at index time')
    args = parser.parse_args()

    truth = json.loads(GROUND_TRUTH.read_text())
    if args.limit:
        truth = truth[:args.limit]

    encoder = Encoder(args.model_dir)

    passages, owners = [], []
    for entry in truth:
        text = read_pdf(CORPUS / entry['filename'])
        for piece in chunk(text):
            indexed = augment(piece) if args.dates else piece
            passages.append(f'passage: {indexed}')
            owners.append(entry['filename'])

    print(f'{len(truth)} documents, {len(passages)} chunks')
    vectors = encoder.encode(passages)
    owners = np.array(owners)

    queries = [f'query: {entry["description"]}' for entry in truth]
    wanted = [entry['filename'] for entry in truth]
    query_vectors = encoder.encode(queries)

    scores = query_vectors @ vectors.T
    order = np.argsort(-scores, axis=1)

    bm25 = Bm25([p.removeprefix('passage: ') for p in passages]) if args.hybrid else None

    hits = {1: 0, 3: 0, 5: 0}
    misses = []
    for row, target in enumerate(wanted):
        if bm25 is not None:
            lexical = np.argsort(-np.array(bm25.scores(truth[row]['description'])))
            fused = reciprocal_rank_fusion([list(order[row][:50]), list(lexical[:50])])
            columns = [c for c, _ in sorted(fused.items(), key=lambda kv: -kv[1])]
        else:
            columns = list(order[row])

        ranked, seen = [], set()
        for column in columns:
            owner = owners[column]
            if owner not in seen:
                seen.add(owner)
                ranked.append(owner)
            if len(ranked) >= 5:
                break
        for k in hits:
            if target in ranked[:k]:
                hits[k] += 1
        if target != ranked[0]:
            misses.append((target, ranked[0], truth[row]['description']))

    total = len(wanted)
    print()
    for k in sorted(hits):
        print(f'recall@{k}: {hits[k]}/{total} = {hits[k] / total:.1%}')

    if misses:
        print(f'\n{len(misses)} not ranked first:')
        for target, got, description in misses[:12]:
            print(f'  want {target:38s} got {got:38s} | {description[:60]}')


if __name__ == '__main__':
    main()
