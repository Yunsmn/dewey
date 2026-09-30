# Dewey: Devpost submission text

Paste-ready. Every number here is measured and reproducible from `tools/eval/`.

---

## Elevator pitch

Dewey finds the file you cannot name. One tap reads every PDF on your phone,
files them into folders it creates, and answers questions about them. The
scanner and fourteen PDF tools are free.

---

## About the project

## Inspiration

Everyone has a Downloads folder like mine. Somewhere in mine is my rent
contract, three months of bills, and the medical note the university asked me
for twice. Every one of them is called something like `document (5).pdf` or
`IMG_4431.pdf`.

In Morocco it is worse than usual. Bills, bank statements and leases arrive in
French, Arabic and English, often mixed inside one page, as scans and WhatsApp
forwards named `WhatsApp Doc 2023-10-22 at 07.05.19.pdf`. Every file manager
searches names. Nothing searches what is actually inside the document, and
nothing files it for you.

I wanted the folder to sort itself, without any of it leaving my phone.

## What it does

**Free, with no ads and no account.** A real document scanner with edge
detection, perspective correction and OCR, plus fourteen PDF tools: merge,
split, extract, rotate, reorder and delete pages, sign, compress, add or remove
a password, watermark, page numbers, and PDF to and from images. The page tools
open a document as thumbnails you drag, so you never type a page number.

**Librarian, the paid tier:**

- **Sort.** Point Dewey at a folder and tap once. It reads every PDF, works out
  what each one is, creates folders and files them. Anything it is not sure
  about goes to a review queue instead of being guessed at, and the whole sort
  can be undone.
- **Ask.** "When is my Lydec bill due and how much is it?" The answer comes out
  of the documents themselves, with the files it used shown beside it.
- **Bills and notes.** Detected bills with vendor, amount and due date, grouped
  by what is due first, plus notes of your own. Both have home screen widgets.

## How we built it

- **Kotlin, Jetpack Compose, Room, WorkManager.** Sorting four hundred files is
  a WorkManager job, not a screen's, so closing the app does not cancel it.
- **On-device embeddings.** `multilingual-e5-small` through ONNX Runtime, with a
  Kotlin SentencePiece tokenizer checked token for token against HuggingFace on
  French, Arabic, mixed script and malformed input.
- **Sorting is a nearest neighbour lookup**, not a cloud call. A category is
  another point in the same embedding space as the documents, so filing a whole
  folder sends nothing anywhere.
- **Storage Access Framework, not `MANAGE_EXTERNAL_STORAGE`.** You grant one
  folder and Dewey gets that folder, nothing else.
- **Gemini through Firebase AI Logic** composes answers from retrieved passages
  only, so no API key lives in the source.
- **RevenueCat** for the entire purchase path.

## How we used RevenueCat

Librarian is a single entitlement, `dewey_app_pro`, sold as monthly and annual
packages on the current offering.

- `Purchases.configure` at app start, with the signed in user's id as
  `appUserID`, so a returning subscriber is never briefly anonymous.
- `UpdatedCustomerInfoListener` plus an initial `awaitCustomerInfo`, so every
  screen reacts the moment the entitlement changes.
- **A custom paywall driven entirely by the offering.** Prices, the per month
  price, the "Save 44%" badge computed from the two real products, and trial
  wording all come from `awaitOfferings`. Nothing is hardcoded.
- `awaitPurchase` on the main thread, and `awaitRestore`, which only reports
  success when the entitlement actually came back.
- **An optional account.** Firebase email sign in calls `awaitLogIn(uid)`, so a
  purchase follows the person to a new phone; signing out calls `awaitLogOut`.
- **RevenueCat's Customer Center** for restores, cancellations and plan changes
  inside the app, plus the `managementURL` link.
- The paywall appears where the value is: on the locked Documents, Notes and
  assistant screens, which show what they would do before asking for anything.

It runs on RevenueCat's **Test Store** deliberately. This entry is judged on a
repo and a video, so a separate `demo` build type carries the test key and the
`release` variant carries no purchase key at all.

## Challenges we ran into

- **Arabic that looks right but reads wrong.** Text extraction loses content at
  direction boundaries. Every generated test document is round tripped and gated
  at 92% token recall, so a corrupted document cannot quietly skew the
  evaluation.
- **A tokenizer that is subtly wrong never crashes.** It just produces slightly
  worse vectors forever, which is why it is tested against the reference
  implementation rather than eyeballed.
- **Confident misfiles cost more than review.** The sort threshold sits high on
  purpose. A looser one pulled an English transcript into "Papers", so it was
  dropped.
- **Android refuses to grant the Download root** through SAF since Android 11.
  We kept SAF anyway and documented the trade rather than asking for the whole
  disk.
- **PDFBox's `importPage` already appends the page.** Wrapping it in `addPage`
  put every page in twice. Found on a real device, fixed, and pinned by tests
  that read the output PDFs back.

## Accomplishments that we're proud of

- On a live run in the demo, **100 unnamed documents became 11 folders in one
  tap**: 98 filed, 2 held for review rather than guessed at.
- Sorting makes **zero network calls**.
- **81% recall@1 and 95% recall@3** for search across three languages.
- Over **760 unit tests**, including PDFBox round trips on real documents.
- The free half is a complete app on its own: a scanner and fourteen PDF tools,
  no ads, no account.

## What we learned

The honest place for a paywall is the part that costs something to build and
saves someone an afternoon. The scanner and the PDF tools are a complete free
app. What Librarian sells is the reading.

## What's next for Dewey

App Check on the Firebase config, a Play Store listing with real products on the
same RevenueCat offering, and learning categories from the folders people
already keep. That last one is already measured: a folder of your own documents
describes them about three times more sharply than any description we wrote.

---

## Built with

kotlin · android · jetpack-compose · material-design · revenuecat · onnx-runtime
· onnx · firebase · firebase-ai-logic · gemini · ml-kit · pdfbox · room
· workmanager · datastore · kotlin-coroutines · jetpack-glance · sentencepiece
· storage-access-framework · firebase-auth · python · ffmpeg

## Try it out

- https://github.com/Yunsmn/dewey
- https://github.com/Yunsmn/dewey/releases/latest
