# Dewey

An Android document app that finds the file you can't name.

Everyone's Downloads folder has a `2847373.pdf` in it. You remember it was the
electricity bill from the month the meter was replaced. You do not remember that
it is called `2847373.pdf`. Dewey is built around that gap.

**Status:** in development for RevenueCat Shipaton 2026. See
[What's not done](#whats-not-done) for the honest state of things.

---

## What it does

**Free, with no ads and no account.**

- Document scanner with edge detection, perspective correction, multi-page capture
- OCR over scanned pages
- PDF viewer
- PDF toolkit: merge, split, rotate, reorder, compress
- Local search across everything you've stored

**The Librarian — a paid tier, gated by a RevenueCat entitlement.**

- **Sort.** Point it at a folder. It reads every PDF, works out what each one is,
  creates folders, and moves them. Files it isn't sure about go to a review queue
  rather than interrupting you four hundred times.
- **Find.** Ask for a document by what it was about, not what it was named.
- **Bills.** Detected bills and invoices, with vendor, amount and due date pulled
  out, plus reminders and notes. Opens the source PDF from the entry.
- **Travel.** Boarding passes and bookings, dates extracted, reminders set.
- **Summarize.** For the long ones.

The free tier is deliberately a complete app. The paywall is the agent and
nothing else.

---

## Architecture

### Storage: SAF, not `MANAGE_EXTERNAL_STORAGE`

Dewey uses the Storage Access Framework's document-tree picker. You grant it one
folder; it gets that folder.

The alternative, `MANAGE_EXTERNAL_STORAGE`, is easier to write against — real
file paths, no permission dance — and since this app is not shipping to Play,
its restrictions would not have bound us. We still didn't use it. Asking for
read-write access to the whole device in order to tidy one folder is the wrong
trade for the user, and a document app that demands it has answered a design
question by avoiding it.

The practical consequence, which shapes a lot of the code: **SAF hands back
content URIs, not file paths.** Nothing in the app may assume a `File`.

### Retrieval: embeddings computed on-device

Every document is embedded once at import, on the phone, using
`multilingual-e5-small` through ONNX Runtime Android. Vectors persist locally and
are never recomputed per query.

Multilingual is a requirement rather than a nicety here. The corpus this is built
for is Moroccan: French, Arabic and English, often mixed inside one document. An
English-only encoder would fail most of it.

Computing embeddings on-device costs APK size and a couple of seconds per
document at import. It buys the thing that makes the privacy claim below true
rather than aspirational: the corpus never leaves the phone in order to become
searchable.

### What actually leaves your device

Stated precisely, because vague privacy claims are worse than none:

- **Document text stays local by default.** Import, OCR, embedding, indexing and
  retrieval all run on the phone.
- **Retrieved chunks go to a cloud model.** When you ask a question, the passages
  retrieved for it are sent to Gemini via Firebase AI Logic to compose an answer.
  Not your corpus — the passages that matched.
- **Files needing field extraction go to the same model.** Pulling a vendor,
  amount and due date off a bill sends that document's text.
- **Purchases go through RevenueCat**, which is how the entitlement is checked.

This is not an offline app and it does not claim to be.

### Cloud access: Firebase AI Logic

The repo is public, so an API key cannot live in it. Firebase AI Logic proxies
requests and attests the caller with App Check, so no key is committed and there
is no separate backend to deploy or keep alive.

### Long-running work

Sorting four hundred files is not a screen's job. Batch sort, indexing and
exports run in a WorkManager job owned at the application level. Screens observe
progress; they do not own the work. Closing the screen does not cancel the sort,
and cancelling actually reaches the running task.

---

## Build

Requires JDK 17 and the Android SDK. Both live under `$HOME` in this setup — no
root, nothing installed system-wide:

```
git clone <this repo> && cd document-archive
. scripts/env.sh          # JAVA_HOME, ANDROID_HOME, PATH
./gradlew assembleDebug
```

`scripts/env.sh` honours `JAVA_HOME` and `ANDROID_HOME` if you already have them
set, so it will not fight an existing Android Studio install.

If you need the SDK from scratch, the command-line tools bootstrap themselves:

```
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip -q commandlinetools-*.zip && mv cmdline-tools latest
latest/bin/android sdk install platform-tools "platforms;android-36" "build-tools;36.0.0"
```

Firebase configuration is not committed. To run the Librarian tier you need your
own `google-services.json` in `app/` — see [docs/firebase-setup.md](docs/firebase-setup.md).
The free tier builds and runs without it.

### The encoder

The encoder is not committed — 113MB of third-party weights do not belong in git
history. Fetch and prepare it before building or running the tests:

```
tools/eval/fetch_model.sh
python tools/model/prepare_assets.py --model-dir tools/eval/model
```

That downloads `Xenova/multilingual-e5-small` (an ONNX export of
`intfloat/multilingual-e5-small`, MIT licensed), packs its 250k-entry
SentencePiece vocabulary into a compact binary the app can load without parsing
17MB of JSON at startup, and writes the fixtures the tokenizer is tested against.

The Kotlin tokenizer is checked token-for-token against HuggingFace `tokenizers`
on French, Arabic, mixed-script, emoji and malformed input. This matters more
than it looks: a tokenizer that is subtly wrong never crashes, it just quietly
produces slightly wrong vectors and slightly worse search, forever.

### The test corpus

`tools/corpus/` generates the archive this app is developed and evaluated
against: 100 Moroccan documents in French, Arabic and English — utility bills,
bank statements, rental contracts, invoices, medical letters, transcripts,
insurance policies, tax forms — with the uninformative filenames real archives
actually contain (`Scan_20240312_004.pdf`, `Nouveau document 7.pdf`).

It is confusable on purpose. Six consecutive months of Lydec bills, six
consecutive Attijariwafa statements, repeat visits to the same clinic. Retrieval
that only works on a corpus of obviously-different documents measures nothing.

Every generated document is round-trip verified: text is extracted back out of
the finished PDF and checked against the source, gated at 0.92 token recall.
Arabic in particular loses content at direction boundaries, and a document whose
text didn't survive would quietly corrupt the evaluation.

```
python -m venv .venv && ./.venv/bin/pip install -r tools/corpus/requirements.txt
./.venv/bin/python tools/corpus/generate_corpus.py
```

Writes `tools/corpus/corpus/` and a `ground_truth.json` answer key.

---

## What's not done

Kept current and honest.

- [ ] Stage 1 — scanner, storage, index
- [ ] Stage 2 — Librarian: find
- [ ] Stage 3 — Librarian: sort
- [ ] Stage 4 — bills dashboard
- [ ] Stage 5 — RevenueCat paywall
- [ ] Stage 6 — PDF toolkit

Done: the evaluation corpus and its ground truth.

---

## Licence

MIT. See [LICENSE](LICENSE).
