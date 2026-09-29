# Needs you

Things only you can do, most urgent first. Delete this file before merging to master.

## 1. Build and run the tests on your machine (blocking)

This cloud session could not compile anything. Its network policy blocks
`dl.google.com`, which Google's Maven repository (AGP, AndroidX, Firebase)
redirects to. Everything since `afb8c37` was written without a compiler, then
checked line by line by a separate review agent against the real RevenueCat and
PDFBox jars. It found no compile errors, but a real build is the proof:

```
. scripts/env.sh
./gradlew :app:testDemoUnitTest --max-workers=2
./gradlew installDemo
```

If anything fails, paste the error into the session and I'll fix it. To let
the cloud session build next time: open the environment menu in the session's
title bar → **Edit** → **Network access**, and allow `dl.google.com`.

## 2. Firebase console: enable Email/Password sign-in (2 minutes)

Authentication → Get started → Sign-in method → Email/Password → Enable.
The new optional account on the Me tab uses it. See docs/firebase-setup.md §6.

## 3. RevenueCat dashboard

- **Prices:** $39.99 yearly and $5.99 monthly on the Test Store products (from
  the handoff; the paywall reads them live).
- **Customer Center:** enable and configure it (Customer Center in the
  dashboard's sidebar). The new "Help with my subscription" button on the Me
  tab opens it, and it shows an error if the dashboard has no configuration.
- Restore behaviour: leave it on the default ("Transfer to new App User ID"),
  which is what signing in on a second phone expects.

## 4. Check the new things on the emulator (none have run on a screen)

- Page grids: drag in Reorder and confirm the saved order; select pages in
  Extract, Delete and Rotate.
- Sign: draw in **dark theme** (the ink used to be near-white, now fixed to a
  dark pen), place it on a normal page and on a **rotated** page. Both should
  be upright.
- Split, Merge with several files picked at once.
- Me tab: create an account, sign out, sign in, then Help with my subscription.
- Restore purchases with nothing bought should now say "No Librarian purchase
  was found" instead of "Purchases restored".

## 5. Record four clips for the demo video

Follow `demo/RECORDING.md`, drop the clips in `demo/footage/`, and run
`python demo/build_demo.py`. The full cut with narration, subtitles and every
card is already built (`demo/out/dewey-demo.mp4`). Only the four phone slots are
placeholders. The voice is Kokoro `af_heart`; try `--voice am_michael` (male,
American) or `bm_george` (male, British) if you prefer.

## 6. Devpost

Paste from `docs/SUBMISSION.md`. Use `docs/press/banner.png` as the cover image
and `docs/press/icon-1024.png` as the thumbnail.
