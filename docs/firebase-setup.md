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

1. In the left sidebar: **Build → AI Logic** (it may read "Firebase AI Logic").
2. **Get started**.
3. When asked which backend, choose the **Gemini Developer API** — the option
   described as having a free tier. **Not** "Vertex AI Gemini API", which
   requires a billing account.
4. Accept the prompt to enable the required APIs. Firebase creates an API key
   scoped to this project; you never see or copy it, which is the point.

## 5. Turn on App Check

This is what stops anyone who finds the project id from spending your quota.

1. Sidebar: **Build → App Check**.
2. Under **Apps**, find `app.dewey` and click it.
3. Register the **Play Integrity** provider. Accept the defaults.
4. Leave enforcement **off** for now. Turning it on before a debug token exists
   locks out your own emulator.

### Debug token, for the emulator

Play Integrity cannot attest an emulator, so debug builds use a debug provider.

1. Run the app once on the emulator with `google-services.json` in place.
2. In logcat, find a line from `DebugAppCheckProvider` containing a UUID:
   ```
   adb logcat -d | grep -i "DebugAppCheckProvider"
   ```
3. Copy the UUID.
4. Console: **App Check → Apps → app.dewey → ⋮ → Manage debug tokens → Add
   debug token**. Paste it, name it `emulator`.

Do not commit that token. It is per-machine and grants quota.

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
