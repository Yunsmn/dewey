# Firebase setup

Dewey reaches Gemini through **Firebase AI Logic**. The reason is the repo: it is
public, so an API key cannot live in it. Firebase proxies the request and App
Check attests that the call came from this app, so no key is ever committed and
there is no separate backend to deploy or keep alive.

The free tier covers the whole app. **Do not enable billing** — the Vertex AI
backend requires it and we are not using that one.

Everything below is one-time, takes about ten minutes, and only the Librarian
features need it. The free tier of the app builds and runs without any of this.

---

## Values you will be asked for

| Field | Value |
|---|---|
| Android package name | `app.dewey` |
| Debug SHA-1 | `32:63:6F:4B:C6:F7:92:8E:82:3E:FA:6D:F8:A4:2C:55:D6:85:A3:39` |
| Debug SHA-256 | `93:75:3C:A5:25:10:8F:D7:B7:D8:19:73:40:B8:68:B6:AD:4A:B1:60:82:8D:47:59:4A:B5:3C:7B:1E:DE:4D:53` |

Those fingerprints are from the standard Android debug keystore at
`~/.android/debug.keystore`. They are not secret — a debug keystore is shared by
every debug build on the machine — but they are specific to this machine, so
regenerate them elsewhere with:

```
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore \
  -storepass android -keypass android
```

---

## 1. Create the project

1. Open <https://console.firebase.google.com> and sign in.
2. **Create a project**. Name it `Dewey`.
3. Google Analytics: **turn it off**. Nothing in the app uses it, and leaving it
   on adds a consent surface we would then have to justify in the README.
4. Wait for provisioning, then **Continue**.

## 2. Register the Android app

1. On the project overview, click the **Android** icon ("Add app").
2. **Android package name:** `app.dewey` — exactly, no trailing spaces. It must
   match `applicationId` in `app/build.gradle.kts` or nothing will authenticate.
3. **App nickname:** `Dewey debug`.
4. **Debug signing certificate SHA-1:** paste the SHA-1 from the table above.
5. **Register app**.

## 3. Download the config file

1. Download **`google-services.json`**.
2. Put it at **`app/google-services.json`** in this repo — the `app/` directory,
   not the root.
3. It is already in `.gitignore` and must stay there. It is not a secret in the
   password sense, but it names your project and is yours rather than the repo's.
4. Skip the console's "add the SDK" Gradle instructions — that wiring is already
   in the project.

## 4. Turn on Firebase AI Logic

Direct link, which is easier than hunting the sidebar:

**<https://console.firebase.google.com/project/dewey-212ac/ailogic>**

By hand it lives under **AI Services → AI Logic** — *not* under "Build", where
Firebase's other product areas sit.

1. Click **Get started**. It runs a guided workflow rather than a single toggle.
2. When it asks for a provider, choose **Gemini Developer API** — the one
   described as letting you "get started quickly at no cost".
3. Do **not** choose **Agent Platform Gemini API**. That is the product formerly
   called Vertex AI; it was renamed, and it requires a billing account.
4. Let the workflow enable the APIs it asks for.

> The workflow **registers the app with App Check** but leaves enforcement off —
> the console shows `Registered (Unenforced)`. Nothing is blocked in that state,
> which is why the app works without an App Check provider. See step 5 before
> turning enforcement on.

Afterwards, re-download `google-services.json` and replace `app/google-services.json`.
Check your browser did not save it as `google-services (1).json` beside the old
one; that has already happened once.

## 5. App Check: not wired up

**The app does not install an App Check provider.** There is no `firebase-appcheck`
dependency and no `FirebaseAppCheck.getInstance().installAppCheckProviderFactory(...)`
call anywhere in `app/src`. This section says so plainly because an earlier draft
claimed the opposite, and following those instructions produced a debug token
that never appeared in logcat.

That is fine while enforcement is off. **If you turn enforcement on, every cloud
answer will start failing** — the app has nothing to attest with. Turning it on
means three changes first:

1. Add `firebase-appcheck-playintegrity` and `firebase-appcheck-debug` to
   `gradle/libs.versions.toml` and `app/build.gradle.kts`.
2. Install the debug provider for debug builds and Play Integrity for release,
   in `DeweyApplication.onCreate`, guarded by `BuildConfig.HAS_FIREBASE`.
3. Run the app once, read the token out of logcat
   (`adb logcat -d | grep -A2 DebugAppCheckProvider`), and register it under
   **Security → App Check → Apps → ⋮ → Manage debug tokens**. That token grants
   quota to whoever holds it, so it is per-machine and must never be committed.

Play Integrity cannot attest an emulator at all, which is what the debug provider
is for.

## 6. Confirm

```
ls -l app/google-services.json
. scripts/env.sh && ./gradlew assembleDebug
```

The build should succeed and the Librarian features should stop reporting that
the cloud model is unconfigured.

---

## What actually gets sent

Worth being precise about, since it is the claim the README makes:

- **Not** your documents. Import, OCR, embedding, indexing, retrieval and
  classification all run on the phone.
- When you ask a question, the **passages retrieved for it** are sent so the
  model can compose an answer from them.
- When a bill needs a vendor, amount and due date pulled out, **that document's
  text** is sent.
- Purchases go through RevenueCat, separately.

Sorting sends nothing at all: classification is a nearest-neighbour lookup
against on-device embeddings.
