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
  out, grouped by how soon they are due. Opens the source PDF from the entry.

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

There is a real cost, and it is worth stating rather than hiding. Since Android
11 the system refuses to grant a document tree over the `Download` root itself —
the picker says "Can't use this folder" and disables the button. The same applies
to the storage root and `Android/data`. Any *subfolder* of Downloads is granted
normally, so Dewey works on `Download/Statements` but cannot be pointed at
`Download` wholesale.

`MANAGE_EXTERNAL_STORAGE` would lift that restriction. It is not used here. The
platform is drawing a deliberate line around a folder full of everything a person
has ever downloaded, and an app that reads one folder does not need a key to all
of them — which is the same reasoning that chose SAF in the first place, so
honouring it when it is inconvenient is rather the point.

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
- **Sorting sends nothing.** Classification is a nearest-neighbour lookup against
  on-device embeddings, so filing four hundred documents makes no network call.
- **Purchases go through RevenueCat**, which is how the entitlement is checked.

This is not an offline app and it does not claim to be.

### Sorting runs on the phone

The obvious design sends every document to a cloud model to be labelled. Dewey
does not, because it does not need to: each document is already embedded for
search, and a category is another point in the same space, so a label is a
nearest-neighbour lookup against a short description of each kind of document.

Measured against the test corpus's own labels, that is **100% accurate** across
thirteen categories in French, Arabic and English. It costs nothing per file,
works with no network, and means sorting a folder sends nothing anywhere at all.

A real Downloads folder is not only admin, so `Papers` is one of the thirteen:
sixteen genuine arXiv papers and an RFC are all recognised as papers, fourteen of
them confidently enough to file. Adding it cost nothing on the corpus — still
100% — but it was worth checking, because a maths-heavy phrasing tried during
tuning pulled an English university transcript into Papers and was dropped for it.

It also declines to answer. A document has to clear 0.80 similarity against a
category before the sort will act on it; below that it is left exactly where it
is and surfaced for review rather than confidently filed somewhere wrong. Two of
those sixteen papers land there — right about what they are, not quite sure
enough to move. Erring high is deliberate: review is a mild annoyance, a
confident misfile costs trust in the whole feature.

Every move is written to an undo log as it happens, so an interrupted sort is
still reversible, and the button that reverses it sits next to the one that
starts it.

### Purchases: RevenueCat, on the Test Store

The paid tier is gated by a RevenueCat entitlement, and the paywall is
RevenueCat-hosted so its copy and prices come from the dashboard rather than
from a build.

**Purchases are simulated.** This entry is judged on a repo and a video and is
never going to a store, so it uses RevenueCat's Test Store: a self-contained
sandbox needing no Play Console and no real products. That is a deliberate
choice, not an unfinished one. RevenueCat refuses a Test Store key in a
non-debuggable build — rightly, since such a key earns nothing — which is why
there is a separate `demo` build type. The `release` variant carries no purchase
key at all, which is a stronger guarantee that a test key cannot reach a store
than remembering not to ship one.

A clone with no `revenuecat.properties` unlocks every feature rather than
locking them. There is nothing to buy without a purchase system, and a repo
someone clones to read should run.

### Cloud access: Firebase AI Logic

The repo is public, so an API key cannot live in it. Firebase AI Logic proxies
the request through Google's own credentials, which arrive in
`google-services.json` — a file this repo does not commit and does not need to
build. So no key is in the source, and there is no separate backend to deploy or
keep alive.

App Check would be the next thing to add here: it is what stops a copy of the
config file being used from somewhere that is not this app. It is not wired up
yet, and the app builds and runs without it.

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

- [x] Stage 1 — storage, extraction, on-device index. Verified on device against
      114 real documents, including a 1012-page scan with no text layer.
- [x] Stage 2 — find. Hybrid retrieval on device, with the cloud answer layer
      wired through Firebase AI Logic.
- [x] Stage 3 — sort. Classification, folder creation, moves, review queue and
      undo, confirmed end to end on a signed build: 96 of 114 documents filed
      into 11 folders, the rest held back for review.
- [x] Stage 4 — bills dashboard
- [x] Stage 5 — RevenueCat paywall. Test Store, so purchases are simulated and
      earn nothing — see [Cloud access](#cloud-access-firebase-ai-logic) below
      for why that is deliberate rather than unfinished.
- [~] Stage 6 — PDF toolkit. In progress.

Measured, not asserted: retrieval is 81% recall@1 and 95% recall@3 over the test
corpus; classification is 100% over thirteen categories, and the app also learns
categories from folders you already keep — a folder of your own documents
describes them about three times more sharply than any description we wrote
(margin 0.089 against 0.027, leave-one-out). Every harness is in `tools/eval/`
and can be re-run.

---

## Licence

MIT. See [LICENSE](LICENSE).
