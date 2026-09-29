# Dewey: Devpost submission text

Paste-ready sections for the Devpost form. Every number here is measured and
reproducible from `tools/eval/`. Keep it that way: a judge who clones the repo
should find each claim true.

---

## Tagline (one line)

An Android document app that finds the file you can't name, and sorts your
whole Downloads folder on the phone.

## Inspiration

Everyone's Downloads folder has a `2847373.pdf` in it. You know it's the
electricity bill from the month the meter was replaced. You don't know it's
called `2847373.pdf`.

In Morocco this is worse than usual. Bills, bank statements and leases arrive in
French, Arabic and English, often mixed in one document, as scans and WhatsApp
forwards with names like `WhatsApp Doc 2023-10-22 at 07.05.19.pdf`. File
managers search names. Nothing searches what's inside, and nothing files it for you.

## What it does

**Free, with no ads and no account:** a real document scanner (edge detection,
perspective correction, OCR) and fourteen PDF tools, including merge, split,
drag-to-reorder page grids, sign, compress, password protect and watermark.
Every tool writes a new file and never touches the original.

**Librarian, the paid tier:**
- **Sort.** Point it at a folder and tap once. Dewey reads every PDF, works out
  what it is, creates folders and files it. Anything it isn't sure of goes to a
  review queue rather than being guessed, and the whole sort can be undone.
- **Ask.** "When is my Lydec bill due, and how much?" The answer comes from
  what's in your files, with the documents it used shown beside it.
- **Bills and notes.** Detected bills grouped by due date, notes pinned to
  them, and home-screen widgets for both.

## How we built it

- **Kotlin, Jetpack Compose, Room, WorkManager.** Sorting 400 files is a
  WorkManager job, not a screen's; closing the app doesn't cancel it.
- **On-device embeddings.** `multilingual-e5-small` through ONNX Runtime, with
  a Kotlin SentencePiece tokenizer checked token for token against HuggingFace
  on French, Arabic, mixed-script and malformed input.
- **Sorting is a nearest-neighbour lookup**, not a cloud call. A category is
  just another point in the same embedding space as the documents, so filing a
  folder sends nothing anywhere.
- **Storage Access Framework, not `MANAGE_EXTERNAL_STORAGE`.** You grant one
  folder, and Dewey gets that folder only.
- **Gemini through Firebase AI Logic** composes answers from the retrieved
  passages only. No API key is in the source.
- **RevenueCat** for the entire purchase path (below).

## How we used RevenueCat

Librarian is a single entitlement, `dewey_app_pro`, sold as monthly and annual
packages on the current offering.

- `Purchases.configure` at app start, with the **signed-in user's id** as
  `appUserID`, so a returning subscriber is never briefly anonymous.
- `UpdatedCustomerInfoListener` plus an initial `awaitCustomerInfo`, so every
  screen reacts the moment the entitlement changes.
- **A custom paywall driven entirely by the offering.** Prices, the per-month
  price, the "Save N%" badge (computed from the two real products) and trial
  wording all come from `awaitOfferings`. Nothing is hardcoded.
- `awaitPurchase` on the main thread; `awaitRestore`, which only reports success
  when the entitlement actually came back.
- **An optional account.** Firebase email sign-in calls `awaitLogIn(uid)`, so
  the purchase follows the person to a new phone; signing out calls `awaitLogOut`.
- **RevenueCat's Customer Center** (`purchases-ui`) for restores, cancellations
  and plan changes inside the app, plus the `managementURL` link.
- The paywall appears **where the value is**: on the locked Documents, Notes and
  assistant screens, which show what they would do before asking for anything.

It runs on RevenueCat's **Test Store**, deliberately: this entry is judged on a
repo and a video, and a separate `demo` build type keeps the test key out of
the `release` variant entirely.

## Challenges we ran into

- **Arabic that looks right but reads wrong.** Text extraction loses content at
  direction boundaries. The test corpus round-trips every generated document
  and gates on 92% token recall, so a corrupted document can't quietly skew the evaluation.
- **A tokenizer that is subtly wrong never crashes.** It just produces slightly
  worse vectors forever, which is why it is tested against the reference
  implementation rather than eyeballed.
- **Confident misfiles cost more than review.** The sort threshold (0.80
  similarity) was set high on purpose; we measured that a looser one pulled an
  English transcript into "Papers".
- **Android refuses to grant the Download root** through SAF since Android 11.
  We kept SAF anyway and documented the trade instead of asking for the whole disk.

## Accomplishments we're proud of

- **97 of 100** test documents filed, **none wrong**; 3 held for review. On
  114 real documents on a signed build, 96 filed into 11 folders.
- **81% recall@1, 95% recall@3** for search across three languages.
- **Sorting makes zero network calls.**
- Over 760 unit tests, including PDFBox round trips on real documents.

## What we learned

The honest place for a paywall is the part that costs something to build and
saves an afternoon. The scanner and PDF tools are a complete free app. What
Librarian sells is the reading.

## What's next

App Check on the Firebase config, a Play Store listing with real products on
the same RevenueCat offering, and learning categories from the folders people
already keep (already measured: a user's own folder describes its documents
about three times more sharply than our descriptions do).

## Built with

kotlin · jetpack-compose · revenuecat · onnx-runtime · firebase · gemini ·
ml-kit · pdfbox · room · workmanager
