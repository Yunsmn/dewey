"""
Turns the HuggingFace tokenizer into something an app can load quickly, and
writes the fixtures the Kotlin tokenizer is tested against.

tokenizer.json is 17MB of JSON holding 250k [token, score] pairs. Parsing that
at startup costs seconds and a large transient allocation, on the main thread of
an app whose whole promise is that search feels instant. The binary form below
is read with sequential bulk reads and no object churn.

Format (little-endian throughout):

    magic       4 bytes   "DWT1"
    unk_id      int32     id substituted for unmatched text
    max_len     int32     longest token in bytes; bounds the Viterbi lookahead
    count       int32     number of vocabulary entries
    then count times, in id order:
        score   float32
        length  uint16    token length in UTF-8 bytes
        token   bytes

Fixtures are the real defence. A tokenizer that is subtly wrong does not crash —
it silently returns worse search results forever. So the Kotlin implementation is
checked token-for-token against this one on text that actually exercises the
corpus: French accents, Arabic, mixed scripts, digits, punctuation.
"""

import argparse
import json
import struct
from pathlib import Path

from tokenizers import Tokenizer

MAGIC = b'DWT1'

# Chosen to exercise what the corpus and its queries actually contain, plus the
# inputs most likely to crash a hand-written tokenizer.
FIXTURE_TEXTS = [
    '',
    ' ',
    '\n\t  \n',
    'a',
    'Facture Lydec',
    'query: Lydec electricity and water bill for janvier 2023, Casablanca',
    'passage: Montant total a payer : 281.26 MAD',
    'Attijariwafa Bank releve de compte fevrier 2024',
    'المكتب الوطني للكهرباء والماء الصالح للشرب',
    'فاتورة الكهرباء لشهر نونبر 2024',
    'Universite Mohammed VI Polytechnique, Benguerir',
    'Bill: 1.234,56 MAD — due 03/04/2024',
    'Mixed العربية and francais and English in one line',
    'Ø±ÙˆØ¨Ø±Øª mojibake test',
    'ß‡ ß‡ ß‡',
    'a' * 600,
    'مرحبا' * 120,
    '🙂 emoji and 🇲🇦 flags',
    'CamScanner 07-16-2021 17.06',
    'ONEE - Office National de l\'Electricite et de l\'Eau Potable',
    'ligne\navec\nsauts',
    '   leading and trailing   ',
    'ÀÉÎÔÜ àéîôü ç',
    '0123456789',
    '!@#$%^&*()_+-=[]{}|;:\'",.<>/?`~',
]


def write_vocab(tokenizer_json: Path, destination: Path) -> tuple[int, int]:
    data = json.loads(tokenizer_json.read_text())
    model = data['model']
    if model['type'] != 'Unigram':
        raise SystemExit(f"Expected a Unigram model, found {model['type']}")

    vocab = model['vocab']
    unk_id = int(model.get('unk_id', 0))

    encoded = [(token.encode('utf-8'), float(score)) for token, score in vocab]
    max_len = max(len(b) for b, _ in encoded)

    with destination.open('wb') as out:
        out.write(MAGIC)
        out.write(struct.pack('<iii', unk_id, max_len, len(encoded)))
        for token_bytes, score in encoded:
            out.write(struct.pack('<fH', score, len(token_bytes)))
            out.write(token_bytes)

    return len(encoded), max_len


def write_fixtures(tokenizer_json: Path, destination: Path) -> int:
    tokenizer = Tokenizer.from_file(str(tokenizer_json))
    cases = []
    for text in FIXTURE_TEXTS:
        encoding = tokenizer.encode(text)
        cases.append({'text': text, 'ids': encoding.ids, 'tokens': encoding.tokens})
    destination.write_text(json.dumps(cases, ensure_ascii=False, indent=2))
    return len(cases)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--model-dir', type=Path, required=True)
    parser.add_argument('--assets', type=Path, default=Path('app/src/main/assets/models'))
    parser.add_argument('--fixtures', type=Path, default=Path('app/src/test/resources'))
    args = parser.parse_args()

    args.assets.mkdir(parents=True, exist_ok=True)
    args.fixtures.mkdir(parents=True, exist_ok=True)

    count, max_len = write_vocab(args.model_dir / 'tokenizer.json', args.assets / 'tokenizer.bin')
    size_mb = (args.assets / 'tokenizer.bin').stat().st_size / 1e6
    print(f'vocab: {count} entries, longest token {max_len} bytes, {size_mb:.1f}MB')

    onnx_source = args.model_dir / 'model_quantized.onnx'
    onnx_target = args.assets / 'encoder.onnx'
    onnx_target.write_bytes(onnx_source.read_bytes())
    print(f'encoder: {onnx_target.stat().st_size / 1e6:.1f}MB')

    fixtures = write_fixtures(args.model_dir / 'tokenizer.json', args.fixtures / 'tokenizer_fixtures.json')
    print(f'fixtures: {fixtures} cases')


if __name__ == '__main__':
    main()
